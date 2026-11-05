@echo off
setlocal enabledelayedexpansion
title SES Security Suite Mobile - Dev Runner

:: Tentukan path ADB lokal jika tidak ada di PATH sistem
set "ADB=D:\GIT\GitHub\AndroidSDK\platform-tools\adb.exe"
if not exist "%ADB%" (
    where adb >nul 2>&1
    if !errorlevel! equ 0 (
        set "ADB=adb"
    ) else (
        echo [WARN] ADB tidak ditemukan di PATH atau path default SDK.
        set "ADB=adb"
    )
)

:MENU
cls
echo ======================================================================
echo          SES SECURITY SUITE MOBILE - PT BADAK NGL
echo             Interactive Development & Runtime Tool
echo ======================================================================
echo.
echo   [1] Run Live di HP Fisik (Infinix via USB - Hot Reload Aktif)
echo   [2] Launch Emulator AVD & Run (Medium Phone API 37)
echo   [3] Build Debug APK dan Auto-Install ke HP Terhubung
echo   [4] Jalankan Rangkaian Pengujian Otomatis (flutter test & analyze)
echo   [5] Ambil Screenshot dari HP Fisik (Simpan ke screenshots\)
echo   [6] Bersihkan Cache & Perbaiki Lockfile (flutter clean & pub get)
echo   [0] Keluar
echo.
echo ======================================================================
set /p opt="Pilih opsi [0-6]: "

if "%opt%"=="1" goto RUN_HP
if "%opt%"=="2" goto RUN_EMU
if "%opt%"=="3" goto BUILD_INSTALL
if "%opt%"=="4" goto RUN_TESTS
if "%opt%"=="5" goto SCREENSHOT
if "%opt%"=="6" goto CLEAN_ALL
if "%opt%"=="0" goto EXIT
goto MENU

:RUN_HP
cls
echo [INFO] Memeriksa perangkat terhubung...
"%ADB%" devices
echo.
echo [INFO] Menyiapkan reverse proxy port 8084 untuk koneksi ke backend ses-api...
"%ADB%" reverse tcp:8084 tcp:8084
echo.
echo [INFO] Menjalankan SES Mobile di HP Fisik (Tekan 'r' untuk Hot Reload, 'q' untuk keluar)...
flutter run -d 10275333AD005736
pause
goto MENU

:RUN_EMU
cls
echo [INFO] Memulai Android Emulator AVD (Medium_Phone_API_37.0)...
flutter emulators --launch Medium_Phone_API_37.0
echo [INFO] Menunggu emulator siap...
timeout /t 8 >nul
echo [INFO] Menjalankan SES Mobile di emulator...
flutter run -d emulator-5554
pause
goto MENU

:BUILD_INSTALL
cls
echo [INFO] Membangun Debug APK...
flutter build apk --debug
if %errorlevel% neq 0 (
    echo [ERROR] Build APK gagal.
    pause
    goto MENU
)
echo.
echo [INFO] Menginstal APK ke perangkat terhubung...
"%ADB%" install -r build\app\outputs\flutter-apk\app-debug.apk
echo.
echo [INFO] Membuka aplikasi di perangkat...
"%ADB%" shell am start -n com.example.ses_masuk_barang/.MainActivity
echo.
echo [SUKSES] Aplikasi berhasil diinstal dan berjalan di layar HP.
pause
goto MENU

:RUN_TESTS
cls
echo ======================================================================
echo                  RUNNING AUTOMATED TESTS & ANALYZE
echo ======================================================================
echo.
echo [1/2] Menjalankan flutter analyze...
flutter analyze
echo.
echo [2/2] Menjalankan flutter test...
flutter test
echo.
echo [INFO] Seluruh pengujian selesai.
pause
goto MENU

:SCREENSHOT
cls
if not exist "screenshots" mkdir screenshots
set "STAMP=%date:~10,4%%date:~4,2%%date:~7,2%_%time:~0,2%%time:~3,2%%time:~6,2%"
set "STAMP=%STAMP: =0%"
set "TARGET_FILE=screenshots\capture_%STAMP%.png"
echo [INFO] Mengambil screenshot dari HP...
"%ADB%" shell screencap -p /sdcard/ses_screenshot_temp.png
"%ADB%" pull /sdcard/ses_screenshot_temp.png "%TARGET_FILE%"
"%ADB%" shell rm /sdcard/ses_screenshot_temp.png
echo.
echo [SUKSES] Screenshot tersimpan di: %TARGET_FILE%
echo.
pause
goto MENU

:CLEAN_ALL
cls
echo [INFO] Membersihkan build artifacts dan lockfile...
flutter clean
flutter pub get
echo.
echo [SUKSES] Cache bersih dan dependency diperbarui.
pause
goto MENU

:EXIT
exit /b 0

