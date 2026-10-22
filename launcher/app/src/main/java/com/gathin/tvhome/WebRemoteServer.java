package com.gathin.tvhome;

import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.util.Log;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class WebRemoteServer {
    private static final String TAG = "WebRemoteServer";
    public static final int PORT = 8080;

    private static WebRemoteServer instance;
    private final Context context;
    private ServerSocket serverSocket;
    private Thread serverThread;
    private volatile boolean running = false;

    // Cache screenshot for 1.5 seconds so rapid requests don't hit CPU
    private volatile byte[] cachedScreen = null;
    private volatile long lastScreenTime = 0;

    // Sleep Timer (auto turn off TV)
    private static volatile long sleepTimerDeadline = 0;
    private static volatile Thread sleepTimerThread = null;

    private synchronized void setSleepTimer(int minutes) {
        cancelSleepTimer();
        if (minutes <= 0) return;
        sleepTimerDeadline = System.currentTimeMillis() + (minutes * 60 * 1000L);
        sleepTimerThread = new Thread(() -> {
            try {
                Log.i(TAG, "Sleep timer active: " + minutes + " minutes");
                while (System.currentTimeMillis() < sleepTimerDeadline) {
                    Thread.sleep(1000);
                }
                Log.i(TAG, "Sleep timer expired! Turning off TV...");
                // Send power off / standby
                AdbClient.getInstance(context).execute("input keyevent 26");
            } catch (InterruptedException ignored) {
            } finally {
                sleepTimerDeadline = 0;
            }
        }, "TvSleepTimerThread");
        sleepTimerThread.setDaemon(true);
        sleepTimerThread.start();
    }

    private synchronized void cancelSleepTimer() {
        if (sleepTimerThread != null) {
            sleepTimerThread.interrupt();
            sleepTimerThread = null;
        }
        sleepTimerDeadline = 0;
    }

    private long getSleepTimerRemainingSeconds() {
        if (sleepTimerDeadline <= 0) return 0;
        long rem = (sleepTimerDeadline - System.currentTimeMillis()) / 1000L;
        return Math.max(0, rem);
    }

    public static String getIpAddress(Context context) {
        try {
            if (context != null) {
                WifiManager wm = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                if (wm != null && wm.getConnectionInfo() != null) {
                    int ip = wm.getConnectionInfo().getIpAddress();
                    if (ip != 0) {
                        return String.format(Locale.US, "%d.%d.%d.%d",
                                (ip & 0xff), (ip >> 8 & 0xff), (ip >> 16 & 0xff), (ip >> 24 & 0xff));
                    }
                }
            }
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements(); ) {
                NetworkInterface intf = en.nextElement();
                for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements(); ) {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    if (!inetAddress.isLoopbackAddress() && inetAddress instanceof Inet4Address) {
                        return inetAddress.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {}
        return "192.168.0.33";
    }

    public static synchronized void start(Context context) {
        if (instance == null) {
            instance = new WebRemoteServer(context.getApplicationContext());
        }
        instance.startInternal();
    }

    public static synchronized void stop() {
        if (instance != null) {
            instance.stopInternal();
            instance = null;
        }
    }

    public static synchronized void toggle(Context context, boolean enable) {
        if (enable) {
            start(context);
        } else {
            stop();
        }
    }

    public static synchronized boolean isRunning() {
        return instance != null && instance.running;
    }

    private WebRemoteServer(Context context) {
        this.context = context;
    }

    private synchronized void startInternal() {
        if (running) return;
        running = true;
        serverThread = new Thread(() -> {
            try {
                serverSocket = new ServerSocket(PORT);
                serverSocket.setReuseAddress(true);
                Log.i(TAG, "Web Remote started on port " + PORT);
                while (running) {
                    try {
                        Socket client = serverSocket.accept();
                        client.setSoTimeout(5000);
                        new Thread(() -> handleClient(client)).start();
                    } catch (Exception e) {
                        if (!running) break;
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Web Remote server error: " + e.getMessage());
            } finally {
                running = false;
            }
        }, "WebRemoteThread");
        serverThread.setDaemon(true);
        serverThread.start();
    }

    private synchronized void stopInternal() {
        running = false;
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (Exception ignored) {}
        serverSocket = null;
        if (serverThread != null) {
            serverThread.interrupt();
            serverThread = null;
        }
        AdbClient adb = AdbClient.getInstance(context);
        if (adb != null) adb.close();
        cachedScreen = null;
        Log.i(TAG, "Web Remote stopped.");
    }

    private void handleClient(Socket socket) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             OutputStream out = socket.getOutputStream()) {

            String line = reader.readLine();
            if (line == null) return;
            String[] parts = line.split(" ");
            if (parts.length < 2) return;

            String path = parts[1];
            String uriPath = path;
            Map<String, String> params = new HashMap<>();

            int qIdx = path.indexOf('?');
            if (qIdx != -1) {
                uriPath = path.substring(0, qIdx);
                String query = path.substring(qIdx + 1);
                for (String pair : query.split("&")) {
                    String[] kv = pair.split("=");
                    if (kv.length == 2) {
                        params.put(URLDecoder.decode(kv[0], "UTF-8"), URLDecoder.decode(kv[1], "UTF-8"));
                    } else if (kv.length == 1) {
                        params.put(URLDecoder.decode(kv[0], "UTF-8"), "");
                    }
                }
            }

            if ("/".equals(uriPath) || "/index.html".equals(uriPath)) {
                byte[] html = getHtmlPage().getBytes("UTF-8");
                sendResponse(out, "200 OK", "text/html; charset=utf-8", html);
            } else if ("/api/screen".equals(uriPath)) {
                byte[] img = getScreenBytes();
                if (img != null && img.length > 0) {
                    sendResponse(out, "200 OK", "image/png", img);
                } else {
                    sendResponse(out, "503 Service Unavailable", "text/plain", "Screen unavailable".getBytes());
                }
            } else if ("/api/key".equals(uriPath)) {
                String code = params.get("code");
                handleKey(code);
                sendResponse(out, "200 OK", "application/json", "{\"status\":\"ok\"}".getBytes());
            } else if ("/api/text".equals(uriPath)) {
                String text = params.get("text");
                if (text != null && !text.isEmpty()) {
                    handleText(text);
                }
                sendResponse(out, "200 OK", "application/json", "{\"status\":\"ok\"}".getBytes());
            } else if ("/api/app".equals(uriPath)) {
                String pkg = params.get("pkg");
                if (pkg != null && !pkg.isEmpty()) {
                    launchApp(pkg);
                }
                sendResponse(out, "200 OK", "application/json", "{\"status\":\"ok\"}".getBytes());
            } else if ("/api/timer".equals(uriPath)) {
                String action = params.get("action");
                if ("set".equals(action)) {
                    String minsStr = params.get("minutes");
                    int mins = 0;
                    try { mins = Integer.parseInt(minsStr); } catch (Exception ignored) {}
                    setSleepTimer(mins);
                    long rem = getSleepTimerRemainingSeconds();
                    sendResponse(out, "200 OK", "application/json", ("{\"status\":\"ok\",\"remaining\":" + rem + "}").getBytes());
                } else if ("cancel".equals(action)) {
                    cancelSleepTimer();
                    sendResponse(out, "200 OK", "application/json", "{\"status\":\"cancelled\",\"remaining\":0}".getBytes());
                } else {
                    long rem = getSleepTimerRemainingSeconds();
                    sendResponse(out, "200 OK", "application/json", ("{\"status\":\"ok\",\"remaining\":" + rem + "}").getBytes());
                }
            } else {
                sendResponse(out, "404 Not Found", "text/plain", "Not Found".getBytes());
            }

        } catch (Exception ignored) {
        } finally {
            try {
                socket.close();
            } catch (Exception ignored) {}
        }
    }

    private void sendResponse(OutputStream out, String status, String contentType, byte[] data) throws Exception {
        String headers = "HTTP/1.1 " + status + "\r\n" +
                "Content-Type: " + contentType + "\r\n" +
                "Content-Length: " + data.length + "\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Connection: close\r\n\r\n";
        out.write(headers.getBytes("UTF-8"));
        out.write(data);
        out.flush();
    }

    private byte[] getScreenBytes() {
        long now = System.currentTimeMillis();
        if (cachedScreen != null && (now - lastScreenTime) < 1500) {
            return cachedScreen;
        }
        try {
            byte[] fresh = AdbClient.getInstance(context).screencap();
            if (fresh != null && fresh.length > 1000) {
                cachedScreen = fresh;
                lastScreenTime = now;
                return fresh;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting screen: " + e.getMessage());
        }
        return cachedScreen;
    }

    private void handleKey(String code) {
        if (code == null) return;
        switch (code.toUpperCase()) {
            case "HOME":
                try {
                    Intent home = new Intent(Intent.ACTION_MAIN);
                    home.addCategory(Intent.CATEGORY_HOME);
                    home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(home);
                } catch (Exception e) {
                    AdbClient.getInstance(context).execute("input keyevent 3");
                }
                break;
            case "BACK":
                AdbClient.getInstance(context).execute("input keyevent 4");
                break;
            case "UP":
                AdbClient.getInstance(context).execute("input keyevent 19");
                break;
            case "DOWN":
                AdbClient.getInstance(context).execute("input keyevent 20");
                break;
            case "LEFT":
                AdbClient.getInstance(context).execute("input keyevent 21");
                break;
            case "RIGHT":
                AdbClient.getInstance(context).execute("input keyevent 22");
                break;
            case "OK":
            case "ENTER":
                AdbClient.getInstance(context).execute("input keyevent 23");
                break;
            case "VOL_UP":
                adjustVolume(AudioManager.ADJUST_RAISE);
                break;
            case "VOL_DOWN":
                adjustVolume(AudioManager.ADJUST_LOWER);
                break;
            case "MUTE":
                adjustVolume(AudioManager.ADJUST_TOGGLE_MUTE);
                break;
            case "POWER":
                AdbClient.getInstance(context).execute("input keyevent 26");
                break;
            case "POWER_OFF":
                AdbClient.getInstance(context).execute("reboot -p || input keyevent --longpress 26");
                break;
            case "SLEEP":
                AdbClient.getInstance(context).execute("input keyevent 223");
                break;
            case "POWER_MENU":
                AdbClient.getInstance(context).execute("input keyevent --longpress 26");
                break;
            case "WAKE":
                AdbClient.getInstance(context).execute("input keyevent 224");
                break;
            default:
                if (code.startsWith("KEYCODE_") || code.matches("\\d+")) {
                    AdbClient.getInstance(context).execute("input keyevent " + code);
                }
                break;
        }
    }

    private void adjustVolume(int direction) {
        try {
            AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (am != null) {
                am.adjustSuggestedStreamVolume(direction, AudioManager.USE_DEFAULT_STREAM_TYPE, AudioManager.FLAG_SHOW_UI);
            }
        } catch (Exception e) {
            int key = direction == AudioManager.ADJUST_RAISE ? 24 : direction == AudioManager.ADJUST_LOWER ? 25 : 164;
            AdbClient.getInstance(context).execute("input keyevent " + key);
        }
    }

    private void handleText(String text) {
        // Sanitize for shell input: quote or replace spaces with %s
        String escaped = text.replace(" ", "%s").replace("'", "\\'");
        AdbClient.getInstance(context).execute("input text '" + escaped + "'");
    }

    private void launchApp(String pkg) {
        try {
            Intent intent = context.getPackageManager().getLaunchIntentForPackage(pkg);
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
            } else {
                AdbClient.getInstance(context).execute("monkey -p " + pkg + " 1");
            }
        } catch (Exception e) {
            AdbClient.getInstance(context).execute("monkey -p " + pkg + " 1");
        }
    }

    private String getHtmlPage() {
        String tvIp = getIpAddress(context);
        return "<!DOCTYPE html>\n" +
                "<html lang=\"id\">\n" +
                "<head>\n" +
                "  <meta charset=\"UTF-8\">\n" +
                "  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no\">\n" +
                "  <title>Xiaomi TV Remote</title>\n" +
                "  <link href=\"https://fonts.googleapis.com/css2?family=Outfit:wght@400;500;600;700&display=swap\" rel=\"stylesheet\">\n" +
                "  <style>\n" +
                "    * { box-sizing: border-box; margin: 0; padding: 0; user-select: none; -webkit-tap-highlight-color: transparent; }\n" +
                "    body { background: #0c0e12; color: #f3f4f6; font-family: 'Outfit', sans-serif; display: flex; justify-content: center; min-height: 100vh; padding: 12px; }\n" +
                "    .app { width: 100%; max-width: 410px; background: #13161c; border-radius: 28px; border: 1px solid #232732; overflow: hidden; display: flex; flex-direction: column; box-shadow: 0 20px 50px rgba(0,0,0,0.8); }\n" +
                "    .hdr { padding: 14px 18px; background: #181c24; border-bottom: 1px solid #232732; display: flex; justify-content: space-between; align-items: center; }\n" +
                "    .hdr h1 { font-size: 17px; font-weight: 600; }\n" +
                "    .stat { display: flex; align-items: center; gap: 6px; font-size: 12px; color: #9ca3af; margin-top: 2px; }\n" +
                "    .dot { width: 7px; height: 7px; background: #22c55e; border-radius: 50%; box-shadow: 0 0 6px #22c55e; }\n" +
                "    .pwr { width: 40px; height: 40px; border-radius: 12px; background: rgba(239,68,68,0.15); border: 1px solid rgba(239,68,68,0.4); color: #ef4444; cursor: pointer; display: flex; align-items: center; justify-content: center; transition: all 0.2s ease; }\n" +
                "    .pwr:active { transform: scale(0.92); background: rgba(239,68,68,0.3); }\n" +
                "    .body { padding: 14px 16px 20px; display: flex; flex-direction: column; gap: 14px; }\n" +
                "    .screen { position: relative; width: 100%; aspect-ratio: 16/9; background: #000; border-radius: 14px; overflow: hidden; border: 1px solid #232732; display: none; }\n" +
                "    .screen img { width: 100%; height: 100%; object-fit: cover; display: block; }\n" +
                "    .badge { position: absolute; top: 8px; left: 8px; background: rgba(0,0,0,0.7); backdrop-filter: blur(4px); padding: 3px 8px; border-radius: 6px; font-size: 10px; font-weight: 600; color: #fff; border: 1px solid #ef4444; display: flex; align-items: center; gap: 4px; }\n" +
                "    .bdot { width: 5px; height: 5px; background: #ef4444; border-radius: 50%; }\n" +
                "    .screen-toggle { align-self: flex-end; background: #1e232d; border: 1px solid #2d3340; color: #9ca3af; padding: 4px 12px; border-radius: 20px; font-size: 11px; font-weight: 500; cursor: pointer; }\n" +
                "    .screen-toggle.on { color: #22c55e; border-color: rgba(34,197,94,0.5); background: rgba(34,197,94,0.1); }\n" +
                "    .inp-bar { display: flex; gap: 6px; background: #1a1e27; padding: 5px; border-radius: 12px; border: 1px solid #2d3340; }\n" +
                "    .inp-bar input { flex: 1; background: transparent; border: none; outline: none; color: #fff; font-family: inherit; font-size: 13px; padding: 6px 8px; }\n" +
                "    .inp-bar input::placeholder { color: #6b7280; }\n" +
                "    .btn-send { background: #2563eb; color: #fff; border: none; border-radius: 8px; padding: 6px 14px; font-size: 12px; font-weight: 600; cursor: pointer; }\n" +
                "    .nav { display: grid; grid-template-columns: 60px 1fr 60px; align-items: center; justify-items: center; margin: 4px 0; }\n" +
                "    .side-btn { width: 56px; height: 56px; border-radius: 16px; background: #1a1e27; border: 1px solid #2d3340; color: #e5e7eb; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 3px; cursor: pointer; }\n" +
                "    .side-btn:active { transform: scale(0.92); background: #232835; }\n" +
                "    .side-btn svg { width: 18px; height: 18px; fill: currentColor; }\n" +
                "    .side-btn span { font-size: 9px; font-weight: 600; color: #9ca3af; }\n" +
                "    .dpad { position: relative; width: 176px; height: 176px; border-radius: 50%; background: radial-gradient(circle, #222733 0%, #151820 100%); border: 2px solid #2d3340; display: flex; align-items: center; justify-content: center; box-shadow: 0 8px 24px rgba(0,0,0,0.4); }\n" +
                "    .dbtn { position: absolute; background: transparent; border: none; color: #cbd5e1; cursor: pointer; display: flex; align-items: center; justify-content: center; font-size: 16px; }\n" +
                "    .dbtn:active { color: #fff; transform: scale(1.2); }\n" +
                "    .dup { top: 8px; left: 50%; transform: translateX(-50%); width: 50px; height: 35px; }\n" +
                "    .ddn { bottom: 8px; left: 50%; transform: translateX(-50%); width: 50px; height: 35px; }\n" +
                "    .dlf { left: 8px; top: 50%; transform: translateY(-50%); width: 35px; height: 50px; }\n" +
                "    .drt { right: 8px; top: 50%; transform: translateY(-50%); width: 35px; height: 50px; }\n" +
                "    .dok { position: relative; width: 70px; height: 70px; border-radius: 50%; background: linear-gradient(145deg, #2b3140, #191c24); border: 1px solid #3d4558; color: #fff; font-weight: 700; font-size: 16px; display: flex; align-items: center; justify-content: center; cursor: pointer; box-shadow: 0 4px 12px rgba(0,0,0,0.3); }\n" +
                "    .dok:active { transform: scale(0.92); background: #161820; }\n" +
                "    .vol-row { display: grid; grid-template-columns: 1fr 1fr 1fr; gap: 8px; }\n" +
                "    .vol-btn { background: #1a1e27; border: 1px solid #2d3340; border-radius: 10px; padding: 10px; color: #e5e7eb; font-family: inherit; font-size: 12px; font-weight: 600; cursor: pointer; text-align: center; }\n" +
                "    .vol-btn:active { transform: scale(0.94); background: #232835; }\n" +
                "    .lbl { font-size: 10px; font-weight: 600; letter-spacing: 0.08em; color: #6b7280; text-transform: uppercase; margin-top: 2px; }\n" +
                "    .apps { display: grid; grid-template-columns: repeat(4, 1fr); gap: 8px; }\n" +
                "    .app-btn { background: #1a1e27; border: 1px solid #2d3340; border-radius: 10px; padding: 8px 4px; display: flex; flex-direction: column; align-items: center; gap: 4px; font-size: 11px; font-weight: 500; color: #e5e7eb; cursor: pointer; }\n" +
                "    .app-btn:active { transform: scale(0.92); }\n" +
                "    .app-btn.yt { border-color: rgba(239,68,68,0.4); }\n" +
                "    .app-btn.nf { border-color: rgba(229,9,20,0.4); }\n" +
                "    .timer-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 6px; }\n" +
                "    .timer-btn { background: #1a1e27; border: 1px solid #2d3340; border-radius: 12px; padding: 11px 4px; color: #cbd5e1; font-family: inherit; font-size: 11px; font-weight: 600; cursor: pointer; text-align: center; transition: all 0.2s ease; }\n" +
                "    .timer-btn:active { transform: scale(0.93); background: #232835; }\n" +
                "    .timer-btn.active { background: rgba(34,197,94,0.15); border-color: #22c55e; color: #4ade80; box-shadow: 0 0 12px rgba(34,197,94,0.25); }\n" +
                "    .timer-status { background: #151c18; border: 1px solid rgba(34,197,94,0.3); border-radius: 12px; padding: 10px 14px; display: flex; align-items: center; justify-content: space-between; font-size: 12px; font-weight: 500; color: #e5e7eb; margin-top: 4px; }\n" +
                "    .timer-info { display: flex; align-items: center; gap: 8px; }\n" +
                "    .t-dot { width: 8px; height: 8px; background: #22c55e; border-radius: 50%; box-shadow: 0 0 8px #22c55e; }\n" +
                "    .timer-cancel { background: rgba(239,68,68,0.15); border: 1px solid rgba(239,68,68,0.4); color: #ef4444; border-radius: 8px; padding: 5px 12px; font-size: 11px; font-weight: 600; cursor: pointer; font-family: inherit; }\n" +
                "    .timer-cancel:active { transform: scale(0.93); }\n" +
                "    .modal { position: fixed; top: 0; left: 0; right: 0; bottom: 0; background: rgba(0,0,0,0.8); backdrop-filter: blur(8px); display: none; align-items: center; justify-content: center; z-index: 999; padding: 20px; }\n" +
                "    .modal-box { width: 100%; max-width: 350px; background: #161922; border: 1px solid #2d3340; border-radius: 22px; padding: 22px; display: flex; flex-direction: column; gap: 10px; box-shadow: 0 20px 50px rgba(0,0,0,0.8); }\n" +
                "    .modal-box h3 { font-size: 16px; font-weight: 600; color: #f3f4f6; }\n" +
                "    .modal-box p { font-size: 12px; color: #9ca3af; margin-bottom: 4px; }\n" +
                "    .m-btn { background: #1c202a; border: 1px solid #2d3340; border-radius: 14px; padding: 12px 14px; text-align: left; display: flex; flex-direction: column; gap: 3px; color: #e5e7eb; cursor: pointer; font-family: inherit; }\n" +
                "    .m-btn:active { transform: scale(0.97); background: #242936; }\n" +
                "    .m-btn span { font-size: 13px; font-weight: 600; }\n" +
                "    .m-btn small { font-size: 11px; color: #9ca3af; }\n" +
                "    .m-btn.danger { border-color: rgba(239,68,68,0.5); background: rgba(239,68,68,0.12); color: #fca5a5; }\n" +
                "    .m-btn.danger small { color: #f87171; }\n" +
                "    .m-btn.danger-sub { border-color: rgba(239,68,68,0.3); background: rgba(239,68,68,0.06); color: #f87171; }\n" +
                "    .m-btn.cancel { background: transparent; border: none; text-align: center; color: #9ca3af; font-size: 12px; font-weight: 600; padding: 10px; margin-top: 4px; }\n" +
                "  </style>\n" +
                "</head>\n" +
                "<body>\n" +
                "<div class=\"app\">\n" +
                "  <div class=\"hdr\">\n" +
                "    <div>\n" +
                "      <h1>Xiaomi Android TV</h1>\n" +
                "      <div class=\"stat\"><span class=\"dot\"></span><span>Terhubung · " + tvIp + "</span></div>\n" +
                "    </div>\n" +
                "    <button class=\"pwr\" onclick=\"openPowerModal()\" title=\"Menu Kontrol Daya\">\n" +
                "      <svg viewBox=\"0 0 24 24\" width=\"20\" height=\"20\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2.5\" stroke-linecap=\"round\" stroke-linejoin=\"round\">\n" +
                "        <path d=\"M18.36 6.64a9 9 0 1 1-12.73 0\"></path>\n" +
                "        <line x1=\"12\" y1=\"2\" x2=\"12\" y2=\"12\"></line>\n" +
                "      </svg>\n" +
                "    </button>\n" +
                "  </div>\n" +
                "  <div class=\"body\">\n" +
                "    <button id=\"toggleScreen\" class=\"screen-toggle\" onclick=\"toggleLiveScreen()\">Layar: OFF</button>\n" +
                "    <div id=\"screenBox\" class=\"screen\">\n" +
                "      <img id=\"screenImg\" src=\"/api/screen\" alt=\"Layar TV\">\n" +
                "      <div class=\"badge\"><span class=\"bdot\"></span><span>LIVE</span></div>\n" +
                "    </div>\n" +
                "    <div class=\"inp-bar\">\n" +
                "      <input type=\"text\" id=\"txt\" placeholder=\"Ketik teks / pencarian YouTube...\">\n" +
                "      <button class=\"btn-send\" onclick=\"sendText()\">KIRIM</button>\n" +
                "    </div>\n" +
                "    <div class=\"nav\">\n" +
                "      <button class=\"side-btn\" onclick=\"sendKey('BACK')\">\n" +
                "        <svg viewBox=\"0 0 24 24\"><path d=\"M20 11H7.83l5.59-5.59L12 4l-8 8 8 8 1.41-1.41L7.83 13H20v-2z\"/></svg>\n" +
                "        <span>KEMBALI</span>\n" +
                "      </button>\n" +
                "      <div class=\"dpad\">\n" +
                "        <button class=\"dbtn dup\" onclick=\"sendKey('UP')\">▲</button>\n" +
                "        <button class=\"dbtn dlf\" onclick=\"sendKey('LEFT')\">◀</button>\n" +
                "        <button class=\"dok\" onclick=\"sendKey('OK')\">OK</button>\n" +
                "        <button class=\"dbtn drt\" onclick=\"sendKey('RIGHT')\">▶</button>\n" +
                "        <button class=\"dbtn ddn\" onclick=\"sendKey('DOWN')\">▼</button>\n" +
                "      </div>\n" +
                "      <button class=\"side-btn\" onclick=\"sendKey('HOME')\">\n" +
                "        <svg viewBox=\"0 0 24 24\"><path d=\"M10 20v-6h4v6h5v-8h3L12 3 2 12h3v8z\"/></svg>\n" +
                "        <span>BERANDA</span>\n" +
                "      </button>\n" +
                "    </div>\n" +
                "    <div class=\"vol-row\">\n" +
                "      <button class=\"vol-btn\" onclick=\"sendKey('VOL_DOWN')\">VOL -</button>\n" +
                "      <button class=\"vol-btn\" onclick=\"sendKey('MUTE')\">MUTE</button>\n" +
                "      <button class=\"vol-btn\" onclick=\"sendKey('VOL_UP')\">VOL +</button>\n" +
                "    </div>\n" +
                "    <div class=\"lbl\">Buka Cepat Aplikasi</div>\n" +
                "    <div class=\"apps\">\n" +
                "      <div class=\"app-btn yt\" onclick=\"openApp('com.google.android.youtube.tv')\"><span>▶</span><span>YouTube</span></div>\n" +
                "      <div class=\"app-btn nf\" onclick=\"openApp('com.netflix.ninja')\"><span style=\"color:#ef4444;font-weight:bold;\">N</span><span>Netflix</span></div>\n" +
                "      <div class=\"app-btn\" onclick=\"openApp('com.google.android.youtube.tvmusic')\"><span>🎵</span><span>Music</span></div>\n" +
                "      <div class=\"app-btn\" onclick=\"openApp('com.mitv.media')\"><span>📁</span><span>Media</span></div>\n" +
                "    </div>\n" +
                "    <div class=\"lbl\">Timer Mematikan Perangkat</div>\n" +
                "    <div class=\"timer-grid\">\n" +
                "      <button class=\"timer-btn\" onclick=\"setTimer(30)\" id=\"tbtn-30\">30 Menit</button>\n" +
                "      <button class=\"timer-btn\" onclick=\"setTimer(60)\" id=\"tbtn-60\">60 Menit</button>\n" +
                "      <button class=\"timer-btn\" onclick=\"setTimer(90)\" id=\"tbtn-90\">90 Menit</button>\n" +
                "      <button class=\"timer-btn\" onclick=\"setTimer(120)\" id=\"tbtn-120\">120 Menit</button>\n" +
                "    </div>\n" +
                "    <div id=\"timerStatusBox\" class=\"timer-status\" style=\"display:none;\">\n" +
                "      <div class=\"timer-info\">\n" +
                "        <span class=\"t-dot\"></span>\n" +
                "        <span id=\"timerStatusText\">TV akan mati dalam --:--</span>\n" +
                "      </div>\n" +
                "      <button class=\"timer-cancel\" onclick=\"cancelTimer()\">Batalkan</button>\n" +
                "    </div>\n" +
                "  </div>\n" +
                "</div>\n" +
                "<div id=\"powerModal\" class=\"modal\" onclick=\"if(event.target===this)closePowerModal()\">\n" +
                "  <div class=\"modal-box\">\n" +
                "    <h3>Kontrol Daya TV</h3>\n" +
                "    <p>Pilih tindakan daya untuk televisi Anda:</p>\n" +
                "    <button class=\"m-btn danger\" onclick=\"execPower('POWER_MENU')\">\n" +
                "      <span>📋 Buka Menu Matikan di TV (Tahan Daya)</span>\n" +
                "      <small>Tahan tombol daya TV (pilih Matikan / Restart / Tidur)</small>\n" +
                "    </button>\n" +
                "    <button class=\"m-btn danger-sub\" onclick=\"execPower('POWER_OFF')\">\n" +
                "      <span>⏻ Matikan Total (Shutdown)</span>\n" +
                "      <small>Matikan seluruh sistem daya televisi</small>\n" +
                "    </button>\n" +
                "    <button class=\"m-btn\" onclick=\"execPower('POWER')\">\n" +
                "      <span>🌙 Standby / Layar Mati</span>\n" +
                "      <small>Mode tidur hemat daya (layar mati seketika)</small>\n" +
                "    </button>\n" +
                "    <button class=\"m-btn cancel\" onclick=\"closePowerModal()\">Batal</button>\n" +
                "  </div>\n" +
                "</div>\n" +
                "<script>\n" +
                "  function sendKey(k) {\n" +
                "    fetch('/api/key?code=' + k);\n" +
                "    if (liveActive) refreshScreen();\n" +
                "  }\n" +
                "  function sendText() {\n" +
                "    const el = document.getElementById('txt');\n" +
                "    if (!el.value) return;\n" +
                "    fetch('/api/text?text=' + encodeURIComponent(el.value));\n" +
                "    el.value = '';\n" +
                "  }\n" +
                "  function openApp(p) {\n" +
                "    fetch('/api/app?pkg=' + encodeURIComponent(p));\n" +
                "  }\n" +
                "  function openPowerModal() {\n" +
                "    document.getElementById('powerModal').style.display = 'flex';\n" +
                "  }\n" +
                "  function closePowerModal() {\n" +
                "    document.getElementById('powerModal').style.display = 'none';\n" +
                "  }\n" +
                "  function execPower(k) {\n" +
                "    closePowerModal();\n" +
                "    sendKey(k);\n" +
                "  }\n" +
                "  let activeTimerMins = 0;\n" +
                "  function setTimer(mins) {\n" +
                "    fetch('/api/timer?action=set&minutes=' + mins)\n" +
                "      .then(r => r.json())\n" +
                "      .then(d => {\n" +
                "        activeTimerMins = mins;\n" +
                "        highlightTimerBtn(mins);\n" +
                "        updateTimerDisplay(d.remaining);\n" +
                "      });\n" +
                "  }\n" +
                "  function cancelTimer() {\n" +
                "    fetch('/api/timer?action=cancel')\n" +
                "      .then(r => r.json())\n" +
                "      .then(() => {\n" +
                "        activeTimerMins = 0;\n" +
                "        highlightTimerBtn(0);\n" +
                "        document.getElementById('timerStatusBox').style.display = 'none';\n" +
                "      });\n" +
                "  }\n" +
                "  function highlightTimerBtn(mins) {\n" +
                "    [30, 60, 90, 120].forEach(m => {\n" +
                "      const b = document.getElementById('tbtn-' + m);\n" +
                "      if (b) {\n" +
                "        if (m === mins) b.classList.add('active');\n" +
                "        else b.classList.remove('active');\n" +
                "      }\n" +
                "    });\n" +
                "  }\n" +
                "  function updateTimerDisplay(sec) {\n" +
                "    const box = document.getElementById('timerStatusBox');\n" +
                "    const txt = document.getElementById('timerStatusText');\n" +
                "    if (!sec || sec <= 0) {\n" +
                "      box.style.display = 'none';\n" +
                "      highlightTimerBtn(0);\n" +
                "      return;\n" +
                "    }\n" +
                "    box.style.display = 'flex';\n" +
                "    const m = Math.floor(sec / 60);\n" +
                "    const s = sec % 60;\n" +
                "    txt.textContent = 'TV akan mati dalam ' + m + 'm ' + (s < 10 ? '0' : '') + s + 'd';\n" +
                "  }\n" +
                "  setInterval(() => {\n" +
                "    fetch('/api/timer?action=status')\n" +
                "      .then(r => r.json())\n" +
                "      .then(d => {\n" +
                "        if (d.remaining > 0) {\n" +
                "          updateTimerDisplay(d.remaining);\n" +
                "        } else if (activeTimerMins > 0) {\n" +
                "          document.getElementById('timerStatusBox').style.display = 'none';\n" +
                "          highlightTimerBtn(0);\n" +
                "          activeTimerMins = 0;\n" +
                "        }\n" +
                "      }).catch(()=>{});\n" +
                "  }, 3000);\n" +
                "  let liveActive = false;\n" +
                "  let screenTimer = null;\n" +
                "  function toggleLiveScreen() {\n" +
                "    liveActive = !liveActive;\n" +
                "    const btn = document.getElementById('toggleScreen');\n" +
                "    const box = document.getElementById('screenBox');\n" +
                "    if (liveActive) {\n" +
                "      btn.textContent = 'Layar: ON';\n" +
                "      btn.classList.add('on');\n" +
                "      box.style.display = 'block';\n" +
                "      refreshScreen();\n" +
                "      screenTimer = setInterval(refreshScreen, 2000);\n" +
                "    } else {\n" +
                "      btn.textContent = 'Layar: OFF';\n" +
                "      btn.classList.remove('on');\n" +
                "      box.style.display = 'none';\n" +
                "      clearInterval(screenTimer);\n" +
                "    }\n" +
                "  }\n" +
                "  function refreshScreen() {\n" +
                "    if (!liveActive) return;\n" +
                "    document.getElementById('screenImg').src = '/api/screen?t=' + Date.now();\n" +
                "  }\n" +
                "</script>\n" +
                "</body>\n" +
                "</html>";
    }
}
