# ses-app — Aplikasi Internal (Intranet)

Modul Spring Boot yang menjadi **aplikasi internal SES** untuk pegawai/officer PT Badak LNG — berjalan di port `8082` dengan context-root `/ses`, dipaketkan jadi `ses-app.war` untuk deploy ke WebLogic 12c. Untuk daftar fitur bisnis lengkap lihat [README.md](../README.md); untuk struktur tabel/view database lihat [docs/database-schema.md](../docs/database-schema.md). Dokumen ini khusus membahas **bagaimana kode di ses-app sendiri diorganisir** — controller, template, config, auth — dengan asumsi pembaca sudah paham fitur bisnis dan skema DB dari dua dokumen tersebut.

ses-app adalah lapisan **presentasi (web/MVC)**: hampir semua logika domain (entity, repository, service, aturan bisnis) hidup di modul `ses-core` dan di-`import` sebagai dependency Maven. Controller di sini pada dasarnya tipis — mengurus routing, keamanan halaman (`PageAccessGuard`), binding form, dan rendering Thymeleaf — lalu mendelegasikan ke service `ses-core`. Autentikasi memakai dua jalur paralel (lihat `com.badaklng.ses.config.SecurityConfig`):
- **LDAP** (`SesUserProvisioningProvider`) — bind ke `rldapbtg.badaklng.com`/`.home`, fallback dua base DN (`o=users` lalu `o=jkt`), auto-provisioning `APP_USER` dengan role default `REQUESTOR` saat login LDAP pertama kali berhasil.
- **LOCAL** (`CustomAuthenticationProvider`, dari `platform`) — password tersimpan di `APP_USER.PASSWORD`, dipakai untuk akun admin/test.

Kedua provider didaftarkan bersama di `AuthenticationManagerBuilder`; Spring Security mencoba keduanya sampai salah satu berhasil.

---

## 1. Peta controller per fitur

| Package / Controller | Base route | Tanggung jawab | Bergantung pada (`ses-core`) |
|---|---|---|---|
| `request.controller.RequestController` | `/request` | List/form/view pengajuan badge, submit/draft/cancel, render PDF izin (`/{id}/document.pdf`), QR code, pencarian AJAX (kontrak, pegawai, worker lookup, evaluator keamanan), penyimpanan worker per-baris via modal (`/draft/ensure`, `/{id}/worker/save`) | `RequestService`, `RequestDocumentService`, `RequestQrCodeService`, `RequestTypeService`, `SecurityEvaluatorService`, `ApprovalService`, `SesWorkerLookupViewRepository`, `ContractVRepository`, `AppEmpLkpViewRepository` |
| `request.controller.RequestTypeController` | `/admin/request-type` | Admin: CRUD jenis pengajuan (`SES_REQUEST_TYPE`) — nama, biaya, flag butuh approval manajer/procurement, dokumen wajib per tipe | `SesRequestTypeRepository`, entity `SesRequestType`/`SesRequestTypeDoc` |
| `sticker.controller.StickerController` | `/sticker` | Setara `RequestController` tapi untuk pengajuan stiker kendaraan — list/form/view, renew, autocomplete worker/driver history | `StickerService`, `ApprovalService`, `UserScopeService`, `UserDisplayNameService` |
| `sticker.controller.StickerTypeController` | `/admin/sticker-type` | Admin: CRUD jenis stiker + harga + dokumen wajib | `SesStickerTypeRepository` |
| `approval.controller.ApprovalController` | `/approval` | Inbox approval pribadi, detail approve/reject/skip, checkpoint (`PAYMENT_VERIFICATION`, `SCREENING_SCHEDULE`, `BADGE_ISSUANCE`) | `ApprovalService` |
| `approval.controller.ApprovalChainController` | `/approval/chain` | Admin: desain chain/step/assignee approval (`SES_APPROVAL_CHAIN/STEP/ASSIGNEE`) | entity approval di `ses-core` |
| `approval.controller.ApprovalFormMapController` | `/approval/map` | Admin: memetakan `FORM_CODE`(+`MATCH_KEY`) ke chain mana yang berlaku (`SES_FORM_APPROVAL_MAP`) | — |
| `approval.controller.ApprovalGlobalModelAttributes` | *(bukan route, `@ControllerAdvice`)* | Menyuntik badge jumlah approval pending ke semua halaman (cache TTL 30 dtk — lihat `ses.approval.inbox-cache-ttl-ms`) | `ApprovalService` |
| `scheduling.controller.SchedulingController` | `/scheduling` | Antrian jadwal screening/pengambilan badge, kalender, input kehadiran per-worker (Hadir/Tidak Hadir/Tidak Lulus), simpan draft screening | `SchedulingService`-sejenis di `ses-core` |
| `payment.controller.PaymentController` | `/payment` | Daftar tagihan & pembayaran lintas modul (badge + stiker), filter status/modul | `PaymentService` |
| `payment.controller.PaymentVerificationController` | `/payment/verify` | Upload bukti bayar, verifikasi oleh finance officer ("bayarkan atas nama pemohon") | `PaymentService` |
| `payment.controller.PaymentRecapController` | `/payment/recap` | Rekap cash-inflow terverifikasi, export Excel multi-sheet (Apache POI) | `PaymentService`, cache 60 dtk (`ses.payment.verified-cache-ttl-ms`) |
| `cash.controller.CashExpenseController` | `/admin/cash-expense` | Catat pengeluaran kas kecil, export Excel bulanan | `CashExpenseService`-sejenis |
| `dashboard.controller.DashboardController` | `/dashboard` | Snapshot admin (cache 60 dtk, `ses.dashboard.snapshot-cache-ttl-ms`), landing page setelah login | `DashboardService` |
| `history.controller.HistoryController` | `/history` | Histori terpadu lintas badge/stiker/payment/approval, dibangun di atas view `SES_HISTORY_V`, scoping berjenjang (Section → Department → Org-wide) | `HistoryService`/view repository |
| `switchuser.controller.SwitchUserController` | `/switchuser` | Fitur "login sebagai user lain" untuk admin (opsional — lihat §3) | Platform `SwitchUserFilter` |
| `ses.file.AppFileController` | `/file/{fileId}` | Streaming unduh file dari `APP_FILE`, otorisasi lewat SPI `FileOwnershipResolver` (deny-by-default: resolver mana pun yang tidak mengklaim `refId` → 403) | `AppFileService` |

Semua route di atas (kecuali yang eksplisit `permitAll` di `SecurityConfig`: `/login`, static assets, `/actuator/health`, `/actuator/info`) wajib login; otorisasi per-halaman lebih detail dicek lewat `PageAccessGuard`/`APP_ROLE_PAGE.ACTION_CODE` (mis. butuh action `SUBMIT` untuk menulis, `READ` untuk sekadar lihat). **Catatan penting**: route `/request/upload-docs/**` dan `/verify/**` yang disebut di komentar `SecurityConfig` **hidup sepenuhnya di modul `ses-ext`**, bukan di sini — lihat [ses-ext/README.md](../ses-ext/README.md).

---

## 2. Fitur sedang berjalan (uncommitted): PDF Splitter & Bundle Upload

`git status` saat ini menunjukkan perubahan belum di-commit di `StickerController.java`, `templates/request/form.html`, `templates/sticker/form.html`, `templates/sticker/fragments/sticker-detail.html`, plus file baru `static/css/pdf-splitter.css`, `static/js/pdf-splitter-workbench.js`, `static/vendor/pdf-lib/`, `static/vendor/pdfjs/`. Ini adalah implementasi fitur **PDF Splitter & Bundle Document Upload** yang sudah terdokumentasi lengkap di [docs/DOKUMENTASI_FITUR_PDF_SPLITTER_BUNDLE_UPLOAD.md](../docs/DOKUMENTASI_FITUR_PDF_SPLITTER_BUNDLE_UPLOAD.md) — ringkasnya:

- **100% client-side**: `pdf-lib.min.js` + `pdf.min.js`/`pdf.worker.min.js` (divendor lokal di `static/vendor/`, bukan CDN — supaya tetap jalan di jaringan intranet/air-gapped kilang) membaca, merender, dan memotong file bundle PDF (maks. 200 MB) langsung di browser pengguna. Server tidak pernah menerima file 200 MB mentah — hanya potongan kecil (ratusan KB–2 MB) hasil split, lewat endpoint upload yang sama seperti upload biasa, jadi `multipart.max-file-size=20MB` di `application.properties` tetap aman.
- **Temporary Holding Shelf**: potongan hasil split ditampung sebagai `Blob`/Object URL di memori browser — belum ada panggilan POST ke server dan belum ada row baru di `APP_FILE` sampai pengguna benar-benar drag-and-drop kartu potongan ke slot dokumen yang dituju. Ini mencegah file sampah ("orphaned files") di database.
- **Auto-naming**: hasil drop otomatis diberi nama standar `[Nama Dokumen] - [Nama Pekerja/No Polisi].pdf`.
- **Integrasi**: dipasang sebagai mode-switch tambahan ("File Terpisah" vs "Bundle PDF Splitter") di modal dokumen pekerja (`request/form.html`) dan modal dokumen kendaraan (`sticker/form.html`) — `StickerController`/`RequestController` sendiri tidak berubah secara logika server, perubahan `StickerController.java` kemungkinan hanya penyesuaian kecil untuk mendukung field yang dikirim dari mode baru ini (periksa diff terbaru sebelum commit).
- Class JS-nya reusable: `new PdfSplitterWorkbench({ containerId, maxFileSizeMb, onSplitCreated, onItemRemoved })` — sehingga mudah dipasang ulang di form lain yang butuh fitur sama (mis. dokumen kendaraan stiker memakainya dengan konfigurasi berbeda, lihat bagian integrasi di dokumen fitur tsb.).

ses-ext punya salinan file statis yang identik (`pdf-splitter.css`, `pdf-splitter-workbench.js`, `vendor/pdf-lib`, `vendor/pdfjs`) karena halaman upload eksternal kontraktor juga memakai fitur yang sama — lihat [ses-ext/README.md](../ses-ext/README.md).

---

## 3. Konvensi template (Thymeleaf)

Struktur `src/main/resources/templates/`:
```
layout/
  base.html       — skeleton halaman: <head>, sidebar+navbar via th:replace, flash message, layout:fragment="head-extra"/"content"
  sidebar.html, navbar.html, footer.html — fragment bersama di semua halaman
  fragments/      — pager.html, attachment.html (dipakai lintas modul)
<fitur>/
  list.html, form.html, view.html, detail.html, ...
  fragments/      — potongan halaman yang di-reuse / di-fetch via AJAX (mis. sticker/fragments/sticker-detail.html, payment/fragments/verify-inline.html)
admin/<fitur>/    — layar admin (request-type, sticker-type, cash-expense, parameter)
```
Halaman fitur meng-`layout:decorate` ke `layout/base`, mengisi fragment `content` dan opsional `head-extra` untuk CSS/JS tambahan spesifik halaman (begitulah `pdf-splitter.css`/`pdf-splitter-workbench.js` dimuat hanya di `request/form.html` & `sticker/form.html`, bukan global).

`spring.thymeleaf.cache=true` di `application.properties` (lihat komentar panjang di sana) adalah default produksi — set `false` di `application-local.properties` saat aktif mengedit template, atau setiap request akan re-parse base+sidebar+navbar+footer dan terasa lambat (~420 ms jadi jauh lebih lama).

**Vendor assets** (`static/vendor/`): Bootstrap, Bootstrap Icons, DataTables, Flatpickr, dan kini pdf-lib/pdf.js — semua di-vendor lokal, bukan CDN, konsisten dengan kebutuhan intranet/air-gapped. CSS non-kritis (DataTables, Flatpickr, Select2 dari webjars) dimuat async (`media="print" onload="this.media='all'"`) untuk tidak memblokir render awal — lihat `layout/base.html:22-26`.

---

## 4. Package `com.badaklng.ses.config`

Hanya dua file:
- **`SecurityConfig`** — `WebSecurityConfigurerAdapter`: mendaftarkan kedua `AuthenticationProvider` (LDAP + LOCAL), whitelist static assets (`web.ignoring()`), aturan otorisasi URL (login/actuator permitAll, `/switchuser/**` butuh action `READ` via `PageAccessGuard`, sisanya `authenticated()`), form-login ke `/login` dengan redirect sukses ke `/dashboard`, logout, dan **session management** (`maximumSessions(10)` — ada catatan `TEMP` di kode bahwa ini sengaja dinaikkan dari 1 ke 10 pada 2026-08-21 dan perlu dikembalikan nanti). `SwitchUserFilter` di-inject sebagai `Optional` karena modul `switchuser` bersifat pluggable (boleh tidak ada di classpath).
- **`LdapConfig`** — aktif hanya jika `app.ldap.enabled=true` (`@ConditionalOnProperty`). Membuat dua `LdapContextSource`+`LdapAuthenticator` (primary `o=users`, secondary `o=jkt`) dari properti `spring.ldap.*`/`app.ldap.*`, dikonsumsi oleh `SesUserProvisioningProvider` di package `ses.auth`.

Auth flow lengkap: lihat `com.badaklng.ses.auth.SesUserProvisioningProvider` — percobaan bind LDAP primary lalu secondary, resolusi nama tampilan dari HR (`AppEmpInfoViewRepository`) lalu fallback atribut LDAP `displayName`, auto-provisioning `APP_USER` baru dengan role `REQUESTOR` jika `app.ldap.auto-provision=true`.
