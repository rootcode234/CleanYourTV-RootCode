#!/usr/bin/env python3
"""
CleanYourTV - Automated TV Debloat & Launcher Setup Tool
Supports: Xiaomi, TCL, Realme, Coocaa, Google TV / Sony, Polytron, Changhong, etc.
"""

import os
import sys
import time
import argparse
import subprocess
from pathlib import Path

BASE_DIR = Path(__file__).resolve().parent
RELEASE_APK = BASE_DIR / "release" / "CleanYourTV-v2.5.apk"
PROFILES_DIR = BASE_DIR / "debloat_profiles"

PROFILES = {
    "1": ("xiaomi", "Xiaomi / Redmi Android TV", PROFILES_DIR / "xiaomi.txt"),
    "2": ("tcl", "TCL Android TV / Google TV", PROFILES_DIR / "tcl.txt"),
    "3": ("realme", "Realme / Coocaa / Skyworth", PROFILES_DIR / "realme_coocaa.txt"),
    "4": ("google", "Google TV / Chromecast / Sony", PROFILES_DIR / "google_tv.txt"),
    "5": ("generic", "Generic / Universal (Polytron, Changhong, Sharp, dll)", PROFILES_DIR / "generic.txt"),
}

def find_adb():
    """Find adb binary in PATH or common Android SDK locations."""
    if os.environ.get("ADB_PATH"):
        return os.environ.get("ADB_PATH")
    
    # Check system PATH
    for path in os.environ.get("PATH", "").split(os.pathsep):
        adb_exec = Path(path) / ("adb.exe" if os.name == "nt" else "adb")
        if adb_exec.is_file():
            return str(adb_exec)
    
    # Common Windows locations
    local_app_data = os.environ.get("LOCALAPPDATA")
    if local_app_data:
        p = Path(local_app_data) / "Android" / "Sdk" / "platform-tools" / "adb.exe"
        if p.is_file():
            return str(p)

    return "adb"

def run_adb(adb_bin, target, *args):
    """Run an ADB command targeting a specific device."""
    cmd = [adb_bin]
    if target:
        cmd.extend(["-s", target])
    cmd.extend(args)
    try:
        res = subprocess.run(cmd, capture_output=True, text=True, timeout=30)
        return res.returncode == 0, res.stdout.strip(), res.stderr.strip()
    except Exception as e:
        return False, "", str(e)

def detect_brand(adb_bin, target):
    """Auto-detect TV brand using Android getprop."""
    ok, brand, _ = run_adb(adb_bin, target, "shell", "getprop", "ro.product.brand")
    ok2, mfg, _ = run_adb(adb_bin, target, "shell", "getprop", "ro.product.manufacturer")
    combined = f"{brand.lower()} {mfg.lower()}".strip()

    if any(k in combined for k in ["xiaomi", "redmi", "mitv"]):
        return "1"
    elif "tcl" in combined:
        return "2"
    elif any(k in combined for k in ["realme", "coocaa", "skyworth"]):
        return "3"
    elif any(k in combined for k in ["google", "sony"]):
        return "4"
    return "5"

def read_packages(profile_file):
    """Read non-comment package names from profile."""
    if not profile_file.is_file():
        return []
    pkgs = []
    with open(profile_file, "r", encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if line and not line.startswith("#"):
                pkgs.append(line)
    return pkgs

def main():
    parser = argparse.ArgumentParser(description="CleanYourTV Automated Setup Tool")
    parser.add_argument("--ip", help="IP address of Android TV (e.g., 192.168.0.33)")
    parser.add_argument("--brand", choices=["xiaomi", "tcl", "realme", "google", "generic", "auto"],
                        default="auto", help="TV Brand / Profile")
    parser.add_argument("--restore", action="store_true", help="Restore / Re-enable all disabled bloatware")
    parser.add_argument("--skip-apk", action="store_true", help="Skip installing launcher APK")
    args = parser.parse_args()

    print("=" * 60)
    print("   CleanYourTV - Setup & Debloat Otomatis Multi-Merk")
    print("   Launcher Ringan + Jadwal Sholat + Web Remote HP")
    print("=" * 60)

    adb_bin = find_adb()
    print(f"[*] Menggunakan ADB: {adb_bin}")

    # 1. Get TV IP
    tv_ip = args.ip
    if not tv_ip:
        try:
            tv_ip = input("\n[?] Masukkan IP Android TV Anda (contoh: 192.168.0.33): ").strip()
        except KeyboardInterrupt:
            print("\nBatal.")
            sys.exit(0)

    if not tv_ip:
        print("[!] IP TV tidak boleh kosong.")
        sys.exit(1)

    target = f"{tv_ip}:5555"
    print(f"\n[*] Menghubungkan ke {target}...")
    subprocess.run([adb_bin, "connect", target], capture_output=True)
    time.sleep(2)

    ok, out, _ = run_adb(adb_bin, target, "get-state")
    if not ok or "device" not in out:
        print(f"[!] Gagal terhubung ke {target}. Pastikan:")
        print("    1. TV dan Komputer berada di jaringan Wi-Fi yang sama.")
        print("    2. 'Opsi Pengembang' dan 'Debugging USB' di TV sudah ON.")
        print("    3. Tekan 'Izinkan' jika muncul jendela konfirmasi di layar TV.")
        sys.exit(1)

    print(f"[+] Berhasil terhubung ke TV ({target})!")

    # 2. Brand Detection
    ok_b, b_name, _ = run_adb(adb_bin, target, "shell", "getprop", "ro.product.brand")
    ok_m, m_name, _ = run_adb(adb_bin, target, "shell", "getprop", "ro.product.model")
    print(f"[*] Perangkat terdeteksi: {b_name} ({m_name})")

    selected_key = "5"
    if args.brand == "auto":
        detected_key = detect_brand(adb_bin, target)
        _, detected_desc, _ = PROFILES[detected_key]
        print(f"[+] Deteksi profil otomatis: {detected_desc}")
        selected_key = detected_key
    else:
        for k, (name, _, _) in PROFILES.items():
            if name == args.brand:
                selected_key = k
                break

    profile_name, profile_desc, profile_file = PROFILES[selected_key]
    pkgs = read_packages(profile_file)
    print(f"[*] Menggunakan profil: {profile_desc} ({len(pkgs)} paket bloatware)")

    # 3. Restore Mode
    if args.restore:
        print("\n[*] Menjalankan MODE RESTORE (Mengaktifkan kembali bloatware)...")
        for pkg in pkgs:
            ok, _, _ = run_adb(adb_bin, target, "shell", "pm", "enable", pkg)
            status = "DIPULIHKAN" if ok else "GAGAL"
            print(f"    - {pkg}: {status}")
        print("\n[+] Seluruh aplikasi bawaan berhasil diaktifkan kembali!")
        return

    # 4. Install CleanYourTV APK
    if not args.skip_apk:
        if RELEASE_APK.is_file():
            print(f"\n[*] Memasang CleanYourTV Launcher ({RELEASE_APK.name})...")
            run_adb(adb_bin, target, "push", str(RELEASE_APK), "/data/local/tmp/app-release.apk")
            ok_ins, out_ins, err_ins = run_adb(adb_bin, target, "shell", "pm", "install", "-r", "-d", "/data/local/tmp/app-release.apk")
            if "Success" in out_ins or ok_ins:
                print("[+] Pemasangan APK berhasil (Success)!")
                # Berikan izin lokasi untuk baca nama SSID Wi-Fi
                run_adb(adb_bin, target, "shell", "pm", "grant", "com.gathin.tvhome", "android.permission.ACCESS_FINE_LOCATION")
                run_adb(adb_bin, target, "shell", "pm", "grant", "com.gathin.tvhome", "android.permission.ACCESS_COARSE_LOCATION")
            else:
                print(f"[!] Gagal memasang APK: {out_ins} {err_ins}")
        else:
            print(f"[!] File APK tidak ditemukan di: {RELEASE_APK}")

    # 5. Debloat Packages
    print(f"\n[*] Menjalankan pembersihan bloatware aman ({profile_name})...")
    disabled_count = 0
    for pkg in pkgs:
        ok, out, _ = run_adb(adb_bin, target, "shell", "pm", "disable-user", "--user", "0", pkg)
        if "disabled" in out.lower() or "new state" in out.lower() or ok:
            print(f"    [√] Dinonaktifkan: {pkg}")
            disabled_count += 1
        else:
            print(f"    [-] Dilewati/Tidak ada: {pkg}")

    print(f"\n[+] Sukses menonaktifkan {disabled_count} aplikasi sampah!")

    # 6. Start CleanYourTV
    print("\n[*] Menjalankan CleanYourTV di layar TV...")
    run_adb(adb_bin, target, "shell", "am", "start", "-n", "com.gathin.tvhome/.MainActivity")

    print("\n" + "=" * 60)
    print("   SETUP SELESAI DENGAN SUKSES! 🎉")
    print("=" * 60)
    print(f"1. TV Anda sekarang jauh lebih enteng, responsif, dan bebas iklan!")
    print(f"2. Launcher CleanYourTV sudah aktif di layar utama.")
    print(f"3. Buka Web Remote di browser HP Anda:")
    print(f"   👉 http://{tv_ip}:8080/")
    print(f"   (Fitur: D-Pad, Cermin Layar Live, Sleep Timer 30-120 mnt, Kontrol Daya)")
    print("=" * 60)

if __name__ == "__main__":
    main()
