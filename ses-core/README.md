# `ses-core` — Domain & Service Library

## Peran modul ini

`ses-core` adalah library (packaging `jar`, lihat [pom.xml](pom.xml)) berisi seluruh JPA entity, Spring Data repository, service, dan validator bisnis SES. Modul ini **tidak bisa dijalankan sendiri** (tidak ada `@SpringBootApplication`, tidak ada controller) — ia dikonsumsi sebagai dependency oleh dua aplikasi Spring Boot yang bisa dijalankan:

- **`ses-app`** — aplikasi intranet internal (semua controller + halaman Thymeleaf)
- **`ses-ext`** — gateway eksternal DMZ (upload dokumen kontraktor)

Keduanya memanggil service yang sama di sini untuk logika bisnis (approval, file, mail, payment, dst.) sehingga aturan bisnis hanya ada di satu tempat, bukan terduplikasi di dua WAR. Satu-satunya dependency eksternal besar selain Spring Boot adalah `com.badaklng:platform:1.4.7` (RBAC, user/role/menu, file dasar, mail scheduler) — lihat [pom.xml](pom.xml).

Karena `ses-app`/`ses-ext` butuh JAR hasil build modul ini di `.m2` lokal, root project menyediakan `rebuild-ses-core.bat` (`mvn clean install -DskipTests` khusus modul ini) supaya iterasi saat mengubah entity/service di sini tidak perlu rebuild seluruh reactor — lihat [README.md](../README.md#1-rebuild-ses-core-utility-script) di root.

Untuk daftar lengkap tabel/view database, lihat **[docs/database-schema.md](../docs/database-schema.md)** — dokumen ini hanya membahas organisasi kode, bukan mengulang skema kolom.

---

## Konvensi sub-package

Hampir setiap fitur (`approval`, `request`, `payment`, `scheduling`, `sticker`, `cash`, `history`) dipecah dengan pola sub-package yang sama. Artinya begitu Anda paham satu modul fitur, Anda paham semuanya:

| Sub-package | Isi | Catatan |
|---|---|---|
| `entity/` | `@Entity` JPA, enum status, composite-key `@Embeddable` | Pemetaan langsung ke tabel/view — lihat database-schema.md untuk nama tabel |
| `repository/` | Interface `JpaRepository`/`CrudRepository` | Query method Spring Data, kadang `@Query` native untuk kasus kompleks |
| `service/` | `@Service` — logika bisnis, transaksi | Satu-satunya tempat yang boleh dipanggil controller di `ses-app`/`ses-ext` |
| `model/` | DTO/form biasa (bukan entity) | Dipakai sebagai backing object form Thymeleaf (`@ModelAttribute`), bukan untuk persistence |
| `spi/` | Interface kontrak lintas-modul | Lihat bagian "Approval Engine" di bawah — ini yang membuat modul tetap saling lepas (*decoupled*) |
| `file/` | Implementasi `FileOwnershipResolver` | Menjawab "siapa pemilik `APP_FILE` ini" per modul — dipakai saat cek akses unduh dokumen |
| `job/` | Quartz `Job` + registrasinya | Hanya ada di `request` (`RequestExpiryJob`) saat ini |
| `config/` | `@Configuration` bean wiring | Mis. sinkronisasi form registry, filter Spring Security |

---

## Peta package ke tabel/fitur

| Package | Entity inti | Tabel/view (lihat database-schema.md) | Fungsi |
|---|---|---|---|
| `approval` | `SesApprovalChain/Step/Instance/Task/...` | `SES_APPROVAL_*`, view `SES_APPROVAL_PENDING_VIEW`, `APP_APPROVAL_VIEW` | Mesin approval generik — lihat bagian khusus di bawah |
| `request` | `SesRequest`, `SesRequestWorker(Doc)`, `SesRequestType(Doc)`, `SesZone`, `SesDocRequirement` | `SES_REQUEST*`, `SES_ZONE`, `SES_DOC_REQUIREMENT`, view HR (`AppEmpInfoView`, `AppEmpLkpView`, `ContractV`, `SesWorkerLookupView`) | Modul badge request — fitur inti aplikasi |
| `payment` | `SesRequestPayment(History)` | `SES_REQUEST_PAYMENT*` | Model pembayaran generik dipakai bersama oleh `request` **dan** `sticker` (lihat `BUSINESS_TYPE`/`BUSINESS_REF_ID`) |
| `scheduling` | `SesRequestSchedule(History)` | `SES_REQUEST_SCHEDULE*` | Penjadwalan screening/pengambilan badge |
| `sticker` | `SesStickerRequest/Type/Vehicle/VehDoc` | `SES_STICKER_*` | Modul stiker kendaraan, struktur mirip `request` |
| `cash` | `SesCashExpense` | `SES_CASH_EXPENSE` | Pencatatan kas kecil untuk rekap keuangan |
| `history` | `SesHistoryView` | view `SES_HISTORY_V` | Agregator read-only untuk layar `/history` |
| `dashboard` | *(tanpa entity sendiri)* | — | Service agregasi lintas-modul, baca langsung dari repository `request`/`sticker`/`payment`/`cash` |
| `ses.file` | `SesAppFile` | `APP_FILE` | Upload generik — lihat di bawah |
| `ses.mail` | — | `APP_MAIL` (via platform) | Antrian & dekorasi email |
| `ses.user` | — | view `AppEmpLkpView` | Resolusi scope data user (section/department/personal) |
| `ses.validation` | `@SafeText`, `@ValidFileExtension` | — | Bean Validation custom |
| `ses.web` | `SesExceptionHandler` | — | `@ControllerAdvice` global |
| `ses.async`, `ses.scheduler`, `ses.jdbc` | — | — | Util kecil: konfigurasi `@Async`, guard auto-start Quartz, helper `IN (...)` ber-chunk untuk batasan 1000 item Oracle |
| `switchuser` | — | — | Wiring `SwitchUserFilter` Spring Security (impersonasi user oleh admin) |

---

## Approval Engine — kontrak SPI

Paket `approval` adalah mesin workflow **generik**: ia tidak tahu apa itu "badge request" atau "sticker". Modul bisnis (`request`, `payment`, `scheduling`, `sticker`) mendaftar ke mesin ini lewat package `spi/` masing-masing, dan arah dependency selalu satu arah — **modul bisnis bergantung pada `approval`, tidak pernah sebaliknya**. `approval` menemukan implementasi lewat `ObjectProvider<...>` (Spring bean discovery), jadi tidak ada compile-time dependency balik ke modul bisnis.

Interface kunci (semua di `approval/spi/`):

| Interface | Dipakai untuk | Implementasi contoh |
|---|---|---|
| `ApprovalFormDescriptor` | Mendaftarkan sebuah "form" (jenis transaksi) ke engine: nama, dropdown routing (`matchKeys`), data baris worklist, detail model untuk halaman approval, isi email | `request/spi/RequestApprovalBridge.java` |
| `ApprovalOutcomeListener` | Dipanggil **dalam transaksi yang sama** saat instance approval selesai (APPROVED/REJECTED) — dipakai modul bisnis untuk menulis status final ke tabel miliknya sendiri | `RequestApprovalBridge.onCompleted()`, `payment/spi/PaymentApprovalBridge.java` (hanya bereaksi ke REJECTED) |
| `CheckpointActionProvider` | Mengizinkan modul pemilik sebuah checkpoint (mis. `PAYMENT_VERIFICATION`, `SCREENING_SCHEDULE`) merender form inline di halaman detail approval, supaya approver tidak perlu pindah halaman | `PaymentApprovalBridge.actionFor()` |
| `CheckpointTaskType` | Registrasi nama checkpoint key yang muncul di dropdown admin saat membuat chain | `PaymentApprovalBridge.paymentVerificationCheckpoint()` |

Pola yang konsisten di semua modul bisnis: **satu kelas "Bridge"** (mis. `RequestApprovalBridge`, `PaymentApprovalBridge`, `StickerApprovalBridge`, `SchedulingApprovalBridge`) adalah **satu-satunya** kelas di modul itu yang boleh meng-import `com.badaklng.approval.spi.*` atau `com.badaklng.approval.entity.ApprovalStatus`. Kelas lain di modul tersebut hanya memanggil `ApprovalService` (bean `@Service` biasa) tanpa menyentuh tipe internal approval. Ini membuat batas modul eksplisit dan mudah di-grep (`grep -r "approval.spi" com/badaklng/<modul>/`).

Pola SPI yang sama diulang untuk kebutuhan lain:
- `payment.spi.PaymentBusinessDescriptor` — modul bisnis memberi tahu `payment` berapa `amountDue` untuk transaksinya.
- `history.spi.HistorySource` — modul bisnis memberi daftar baris untuk layar `/history` (bulk, bukan per-row, karena ini listing).
- `request.spi.RequestDetailContributor`, `RequestHistorySource`, `RequestPaymentDescriptor`, `scheduling.spi.SchedulingApprovalBridge`/`SchedulingRequestBridge` — variasi kontrak serupa untuk pasangan modul lain.

**Mengapa penting**: saat menambah modul bisnis baru dengan approval, Anda tidak pernah mengubah kode di `approval/`. Cukup implementasikan interface SPI yang relevan sebagai `@Component`/`@Configuration` bean di modul baru.

---

## Cross-cutting concerns

**Upload file (`ses.file.AppFileService`)** — satu service generik dipakai semua modul untuk simpan dokumen ke `APP_FILE`. Validasi terpusat di sini: batas ukuran (`ses.upload.max-document-size`), deteksi tipe file dari *magic number* (bukan percaya `Content-Type` dari browser — lihat `sniffContentType()`), dan sanitasi nama file untuk cegah path traversal. Setiap modul (`request/file`, `payment/file`, `sticker/file`) hanya perlu mengimplementasikan `FileOwnershipResolver` untuk menjawab "siapa pemilik file ini" saat cek akses unduh.

**Mail (`ses.mail`)** — `SesMailDispatcherService` adalah satu-satunya implementasi pengiriman (HTML sniffing, safety valve `TESTING_MODE`/`REDIRECT_MAIL`, retry). `SesMailJob` (Quartz) hanya memanggil `drainQueue()` miliknya setiap menit; jalur instan (`dispatchPendingAsync()`) memakai service yang sama — sengaja didesain begitu supaya dua jalur tidak punya dua implementasi SMTP yang bisa saling berbeda (lihat komentar panjang di `SesMailJob.java:24-31` yang menjelaskan insiden logo app yang pernah terjadi karena duplikasi semacam ini).

**Cache in-memory TTL** — `DashboardService` (`ses.dashboard.snapshot-cache-ttl-ms`, default 60 dtk) dan `PaymentService` (`ses.payment.verified-cache-ttl-ms`) meng-cache hasil query full-table yang mahal, di-invalidate eksplisit setiap ada approve/verify baru. Bukan cache library — hanya field biasa + timestamp (lihat `DashboardService.java:58-59`), cukup untuk satu instance aplikasi tanpa perlu Redis/Caffeine.

**Scope data user (`ses.user.UserScopeService`)** — resolusi "siapa saja yang boleh dilihat user ini" berdasarkan hierarki section → department → personal dari view HR (`AppEmpLkpView`), dipakai `/history` untuk membatasi visibilitas data REQUESTOR biasa.

**Batasan Oracle 1000-item `IN (...)`** — `ses.jdbc.InChunks` adalah helper kecil yang dipanggil setiap kali kode perlu `findAllById()` dengan daftar id yang jumlahnya bisa besar (lihat pemakaiannya di `RequestApprovalBridge.rowData()`), supaya tidak meledak saat daftar id > 1000.
