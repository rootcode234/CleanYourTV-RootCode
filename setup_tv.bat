@echo off
setlocal enabledelayedexpansion
title CleanYourTV - Setup & Debloat Otomatis Multi-Merk
cls

echo ============================================================
echo   CleanYourTV - Setup & Debloat Otomatis Multi-Merk
echo   Launcher Ringan + Jadwal Sholat + Web Remote HP
echo ============================================================
echo.

where python >nul 2>nul
if %ERRORLEVEL% equ 0 (
    python setup_tv.py %*
    pause
    exit /b 0
)

:: Fallback direct ADB if python is not installed
where adb >nul 2>nul
if %ERRORLEVEL% neq 0 (
    if exist "%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" (
        set "PATH=%PATH%;%LOCALAPPDATA%\Android\Sdk\platform-tools"
    ) else (
        echo [!] ADB tidak ditemukan. Silakan pasang Android Platform Tools atau Python.
        pause
        exit /b 1
    )
)

set /p TV_IP="[?] Masukkan IP Android TV Anda (contoh: 192.168.0.33): "
if "%TV_IP%"=="" (
    echo [!] IP TV tidak boleh kosong.
    pause
    exit /b 1
)

echo.
echo [*] Menghubungkan ke %TV_IP%:5555...
adb connect %TV_IP%:5555
timeout /t 2 >nul

echo.
echo [1] Xiaomi / Redmi Android TV
echo [2] TCL Android TV / Google TV
echo [3] Realme / Coocaa / Skyworth
echo [4] Google TV / Chromecast / Sony
echo [5] Generic / Universal (Polytron, Changhong, dll)
echo.
set /p BRAND_CHOICE="[?] Pilih Merk TV Anda [1-5]: "

set "PROFILE=generic.txt"
if "%BRAND_CHOICE%"=="1" set "PROFILE=xiaomi.txt"
if "%BRAND_CHOICE%"=="2" set "PROFILE=tcl.txt"
if "%BRAND_CHOICE%"=="3" set "PROFILE=realme_coocaa.txt"
if "%BRAND_CHOICE%"=="4" set "PROFILE=google_tv.txt"
if "%BRAND_CHOICE%"=="5" set "PROFILE=generic.txt"

echo.
echo [*] Memasang CleanYourTV Launcher...
adb -s %TV_IP%:5555 push release\CleanYourTV-v2.5.apk /data/local/tmp/app-release.apk
adb -s %TV_IP%:5555 shell pm install -r -d /data/local/tmp/app-release.apk
adb -s %TV_IP%:5555 shell pm grant com.gathin.tvhome android.permission.ACCESS_FINE_LOCATION

echo.
echo [*] Menonaktifkan aplikasi sampah (%PROFILE%)...
for /f "usebackq tokens=* delims=" %%P in ("debloat_profiles\%PROFILE%") do (
    set "LINE=%%P"
    if not "!LINE:~0,1!"=="#" (
        adb -s %TV_IP%:5555 shell pm disable-user --user 0 %%P >nul 2>nul
        echo     [-] Dinonaktifkan: %%P
    )
)

echo.
echo [*] Menjalankan CleanYourTV di layar TV...
adb -s %TV_IP%:5555 shell am start -n com.gathin.tvhome/.MainActivity

echo.
echo ============================================================
echo   SETUP SELESAI DENGAN SUKSES! 🎉
echo ============================================================
echo 1. TV Anda sekarang jauh lebih enteng dan bebas iklan!
echo 2. Buka Web Remote di browser HP: http://%TV_IP%:8080/
echo ============================================================
echo.
pause
