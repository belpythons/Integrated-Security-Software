# ======================================================================
# SES SECURITY SUITE MOBILE - PT BADAK NGL
# Interactive Development & Runtime Runner (PowerShell)
# ======================================================================

$ErrorActionPreference = "Continue"

# Resolve ADB path
$AdbPath = "D:\GIT\GitHub\AndroidSDK\platform-tools\adb.exe"
if (-not (Test-Path $AdbPath)) {
    $found = Get-Command "adb" -ErrorAction SilentlyContinue
    if ($found) {
        $AdbPath = $found.Source
    } else {
        $AdbPath = "adb"
    }
}

function Show-Menu {
    Clear-Host
    Write-Host "======================================================================" -ForegroundColor Cyan
    Write-Host "          SES SECURITY SUITE MOBILE - PT BADAK NGL" -ForegroundColor Yellow
    Write-Host "             Interactive Development & Runtime Tool" -ForegroundColor Cyan
    Write-Host "======================================================================" -ForegroundColor Cyan
    Write-Host ""
    Write-Host "  [1] Run Live di HP Fisik (Infinix via USB - Hot Reload Aktif)" -ForegroundColor Green
    Write-Host "  [2] Launch Emulator AVD & Run (Medium Phone API 37)" -ForegroundColor Green
    Write-Host "  [3] Build Debug APK dan Auto-Install ke HP Terhubung" -ForegroundColor Yellow
    Write-Host "  [4] Jalankan Rangkaian Pengujian Otomatis (flutter test & analyze)" -ForegroundColor Cyan
    Write-Host "  [5] Ambil Screenshot dari HP Fisik (Simpan ke screenshots\)" -ForegroundColor Magenta
    Write-Host "  [6] Bersihkan Cache & Perbaiki Lockfile (flutter clean & pub get)" -ForegroundColor DarkYellow
    Write-Host "  [0] Keluar" -ForegroundColor Gray
    Write-Host ""
    Write-Host "======================================================================" -ForegroundColor Cyan
}

do {
    Show-Menu
    $choice = Read-Host "Pilih opsi [0-6]"

    switch ($choice) {
        "1" {
            Clear-Host
            Write-Host "[INFO] Memeriksa perangkat terhubung..." -ForegroundColor Cyan
            & $AdbPath devices
            Write-Host "`n[INFO] Menyiapkan reverse proxy port 8084 untuk koneksi ke backend ses-api..." -ForegroundColor Cyan
            & $AdbPath -s 10275333AD005736 reverse tcp:8084 tcp:8084
            Write-Host ""
            Write-Host "[INFO] Menjalankan SES Mobile di HP Fisik..." -ForegroundColor Green
            Write-Host "       Tekan 'r' untuk Hot Reload, 'R' untuk Restart, 'q' untuk keluar." -ForegroundColor Yellow
            flutter run -d 10275333AD005736
            Write-Host "`nTekan tombol apa saja untuk kembali ke menu..." -ForegroundColor Gray
            $null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")
        }
        "2" {
            Clear-Host
            Write-Host "[INFO] Menjalankan Android Emulator AVD (Medium_Phone_API_37.0)..." -ForegroundColor Cyan
            flutter emulators --launch Medium_Phone_API_37.0
            Start-Sleep -Seconds 8
            Write-Host "[INFO] Menjalankan SES Mobile di emulator..." -ForegroundColor Green
            flutter run -d emulator-5554
            Write-Host "`nTekan tombol apa saja untuk kembali ke menu..." -ForegroundColor Gray
            $null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")
        }
        "3" {
            Clear-Host
            Write-Host "[INFO] Membangun Debug APK..." -ForegroundColor Cyan
            flutter build apk --debug
            if ($LASTEXITCODE -eq 0) {
                Write-Host "`n[INFO] Menginstal APK ke perangkat Infinix..." -ForegroundColor Green
                & $AdbPath -s 10275333AD005736 install -r build\app\outputs\flutter-apk\app-debug.apk
                Write-Host "`n[INFO] Membuka aplikasi di layar ponsel..." -ForegroundColor Green
                & $AdbPath -s 10275333AD005736 shell am start -n com.example.ses_masuk_barang/.MainActivity
                Write-Host "`n[SUKSES] Aplikasi aktif di layar HP Infinix!" -ForegroundColor Green
            } else {
                Write-Host "`n[ERROR] Build APK gagal!" -ForegroundColor Red
            }
            Write-Host "`nTekan tombol apa saja untuk kembali ke menu..." -ForegroundColor Gray
            $null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")
        }
        "4" {
            Clear-Host
            Write-Host "======================================================================" -ForegroundColor Cyan
            Write-Host "                  RUNNING AUTOMATED TESTS & ANALYZE" -ForegroundColor Yellow
            Write-Host "======================================================================" -ForegroundColor Cyan
            Write-Host "`n[1/2] Menjalankan flutter analyze..." -ForegroundColor Cyan
            flutter analyze
            Write-Host "`n[2/2] Menjalankan flutter test..." -ForegroundColor Cyan
            flutter test
            Write-Host "`nTekan tombol apa saja untuk kembali ke menu..." -ForegroundColor Gray
            $null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")
        }
        "5" {
            Clear-Host
            if (-not (Test-Path "screenshots")) { New-Item -ItemType Directory -Path "screenshots" | Out-Null }
            $timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
            $targetPath = "screenshots\screenshot_$timestamp.png"
            Write-Host "[INFO] Mengambil screenshot dari HP..." -ForegroundColor Cyan
            & $AdbPath -s 10275333AD005736 shell screencap -p /sdcard/temp_screen.png
            & $AdbPath -s 10275333AD005736 pull /sdcard/temp_screen.png $targetPath
            & $AdbPath -s 10275333AD005736 shell rm /sdcard/temp_screen.png
            Write-Host "`n[SUKSES] Screenshot tersimpan di: $targetPath" -ForegroundColor Green
            Write-Host "`nTekan tombol apa saja untuk kembali ke menu..." -ForegroundColor Gray
            $null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")
        }
        "6" {
            Clear-Host
            Write-Host "[INFO] Membersihkan build cache dan memperbarui paket..." -ForegroundColor Yellow
            flutter clean
            flutter pub get
            Write-Host "`n[SUKSES] Cache dibersihkan!" -ForegroundColor Green
            Write-Host "`nTekan tombol apa saja untuk kembali ke menu..." -ForegroundColor Gray
            $null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")
        }
        "0" {
            Write-Host "`nKeluar dari Dev Runner. Selamat beraktivitas!" -ForegroundColor Cyan
            break
        }
    }
} while ($choice -ne "0")

