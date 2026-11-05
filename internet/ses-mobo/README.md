# SES Security Suite Mobile (ses-mobo)
### Aplikasi Manajemen Gerbang Keamanan & Perizinan Masuk Barang — PT. Seterah (PT. S)

Aplikasi seluler Flutter untuk petugas keamanan (*Gate Security Officer*) di posko gerbang PT. Seterah (PT. S) guna melakukan validasi izin masuk barang/material (*Material Entry Permit*), pemindaian barcode QR secara riil, pencatatan supir & armada truk, inspeksi fisik muatan berbasis SOP kilang, serta pelacakan kendaraan aktif di dalam area kilang (*On-Site Fleet Tracking*).

---

## 🚀 Quick Start Development

Untuk mempermudah menjalankan project selama tahap pengembangan, gunakan skrip menu interaktif All-in-One:

### 1. Jalankan via Double-Click (Windows)
Cukup buka berkas **[`dev.bat`](file:///d:/GIT/Gitea/ses-mobo/dev.bat)** di File Explorer Windows Anda.

### 2. Jalankan via PowerShell Terminal
```powershell
./dev.ps1
```

### Opsi Menu Interaktif:
```text
  [1] Run Live di HP Fisik (Infinix via USB - Hot Reload Aktif)
  [2] Launch Emulator AVD & Run (Medium Phone API 37)
  [3] Build Debug APK dan Auto-Install ke HP Terhubung
  [4] Jalankan Rangkaian Pengujian Otomatis (flutter test & analyze)
  [5] Ambil Screenshot dari HP Fisik (Simpan ke screenshots\)
  [6] Bersihkan Cache & Perbaiki Lockfile (flutter clean & pub get)
  [0] Keluar
```

---

## 📱 Panduan Lengkap Lingkungan & Runtime

Panduan mendalam mengenai pengaturan USB debugging, emulator Android AVD, perintah Hot Reload/Hot Restart, panduan build APK debug & release, serta *troubleshooting* lingkungan Windows tersedia di:
👉 **[`docs/panduan_development_dan_runtime.md`](file:///d:/GIT/Gitea/ses-mobo/docs/panduan_development_dan_runtime.md)**

---

## ✨ Fitur Utama Aplikasi

- **Material 3 Design System & Ergonomi Lapangan:**
  - Layout *breathable* dengan pembagian *white space* luas, sudut melengkung halus (16dp), dan kontras tinggi untuk pemakaian di bawah terik matahari pos gerbang.
- **Pemindai Barcode Kamera Riil:**
  - Integrasi pustaka kamera pemindai [`mobile_scanner`](https://pub.dev/packages/mobile_scanner) dengan garis laser animasi, kontrol lampu senter (*torch*), serta deteksi format QR Code.
- **SOP Kilang & Keamanan Terintegrasi:**
  - *Same-Day Re-entry Bypass*: Otomatis mendeteksi supir dan truk yang telah lolos inspeksi di hari yang sama untuk menghindari inspeksi berulang.
  - *Price Masking*: Menyembunyikan nominal harga dan nilai komersial barang pada PO demi kerahasiaan data pengadaan.
  - *Document & Fleet Validation*: Peringatan dan pemblokiran otomatis untuk supir dengan SIM kedaluwarsa atau truk berstatus *Maintenance Lockout*.
- **Tombol Akselerasi Demonstrasi (Demo Shortcuts):**
  - **Quick Sign-In**: Akses instan petugas Dedi (Gate 1).
  - **Sample Permits Bottom Sheet**: Kartu QR sampel untuk berbagai skenario izin (Valid, Expired, Canceled, Ready to Check-Out).
  - **"SUPER FAST PASS"**: Menerbitkan tiket izin masuk instan untuk kebutuhan presentasi kilat.
  - **"ALL FIT" & "Attach Sample Photos"**: Pengisian instan 10 poin checklist inspeksi beserta foto bukti kendaraan dummy.

---

## 🧪 Validasi Kualitas & Status Pengujian

- **Flutter Analyze:** `No issues found!` (0 lint error/warning).
- **Flutter Test Suites:** `36/36 tests passed` (100% kelulusan di 9 berkas pengujian).
- **Target Device Teruji:** Infinix Hot 30 (X6833B - Android 13/14) & Android AVD API 37.

---

## 📂 Struktur Direktori Utama

```text
ses-mobo/
├── android/              # Konfigurasi Gradle Android & AndroidManifest
├── assets/               # Aset gambar, ikon, dan dummy barcode
├── dev.bat               # Skrip interaktif launcher Windows Batch
├── dev.ps1               # Skrip interaktif launcher PowerShell
├── docs/                 # Dokumentasi blueprint spesifikasi & panduan dev
│   ├── master_spesifikasi_blueprint_modul_izin_barang_masuk_ses_revisi_final_audio.md
│   └── panduan_development_dan_runtime.md
├── lib/
│   ├── core/             # Design tokens warna (AppColors) & Tema (AppTheme) M3
│   ├── models/           # Data models (GatePermitRequest, Driver, Vehicle, Checklist)
│   ├── pages/            # Seluruh antarmuka layar (Login, Gate, Scanner, Detail, dll.)
│   ├── services/         # MockPermitService dataset & state management posko
│   └── main.dart         # Entry point aplikasi Flutter
├── screenshots/          # Tangkapan layar otomatis dari perangkat fisik/emulator
└── test/                 # 9 unit & widget test suites (36 kasus uji)
```
