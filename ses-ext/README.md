# ses-ext — Gateway Eksternal DMZ

Modul Maven kedua dari aplikasi SES: WAR terpisah yang dijalankan sebagai JVM independen untuk mengekspos **satu-satunya halaman di SES yang bisa diakses tanpa login**. Dokumen ini membahas bagaimana *kode* modul ini disusun. Untuk konteks lain, lihat:
- [README.md](../README.md) — alur bisnis "External Contractor 1-Session Document Upload" secara umum
- [docs/database-schema.md](../docs/database-schema.md) — tabel `SES_EXT_UPLOAD_REQUEST`/`SES_EXT_UPLOAD_DOC` yang dimiliki modul ini, plus `SES_REQUEST` yang dibacanya lewat token
- [docs/DOKUMENTASI_FITUR_PDF_SPLITTER_BUNDLE_UPLOAD.md](../docs/DOKUMENTASI_FITUR_PDF_SPLITTER_BUNDLE_UPLOAD.md) — fitur PDF splitter yang tertanam di halaman upload modul ini

---

## 1. Peran modul ini

`ses-ext` adalah **gateway publik** yang sengaja dipisah dari `ses-app` menjadi WAR dan proses JVM sendiri (port `8083`, context-root `/ses-ext`), karena ia akan diletakkan di DMZ — dapat diakses dari luar jaringan intranet tanpa VPN dan tanpa akun SES. Pemisahan ini bukan sekadar organisasi kode, tapi batas keamanan: kalau gateway ini sampai disusupi, penyerang tidak otomatis mendapat akses ke aplikasi intranet (`ses-app`) karena keduanya adalah proses dan WAR yang berbeda, walau berbagi database Oracle yang sama.

Modul ini **tidak mendefinisikan entity, repository, atau service sendiri**. Semua logic domain (resolusi token, staging dokumen, approval, mail) datang dari `ses-core` — lihat [ses-core/README.md](../ses-core/README.md). `ses-ext` hanya berisi:
- 2 controller yang mengekspos sebagian kecil endpoint `ses-core` ke publik
- Konfigurasi Spring Security yang mengunci semuanya selain endpoint itu
- Konfigurasi Spring Boot (`SesExtApplication`) yang membatasi component-scan agar tidak ikut menjalankan scheduler/cron milik `ses-app`
- Template & aset statis untuk 4 halaman publik

### 1.1 Apa yang dibagi dengan `ses-app`, apa yang tidak

| | `ses-app` | `ses-ext` |
|---|---|---|
| Port / context-path | `8082` / `/ses` (lihat `ses-app/src/main/resources/application.properties:10`) | `8083` / `/ses-ext` ([application.properties:7-8](src/main/resources/application.properties#L7-L8)) |
| Database | Sama-sama Oracle `ELLDEV` (`10.10.214.48:1521`), skema `ses` | Sama persis |
| Login / RBAC | LDAP + LOCAL, menu/role Platform | **Tidak ada** — `app.ldap.enabled=false` ([application.properties:108](src/main/resources/application.properties#L108)) |
| Scheduler (Quartz cron `APP_CAT`/`APP_JOB`) | Aktif, mengirim mail | Dikecualikan secara eksplisit dari component-scan (lihat §2) |
| Kirim email (`APP_MAIL` → SMTP) | Satu-satunya pengirim | Hanya **menulis** baris ke `APP_MAIL` (`ses.mail.dispatch.enabled=false`, [application.properties:78](src/main/resources/application.properties#L78)) |

---

## 2. `SesExtApplication` — kenapa component-scan-nya aneh

[`SesExtApplication.java`](src/main/java/com/badaklng/ses/ext/SesExtApplication.java) men-scan paket service/repository/entity `ses-core` yang relevan (request, approval, payment, scheduling, file, mail, user) — bukan `com.badaklng` secara umum — dan secara eksplisit **mengecualikan** dua kelas:

- `AppJobSchedulerConfig` (Platform) — kalau ikut ter-scan, modul ini akan menjalankan salinan sendiri dari semua jadwal `APP_CAT[JOB_SCHEDULE]` di JVM terpisah, termasuk job pengirim mail — menyebabkan **setiap email terkirim dua kali** (sekali dari `SesMailJob` milik `ses-app`, sekali dari `MailJob` mentah milik Platform).
- `SesMailJobRegistration` — alasan sama.

Tapi `com.badaklng.ses.mail` **tetap** ikut di-scan (bukan dikecualikan), karena `SesMailDispatcherService` adalah dependency constructor wajib bagi `ApprovalService`/`RequestService` yang dipakai `ses-ext` — menyelesaikan upload kontraktor memicu `RequestService.triggerApproval()` yang menulis baris mail untuk approver pertama. Baris itu **harus tetap tertulis** ke `APP_MAIL`, hanya *pengirimannya* yang dimatikan lewat flag `ses.mail.dispatch.enabled=false` di `application.properties`, bukan lewat exclude-filter. Detail penalaran ini ada di komentar javadoc kelas tersebut — baca langsung sebelum mengubah component-scan ini.

---

## 3. Alur token upload (ujung ke ujung)

Token diterbitkan dan divalidasi oleh [`UploadTokenService`](../ses-core/src/main/java/com/badaklng/request/service/UploadTokenService.java) di `ses-core` (dipakai bersama oleh `ses-app` saat submit request dan `ses-ext` saat halaman upload dibuka). Yang perlu diketahui dari sisi `ses-ext`:

1. **Token**: 256-bit random (`SecureRandom`, 64 karakter hex) — bukan UUID, karena UUID hanya membawa 122-bit entropy dan membuka celah "diam-diam diganti ke UUID versi lemah" di masa depan.
2. **Resolusi** (`UploadTokenService.resolve(token, sessionId)`) memeriksa 5 syarat sekaligus — token cocok (constant-time compare), belum dipakai (`UPLOAD_TOKEN_USED='N'`), belum kedaluwarsa (`UPLOAD_TOKEN_EXPIRY`, default **7 hari** — `UploadTokenService.VALID_DAYS`), request masih berstatus `WAITING_FOR_UPLOAD`, dan **terikat ke satu HTTP session** (binding in-memory `ConcurrentHashMap<token, sessionId>` — begitu dibuka di satu browser, session lain ditolak).
3. **Tidak terpakai saat GET** — membuka link saja tidak membakar token (`RequestUploadController.form`, baris 101-110), supaya kontraktor bisa buka-tutup tab berkali-kali selama 7 hari.
4. **Upload per-file via AJAX** (`POST /request/upload-docs/file/save-draft`) menyimpan file ke staging (`SES_EXT_UPLOAD_DOC` lewat `ExtUploadStagingService`) **begitu dipilih**, bukan menunggu submit — supaya proses bisa dilanjutkan lintas sesi/hari tanpa kehilangan file yang sudah diunggah.
5. **Finalize** (`POST /request/upload-docs`) men-token ulang di server (tidak percaya hidden field dari GET sebelumnya — request bisa saja sudah dibatalkan/disapu `RequestExpiryJob` di antaranya), lalu memanggil `RequestService.finalizeUpload(requestId, actor)`. Fungsi ini: memverifikasi semua dokumen wajib sudah ada, memindahkan dokumen dari staging ke `SES_REQUEST_WORKER_DOC`, lalu `UploadTokenService.markUsed()` membakar token (dan menghapus binding session-nya) dan memicu `ApprovalService`/`triggerApproval()` untuk memulai approval chain (lihat [docs/database-schema.md §3.1](../docs/database-schema.md#31-approval-engine-generic-reused-by-every-module)).
6. **Penolakan generik** — token salah, sudah dipakai, kedaluwarsa, atau status request sudah berubah semuanya menampilkan pesan yang **sama persis** (`request/upload-invalid`). Membedakan pesan akan membocorkan ke penebak bahwa sebuah token pernah ada.

---

## 4. Controller & rute

| Controller | Rute | Method | Keterangan |
|---|---|---|---|
| [`RequestUploadController`](src/main/java/com/badaklng/request/controller/RequestUploadController.java) | `GET /request/upload-docs?token=...` | — | Render form upload, atau halaman `upload-invalid` bila token gagal resolve. |
| | `POST /request/upload-docs/file/save-draft` | AJAX, `multipart/form-data` | Simpan 1 file ke staging segera setelah dipilih (ownership dicek via `RequestService.assertUploadTarget` — worker harus milik request ini, dokumen harus salah satu yang diminta tipe request). |
| | `POST /request/upload-docs/file/delete` | AJAX | Hapus 1 file staging (`extDocId`, bukan `fileId` — dokumen carry-forward dari request renewal sebelumnya tidak punya baris staging dan memang tidak boleh dihapus dari sini). |
| | `GET /request/upload-docs/file?token=...&fileId=...` | — | Preview/stream dokumen yang sudah terunggah (staging ATAU dokumen existing dari request yang sama). Dilindungi token yang sama, **bukan** endpoint `/file/{id}` generik milik `ses-app` yang menuntut session login. |
| | `POST /request/upload-docs` | form submit | Finalize: cek kelengkapan dokumen wajib, pindahkan staging → permanen, bakar token, picu approval chain. |
| [`RequestVerifyController`](src/main/java/com/badaklng/request/controller/RequestVerifyController.java) | `GET /verify/{requestNo}` | — | Halaman publik hasil scan QR pada dokumen izin cetak. Menampilkan ringkasan kontrak + daftar pekerja, **menyembunyikan** NIK/data pribadi. Render tanpa layout decorator (tanpa sidebar/navbar) karena ditujukan dibuka di HP di gerbang. |

---

## 5. Template & aset statis

```
src/main/resources/
├── templates/request/
│   ├── upload-docs.html      # Form upload utama (2 kolom: PDF Splitter Workbench | daftar pekerja + slot dokumen)
│   ├── upload-success.html   # Halaman sukses setelah finalize
│   ├── upload-invalid.html   # Halaman penolakan generik (token invalid/expired/used)
│   └── verify.html           # Halaman publik hasil scan QR
└── static/                   # BELUM di-commit (lihat git status) — aset client-side fitur PDF Splitter
    ├── css/pdf-splitter.css
    ├── js/pdf-splitter-workbench.js
    └── vendor/
        ├── pdf-lib/pdf-lib.min.js      # ekstraksi halaman PDF, 100% di browser
        └── pdfjs/pdf.min.js, pdf.worker.min.js   # render viewer PDF
```

`upload-docs.html` adalah satu-satunya template di `ses-ext` yang mengintegrasikan **PDF Splitter & Bundle Upload** (kontraktor sering punya satu PDF bundel besar berisi semua dokumen pekerja). Workbench-nya berjalan sepenuhnya di browser (pdf-lib.js + pdf.js, lokal/offline — cocok untuk lingkungan air-gapped kilang) — server hanya pernah menerima potongan kecil hasil split lewat `POST /file/save-draft` yang sama dengan upload manual, bukan file bundel 200MB-nya. Detail lengkap arsitektur & UX ada di [docs/DOKUMENTASI_FITUR_PDF_SPLITTER_BUNDLE_UPLOAD.md](../docs/DOKUMENTASI_FITUR_PDF_SPLITTER_BUNDLE_UPLOAD.md); jangan diduplikasi di sini.

Vendor libs ditaruh lokal (bukan CDN) secara sengaja — persyaratan compliance jaringan intranet/air-gapped.

---

## 6. Keamanan — hal yang benar-benar ada di kode (bukan asumsi)

Dari [`SesExtSecurityConfig`](src/main/java/com/badaklng/ses/ext/config/SesExtSecurityConfig.java):
- **Default-deny**: hanya `/request/upload-docs/**`, `/verify/**`, aset statis, dan `/actuator/health|info` yang `permitAll()`; `anyRequest().denyAll()` menutup sisanya.
- **Tidak ada LDAP, tidak ada form login, tidak ada session berbasis Spring Security** — sumber otorisasi tunggal adalah token 256-bit di query string, divalidasi oleh `UploadTokenService` di level controller, bukan di level filter security.
- **CSRF tetap aktif** — sengaja tidak dimatikan meski halaman publik, karena Spring tetap menerbitkan CSRF token ke session anonim dan Thymeleaf `th:action` otomatis menyisipkannya; mematikannya "karena halaman ini publik" justru salah arah (lihat javadoc `RequestUploadController`, baris 64-67).
- **Larangan eksplisit dalam kode** (javadoc `RequestUploadController`, baris 48-62): controller ini tidak boleh memanggil `SecurityUtils` (tidak ada principal, bisa salah lolos sebagai "passing"), tidak boleh merender link `/file/{id}` generik (menuntut session), dan tidak boleh menampilkan data yang tidak sudah terungkap lewat email (nilai kontrak, email requestor, riwayat approval harus tetap tersembunyi).
- **IDOR diblok via `assertUploadTarget`**: worker harus benar-benar milik request yang sama dengan token, dan `docCode` harus salah satu yang diminta tipe request — percobaan akses dokumen lintas-request dicatat sebagai warning tapi direspons dengan pesan generik yang sama (tidak membocorkan detail ke penyerang).
- **1-session binding**: token yang sudah dibuka di satu browser session ditolak jika dicoba dibuka dari session lain — mencegah link yang "bocor"/diteruskan dipakai dua pihak sekaligus.

---

## 7. Menjalankan secara lokal

```bash
mvn spring-boot:run -pl ses-ext
```
→ `http://localhost:8083/ses-ext`. Butuh `ses-ext/src/main/resources/application-local.properties` (gitignored, copy dari `.example`) berisi kredensial DB yang sama dengan `ses-app`. Lihat [README.md §Running & Building](../README.md#-running--building).
