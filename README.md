# 📺 CleanYourTV Ultimate — Lightweight Launcher, Prayer Times & Web Remote

[![Android TV](https://img.shields.io/badge/Platform-Android%20TV%20%7C%20Google%20TV-blue.svg)](https://android.com/tv)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)
[![Release](https://img.shields.io/badge/Release-v2.5.0-orange.svg)](release/CleanYourTV-v2.5.apk)

**CleanYourTV Ultimate** adalah solusi lengkap untuk mengubah Android TV / Google TV Anda (Xiaomi, TCL, Realme, Coocaa, Sony, Polytron, Changhong, dll.) menjadi **super enteng, bebas iklan/bloatware, dilengkapi Jadwal Sholat & Hijriyah offline, serta Web Remote HP dengan fitur Live TV Screen & Sleep Timer**.

---

## 🌟 Fitur Unggulan

### 1. 🕌 Jadwal Sholat & Tanggal Hijriyah Akurat (Offline)
* **Perhitungan Astronomi Kemenag RI**: 100% offline tanpa perlu koneksi internet atau server luar.
* **Highlight Emas (*Amber Gold*)**: Waktu sholat berikutnya yang akan datang otomatis menyala dengan warna emas hangat (`#FFD166`).
* **Tanggal Hijriyah Dinamis**: Otomatis berganti hari Hijriyah setelah adzan Maghrib berkumandang.
* **Pilihan Kota**: Tersedia pilihan kota (Blora, Pati, Semarang, Surabaya, Jakarta, Bandung, Yogyakarta, dll.) yang bisa diganti langsung lewat menu Setelan Launcher di TV.

### 2. 📱 Web Remote HP & Cermin Layar TV (Port 8080)
Kendalikan TV Anda langsung dari browser HP mana pun di jaringan Wi-Fi lokal (`http://[IP_TV]:8080/`):
* **Navigasi Lengkap**: D-Pad, Tombol OK, Kembali (Back), Beranda (Home), Pengatur Volume, dan Mute.
* **Keyboard Ngetik HP**: Cukup ketik judul film / lagu di HP, teks langsung terketik di kolom pencarian YouTube TV.
* **Cermin Layar TV (*Live Screen*)**: Lihat layar TV langsung di layar HP secara real-time dengan tombol hemat daya *Layar: ON / OFF*.
* **Kontrol Daya Lengkap**:
  * 📋 *Buka Menu Matikan di TV* (simulasi tekan lama tombol power fisik)
  * ⏻ *Matikan Total (Shutdown)*
  * 🌙 *Standby / Layar Mati*
* **⏱️ Sleep Timer TV**: Pilihan timer **30, 60, 90, dan 120 Menit**. Berjalan langsung di proses latar belakang TV, sehingga meskipun HP dimatikan atau browser ditutup, TV akan tetap otomatis mati saat waktu habis.
* **Saklar Hemat Daya**: Bisa dinonaktifkan kapan saja di menu Setelan Antarmuka TV (0% beban CPU jika dimatikan).

### 3. 🚀 Tampilan Bersih & Minimalis
* **Dock 4 Aplikasi**: Hanya menampilkan 4 aplikasi utama di layar depan agar rapi dan tidak ada elemen yang terpotong. Aplikasi lainnya tersimpan rapi di bawah dan muncul saat tombol arah Bawah ditekan.
* **Indikator Wi-Fi Asli**: Menampilkan nama SSID jaringan Wi-Fi Anda yang sebenarnya (bukan sekadar tulisan "Wi-Fi").

---

## 🤖 Panduan untuk AI Coding Agent (Antigravity, Cursor, Claude Code, Devin, dll.)

Jika Anda atau teman Anda menggunakan AI Coding Assistant, Anda **cukup menyalin prompt di bawah ini** dan kirimkan ke AI Agent Anda:

```markdown
Tolong pasang dan konfigurasikan CleanYourTV dari repository ini ke Android TV saya:
- IP TV saya: [MASUKKAN IP TV ANDA, contoh: 192.168.0.33]
- Merk TV saya: [Xiaomi / TCL / Realme / Google TV / Generic] (atau deteksi otomatis)

Instruksi untuk AI:
1. Hubungkan ke TV via ADB port 5555 (`adb connect <IP_TV>:5555`).
2. Pasang file APK launcher yang ada di `release/CleanYourTV-v2.5.apk` (`adb install -r -d`).
3. Berikan izin lokasi agar nama SSID Wi-Fi tampil:
   `adb shell pm grant com.gathin.tvhome android.permission.ACCESS_FINE_LOCATION`
4. Jalankan debloat aplikasi sampah sesuai profil merk TV di folder `debloat_profiles/` menggunakan `pm disable-user --user 0 <pkg>`.
5. Jalankan launcher di TV: `adb shell am start -n com.gathin.tvhome/.MainActivity`.
6. Tampilkan ringkasan status dan URL Web Remote (`http://<IP_TV>:8080/`).
```

---

## ⚡ Panduan Cepat 1-Klik (Pengguna Biasa)

### Persiapan di TV:
1. Buka **Setelan Android TV** → **Preferensi Perangkat** → **Tentang**.
2. Klik baris **Build OS Android TV** sebanyak **7 kali** sampai muncul tulisan *"Anda sekarang adalah pengembang"*.
3. Kembali ke menu sebelumnya → buka **Opsi Pengembang** (*Developer Options*).
4. Aktifkan **Debugging USB** (*USB Debugging*).

### Cara Menjalankan:
#### Pengguna Windows:
1. Hubungkan laptop/PC Anda ke jaringan Wi-Fi yang sama dengan TV.
2. Dobel-klik file **`setup_tv.bat`**.
3. Masukkan alamat IP TV Anda (misal: `192.168.0.33`).
4. Pilih merk TV Anda (atau pilih deteksi otomatis).
5. Selesai!

#### Pengguna Mac / Linux / Python:
Jalankan perintah berikut di Terminal:
```bash
python setup_tv.py --ip 192.168.0.33 --brand auto
```

---

## 🛡️ Profil Debloat yang Didukung & Keamanan Sistem

Semua proses pembersihan bloatware menggunakan perintah aman Android:
`pm disable-user --user 0 <nama_paket>`
> **Catatan Keamanan:** Metode ini **TIDAK memerlukan Root**, tidak membatalkan garansi, tidak merusak update sistem (OTA), dan **100% dapat dipulihkan kapan saja**.

| Profil | Berkas Profil | Aplikasi yang Dinonaktifkan |
| :--- | :--- | :--- |
| **Xiaomi / Redmi TV** | `debloat_profiles/xiaomi.txt` | PatchWall (`com.mitv.tvhome`), Analitik pelacak Xiaomi, Promo mitra, Pemutar media bawaan yang berat. |
| **TCL TV** | `debloat_profiles/tcl.txt` | TCL Channel (`com.tcl.tvplus`), Waterfall overseas launcher, TCL Guard, Browser bawaan. |
| **Realme / Coocaa** | `debloat_profiles/realme_coocaa.txt` | Realme customizer & store, Coocaa Mall, Skyworth recommendation feeds. |
| **Google TV / Sony** | `debloat_profiles/google_tv.txt` | LauncherX Google TV, Feed iklan film Google Tungsten, Rekomendasi promo Sony. |
| **Generic (Universal)** | `debloat_profiles/generic.txt` | Leanback TV launcher standar, Feed rekomendasi Google TV, Layanan feedback. |

### ⛔ Aplikasi yang DIJAMIN AMAN (Tidak Akan Pernah Disentuh):
* ✅ Keyboard Gboard (`com.google.android.inputmethod.latin`)
* ✅ Google Play Services & Play Store (`com.google.android.gms`, `com.android.vending`)
* ✅ TV Input Framework / HDMI Switcher (`com.android.tv`)
* ✅ Driver Audio, Bluetooth, dan Wi-Fi

---

## 🔄 Cara Mengembalikan Aplikasi Bawaan (Restore / Undo)

Jika Anda ingin mengaktifkan kembali seluruh aplikasi bawaan pabrik, Anda cukup menjalankan:

```bash
python setup_tv.py --ip 192.168.0.33 --restore
```
Semua aplikasi yang sebelumnya dinonaktifkan akan langsung aktif kembali seperti semula.

---

## 📄 Lisensi
Didistribusikan di bawah Lisensi MIT. Bebas digunakan, dimodifikasi, dan dibagikan kembali.
