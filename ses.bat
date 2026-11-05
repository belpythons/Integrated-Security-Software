@echo off
setlocal enabledelayedexpansion
cd /d "%~dp0"

:: ==============================================================================
:: SUB-DISPATCHERS (Dijalankan dalam Jendela Terminal Terpisah)
:: ==============================================================================
if "%1"=="_RUN_INTRANET" goto DO_RUN_INTRANET
if "%1"=="_RUN_INTERNET" goto DO_RUN_INTERNET
if "%1"=="_RUN_API"      goto DO_RUN_API
if "%1"=="_RUN_MOBILE"   goto DO_RUN_MOBILE
if "%1"=="_OPEN_CHROME"  goto DO_OPEN_CHROME

title SES - Master Control Center (PT Badak NGL)

:: Deteksi ADB
set "ADB=D:\GIT\GitHub\AndroidSDK\platform-tools\adb.exe"
if not exist "%ADB%" (
    where adb >nul 2>&1
    if !errorlevel! equ 0 (
        set "ADB=adb"
    ) else (
        set "ADB=adb"
    )
)

:DETECT_NET
:: Deteksi Konektivitas Jaringan Intranet ke Oracle ELLDEV
powershell -NoProfile -ExecutionPolicy Bypass -Command "$t = New-Object Net.Sockets.TcpClient; try { $t.Connect('10.10.214.48', 1521); $t.Close(); exit 0 } catch { exit 1 }" >nul 2>&1
if !errorlevel! equ 0 (
    set "SES_DB_MODE=ELLDEV"
    set "SES_DB_DESC=10.10.214.48:1521/ELLDEV (User: ses)"
    set "SES_NET_STATUS=ONLINE (Terhubung Intranet Kilang)"
    set "SES_SPRING_PROFILE=elldev"
) else (
    set "SES_DB_MODE=XE"
    set "SES_DB_DESC=localhost:1522/XEPDB1 (User: ses)"
    set "SES_NET_STATUS=OFFLINE (Dialihkan ke Oracle XE Lokal)"
    set "SES_SPRING_PROFILE=xe"
)

:MENU
cls
echo ==============================================================================
echo                 SECURE ENTRY SYSTEM (SES) - PT BADAK NGL
echo                         MASTER CONTROL CENTER
echo ==============================================================================
echo  Koneksi Intranet : !SES_NET_STATUS!
echo  Database Target  : !SES_DB_DESC! [Profil: !SES_SPRING_PROFILE!]
echo  Arsitektur Monorepo: 3-Tier (Platform, Intranet, Internet)
echo ==============================================================================
echo.
echo  --- RUNTIME SERVICES (Jalankan di Jendela Terpisah + Auto Chrome Guest) ---
echo    [1] Jalankan Intranet Backoffice Web  (ses-app  : http://localhost:8082)
echo    [2] Jalankan Internet Extranet Portal (ses-ext  : http://localhost:8083/ses-ext)
echo    [3] Jalankan RESTful API Gateway      (ses-api  : http://localhost:8084/api/v1)
echo    [4] Jalankan Mobile App (Flutter)     (ses-mobo : Auto USB HP / Emulator)
echo.
echo    [5] ^>^> LAUNCH SEMUA SERVICE WEB (Buka 3 Jendela Sekaligus + Chrome Guest)
echo    [K] HENTIKAN SELURUH SERVICE WEB (Kill Port 8082, 8083, 8084)
echo.
echo  --- BUILD DAN PACKAGING ---
echo    [6] Build Seluruh Proyek (Java Web Modules + Flutter APK)
echo    [7] Build Cepat Java Web Saja (mvn clean install -DskipTests)
echo    [8] Build APK Android Debug (flutter build apk)
echo    [9] Rebuild Pustaka Bersama (platform/ses-core)
echo.
echo  --- DIAGNOSTIK DAN DATABASE ---
echo    [10] Uji Koneksi Langsung ke Oracle ELLDEV / XE
echo    [R]  Refresh Deteksi Jaringan ^& Status Database
echo.
echo    [0] Keluar
echo ==============================================================================
set /p opt="Masukkan pilihan Anda [0-10 / K / R]: "
set "opt=!opt: =!"

if /i "!opt!"=="R" goto DETECT_NET
if /i "!opt!"=="K" goto KILL_SERVICES
if "!opt!"=="1" goto RUN_INTRANET
if "!opt!"=="2" goto RUN_INTERNET
if "!opt!"=="3" goto RUN_API
if "!opt!"=="4" goto RUN_MOBILE
if "!opt!"=="5" goto RUN_ALL
if "!opt!"=="6" goto BUILD_ALL
if "!opt!"=="7" goto BUILD_WEB
if "!opt!"=="8" goto BUILD_APK
if "!opt!"=="9" goto REBUILD_CORE
if "!opt!"=="10" goto TEST_DB
if "!opt!"=="0" goto EXIT_SCRIPT

echo.
echo [ERROR] Pilihan tidak valid. Silakan masukkan angka antara 0 sampai 10, K, atau R.
ping -n 3 127.0.0.1 >nul
goto MENU

:KILL_SERVICES
echo.
echo ==============================================================================
echo [INFO] Menghentikan Seluruh Service SES pada Port 8082, 8083, 8084...
echo ==============================================================================
for %%p in (8082 8083 8084) do (
    for /f "tokens=5" %%a in ('netstat -aon ^| findstr ":%%p" ^| findstr "LISTENING"') do (
        echo [KILL] Mematikan proses PID %%a pada port %%p...
        taskkill /F /T /PID %%a >nul 2>&1
    )
)
echo [SUKSES] Seluruh port runtime (8082, 8083, 8084) telah dibersihkan.
ping -n 3 127.0.0.1 >nul
goto MENU

:RUN_INTRANET
echo.
echo [INFO] Memeriksa ketersediaan port 8082...
for /f "tokens=5" %%a in ('netstat -aon ^| findstr ":8082" ^| findstr "LISTENING"') do (
    echo [INFO] Port 8082 sedang digunakan PID %%a. Menghentikan instance lama...
    taskkill /F /T /PID %%a >nul 2>&1
    ping -n 2 127.0.0.1 >nul
)
echo [INFO] Meluncurkan Intranet ses-app di jendela baru (Port: 8082, Profil: !SES_SPRING_PROFILE!)...
start "SES Intranet (:8082) [!SES_SPRING_PROFILE!]" cmd /k ""%~f0" _RUN_INTRANET "!SES_SPRING_PROFILE!""
echo [INFO] Menyiapkan peluncur otomatis Chrome Guest untuk http://localhost:8082...
start "" /min "%~f0" _OPEN_CHROME 8082 "http://localhost:8082"
ping -n 3 127.0.0.1 >nul
goto MENU

:RUN_INTERNET
echo.
echo [INFO] Memeriksa ketersediaan port 8083...
for /f "tokens=5" %%a in ('netstat -aon ^| findstr ":8083" ^| findstr "LISTENING"') do (
    echo [INFO] Port 8083 sedang digunakan PID %%a. Menghentikan instance lama...
    taskkill /F /T /PID %%a >nul 2>&1
    ping -n 2 127.0.0.1 >nul
)
echo [INFO] Meluncurkan Internet ses-ext di jendela baru (Port: 8083, Profil: !SES_SPRING_PROFILE!)...
start "SES Internet (:8083) [!SES_SPRING_PROFILE!]" cmd /k ""%~f0" _RUN_INTERNET "!SES_SPRING_PROFILE!""
echo [INFO] Menyiapkan peluncur otomatis Chrome Guest untuk http://localhost:8083/ses-ext...
start "" /min "%~f0" _OPEN_CHROME 8083 "http://localhost:8083/ses-ext"
ping -n 3 127.0.0.1 >nul
goto MENU

:RUN_API
echo.
echo [INFO] Memeriksa ketersediaan port 8084...
for /f "tokens=5" %%a in ('netstat -aon ^| findstr ":8084" ^| findstr "LISTENING"') do (
    echo [INFO] Port 8084 sedang digunakan PID %%a. Menghentikan instance lama...
    taskkill /F /T /PID %%a >nul 2>&1
    ping -n 2 127.0.0.1 >nul
)
echo [INFO] Meluncurkan REST API ses-api di jendela baru (Port: 8084, Profil: !SES_SPRING_PROFILE!)...
start "SES API (:8084) [!SES_SPRING_PROFILE!]" cmd /k ""%~f0" _RUN_API "!SES_SPRING_PROFILE!""
ping -n 3 127.0.0.1 >nul
goto MENU

:RUN_MOBILE
echo.
echo [INFO] Memeriksa perangkat Android (HP Fisik USB vs Android Studio Emulator)...
set "TARGET_DEV="
set "DEV_TYPE="

for /f "tokens=1,2" %%a in ('"%ADB%" devices ^| findstr /v "List of" ^| findstr "device$"') do (
    echo %%a | findstr /i "emulator-" >nul
    if !errorlevel! neq 0 (
        if "!TARGET_DEV!"=="" (
            set "TARGET_DEV=%%a"
            set "DEV_TYPE=USB"
        )
    ) else (
        if "!TARGET_DEV!"=="" (
            set "TARGET_DEV=%%a"
            set "DEV_TYPE=EMULATOR_RUNNING"
        )
    )
)

if "!DEV_TYPE!"=="USB" (
    echo [SUKSES] Terdeteksi Smartphone Fisik via USB Debugging: !TARGET_DEV!
    echo [INFO] Meluncurkan Flutter Mobile di jendela baru (Target USB: !TARGET_DEV!)...
    start "SES Mobile Runner (USB: !TARGET_DEV!)" cmd /k ""%~f0" _RUN_MOBILE "!TARGET_DEV!""
) else if "!DEV_TYPE!"=="EMULATOR_RUNNING" (
    echo [SUKSES] Terdeteksi Android Studio Emulator Aktif: !TARGET_DEV!
    echo [INFO] Meluncurkan Flutter Mobile di jendela baru (Target Emulator: !TARGET_DEV!)...
    start "SES Mobile Runner (Emulator: !TARGET_DEV!)" cmd /k ""%~f0" _RUN_MOBILE "!TARGET_DEV!""
) else (
    echo [INFO] Tidak ada HP Fisik USB atau Emulator aktif. Menyiapkan Android Studio SDK Emulator...
    start "SES Mobile Runner (Emulator Cold Boot)" cmd /k ""%~f0" _RUN_MOBILE "EMULATOR_COLD""
)
ping -n 3 127.0.0.1 >nul
goto MENU

:RUN_ALL
echo.
echo ==============================================================================
echo [INFO] Membersihkan port 8082, 8083, 8084 sebelum meluncurkan semua service...
echo ==============================================================================
for %%p in (8082 8083 8084) do (
    for /f "tokens=5" %%a in ('netstat -aon ^| findstr ":%%p" ^| findstr "LISTENING"') do (
        echo [INFO] Mematikan proses lama PID %%a pada port %%p...
        taskkill /F /T /PID %%a >nul 2>&1
    )
)
echo.
echo [INFO] Meluncurkan 3 Layanan Web Backend Secara Simultan (Profil: !SES_SPRING_PROFILE!):
echo   1. Intranet ses-app (:8082)
echo   2. Internet ses-ext (:8083)
echo   3. REST API ses-api (:8084)
echo ==============================================================================
start "SES Intranet (:8082) [!SES_SPRING_PROFILE!]" cmd /k ""%~f0" _RUN_INTRANET "!SES_SPRING_PROFILE!""
ping -n 3 127.0.0.1 >nul
start "SES Internet (:8083) [!SES_SPRING_PROFILE!]" cmd /k ""%~f0" _RUN_INTERNET "!SES_SPRING_PROFILE!""
ping -n 3 127.0.0.1 >nul
start "SES API (:8084) [!SES_SPRING_PROFILE!]" cmd /k ""%~f0" _RUN_API "!SES_SPRING_PROFILE!""

echo [INFO] Menyiapkan peluncur otomatis Chrome Guest untuk Intranet (:8082) dan Internet (:8083)...
start "" /min "%~f0" _OPEN_CHROME 8082 "http://localhost:8082"
start "" /min "%~f0" _OPEN_CHROME 8083 "http://localhost:8083/ses-ext"

echo.
echo [SUKSES] Ketiga server sedang booting di jendela masing-masing.
ping -n 4 127.0.0.1 >nul
goto MENU

:BUILD_ALL
cls
echo ==============================================================================
echo                 BUILD ALL (JAVA WEB MODULES DAN FLUTTER APK)
echo ==============================================================================
echo.
set "LOG_TMP=%TEMP%\ses_build_all_%RANDOM%.log"
echo [1/2] Membangun seluruh modul Java Web (Maven)...
call mvn clean install -DskipTests > "%LOG_TMP%" 2>&1
set "BUILD_ERR=%errorlevel%"
type "%LOG_TMP%"
if !BUILD_ERR! neq 0 (
    echo.
    echo [ERROR] Build Maven gagal dengan kode: !BUILD_ERR!
    call :LOG_ERROR "BUILD_MAVEN_ALL" !BUILD_ERR! "%LOG_TMP%"
    if exist "%LOG_TMP%" del "%LOG_TMP%" >nul 2>&1
    pause
    goto MENU
)
if exist "%LOG_TMP%" del "%LOG_TMP%" >nul 2>&1

echo.
echo [2/2] Membangun Mobile Flutter APK...
if exist "internet\ses-mobo\pubspec.yaml" (
    pushd internet\ses-mobo
    call flutter pub get
    set "APK_LOG_TMP=%TEMP%\ses_apk_%RANDOM%.log"
    call flutter build apk --debug > "%APK_LOG_TMP%" 2>&1
    set "APK_ERR=%errorlevel%"
    type "%APK_LOG_TMP%"
    if !APK_ERR! neq 0 (
        echo.
        echo [ERROR] Build APK gagal dengan kode: !APK_ERR!
        call :LOG_ERROR "BUILD_APK_ALL" !APK_ERR! "%APK_LOG_TMP%"
    )
    if exist "%APK_LOG_TMP%" del "%APK_LOG_TMP%" >nul 2>&1
    popd
)
echo.
echo ==============================================================================
echo                   BUILD PROSES SELESAI
echo ==============================================================================
pause
goto MENU

:BUILD_WEB
cls
echo ==============================================================================
echo                     BUILD JAVA WEB MODULES (MAVEN)
echo ==============================================================================
echo.
set "LOG_TMP=%TEMP%\ses_build_web_%RANDOM%.log"
call mvn clean install -DskipTests > "%LOG_TMP%" 2>&1
set "BUILD_ERR=%errorlevel%"
type "%LOG_TMP%"
if !BUILD_ERR! neq 0 (
    echo.
    echo [ERROR] Build Web Modules gagal dengan kode: !BUILD_ERR!
    call :LOG_ERROR "BUILD_WEB_MAVEN" !BUILD_ERR! "%LOG_TMP%"
) else (
    echo.
    echo [SUKSES] Seluruh modul web berhasil dibangun!
)
if exist "%LOG_TMP%" del "%LOG_TMP%" >nul 2>&1
pause
goto MENU

:BUILD_APK
cls
echo ==============================================================================
echo                      BUILD MOBILE FLUTTER APK
echo ==============================================================================
echo.
if exist "internet\ses-mobo\pubspec.yaml" (
    pushd internet\ses-mobo
    call flutter clean
    call flutter pub get
    set "LOG_TMP=%TEMP%\ses_build_apk_%RANDOM%.log"
    call flutter build apk --debug > "%LOG_TMP%" 2>&1
    set "APK_ERR=%errorlevel%"
    type "%LOG_TMP%"
    if !APK_ERR! equ 0 (
        echo.
        echo [SUKSES] APK berhasil dibangun di:
        echo internet\ses-mobo\build\app\outputs\flutter-apk\app-debug.apk
    ) else (
        echo.
        echo [ERROR] Build APK gagal dengan kode: !APK_ERR!
        call :LOG_ERROR "BUILD_FLUTTER_APK" !APK_ERR! "%LOG_TMP%"
    )
    if exist "%LOG_TMP%" del "%LOG_TMP%" >nul 2>&1
    popd
) else (
    echo [ERROR] Folder internet\ses-mobo tidak ditemukan!
)
pause
goto MENU

:REBUILD_CORE
cls
echo ==============================================================================
echo                    REBUILD PLATFORM / SES-CORE LIBRARY
echo ==============================================================================
echo.
set "LOG_TMP=%TEMP%\ses_rebuild_core_%RANDOM%.log"
call mvn clean install -pl platform/ses-core -DskipTests > "%LOG_TMP%" 2>&1
set "CORE_ERR=%errorlevel%"
type "%LOG_TMP%"
if !CORE_ERR! neq 0 (
    echo.
    echo [ERROR] Rebuild ses-core gagal dengan kode: !CORE_ERR!
    call :LOG_ERROR "REBUILD_CORE" !CORE_ERR! "%LOG_TMP%"
) else (
    echo.
    echo [SUKSES] Library ses-core terbaru berhasil dipasang ke .m2 lokal!
)
if exist "%LOG_TMP%" del "%LOG_TMP%" >nul 2>&1
pause
goto MENU

:TEST_DB
cls
echo ==============================================================================
echo               UJI KONEKSI LANGSUNG KE ORACLE (!SES_DB_MODE!)
echo ==============================================================================
echo.
echo Target: !SES_DB_DESC!
set "LOG_TMP=%TEMP%\ses_test_db_%RANDOM%.log"
python tests\database\test_connection.py > "%LOG_TMP%" 2>&1
set "DB_ERR=%errorlevel%"
type "%LOG_TMP%"
if !DB_ERR! neq 0 (
    echo.
    echo [WARN] Skrip Python gagal atau python/oracledb belum terpasang.
    echo Menjalankan tes koneksi cadangan melalui PowerShell TCP ping...
    powershell -Command "Test-NetConnection -ComputerName 10.10.214.48 -Port 1521" >> "%LOG_TMP%" 2>&1
    set "TCP_ERR=%errorlevel%"
    if !TCP_ERR! neq 0 (
        echo [ERROR] Koneksi database Oracle gagal diakses!
        call :LOG_ERROR "TEST_DATABASE_CONN" !DB_ERR! "%LOG_TMP%"
    )
) else (
    echo.
    echo [SUKSES] Koneksi ke database Oracle terverifikasi aktif!
)
if exist "%LOG_TMP%" del "%LOG_TMP%" >nul 2>&1
echo.
pause
goto MENU

:EXIT_SCRIPT
echo.
echo Terima kasih. Sampai jumpa!
exit /b 0

:: ==============================================================================
:: SUB-ROUTINES & LOGGING HELPERS
:: ==============================================================================

:LOG_ERROR
set "ERR_CATEGORY=%~1"
set "ERR_CODE=%~2"
set "ERR_TEMP_LOG=%~3"

if "!SES_DB_MODE!"=="" (
    powershell -NoProfile -ExecutionPolicy Bypass -Command "$t = New-Object Net.Sockets.TcpClient; try { $t.Connect('10.10.214.48', 1521); $t.Close(); exit 0 } catch { exit 1 }" >nul 2>&1
    if !errorlevel! equ 0 (
        set "SES_DB_MODE=ELLDEV"
        set "SES_DB_DESC=10.10.214.48:1521/ELLDEV (User: ses)"
        set "SES_SPRING_PROFILE=elldev"
    ) else (
        set "SES_DB_MODE=XE"
        set "SES_DB_DESC=localhost:1522/XEPDB1 (User: ses)"
        set "SES_SPRING_PROFILE=xe"
    )
)

if not exist "%~dp0docs\error-logs" mkdir "%~dp0docs\error-logs"

:: Ambil timestamp ISO
for /f %%t in ('powershell -NoProfile -Command "Get-Date -Format 'yyyy-MM-dd_HH-mm-ss'"') do set "ERR_TS=%%t"
if "%ERR_TS%"=="" set "ERR_TS=%date:~10,4%-%date:~4,2%-%date:~7,2%_%time:~0,2%-%time:~3,2%-%time:~6,2%"
set "ERR_TS=!ERR_TS: =0!"

set "ERR_MD_FILE=%~dp0docs\error-logs\ERROR_!ERR_TS!_%ERR_CATEGORY%.md"

(
    echo # Laporan Insiden Eror SES: %ERR_CATEGORY%
    echo.
    echo - **Waktu Insiden**: !ERR_TS!
    echo - **Modul / Kategori**: %ERR_CATEGORY%
    echo - **Exit Code**: %ERR_CODE%
    echo - **Status Database Target**: !SES_DB_MODE! ^(!SES_DB_DESC!^)
    echo - **Profil Spring Boot**: !SES_SPRING_PROFILE!
    echo - **Host System**: %USERNAME%@%COMPUTERNAME%
    echo.
    echo ---
    echo.
    echo ## Log Output / Stack Trace
    echo.
    echo ```text
    if exist "%ERR_TEMP_LOG%" (
        type "%ERR_TEMP_LOG%"
    ) else (
        echo Layanan atau perintah terhenti dengan Exit Code %ERR_CODE%.
        echo Silakan periksa pesan kegagalan atau stack trace pada konsol terminal aktif.
    )
    echo ```
    echo.
    echo ---
    echo.
    echo ## Panduan Investigasi Cepat
    echo 1. Periksa ketersediaan koneksi jaringan ke host database `10.10.214.48:1521` atau Oracle XE `localhost:1522`.
    echo 2. Jika eror terkait Maven compilation, jalankan Rebuild Pustaka Bersama `ses.bat [9]` terlebih dahulu.
    echo 3. Jika eror terkait port collision, jalankan Hentikan Seluruh Service `ses.bat [K]`.
    echo 4. Jika eror terkait Flutter/ADB, pastikan perangkat terdaftar pada `adb devices` dan emulator AVD siap.
) > "!ERR_MD_FILE!"

echo.
echo ==============================================================================
echo [ERROR LOGGED] Laporan insiden otomatis tersimpan di:
echo "!ERR_MD_FILE!"
echo ==============================================================================
echo.
exit /b

:: ==============================================================================
:: CHROME GUEST LAUNCHER & LISTENING WAITER
:: ==============================================================================

:DO_OPEN_CHROME
set "PORT=%~2"
set "URL=%~3"
title SES Waiter (:%PORT%) - Menunggu Port Aktif...
:: Poll status port hingga LISTENING (maksimal 60 detik)
for /L %%i in (1,1,60) do (
    netstat -ano | findstr ":%PORT%" | findstr "LISTENING" >nul 2>&1
    if !errorlevel! equ 0 (
        :: Tunggu 2 detik ekstra untuk memastikan bootstrap HTTP servlet siap
        ping -n 3 127.0.0.1 >nul
        call :OPEN_CHROME_GUEST "!URL!"
        exit 0
    )
    ping -n 2 127.0.0.1 >nul
)
:: Fallback jika timeout
call :OPEN_CHROME_GUEST "!URL!"
exit 0

:OPEN_CHROME_GUEST
set "TARGET_URL=%~1"
set "CHROME_BIN="
if exist "C:\Program Files\Google\Chrome\Application\chrome.exe" set "CHROME_BIN=C:\Program Files\Google\Chrome\Application\chrome.exe"
if not defined CHROME_BIN if exist "C:\Program Files (x86)\Google\Chrome\Application\chrome.exe" set "CHROME_BIN=C:\Program Files (x86)\Google\Chrome\Application\chrome.exe"
if not defined CHROME_BIN if exist "%LOCALAPPDATA%\Google\Chrome\Application\chrome.exe" set "CHROME_BIN=%LOCALAPPDATA%\Google\Chrome\Application\chrome.exe"
if not defined CHROME_BIN if exist "C:\Program Files\Microsoft\Edge\Application\msedge.exe" set "CHROME_BIN=C:\Program Files\Microsoft\Edge\Application\msedge.exe"
if not defined CHROME_BIN if exist "C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe" set "CHROME_BIN=C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe"

if defined CHROME_BIN (
    start "" "%CHROME_BIN%" --guest "%TARGET_URL%"
) else (
    start "" "%TARGET_URL%"
)
exit /b

:: ==============================================================================
:: INTERNAL EXECUTORS (Setiap service berjalan di jendela CMD terpisah)
:: ==============================================================================

:DO_RUN_INTRANET
set "PROF=%~2"
if "%PROF%"=="" set "PROF=local"
title SES Intranet (:8082) - ses-app [Profil: %PROF%]
cls
echo ==============================================================================
echo           SECURE ENTRY SYSTEM (SES) - PT BADAK NGL
echo             INTRANET WEB APPLICATION (ses-app)
echo ==============================================================================
echo  URL Akses  : http://localhost:8082
echo  Profil DB  : %PROF%
echo  Directory  : %~dp0intranet\ses-app
echo ==============================================================================
echo.
echo Menjalankan Spring Boot server di port 8082...
echo Tekan Ctrl+C untuk menghentikan server.
echo.
cd /d "%~dp0"
call mvn spring-boot:run -pl intranet/ses-app -Dspring-boot.run.profiles=%PROF%
set "APP_ERR=%errorlevel%"
if !APP_ERR! neq 0 (
    if !APP_ERR! neq 130 (
        if !APP_ERR! neq -1073741510 (
            echo.
            echo ==============================================================================
            echo [ERROR] Aplikasi ses-app terhenti dengan kode error: !APP_ERR!
            echo ==============================================================================
            call :LOG_ERROR "RUNTIME_INTRANET_8082" !APP_ERR! ""
        )
    )
)
echo.
echo [INFO] Sesi eksekusi selesai. Tekan sembarang tombol untuk menutup jendela...
pause >nul
exit /b

:DO_RUN_INTERNET
set "PROF=%~2"
if "%PROF%"=="" set "PROF=local"
title SES Internet (:8083) - ses-ext [Profil: %PROF%]
cls
echo ==============================================================================
echo           SECURE ENTRY SYSTEM (SES) - PT BADAK NGL
echo             INTERNET / EXTRANET PORTAL (ses-ext)
echo ==============================================================================
echo  URL Akses  : http://localhost:8083/ses-ext
echo  Profil DB  : %PROF%
echo  Directory  : %~dp0internet\ses-ext
echo ==============================================================================
echo.
echo Menjalankan Spring Boot server di port 8083...
echo Tekan Ctrl+C untuk menghentikan server.
echo.
cd /d "%~dp0"
call mvn spring-boot:run -pl internet/ses-ext -Dspring-boot.run.profiles=%PROF%
set "EXT_ERR=%errorlevel%"
if !EXT_ERR! neq 0 (
    if !EXT_ERR! neq 130 (
        if !EXT_ERR! neq -1073741510 (
            echo.
            echo ==============================================================================
            echo [ERROR] Aplikasi ses-ext terhenti dengan kode error: !EXT_ERR!
            echo ==============================================================================
            call :LOG_ERROR "RUNTIME_INTERNET_8083" !EXT_ERR! ""
        )
    )
)
echo.
echo [INFO] Sesi eksekusi selesai. Tekan sembarang tombol untuk menutup jendela...
pause >nul
exit /b

:DO_RUN_API
set "PROF=%~2"
if "%PROF%"=="" set "PROF=local"
title SES REST API (:8084) - ses-api [Profil: %PROF%]
cls
echo ==============================================================================
echo           SECURE ENTRY SYSTEM (SES) - PT BADAK NGL
echo             RESTFUL API GATEWAY (ses-api)
echo ==============================================================================
echo  URL Akses  : http://localhost:8084/api/v1
echo  Profil DB  : %PROF%
echo  Directory  : %~dp0internet\ses-api
echo ==============================================================================
echo.
echo Menjalankan Spring Boot server di port 8084...
echo Tekan Ctrl+C untuk menghentikan server.
echo.
cd /d "%~dp0"
call mvn spring-boot:run -pl internet/ses-api -Dspring-boot.run.profiles=%PROF%
set "API_ERR=%errorlevel%"
if !API_ERR! neq 0 (
    if !API_ERR! neq 130 (
        if !API_ERR! neq -1073741510 (
            echo.
            echo ==============================================================================
            echo [ERROR] Aplikasi ses-api terhenti dengan kode error: !API_ERR!
            echo ==============================================================================
            call :LOG_ERROR "RUNTIME_API_8084" !API_ERR! ""
        )
    )
)
echo.
echo [INFO] Sesi eksekusi selesai. Tekan sembarang tombol untuk menutup jendela...
pause >nul
exit /b

:DO_RUN_MOBILE
set "DEV_MODE=%~2"
title SES Mobile Runner (Flutter) - [Target: %DEV_MODE%]
cls
echo ==============================================================================
echo           SECURE ENTRY SYSTEM (SES) - PT BADAK NGL
echo                 SES MOBILE APPLICATION (Flutter)
echo ==============================================================================
echo  Target API : http://localhost:8084/api/v1 (Reverse via ADB)
echo  Perangkat  : %DEV_MODE%
echo  Directory  : %~dp0internet\ses-mobo
echo ==============================================================================
echo.
cd /d "%~dp0internet\ses-mobo"

if "%DEV_MODE%"=="EMULATOR_COLD" (
    echo [1/3] Menyalakan Android Studio Emulator (Medium_Phone_API_37.0)...
    call flutter emulators --launch Medium_Phone_API_37.0
    echo [2/3] Menunggu emulator online...
    "%ADB%" wait-for-device
    ping -n 8 127.0.0.1 >nul
    echo [3/3] Mengaktifkan ADB reverse port 8084...
    "%ADB%" reverse tcp:8084 tcp:8084
    echo.
    echo Menjalankan Flutter App di emulator...
    call flutter run
) else (
    echo [1/2] Mengaktifkan ADB reverse port 8084 pada device %DEV_MODE%...
    "%ADB%" -s %DEV_MODE% reverse tcp:8084 tcp:8084
    echo [2/2] Menjalankan Flutter App pada device %DEV_MODE%...
    echo.
    call flutter run -d %DEV_MODE%
)
set "MOB_ERR=%errorlevel%"
if !MOB_ERR! neq 0 (
    if !MOB_ERR! neq 130 (
        if !MOB_ERR! neq -1073741510 (
            echo.
            echo ==============================================================================
            echo [ERROR] Flutter run terhenti dengan kode error: !MOB_ERR!
            echo ==============================================================================
            call :LOG_ERROR "RUNTIME_FLUTTER_MOBILE" !MOB_ERR! ""
        )
    )
)
echo.
echo [INFO] Sesi Flutter selesai. Tekan sembarang tombol untuk menutup jendela...
pause >nul
exit /b
