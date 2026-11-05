# SES Testing & Quality Assurance Suite

Direktori ini menampung seluruh skenario pengujian, data uji, skrip verifikasi otomatis, dan pengujian integrasi antarmuka untuk aplikasi **SES (Secure Entry System) Badak LNG**.

---

## 1. Struktur Pengujian

| Direktori | Cakupan Pengujian | Cara Menjalankan |
|---|---|---|
| **`tests/api/`** | Pengujian REST API Gateway (`ses-api`) untuk autentikasi JWT, submit izin masuk barang, scan QR | Skrip verifikasi cURL / Postman / Node |
| **`tests/integration/`** | Pengujian alur *cross-module* antara Intranet, Internet/Extranet, dan Mobile | `mvn test` atau skrip otomasi |
| **`tests/performance/`** | Benchmark kecepatan respon LCP, throughput database, dan query plan Oracle | Audit log dan monitoring report |
| **Dokumentasi E2E GUI** | Protokol & Temuan Pengujian GUI Browser Lengkap (Tanpa Hapus Data Seeder) | Lihat [`docs/testing/e2e-gui-test-protocol.md`](../docs/testing/e2e-gui-test-protocol.md) dan [`docs/testing/e2e-test-findings-report.md`](../docs/testing/e2e-test-findings-report.md) |

---

## 2. Pengujian Unit & Modul Bawaan (Maven)

Unit test bawaan tetap berada di dalam masing-masing modul Maven:
- `platform/ses-core/src/test/` : Pengujian domain repository, entity validation, workflow engine.
- `intranet/ses-app/src/test/` : Pengujian controller, Thymeleaf view rendering, Spring Security auth.
- `internet/ses-ext/src/test/` : Pengujian validasi token upload berkas eksternal dan verifikasi status.
- `internet/ses-api/src/test/` : Pengujian REST endpoints, JWT filter, dan CORS.
- `internet/ses-mobo/test/` : Pengujian unit & widget Flutter mobile app (`flutter test`).

Untuk menjalankan seluruh rangkaian pengujian:
```cmd
ses.bat (pilih menu [6] untuk Build All & Unit Test atau menu [10] untuk Uji Koneksi DB)
:: atau manual via maven:
mvn test
```

