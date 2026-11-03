@echo off
setlocal
title Rebuild ses-core

echo =======================================================
echo          REBUILDING SES-CORE (MAVEN INSTALL)
echo =======================================================
echo.

cd /d "%~dp0ses-core"

echo Menjalankan: mvn clean install -DskipTests ...
echo.
call mvn clean install -DskipTests

if %ERRORLEVEL% equ 0 (
    echo.
    echo =======================================================
    echo  [SUCCESS] ses-core berhasil di-build dan di-install!
    echo  Library terbaru sudah tersimpan di .m2 repository.
    echo  ses-app dan ses-ext kini siap dijalankan kembali.
    echo =======================================================
) else (
    echo.
    echo =======================================================
    echo  [ERROR] Build ses-core GAGAL! Periksa log error di atas.
    echo =======================================================
)

echo.
pause
