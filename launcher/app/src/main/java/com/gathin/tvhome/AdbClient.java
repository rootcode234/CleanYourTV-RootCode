package com.gathin.tvhome;

import android.content.Context;
import android.util.Base64;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.concurrent.atomic.AtomicInteger;
import javax.crypto.Cipher;

public class AdbClient {
    private static final String TAG = "AdbClient";
    private static final int A_CNXN = 0x4e584e43;
    private static final int A_AUTH = 0x48545541;
    private static final int A_OPEN = 0x4e45504f;
    private static final int A_OKAY = 0x59414b4f;
    private static final int A_CLSE = 0x45534c43;
    private static final int A_WRTE = 0x45545257;

    private static final int AUTH_TYPE_SIGNATURE = 2;
    private static final byte[] SHA1_ASN1_PREFIX = new byte[]{
            0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b, 0x0e, 0x03, 0x02, 0x1a, 0x05, 0x00, 0x04, 0x14
    };

    private static AdbClient instance;
    private final Context context;
    private PrivateKey privateKey;
    private Socket socket;
    private InputStream in;
    private OutputStream out;
    private final AtomicInteger channelCounter = new AtomicInteger(10);

    public static synchronized AdbClient getInstance(Context context) {
        if (instance == null) {
            instance = new AdbClient(context.getApplicationContext());
        }
        return instance;
    }

    private AdbClient(Context context) {
        this.context = context;
        loadKey();
    }

    private void loadKey() {
        try (InputStream is = context.getAssets().open("adbkey")) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[1024];
            int n;
            while ((n = is.read(buf)) != -1) baos.write(buf, 0, n);
            String pem = baos.toString("UTF-8");
            pem = pem.replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s+", "");
            byte[] der = Base64.decode(pem, Base64.DEFAULT);
            KeyFactory kf = KeyFactory.getInstance("RSA");
            privateKey = kf.generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (Exception e) {
            Log.e(TAG, "Failed to load adbkey from assets: " + e.getMessage());
        }
    }

    private synchronized boolean ensureConnected() {
        if (socket != null && socket.isConnected() && !socket.isClosed()) {
            return true;
        }
        closeSocket();
        if (privateKey == null) loadKey();
        if (privateKey == null) return false;

        try {
            socket = new Socket();
            socket.setTcpNoDelay(true);
            socket.connect(new InetSocketAddress("127.0.0.1", 5555), 2500);
            socket.setSoTimeout(5000);
            in = socket.getInputStream();
            out = socket.getOutputStream();

            // 1. Send CNXN
            byte[] hostPayload = "host::\0".getBytes("UTF-8");
            sendMessage(A_CNXN, 0x01000000, 4096, hostPayload);

            // 2. Read response (expect A_AUTH)
            Message msg = readMessage();
            if (msg.command == A_AUTH) {
                // Sign token
                byte[] toSign = new byte[SHA1_ASN1_PREFIX.length + msg.data.length];
                System.arraycopy(SHA1_ASN1_PREFIX, 0, toSign, 0, SHA1_ASN1_PREFIX.length);
                System.arraycopy(msg.data, 0, toSign, SHA1_ASN1_PREFIX.length, msg.data.length);

                Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
                cipher.init(Cipher.ENCRYPT_MODE, privateKey);
                byte[] sig = cipher.doFinal(toSign);

                // Send signed auth
                sendMessage(A_AUTH, AUTH_TYPE_SIGNATURE, 0, sig);

                // Expect CNXN
                Message cnxn = readMessage();
                if (cnxn.command != A_CNXN) {
                    Log.e(TAG, "Auth failed, got command: " + Integer.toHexString(cnxn.command));
                    closeSocket();
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to connect to 127.0.0.1:5555: " + e.getMessage());
            closeSocket();
            return false;
        }
    }

    public synchronized boolean execute(String command) {
        if (!ensureConnected()) return false;
        int localId = channelCounter.incrementAndGet();
        try {
            byte[] dest = ("shell:" + command + "\0").getBytes("UTF-8");
            sendMessage(A_OPEN, localId, 0, dest);

            // Wait for OKAY / WRTE / CLSE
            while (true) {
                Message msg = readMessage();
                if (msg.command == A_OKAY) {
                    // channel opened
                } else if (msg.command == A_WRTE) {
                    // Send OKAY ACK
                    sendMessage(A_OKAY, localId, msg.arg0, new byte[0]);
                } else if (msg.command == A_CLSE) {
                    // closed
                    break;
                }
            }
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error executing command '" + command + "': " + e.getMessage());
            closeSocket();
            return false;
        }
    }

    public synchronized byte[] screencap() {
        if (!ensureConnected()) return null;
        try {
            socket.setSoTimeout(12000);
            final String tmpPng = "/data/local/tmp/.sc.png";
            ByteArrayOutputStream baos = new ByteArrayOutputStream(256 * 1024);
            int localId = channelCounter.incrementAndGet();

            // Run in a single atomic shell command:
            // 1) screencap saves clean PNG binary to /data/local/tmp/.sc.png
            // 2) base64 converts it to pure ASCII text stream sent back over ADB
            // This completely avoids CR/LF binary corruption and race conditions.
            byte[] dest = ("shell:screencap -p " + tmpPng + " && base64 " + tmpPng + "\0").getBytes("UTF-8");
            sendMessage(A_OPEN, localId, 0, dest);

            while (true) {
                Message msg = readMessage();
                if (msg.command == A_OKAY) {
                    // channel ready
                } else if (msg.command == A_WRTE) {
                    baos.write(msg.data);
                    sendMessage(A_OKAY, localId, msg.arg0, new byte[0]);
                } else if (msg.command == A_CLSE) {
                    break;
                }
            }

            // Parse base64 output
            // Strip \r from shell: line endings, and skip any debug text lines
            String raw64 = baos.toString("UTF-8");
            String[] lines = raw64.split("\\n");
            StringBuilder cleanB64 = new StringBuilder();
            for (String ln : lines) {
                String trimmed = ln.replace("\r", "").trim();
                if (trimmed.isEmpty()) continue;
                // Only take lines that consist exclusively of base64 chars
                if (trimmed.matches("[A-Za-z0-9+/=]+")) {
                    cleanB64.append(trimmed);
                }
            }

            String b64Text = cleanB64.toString();
            if (b64Text.isEmpty()) {
                Log.e(TAG, "No valid base64 content found in screencap");
                return null;
            }

            byte[] png = android.util.Base64.decode(b64Text, android.util.Base64.DEFAULT);
            if (png.length < 8 || png[0] != (byte)0x89 || png[1] != 0x50) {
                Log.e(TAG, "Decoded data not valid PNG, len=" + png.length);
                return null;
            }
            Log.d(TAG, "Screencap OK: " + png.length + " bytes");
            return png;

        } catch (Exception e) {
            Log.e(TAG, "Screencap failed: " + e.getMessage());
            closeSocket();
            return null;
        }
    }

    /** Execute a shell command and wait for CLSE (output discarded). */
    private boolean executeInternal(String command) throws Exception {
        int localId = channelCounter.incrementAndGet();
        byte[] dest = ("shell:" + command + "\0").getBytes("UTF-8");
        sendMessage(A_OPEN, localId, 0, dest);
        while (true) {
            Message msg = readMessage();
            if (msg.command == A_OKAY) {
                // opened
            } else if (msg.command == A_WRTE) {
                sendMessage(A_OKAY, localId, msg.arg0, new byte[0]);
            } else if (msg.command == A_CLSE) {
                return true;
            }
        }
    }

    private void sendMessage(int cmd, int arg0, int arg1, byte[] data) throws Exception {
        int length = data != null ? data.length : 0;
        int crc = 0;
        if (data != null) {
            for (byte b : data) crc += (b & 0xFF);
        }
        int magic = cmd ^ 0xFFFFFFFF;

        ByteBuffer bb = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN);
        bb.putInt(cmd);
        bb.putInt(arg0);
        bb.putInt(arg1);
        bb.putInt(length);
        bb.putInt(crc);
        bb.putInt(magic);

        out.write(bb.array());
        if (length > 0) {
            out.write(data);
        }
        out.flush();
    }

    private Message readMessage() throws Exception {
        byte[] header = new byte[24];
        readFully(in, header);

        ByteBuffer bb = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
        int cmd = bb.getInt();
        int arg0 = bb.getInt();
        int arg1 = bb.getInt();
        int length = bb.getInt();
        int crc = bb.getInt();
        int magic = bb.getInt();

        byte[] data = new byte[length];
        if (length > 0) {
            readFully(in, data);
        }
        return new Message(cmd, arg0, arg1, data);
    }

    private void readFully(InputStream is, byte[] buf) throws Exception {
        int off = 0;
        while (off < buf.length) {
            int n = is.read(buf, off, buf.length - off);
            if (n < 0) throw new Exception("Unexpected EOF in ADB stream");
            off += n;
        }
    }

    public synchronized void close() {
        closeSocket();
    }

    private void closeSocket() {
        try {
            if (socket != null) socket.close();
        } catch (Exception ignored) {}
        socket = null;
        in = null;
        out = null;
    }

    private static class Message {
        final int command;
        final int arg0;
        final int arg1;
        final byte[] data;

        Message(int command, int arg0, int arg1, byte[] data) {
            this.command = command;
            this.arg0 = arg0;
            this.arg1 = arg1;
            this.data = data;
        }
    }
}
