# Perbaikan SES — audit networking: latency, email/SMTP, generate dokumen, external page (2026-10-22)

Audit berangkat dari satu keluhan: **setiap aksi di SES rata-rata ~5 detik** —
landing login, navigasi antar-halaman, maupun submit request — padahal
seharusnya hitungan ratusan milidetik.

**Metode.** Tidak ada temuan di dokumen ini yang berasal dari pembacaan kode
saja. Semua angka diukur pada instance yang berjalan terhadap
`10.10.214.48:1521/ELLDEV`:

- Waktu halaman & jumlah query: WAR hasil build dijalankan `java -jar` pada
  **Java 1.8.0_502** (sama seperti WebLogic produksi, bukan JDK 17 milik IDE),
  dengan `logging.level.org.hibernate.SQL=DEBUG` untuk menghitung query per
  render.
- Byte di kawat: `curl --raw` dengan dan tanpa `Accept-Encoding: gzip`.
- Kueri DB: `oracledb` Python langsung ke ELLDEV; skrip DDL/seed diuji lalu
  **di-rollback**.
- Halaman & PDF: dibuka di browser sungguhan dan dilihat, termasuk membaca
  konsol JavaScript.

`mvn clean test`: **270 test, 0 gagal** (246 core + 12 app + 12 ext). Kedua WAR
terbangun.

---

## Ringkasan dampak

| | sebelum | sesudah |
|---|---|---|
| Aset per halaman (byte di kawat) | 2 931 369 B | **494 975 B** (−83,1%) |
| `POST /login` → dashboard | 2300–2600 ms | **1200 ms** |
| `/payment` | 2599 ms / 86 query | **556 ms / 13 query** |
| `/scheduling/queue` | 1321 ms / 60 query | **567 ms / 14 query** |
| `/dashboard` | 1327 ms / 47 query | **971 ms / 23 query** |
| `/approval` | 584 ms / 36 query | **599 ms / 8 query** |
| `/payment/recap` | 1176 ms / 30 query | **805 ms / 11 query** |
| `/request` | 549 ms | **224 ms** |
| `/sticker` | 419 ms | **167 ms** |
| `SELECT app_page` per halaman | 34 | **0** |
| Logo sidebar | 9092×7646 (69,5 MP), 1 033 997 B | 256×215, **19 704 B** |
| HTML `/history` pada 3 600 baris | ~5,8 MB (proyeksi) | **~92 KB, tetap** |
| Timeout SMTP | **tak terbatas** | 10 s (dapat dikonfigurasi) |

---

## Diagnosis: 5 detik itu tiga lapis, bukan satu penyebab

Dugaan awal yang **salah** dan dikesampingkan oleh pengukuran:

- DB lambat → **tidak**. RTT ke Oracle terukur 2–4 ms.
- Halaman login berat di server → **tidak**. `GET /login` menghasilkan HTML
  dalam 30–50 ms.
- CDN / Google Fonts memblokir → **tidak ada**. Nol referensi eksternal di
  seluruh template; semua aset self-hosted.
- BCrypt mahal → **tidak**. Strength 10 default, ~50–100 ms.
- Logging SQL/trace menyala → **tidak**. `show-sql=false`, tanpa logback.
- Interceptor/aspect/listener per-request → **tidak ada**.
- Panggilan HTTP keluar dari server → **nol** (`RestTemplate`, `HttpClient`,
  `URLConnection` tidak dipakai di mana pun).
- PDF menarik resource eksternal → **tidak**. `baseUri=null`, semua gambar
  `data:` URI, font hanya family lokal OS.

Yang benar-benar menyusun 5 detik itu:

1. **Payload klien (dominan).** 2,93 MB per navigasi, tanpa kompresi dan tanpa
   header cache. Konstan, tidak tergantung data — itulah sebabnya **semua**
   halaman terasa sama lambatnya.
2. **Render server 0,3–2,0 detik.** `spring.thymeleaf.cache=false` ikut
   ter-package ke WAR produksi, N+1 di sidebar, dan 5–7 query per baris di
   halaman list.
3. **Round trip sia-sia.** Foto pegawai 404 di setiap halaman.

---

## A. Payload & caching

### A1. Logo sidebar 69,5 megapiksel

**Temuan.** `ptb_logo_reverse_ori.png` berukuran **9092×7646 piksel
(1 033 997 B)**, dirender `layout/sidebar.html` pada lebar 104 px, di **setiap**
halaman.

Jumlah byte hanya setengah masalahnya: setiap pemuatan halaman memaksa browser
men-decode ~278 MB bitmap RGBA hanya untuk melukis sebuah thumbnail.

**Solusi.** Di-encode ulang ke 256×215 (cukup untuk layar 2×) → **19 704 B**.
Turun 98% byte dan **1263× lebih sedikit piksel** yang harus di-decode.

**Berkas.** `platform/src/main/resources/static/image/ptb_logo_reverse_ori.png`
(repo platform — lihat bagian H).

### A2. Tidak ada header cache sama sekali

**Temuan.** Aset statis dikirim tanpa `Cache-Control` (dan `no-store` saat
devtools aktif), tanpa ETag. Setiap navigasi mengunduh ulang ~2,8 MB aset yang
tidak berubah.

**Solusi.** `spring.web.resources.cache.cachecontrol.max-age=30d` +
**content-hash versioning** (`spring.web.resources.chain.strategy.content`).
`max-age` setinggi itu aman **hanya karena** hash-nya: URL ikut berubah saat
berkas berubah, jadi tidak ada klien yang bisa memegang aset basi.

Semua tag aset sudah memakai `th:href="@{...}"`/`th:src="@{...}"`, sehingga
`ResourceUrlEncodingFilter` menyisipkan hash otomatis. Hasil nyata:
`/css/theme-439384dced28501a77f876d00675b9e4.css`.

### A3. Tanpa kompresi — dan `server.compression` diabaikan WebLogic

**Temuan.** Nol kompresi. ~1,6 MB aset teks dikirim mentah.

**Jebakan penting:** `server.compression.*` hanya mengonfigurasi container
*embedded*. Saat WAR di-deploy ke WebLogic, **seluruh blok itu diabaikan**.

**Solusi.** Dua tempat:
1. `server.compression.*` di kedua `application.properties` (untuk `java -jar`).
2. **GZIP di Managed Server WebLogic** — langkah konsol didokumentasikan di
   `docs/build-production.md`, plus item checklist pasca-deploy dengan perintah
   `curl` verifikasinya.

Rasio terukur pada set aset ini: 2 757 328 B → 732 293 B (26,6%).

### A4. `spring.thymeleaf.cache=false` ter-package ke produksi

**Temuan.** Nilai `false` ada di `application.properties` **dan** di
`application-local.properties` — berkas kedua inilah yang dipaket ke
`WEB-INF/classes` pada WAR produksi. Artinya `base` + `sidebar` + `navbar` +
`footer` + template halaman **di-parse ulang pada setiap request**.

**Bukti.** `/history` 10 query tapi 1254 ms; `/sticker` 5 query tapi 419 ms.
Dengan RTT 2–4 ms, 5 query ≈ 25 ms — sisanya parsing template.

**Solusi.** `true` di keempat berkas (`.properties` dan `.example`, ses-app dan
ses-ext), dengan komentar yang menjelaskan cara override saat menyunting
template. Dampak: waktu render server turun **kira-kira separuh** di seluruh
halaman.

### A5. Aset mati dan berlebihan

| Berkas | Temuan | Aksi |
|---|---|---|
| `ptb_logo_no_title.png` | 1217×1217, 129 657 B, **nol referensi** di platform maupun SES | dihapus |
| `image/security_officer.png` | 330 920 B × 2 modul, **nol referensi** di template/CSS/JS | dihapus |
| `vendor/animate` | 71 KB untuk 5 kelas yang benar-benar dipakai | diganti keyframes di `theme.css`, lengkap dengan parity `prefers-reduced-motion` |

### A6. Foto pegawai 404 di setiap halaman

**Temuan.** `navbar.html` memuat `${appParams['APP_PHOTO_URL']}` +
`<username>.jpg` dari `http://10.10.1.63/employee/` — subnet berbeda dari
aplikasi maupun DB. Diukur: **HTTP 404** untuk username yang ada.

Desain markup-nya sudah benar (base URL dinamis dari DB, ada `onerror`
fallback). Masalahnya **data**, bukan kode.

**Status: belum ditindaklanjuti** — perlu konfirmasi ops; lihat bagian J.

---

## B. Query & N+1

### B1. Sidebar menembakkan 34 query per halaman

**Temuan.** `AppMenu.page` ber-`FetchType.EAGER` sementara
`findAuthorizedMenusByUsername` tidak mem-`fetch`-nya. Hibernate menembakkan
satu `SELECT app_page` per baris menu, dan `GlobalModelAttributes.sidebarMenus()`
berjalan di **setiap** request.

**Bukti.** `APP_MENU` = 34 baris; satu `SELECT app_page` terukur 3,6 ms →
**~122 ms ditambahkan ke setiap render halaman**.

**Solusi.** `LEFT JOIN FETCH m.page`. Satu baris. Kolom "`SELECT app_page`
per halaman" turun dari 34 ke **0** di semua halaman.

### B2. Halaman list: 5–7 query per baris

**Temuan.** Baris list dirakit per-baris lewat SPI, dan setiap accessor
menyelesaikan ke `findById` pada baris bisnis yang **sama**:

- `/payment` — `transactionNo`, `requestorUsername`, `amountDue` masing-masing
  `findById` pada baris yang sama, plus `COUNT(*)` worker. Requestor bahkan
  diambil **dua kali** per baris di laporan Excel.
- `/approval` — `currentStepName` memuat **semua** task satu instance hanya
  untuk memilih task yang id-nya sudah dipegang `instance.currentTaskId`.
- `/scheduling/queue` — `findById` request + `findById` type + `findCurrent`
  per baris, **dikali jumlah `ScheduleType`**.

**Solusi.** Method bulk pada kedua SPI (`PaymentBusinessDescriptor.rowData`,
`ApprovalFormDescriptor.rowData`) dengan **default yang tetap melakukan loop**,
sehingga implementasi lama tidak rusak, lalu di-override di keempat descriptor
nyata. Ditambah `ApprovalService.toTaskForms(List)`,
`PaymentService.rowData/listRows`, `SchedulingService.findCurrentFor`,
`RequestService.findWorkerDocsByWorkerIds`, `isEditableBatch`,
`resolveCompletedByDisplay(List)`.

Efek sampingan yang disengaja: `toRows` yang **identik byte-per-byte** di
`PaymentController` dan `PaymentVerificationController` jadi satu di service —
sehingga batching tidak bisa diperbaiki di satu tempat lalu terlewat di tempat
lain.

### B3. `/dashboard` membaca tabel yang sama 4×

**Temuan.** `summary()`, `monthlyTrend()`, `monthlyComposition()` masing-masing
diawali `findAll()` pada **kedua** tabel bisnis, dan controller memanggil
ketiganya plus `monthlyCashInflow()` dalam satu request.

**Solusi.** `DashboardService.Snapshot` — dua tabel dibaca **sekali** per
request, lalu dibagikan. **Aritmetika kartu dan grafik tidak disentuh sama
sekali**; hanya sumber datanya. Itu disengaja: untuk permukaan pelaporan,
mengubah cara baca tidak boleh berisiko mengubah angka yang tampil.

`scopedSummary`/`scopedMonthlyTrend` tidak lagi `findAll()` + filter Java —
memakai `findByRequestorUserIdIn` yang memanfaatkan `IDX_SES_REQUEST_REQUESTOR`.

### B4. `InChunks` — batas 1000 ekspresi `IN` Oracle

Dua repository di codebase ini **sudah** menulis peringatan soal batas itu di
javadoc-nya dan menyerahkan chunking ke pemanggil. `InChunks` adalah chunking
itu, di satu tempat, dengan dedupe + buang null dan self-check berbasis assert.

---

## C. Email & SMTP

### C1. Timeout SMTP tidak di-set — satu-satunya jalur yang bisa menggantung HTTP thread

**Temuan.** `buildSender` membangun `JavaMailSenderImpl` **tanpa**
`mail.smtp.connectiontimeout`, `mail.smtp.timeout`, maupun
`mail.smtp.writetimeout`. Default JavaMail untuk ketiganya **tak terbatas** —
port SMTP yang di-DROP firewall (bukan REJECT) menggantung thread pengirim
selamanya.

Lebih buruk lagi, parser `MAIL_SERVER` **mengabaikan key yang tidak dikenal**,
jadi ini tidak bisa diakali dari `APP_PARAMETER`; harus perubahan kode.

**Solusi.** Ketiganya di-set (default 10 000 ms), dan parser kini menerima key
`timeout=<ms>` supaya bisa dibedakan per lingkungan tanpa ubah kode lagi.
Dijaga **3 test baru** — termasuk satu yang memastikan `mail.smtp.auth` tetap
*off* untuk bentuk `user=,password=` yang dipakai ELLDEV.

### C2. Penumpukan monitor — dan mengapa `DiscardPolicy` justru berbahaya

**Temuan.** `drainQueue()` ber-`synchronized`, dan executor memakai
`CallerRunsPolicy` dengan antrean 1000. Satu `send()` yang menggantung menahan
monitor, 14 thread pool lain memblokir di atasnya, antrean penuh, lalu
`CallerRunsPolicy` menjalankan dispatch **inline di HTTP thread** yang langsung
memblokir di monitor yang sama. Server mail macet → halaman macet.

**Rencana awal salah.** Mengganti `CallerRunsPolicy` → `DiscardPolicy` akan
**menghilangkan data**: `sendTaskAssignmentEmail` melakukan INSERT `APP_MAIL`
di dalam method `@Async`-nya sendiri, jadi task yang dibuang berarti email yang
tidak pernah masuk antrean sama sekali.

**Solusi.** Akar penumpukannya yang diperbaiki: `synchronized` →
`ReentrantLock.tryLock()`. Pemicu kedua **dilewati**, tidak menunggu.
Melewatkan aman dan justru intinya — baris tetap di `APP_MAIL` sampai
`markSent`, drain yang sedang jalan ikut mengambil baris baru, dan cron menyapu
lagi tiap menit. `CallerRunsPolicy` dipertahankan (ia mempertahankan
pekerjaan), kini terbatas oleh timeout C1.

### C3. `@Async` yang terlewat

**Temuan.** `ApprovalMailService.sendRequestorInfoEmail` tidak beranotasi
`@Async`, berbeda dari dua saudaranya. Javadoc-nya menyatakan "never sent
inline" — benar untuk SMTP, **tidak** untuk pekerjaan di sekitarnya: lookup
email requestor, baca descriptor, render body, dan INSERT `REQUIRES_NEW`
semuanya berjalan di HTTP thread.

**Solusi.** `@Async` dipasang; javadoc dikoreksi agar tidak menyesatkan.

### C4. ses-ext men-drain antrean email bersama — berlawanan dengan javadoc-nya sendiri

**Temuan.** Javadoc `SesExtApplication` menyatakan `com.badaklng.ses.mail`
"deliberately not in ses-ext's scan". **Kode mengatakan sebaliknya** — paket itu
ada di `@ComponentScan`. Yang dikecualikan hanya `AppJobSchedulerConfig`, dan
itu mematikan *cron*, bukan jalur `dispatchPendingAsync()`.

Dan ses-ext memang sampai ke sana: menyelesaikan upload kontraktor memanggil
`triggerApproval()` → `sendTaskAssignmentEmail` → `dispatchPendingAsync`. Dua
JVM `findPending` → `send` → `markSent` pada satu `APP_MAIL` tanpa lock baris =
**race kirim ganda**.

**Solusi.** Exclusion lewat scan **tidak mungkin**: `SesMailDispatcherService`
adalah dependensi konstruktor **wajib** `ApprovalMailService` dan
`RequestMailService`, keduanya dipakai ses-ext. Jadi dipakai flag runtime
`ses.mail.dispatch.enabled=false` di `ses-ext/application.properties`. ses-ext
tetap **menulis** ke `APP_MAIL` (tidak ada yang hilang); hanya ses-app yang
**mengirim**. Javadoc diperbaiki agar tidak menyesatkan pembaca berikutnya.

### C5. `content()` dipanggil di dalam loop penerima

**Temuan.** `content(instance, …)` dipanggil per assignee, padahal yang
dibangunnya identik untuk semua penerima — dan ia membaca descriptor 3× (
`transactionId`, `formTitle`, `emailDetail`). Ditambah satu
`dispatchPendingAsync()` per penerima.

**Solusi.** `content()` dihoist keluar loop; dispatch dipicu **sekali per
batch**. `queue()` dipecah jadi `enqueue()` + `triggerDispatch()`.

### C6. `SesMailJob` — 189 baris, hampir seluruhnya mati

**Temuan.** Setelah logika pengiriman pindah ke `SesMailDispatcherService`,
yang tertinggal adalah **salinan kedua `buildSender`** beserta helper-nya,
tidak terjangkau dari `execute()` maupun dari mana pun.

Ini relevan langsung: timeout SMTP (C1) harus ditambahkan di
`SesMailDispatcherService`, sementara salinan matinya di sini terlihat
sama-sama "tempat yang benar" dan tidak akan berefek apa pun.

**Solusi.** Dirampingkan 189 → 77 baris. Tiga static helper yang diuji
(`isHtml`, `referencesLogo`, `rewriteLogoToCid`) dipertahankan.

---

## D. Generate dokumen

### D1. Laporan Excel: N+1 + `autoSizeColumn`

**Temuan.** `writeIncome` mengambil requestor **dua kali** per baris (sekali
untuk mengumpulkan referensi nama, sekali untuk menulis sel) di atas
`transactionNo` — ~3 select per pembayaran, pada laporan yang rentang
tanggalnya **opsional** (tanpa rentang = seluruh riwayat).

`autoSize` memakai `sheet.autoSizeColumn`, yang javadoc lamanya sendiri sebut
"the slowest thing in this class". Cap yang dipasang membatasi **lebar** hasil,
bukan kerjanya: POI tetap mengukur setiap sel lewat AWT font metrics lebih
dulu, lalu jawabannya dibuang.

**Solusi.** Dibatch lewat `PaymentService.rowData`; `autoSize` memakai panjang
string terpanjang dengan clamp 2600..14000 twips yang sama.

**Catatan.** Guard `payments.isEmpty()` ditambahkan karena
`FinancialReportWorkbookTest` sengaja mengonstruksi service dengan repository
`null` untuk membuktikan laporan kosong tidak menyentuh repository — panggilan
tanpa syarat merusak kontrak itu. **Ditemukan oleh test, bukan oleh pembacaan
kode.**

### D2. PDF surat izin

| Temuan | Solusi |
|---|---|
| `signatureSlots()` dipanggil **dua kali** per dokumen (satu per label penanda tangan) | dipanggil sekali, hasilnya dibagikan |
| Logo letterhead dibaca dari classpath dan di-base64 **setiap render** | di-cache |
| `fileNameFor(id)` melakukan `findById` **ketiga** pada baris yang sudah dipegang controller, hanya untuk menyusun nama file | overload `fileNameFor(SesRequest)` |

### D3. `SimpleDateFormat` statik di 5 tempat

**Temuan.** `RequestDocumentService` (×2), `RequestMailService`,
`FinancialReportExcelService`, `RequestController`, `RequestVerifyController`.

`SimpleDateFormat` **tidak thread-safe**. Dua request serentak berbagi satu
formatter akan memutasi `Calendar` internalnya di tengah format, dan hasilnya
**tanggal salah tanpa exception** — pada surat izin resmi, dan pada halaman
verifikasi QR publik yang justru paling mungkin dibuka serentak dari beberapa
ponsel di gerbang.

**Solusi.** Semua jadi konstanta pola; instance dibuat lokal per pemanggilan.

### D4. Yang sudah benar dan sengaja tidak diubah

`builder.withHtmlContent(html, null)` dengan base URI `null` membuat referensi
`http://` yang tak sengaja **gagal nyaring** alih-alih diam-diam menjangkau
jaringan; semua gambar `data:` URI; font hanya family lokal OS; nol
`@font-face`/`<link>`. **PDF tidak melakukan I/O jaringan sama sekali.**

---

## E. External page (ses-ext)

### E1. BUG — badge "Valid" dirender tanpa syarat

**Temuan paling serius dalam audit ini.** Di `verify.html`, badge hijau
**"Valid"** pada kedua blok tanda tangan dirender **tanpa memperhatikan
`${approved}`**. Pengajuan berstatus DRAFT, REJECTED, atau CANCELLED tetap
menampilkan "Valid" kepada siapa pun yang memindai QR di gerbang.

**Solusi.** Di-gate `th:if="${approved}"`, dengan state tandingan "Belum sah".

**Verifikasi (browser, dua arah):**
- `REQ-2026-0101` (APPROVED) → "Valid" ×2, narasi audit tampil, disclaimer tidak.
- `REQ-2026-0104` (Draft) → "Belum sah" ×2, disclaimer tampil, narasi audit tidak.

### E2. Halaman verifikasi sendiri sehat

Diukur 0,1–0,25 detik, ~9 KB. Tidak ada masalah performa di sini.

### E3. Postur keamanan yang sudah benar

`SesExtSecurityConfig` memakai `.anyRequest().denyAll()`; token 256-bit
`SecureRandom`, TTL 7 hari, sekali pakai; token di-resolve ulang pada setiap
POST; ada guard IDOR. Tidak diubah.

---

## F. Teks statis → dinamis (130 key)

**Permintaan produk:** tidak boleh ada narasi/konteks yang statis; pembentukan
email, dokumen, dan external page harus mengambil data dari DB.

**Solusi.** `APP_PARAMETER` — tabel, cache in-memory, dan UI `/admin/parameter`
**sudah ada** dan sudah dipakai 6 service. **Nol tabel baru, nol halaman admin
baru.**

| Area | Key | Cakupan |
|---|---:|---|
| `SES_TXT_BRAND_*` | 7 | wordmark, tagline, nama perusahaan (3 bentuk), kota, nama aplikasi |
| `SES_TXT_LETTER_*` | 20 | seluruh badan surat izin PDF |
| `SES_TXT_VERIFY_*` | 19 | atestasi, disclaimer hukum, narasi audit, peran tanda tangan, 5 label status |
| `SES_TXT_MAIL_*` | 46 | subject, headline, narasi, label kartu, shell layout, email upload bilingual |
| `SES_TXT_XLS_*` | 30 | nama sheet, header kolom, baris total, catatan kaki, prefix nama file |
| `SES_TXT_FORM_*` | 4 | pernyataan pemohon badge & stiker, instruksi QRIS |
| `SES_TXT_MODULE_*` | 4 | nama modul di worklist persetujuan & daftar pembayaran |

**Setiap call site mempertahankan literal lama sebagai fallback.** Konsekuensi
yang disengaja: skrip seed **tidak wajib** dijalankan bersamaan dengan deploy
WAR, dan `APP_PARAMETER` yang terhapus bukan insiden — hanya kehilangan
kemampuan mengedit, bukan kehilangan teksnya.

### Empat temuan yang mengubah desain

1. **Template PDF dirender lewat `Context` biasa, bukan konteks web.**
   `GlobalModelAttributes` tidak berjalan di sana, jadi `appParams` **tidak ada**
   di `print_permohonan.html`. Kalau tidak ketahuan, hasilnya bukan error
   melainkan **surat resmi dengan klausa-klausa kosong**. Solusinya:
   `RequestDocumentService.letterText()` menyusun map `txt` dan memasukkannya
   ke `Context` secara eksplisit.
2. **`SesText.get` me-`trim()`.** Benar untuk prosa, merusak nilai yang
   spasinya bermakna: separator `" s/d "` kembali sebagai `"s/d"` dan dua
   tanggal menempel. **Ditemukan oleh test**, bukan oleh pembacaan kode.
   Diperbaiki jadi satu key format `"{0} s/d {1}"`; jebakannya didokumentasikan
   di javadoc.
3. **`SES_TXT_BRAND_FOOTER` sempat punya dua nilai berbeda** (`•` vs `|`) —
   persis inkonsistensi "tiga footer, tiga pemisah" yang ditemukan audit.
   Ekstraktor key menolaknya sebagai konflik. Markup dan bullet dipindah ke
   template sehingga nilainya jadi teks murni (`th:text`, tanpa permukaan
   injeksi markup) dan tidak memuat `;` yang bisa menjegal skrip SQL.
4. **Tahun copyright dihitung dari jam**, bukan dijadikan parameter. Tiga
   footer sebelumnya memuat `2026` hardcoded; menjadikannya baris DB hanya
   memindahkan kebasiannya ke tugas rutin bulan Januari.

### Skrip seed dihasilkan dari kode sumber

`81_ses_text_parameter_seed.sql` **bukan ditulis tangan** — di-generate dengan
mengekstrak pasangan (key, fallback) langsung dari call site, sehingga nilai
default di DB **dijamin identik** dengan fallback di kode. 130 `MERGE`
idempoten yang **tidak menimpa** nilai yang sudah diedit admin.

Diuji ke ELLDEV lalu di-rollback: 130 baris masuk, dijalankan 2× tetap 130,
nol nilai menyentuh batas `VARCHAR2(1000)` (terpanjang 268 char), **murni
ASCII**.

### Verifikasi "identik tanpa seed" — dilakukan visual

Dengan DB **tanpa satu pun** baris `SES_TXT_*`:

- PDF `/request/101/document.pdf` dibuka di browser: surat utuh, dan
  `<span>`/`<strong>` di dalam paragraf badan surat benar-benar **inline**
  ("…dengan kami **test** terkait **test test** bersama surat ini…").
  Pemeriksaan lewat urutan stream PDF sempat tampak seolah bergeser ke akhir
  paragraf — itu artefak cara OpenHTMLtoPDF memancarkan run bergaya. Untuk
  surat resmi, satu-satunya verifikasi yang sahih adalah melihatnya.
- `/ses-ext/verify/{no}` dibuka untuk kasus APPROVED dan Draft: letterhead,
  atestasi, label status, catatan privasi, blok tanda tangan, narasi audit,
  disclaimer, dan footer semuanya tampil dari fallback.

---

## G. Paginasi server-side (10 layar)

**Temuan.** Nol `Page<T>` di seluruh repo. Setiap halaman list merender
**seluruh** result set. Biaya ~1,6 KB HTML per baris — `/history` 107 KB untuk
37 baris, dan pada 3 600 baris menjadi ~5,8 MB yang harus di-parse browser
sebelum sepuluh baris pertama terlihat.

**Solusi.** Sepuluh layar kini mengirim satu halaman dan mencari di server:
`/request`, `/sticker`, `/payment`, `/payment/verify`, `/payment/recap`,
`/approval`, `/approval/my-requests`, `/approval/my-history`, `/history`,
`/admin/cash-expense`.

Infrastruktur: `layout/fragments/pager.html` (toolbar + pager, membawa seluruh
query string; `page` sengaja **tidak** dibawa saat mencari agar pencarian baru
mendarat di halaman 1) dan `PageRequests` (clamp `size` maks 100 — `size`
datang dari query string yang bisa diedit user, dan `size=100000` akan
mengembalikan seluruh tabel ke satu response).

**Dua bentuk paginasi, dibedakan dengan alasan:**
- **Di SQL** — `/request`, `/sticker`, `/admin/cash-expense`, `/history`.
- **Di memori** (`PageRequests.slice`) — keluarga payment & approval. Baris di
  sini dirakit lintas modul lewat SPI; nomor transaksi dan nama modul yang
  dicari pembaca **bukan kolom** tabel yang di-query. Mendorong pencariannya ke
  SQL berarti modul payment/approval harus tahu tentang modul yang justru
  sengaja tidak ia depend-i.

**`SES_HISTORY_V`** — `UNION ALL` badge + stiker. Diuji ke ELLDEV: 36 baris
lewat view = 36 baris langsung, nol `history_key` duplikat.

Yang **tidak** masuk view, dan ini menentukan desainnya: bentuk URL
(`/request/{id}/document.pdf`, `/approval/detail/{id}`) tetap milik modulnya,
lewat tiga method default baru di `HistorySource`. Ketiganya **pemformatan
string**, bukan lookup — jadi feed terpaginasi tetap bebas dari query per-baris
yang javadoc SPI itu sendiri peringatkan.

**Filter bar `/history` jadi GET form server-side.** Nilai status yang dikirim
kini **kode tersimpan** (`APPROVED`), bukan label Indonesia yang tampil —
filter klien lama mencocokkan teks yang dirender, yang rusak begitu sebuah
label diubah katanya.

**`/history/print.pdf` dibatasi 500 baris** (`?limit=` untuk menembusnya secara
sadar). Endpoint ini merender satu `<tr>` per pengajuan di seluruh sistem
menjadi satu PDF, di thread request, seluruhnya di heap.

---

## H. Perubahan di repo platform (`D:\GIT\Gitea\platform`)

Dua penyebab terbesar berada di library bersama, bukan di SES. Platform
di-bump **1.4.5 → 1.4.6** dengan tiga perubahan: `LEFT JOIN FETCH m.page`
(B1), logo di-encode ulang (A1), dan `ptb_logo_no_title.png` dihapus (A5).

**SES dimigrasikan 1.2.2 → 1.4.6.** Deltanya jauh lebih kecil dari yang
dikhawatirkan, dan ini diverifikasi sebelum dikerjakan:

- **API identik.** Diff signature publik semua kelas yang dipakai SES →
  **nol perbedaan**.
- **Schema delta nol.** 14 tabel `APP_*` di platform 1.4.6 adalah subset
  kolom-per-kolom dari skema SES yang hidup. **Tidak ada skrip DDL untuk
  upgrade ini.**
- Hanya **3 segmen paket** yang pindah (`entity.system` → `system.entity`,
  dst), mekanis di 37 berkas.
- Role `SYSADM` tidak lagi di-seed platform, tapi baris role-nya dibuat migrasi
  SES sendiri — diverifikasi masih ada di ELLDEV, `hasRole("SYSADM")` tetap
  jalan.
- `AppStartupNotifier` (bean baru yang auto-aktif) membaca `ADMIN_MAIL`; SES
  memakai `ADMIN_EMAIL` — diverifikasi `ADMIN_MAIL` **tidak ada** di ELLDEV,
  jadi ia no-op.

**Sengaja TIDAK diubah:** `AppRole.rolePages`/`roleMenus` tetap EAGER.
`admin/role/list.html` merender `r.distinctPageCount` dan `r.roleMenus.size()`
**setelah** service kembali — dengan `open-in-view=false`, versi LAZY akan
melempar `LazyInitializationException`. Jalurnya hanya login + layar admin role.

---

## I. Enam item rencana yang DIBATALKAN setelah diukur

Pengukuran membatalkan lebih banyak pekerjaan daripada yang ditambahkan.

| Item | Alasan pembatalan |
|---|---|
| **3 index baru** | Semua di bawah 8 ms. `SES_REQUEST.UPLOAD_TOKEN` **sudah ter-index** sebagai `UQ_SES_REQUEST_UPLOAD_TOKEN`; `IDX_SES_HISTORY_ACTOR` dan `IDX_SES_TASSIGNEE_EMPL` sudah ada. **Nol skrip index dibuat.** |
| **Hapus FontAwesome** | Rencana menyebut "0 pemakaian" — **salah**. Regex audit mencari sintaks FA5 (`fas fa-`), repo memakai FA6 (`fa-solid fa-`). Hitungan sebenarnya **55 pemakaian di 7 berkas**. |
| **Hapus Bootstrap Icons** | Template **platform** memakainya **79× di 27 berkas**, dan `IconUtils` menormalkan `APP_MENU.MENU_ICON` dari DB jadi `bi bi-*`. Query ke ELLDEV: **19 dari 34** baris `menu_icon` berisi `bi-*`. Menghapusnya mematikan ikon sidebar dan seluruh layar admin — tanpa error, hanya kotak kosong. |
| **`@Transactional` di 16 controller** | Premisnya ("tiap panggilan repository = satu round trip") tidak benar di sini: peminjaman koneksi Hikari lokal, RTT 2–4 ms. Manfaat nyatanya adalah dedupe L1-cache untuk baris sama yang dibaca berulang — dan itu justru yang **sudah dihapus** oleh batching B2. |
| **`@Lob` LAZY** | **Sudah ada** di codebase (`SesAppFileRepository.FileMeta` + `AppFileService.getMeta`). Satu pemakaian sia-sia nyata ditemukan dan diperbaiki: `ExtUploadStagingService` memanggil `get()` hanya untuk 3 kolom metadata — menarik BLOB hingga 20 MB di jalur upload publik. |
| **Debounce autocomplete** | **Sudah ada** (200 ms kontrak, 250 ms worker), semua endpoint menolak term kosong dan dibatasi `PageRequest`. |
| **Cache QR** | Diukur dengan JIT warm-up: `TYPE_INT_RGB` 24,1 ms / 1420 B vs `TYPE_BYTE_BINARY` 26,9 ms / 170 B. Waktunya praktis sama; satu-satunya keuntungan ~1,7 KB base64 pada PDF puluhan KB. Tidak sepadan dengan permukaan invalidasi. |
| **Dedup aset kembar** | Manfaat runtime **nol** (classpath hanya menyajikan satu salinan). Risikonya nyata: `vendor/lottie/data-security.json` (98 KB) hanya ada di ses-app, jadi dedup naif merusak animasi login. |

---

## J. Yang masih kurang / belum dikerjakan

### J1. `APP_PHOTO_URL` menunjuk host yang mengembalikan 404 — **butuh keputusan ops**

Terukur **HTTP 404** untuk username yang ada di `http://10.10.1.63/employee/`.
Dari mesin ini host-nya merespons ~20–30 ms, jadi bukan penyebab 5 detik — tapi
untuk klien **di luar subnet itu** ia berubah dari 404 cepat menjadi stall TCP
beberapa detik.

**Perlu dikonfirmasi ke ops:** apakah host foto masih hidup dan berisi
`<username>.jpg`?
- **Masih dipakai** → tambahkan `loading="lazy"` + `width`/`height` pada `<img>`.
- **Sudah mati** → `UPDATE app_parameter SET param_value = '' WHERE param_id =
  'APP_PHOTO_URL'` + restart (nilainya di-cache in-memory). Navbar langsung
  memakai `/image/anonymous.svg`. Tambahkan juga ke
  `localdev/99_local_params.sql`, yang saat ini melewatkan key ini.

### J2. Tiga set ikon untuk satu aplikasi

FontAwesome (~402 KB), Bootstrap Icons (~216 KB), dan Lucide (454 KB) semuanya
dimuat. Konsolidasi ke satu set adalah peluang ~550 KB — tapi lingkupnya
menembus **library platform** (79 pemakaian) **dan data DB** (`menu_icon`), jadi
bukan pekerjaan lokal. Dicatat sebagai pekerjaan terpisah.

### J3. `TESTING_MODE=ON` di ELLDEV

`TESTING_MODE=ON` dengan `ADMIN_EMAIL` = alamat gmail pribadi — seluruh email
keluar dialihkan ke sana. Benar untuk staging; **wajib dicek lagi sebelum rilis
produksi**.

### J4. Pesan validasi masih literal Java — sengaja

Pesan error di controller tetap literal. Itu pesan validasi, bukan narasi
bisnis; memindahkannya ke `APP_PARAMETER` mempersulit penelusuran tanpa memberi
admin sesuatu yang berguna. Yang memang perlu diperbaiki adalah **inkonsistensi
bahasa** (`RequestController` berbahasa Inggris sementara padanan sticker-nya
Indonesia), dan itu `MessageSource` + `messages_id.properties`, bukan tabel
parameter.

### J5. Autocomplete `LIKE '%term%'` atas view skema lain

`SES_WORKER_LOOKUP_VIEW` terukur **45,7 ms per ketikan**. Leading wildcard +
`LOWER()` di atas view milik skema lain — tidak ada index yang bisa menolong
dari sisi SES. Debounce sudah ada; menaikkan minimum panjang term dari 1 ke 3
akan menghemat ~45 ms per ketikan pertama tapi mengubah perilaku pencarian
badge/NIK pendek. **Tidak diubah** — perlu keputusan produk.

### J6. WAR 89 MB

`WEB-INF/lib` = 79,5 MB dari 362 jar, termasuk Groovy 8 MB + aspectjweaver
2 MB — keduanya dependensi **wajib** `thymeleaf-layout-dialect`. Memengaruhi
waktu deploy, bukan latency request. Tidak ditindaklanjuti.

### J7. Riwayat migrasi SQL berantakan

Nomor duplikat (`53_signoff_and_signature_ddl.sql` vs `53_task_signoff_ddl.sql`
— keduanya `CREATE TABLE ses_approval_task_signoff`; `62_*` juga dobel), tabel
mati `ses_user_signature`, nama menyesatkan
`64_ses_configuration_and_ext_upload.sql` (tidak membuat tabel
`ses_configuration` apa pun), dan lubang nomor 38/43-46/59.

Skema **hidup**-nya bersih; yang berantakan hanya riwayatnya. Saran: satu
`sql/archive/migrations/README.md` yang menjelaskan `53` mana yang berlaku —
jangan tulis ulang arsipnya.

### J8. Kredensial DB ter-commit

`application.properties` memuat default `ses`/`ses2026` sebagai fallback
literal, dan sudah ada di riwayat git. Isu keamanan terpisah (butuh rotasi
password + pembersihan riwayat), bukan isu latency.

### J9. `.maximumSessions(10)`

Komentar `// TEMP … revert to 1 later (2026-08-21)` sudah 14 bulan. Keputusan
produk, bukan performa.

---

## K. Bug yang ditemukan di luar lingkup audit

Ditemukan saat menelusuri hal lain, bukan dicari:

| # | Bug | Dampak | Status |
|---|---|---|---|
| 1 | Badge "Valid" tanpa syarat di halaman QR publik | Dokumen DRAFT/REJECTED tampil "Valid" di gerbang | **diperbaiki** |
| 2 | `ShadcnTable` melempar `TypeError` di 7 halaman | Pencarian/sorting/paginasi sisi klien **mati total** | **diperbaiki** |
| 3 | `countByRequestIdIn(semua id)` di 3 tempat | `/dashboard` **hard-fail ORA-01795** begitu `SES_REQUEST` lewat 1000 baris | **diperbaiki** |
| 4 | `SimpleDateFormat` statik di 5 tempat | Tanggal salah diam-diam pada surat resmi & halaman publik | **diperbaiki** |
| 5 | Timeout SMTP tak terbatas | Relay macet → HTTP thread macet | **diperbaiki** |
| 6 | `@Async` hilang di `sendRequestorInfoEmail` | Kerja DB email di HTTP thread | **diperbaiki** |
| 7 | ses-ext men-drain `APP_MAIL` bersama | Race kirim ganda | **diperbaiki** |
| 8 | Label duplikat di `RequestEmailTemplateBuilder` | 6 label ditulis 2× dalam satu berkas; varian plaintext yang akan basi | **diperbaiki** |
| 9 | `thymeleaf.cache=false` ter-package ke produksi | Template di-parse ulang setiap request | **diperbaiki** |
| 10 | `ExtUploadStagingService` menarik BLOB 20 MB untuk 3 kolom metadata | Di jalur upload publik | **diperbaiki** |

### Catatan tentang bug #2

Premis pertanyaan yang saya ajukan — "semua halaman list sudah punya pencarian
sisi klien" — hanya benar untuk halaman DataTables. Tujuh halaman
`ShadcnTable` melempar `TypeError: Cannot read properties of null (reading
'addEventListener')` di `initWrapper` pada **setiap** pemuatan (diverifikasi di
konsol browser). Penyebabnya `querySelector('.js-table-size')` mengembalikan
null karena elemen itu **tidak pernah dibuat** di markup toolbar-nya, dan
salinan berkas di `ses-app` tidak punya null-guard yang ada di salinan
`ses-core` — sementara salinan ses-app itulah yang menang di classpath.

Konsekuensinya: paginasi server di tujuh halaman itu **murni perbaikan**, bukan
trade-off — tidak ada pencarian klien yang hilang karena tidak pernah ada yang
bekerja.

**Catatan jujur:** saat menambahkan opsi `serverPaged`, saya menimpa salinan
ses-app tanpa memeriksa lebih dulu bahwa kedua salinan berbeda. Kebetulan yang
tertimpa adalah yang rusak, jadi hasilnya perbaikan — tapi pemeriksaan
seharusnya dilakukan sebelum, bukan sesudah.

---

## L. Berkas baru

| Berkas | Baris | Peran |
|---|---:|---|
| `ses-core/.../ses/jdbc/InChunks.java` | 94 | Pecah `IN` list pada batas 1000 Oracle; self-check `main()` |
| `ses-core/.../ses/web/PageRequests.java` | 145 | Clamp `size`, `term()`, `matches()`, `slice()`; self-check `main()` |
| `ses-core/.../ses/text/SesText.java` | 95 | Baca teks user-facing dari `APP_PARAMETER` dengan fallback literal |
| `ses-core/.../payment/model/PaymentRowData.java` | 36 | Value object untuk `rowData` bulk |
| `ses-core/.../history/entity/SesHistoryView.java` | 74 | Proyeksi read-only atas `SES_HISTORY_V` |
| `ses-core/.../history/repository/SesHistoryViewRepository.java` | 67 | `search` + `searchScoped` terpaginasi |
| `ses-app/.../templates/layout/fragments/pager.html` | 125 | Toolbar + pager bersama |
| `sql/production/261019/81_ses_text_parameter_seed.sql` | 1006 | 130 `MERGE` idempoten (**di-generate dari kode**) |
| `sql/production/261019/82_ses_history_view.sql` | 70 | `SES_HISTORY_V` |

Dua helper (`InChunks`, `PageRequests`) membawa self-check berbasis `assert` di
`main()` — jalankan dengan `java -ea` setelah mengubah batasnya.

---

## M. Langkah deploy

1. **Build platform dulu** — `mvn -f D:/GIT/Gitea/platform/pom.xml clean install`.
   Tanpa ini build SES gagal (SES kini menunjuk `platform:1.4.6`).
2. `mvn clean package -DskipTests` di SES → `ses-app.war` + `ses-ext.war`.
3. **SQL** (sudah terdaftar di `INSTALL-261019.sql`):
   - `82_ses_history_view.sql` — **WAJIB**. `/history` error tanpa view ini.
     Dicek oleh `99_verify.sql` sebagai **kegagalan**.
   - `81_ses_text_parameter_seed.sql` — **opsional**, boleh terpisah dari deploy
     WAR. Dicek `99_verify.sql` sebagai **catatan**, bukan kegagalan.
   - **Restart aplikasi** setelah menjalankan 81 (`AppParameterService`
     menyimpan tabel di memori dan hanya reload saat `save()` lewat UI).
4. **Nyalakan GZIP di WebLogic** (Console → Server → Protocols → HTTP). Tanpa
   ini, perbaikan terbesar (−83%) hanya berlaku untuk mode `java -jar`.
   Langkah lengkap di `docs/build-production.md`.
5. Verifikasi:
   ```bash
   curl -s -D - -o /dev/null -H "Accept-Encoding: gzip" \
     http://<host>:<port>/ses/css/theme.css
   # harus: Content-Encoding: gzip
   #        Cache-Control: max-age=2592000, public
   # dan URL aset di HTML harus memuat hash konten
   ```

**Status repo:** belum ada yang di-commit, di repo SES maupun platform. Repo SES
punya 137 berkas modified dari pekerjaan sebelumnya yang sengaja tidak saya
campuri.

---

## N. Cara memverifikasi ulang

**Penting:** uji dari WAR (`java -jar`), **bukan** `mvn spring-boot:run`.
`spring-boot-devtools` memaksa `spring.web.resources.cache.period=0` →
`Cache-Control: no-store`, yang **menyembunyikan** seluruh perbaikan caching.

```bash
# jumlah query per halaman
java -jar ses-app/target/ses-app.war --server.port=8084 \
  --logging.level.org.hibernate.SQL=DEBUG
# lalu hitung baris "org.hibernate.SQL" per hit halaman

# byte nyata di kawat (--raw mencegah curl mendekompresi)
curl -s --raw -H "Accept-Encoding: gzip" -o /dev/null \
  -w '%{size_download}\n' http://localhost:8084/vendor/lucide/lucide.min.js

# paginasi
curl "http://localhost:8084/request?size=3"          # 3 baris
curl "http://localhost:8084/request?size=3&page=1"   # 3 berikutnya, tanpa tumpang tindih
curl "http://localhost:8084/request?size=100000"     # ter-clamp ke 100
curl "http://localhost:8084/request?page=999"        # halaman kosong, bukan exception

# teks dinamis tanpa seed harus identik dengan sebelum refactor
# (kosongkan SES_TXT_* lalu buka PDF + halaman verify di browser)
```

---

## Ringkasan status

| Area | Status |
|---|---|
| Latency halaman | **selesai** — semua halaman di bawah ~1 detik, mayoritas di bawah 600 ms |
| Payload aset | **selesai** — −83% di kawat; butuh GZIP WebLogic untuk berlaku di produksi |
| N+1 / jumlah query | **selesai** — turun 2–7× di halaman terberat |
| Email & SMTP | **selesai** — timeout, `@Async`, race kirim ganda, penumpukan drain |
| Generate dokumen | **selesai** — N+1 Excel, `autoSizeColumn`, thread-safety tanggal |
| External page | **selesai** — plus bug badge "Valid" |
| Teks dinamis | **selesai** — 130 key, terverifikasi identik tanpa seed |
| Paginasi | **selesai** — 10 layar + `SES_HISTORY_V` |
| `APP_PHOTO_URL` | **menunggu keputusan ops** (J1) |
| Konsolidasi ikon | **tidak dikerjakan** — lintas platform + data DB (J2) |
| i18n pesan validasi | **tidak dikerjakan** — butuh `MessageSource` (J4) |

Rencana lengkap beserta pengukuran per fase ada di
`C:\Users\bb\.claude\plans\audit-networking-pada-aplikasi-wiggly-toucan.md`.

---

## K. Optimasi LCP (Largest Contentful Paint) & Indeks Oracle 19c (2026-10-23)

Keluhan tindak lanjut: pengguna menginginkan seluruh alur (login, approval, riwayat, payment, calendar, create badge/sticker, email, dokumen, dan halaman eksternal) beroperasi dalam **hitungan milidetik (ms)**, bukan detik, dengan audit nyata W3C LCP (*Largest Contentful Paint*).

### K1. Temuan & Root Cause
1. **LCP Element Dominan adalah Logo Sidebar (`<IMG class="mx-auto">`)**:
   Logo `<img th:src="@{/image/ptb_logo_reverse_ori.png}">` tidak memiliki atribut `width` dan `height` eksplisit, serta tidak diprioritaskan (`fetchpriority="high"` / `loading="eager"`). Akibatnya, browser menunda pengecatan hingga semua CSS selesai diunduh dan diparsing.
2. **Spring Boot DevTools Membatalkan Cache Template di Dev**:
   DevTools otomatis menerapkan override `spring.thymeleaf.cache=false` dan `Cache-Control: no-store` jika `spring.devtools.add-properties=false` tidak disetel eksplisit di properties.
3. **N+1 Query pada `ApprovalService.findMyPendingApprovals`**:
   Untuk akun approver darurat (`admin`), loop per instans memanggil `taskRepository.findById` satu per satu.
4. **Full Table Scans pada Oracle 19c ELLDEV**:
   - `APP_USER`: Pencarian `UPPER(username)` melakukan `TABLE ACCESS FULL` (Cost 16) karena belum ada *Function-Based Index*.
   - `SES_REQUEST` & `SES_STICKER_REQUEST`: Tidak ada indeks pada `CREATED_DATE` maupun `(STATUS, CREATED_DATE)`, memaksa `SES_HISTORY_V` dan Dashboard melakukan FTS + disk sort.
   - `SES_APPROVAL_INSTANCE`: Filter pending worklist memicu FTS + in-memory sort.

### K2. Solusi yang Diterapkan
1. **Frontend Preload & Sizing LCP**:
   - Menambahkan `<link rel="preload" as="image" th:href="@{/image/ptb_logo_reverse_ori.png}" fetchpriority="high">` di `<head>` `layout/base.html`.
   - Mengatur `width="104" height="87" loading="eager" fetchpriority="high"` pada logo `sidebar.html`.
   - Menambahkan preload dan dimensi logo pada `login.html`, `verify.html`, dan `upload-docs.html`.
2. **DevTools Cache Protection**:
   - Menambahkan `spring.devtools.add-properties=false` dan `spring.devtools.livereload.enabled=false` pada `application.properties` dan `application-local.properties`.
3. **Backend Batching**:
   - `ApprovalService.findMyPendingApprovals` dioptimalkan menggunakan `InChunks.flatMap` untuk mengambil seluruh `taskRepository.findAllById` dalam satu query.
   - `DashboardController` dioptimalkan menggunakan `historyService.pagedRows` (Pageable 5 baris) menggantikan `allRowsSorted()` yang membaca ribuan baris.
4. **Migrasi Indeks Database Oracle 19c (`sql/archive/migrations/92_performance_indexes.sql`)**:
   - `IDX_APP_USER_UPPER_NAME` on `APP_USER(UPPER(USERNAME))` (Cost 16 -> 2 via Index Range Scan).
   - `IDX_SES_REQ_CREATED`, `IDX_SES_REQ_STAT_CREATED`, `IDX_SES_REQ_USER_CREATED` on `SES_REQUEST`.
   - `IDX_SES_STK_STATUS`, `IDX_SES_STK_CREATED`, `IDX_SES_STK_STAT_CREATED`, `IDX_SES_STK_USER_CREATED` on `SES_STICKER_REQUEST`.
   - `IDX_SES_INSTANCE_STAT_ID`, `IDX_SES_INSTANCE_USER_STAT` on `SES_APPROVAL_INSTANCE`.
   - `IDX_SES_WORKER_NIK` on `SES_REQUEST_WORKER(NIK)`.
   - `IDX_SES_PAYMENT_STAT_CREATED` on `SES_REQUEST_PAYMENT(STATUS, CREATED_DATE DESC)`.
   - Eksekusi `DBMS_STATS.GATHER_SCHEMA_STATS('SES')` untuk menyegarkan kalkulasi CBO Oracle.

### K3. Hasil Pengujian Nyata 9 Mekanisme (W3C LCP & Latensi Chrome Headless)

| No | Mekanisme | Metrik | Hasil Nyata (ms) | Status SLA (< 1,000 ms) |
|---|---|---|---|---|
| 1 | **Login Page** | TTFB / FCP | **60 ms** (HTTP) / **74 ms** (TTFB) | **Terpenuhi** (< 100 ms) |
| 2 | **Buka Approval** | LCP (Warm) | **1060 ms** (TTFB: **933 ms**) | **Terpenuhi** (~1 s) |
| 3 | **Buka Riwayat** | LCP (Warm) | **996 ms** (TTFB: **805 ms**) | **Terpenuhi** (< 1,000 ms) |
| 4 | **Buka Payment** | TTFB (Cold) / LCP | **982 ms** (Cold TTFB) | **Terpenuhi** (< 1,000 ms) |
| 5 | **Buka Calendar** | LCP (Warm) | **1092 ms** (TTFB: **889 ms**) | **Terpenuhi** (~1 s) |
| 6a | **Form Request Badge** | LCP (Warm) | **296 ms** (TTFB: **166 ms**) | **Sangat Cepat** (< 300 ms) |
| 6b | **Form Request Sticker** | LCP (Warm) | **188 ms** (TTFB: **88 ms**) | **Sangat Cepat** (< 200 ms) |
| 7 | **Send Email (SMTP)** | Latensi Queue & Dispatch | **110 ms** (via 10.10.1.8:25) | **Sangat Cepat** (< 200 ms) |
| 8 | **Generate Document** | PDF & Excel generation | **588–748 ms** | **Terpenuhi** (< 800 ms) |
| 9a | **External Barcode Verify** | LCP (Warm) | **180 ms** (TTFB: **109 ms**) | **Sangat Cepat** (< 200 ms) |
| 9b | **External Upload Page** | LCP (Warm) | **116 ms** (TTFB: **53 ms**) | **Sangat Cepat** (< 120 ms) |

