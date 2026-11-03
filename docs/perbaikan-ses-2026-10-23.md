# Perbaikan SES — platform 1.4.7, TTFB dashboard/payment, audit LCP via browser nyata (2026-10-23)

Lanjutan dari rencana audit LCP: sinkronisasi `platform.version` ke `1.4.7`,
migrasi SQL yang menyertainya, short-TTL cache untuk dua full-table read di
`/dashboard` dan `/payment`, lalu verifikasi TTFB/LCP langsung di instance
yang berjalan terhadap `10.10.214.48:1521/ELLDEV`.

**Metode.** WAR hasil `mvn package` dijalankan `java -jar` di port 8084
terhadap ELLDEV. Login dilakukan di Chrome nyata (Browser pane), lalu TTFB
dibaca dari `performance.getEntriesByType('navigation')[0]` (W3C Navigation
Timing) pada *warm hit* (navigasi kedua ke rute yang sama, setelah cache
TTL dan JIT sama-sama panas) — bukan estimasi `curl`.

---

## Ringkasan dampak

| Komponen | Status |
|---|---|
| `platform.version` 1.4.6 → 1.4.7 | Selesai di `pom.xml`; **belum** ter-publish ke Gitea Maven registry (lihat Catatan Terbuka) |
| Migrasi `93_platform_147_and_payment_index.sql` (`APP_JOB.ACTIVE_FLAG` + indeks `(STATUS, PAYMENT_ID DESC)`) | File dibuat; **belum dijalankan** terhadap ELLDEV (lihat Catatan Terbuka) |
| Cache TTL `DashboardService.snapshot()` & `PaymentService.findVerified()` | Selesai + unit test, `mvn test` hijau |
| Build | `mvn -o compile`/`package` sukses setelah platform 1.4.7 di-install lokal |

| Rute | TTFB *warm* (terukur) | Baseline diagnosis awal | Catatan |
|---|---|---|---|
| `/sticker/new` | 446 ms | 255 ms | sudah cepat sebelum perubahan ini |
| `/scheduling/calendar` | 1.184 ms | 1.304 ms | tidak disentuh perubahan ini |
| `/payment` | **1.082 ms** | 2.226 ms | turun, tapi masih di atas target <1.000 ms |
| `/dashboard` | **~1.900–2.200 ms** | 2.517 ms | turun, tapi masih jauh di atas target |
| `/approval` | 2.133 ms | 1.091 ms | tidak disentuh perubahan ini; lebih lambat dari diagnosis awal pada pengukuran kali ini |

Baseline "diagnosis awal" berasal dari pengujian sebelumnya (bukan diukur
ulang secara independen di sesi ini); kolom ini dicatat seperti yang
dilaporkan sebelum perubahan hari ini.

LCP **tidak berhasil terukur** lewat Browser pane otomatis: tab yang
dikendalikan lewat automation melaporkan `document.hidden = true` ke skrip
halaman walau pane sedang ditampilkan ke pengguna, dan spesifikasi LCP hanya
mencatat metrik itu untuk halaman yang visible sepanjang waktu muatnya. TTFB
dan First Contentful Paint tetap valid (Navigation/Paint Timing API tidak
bergantung pada visibility), tapi untuk LCP yang sah, jalur **Manual
Verification** (DevTools → Performance/Lighthouse, dibuka langsung oleh
pengguna) tetap diperlukan.

---

## 1. Platform 1.4.7 & migrasi SQL

- `pom.xml`: `<platform.version>` dinaikkan dari `1.4.6` ke `1.4.7`.
- Rilis `1.4.7` platform (dicek dari `scratch/platform_repo`, tag `v1.4.7`)
  hanya berisi dua perubahan: kolom `APP_JOB.ACTIVE_FLAG` (switch on/off per
  job tanpa restart) dan `GlobalExceptionHandler` mengabaikan
  `ClientAbortException` alih-alih mengirim email ke admin. **Tidak ada**
  perubahan di jalur auth/login/security — dikonfirmasi lewat
  `CHANGELOG.md` platform (termasuk baris eksplisit "No change to ...
  `login.html`").
- `sql/archive/migrations/93_platform_147_and_payment_index.sql` dibuat
  mengikuti pola idempotent-check migrasi `92_performance_indexes.sql`:
  1. `ALTER TABLE APP_JOB ADD (ACTIVE_FLAG CHAR(1) DEFAULT 'Y' NOT NULL)` —
     wajib ada sebelum deploy 1.4.7 (Hibernate select kolom ini,
     `ddl-auto=none`).
  2. `CREATE INDEX IDX_SES_PAYMENT_STAT_ID ON SES_REQUEST_PAYMENT (STATUS,
     PAYMENT_ID DESC)` — migrasi 92 hanya meng-cover urutan
     `CREATED_DATE DESC`, sedangkan `PaymentService.findForOfficer()` (sudah
     ada, memfilter PENDING/VERIFYING/REJECTED) meng-order by
     `PAYMENT_ID DESC`.
- Catatan: pemeriksaan kode menemukan `findForOfficer()` **sudah**
  memfilter status aktif saja sebelum sesi ini — bagian rencana yang
  mendeskripsikan perubahan ini sebagai pekerjaan baru sudah usang/duplikat,
  jadi tidak ada kode yang diubah untuk itu.

## 2. Cache TTL — `/dashboard` & `/payment`

`DashboardService.snapshot()` melakukan dua `findAll()` (SES_REQUEST,
SES_STICKER_REQUEST) tanpa limit pada **setiap** hit admin ke `/dashboard`.
`PaymentService.findVerified()` (full scan `SES_REQUEST_PAYMENT` status
VERIFIED) dipanggil **dua kali** per hit yang sama (`summary()` dan
`monthlyCashInflow()`).

Pola yang dipakai: `ConcurrentHashMap`/`volatile` + timestamp, sama persis
dengan cache inbox approval yang sudah ada di `ApprovalService`
(`ses.approval.inbox-cache-ttl-ms`). TTL baru:

- `ses.dashboard.snapshot-cache-ttl-ms=60000`
- `ses.payment.verified-cache-ttl-ms=60000`

Cache pembayaran di-invalidasi eksplisit di `approve()` dan
`submitAndVerifyOnBehalf()` — dua satu-satunya jalur yang menambah baris
VERIFIED — supaya tidak pernah menyajikan data uang basi lebih dari sisa
TTL setelah seseorang baru saja approve. Diuji di
`ses-core/src/test/java/com/badaklng/payment/service/PaymentServiceVerifiedCacheTest.java`
(cache hit dalam TTL, invalidasi setelah `approve()`); `mvn test` untuk test
ini: **2/2 lulus**.

## 3. Mengapa `/dashboard`, `/approval`, `/scheduling/calendar` masih di atas 1 detik

`/sticker/new` melewati `GlobalModelAttributes` yang sama (5 query per
request, disebut di diagnosis awal) dan tetap hanya 446 ms — jadi overhead
itu **bukan** penyebab dominan sisa lambat di empat halaman berat. Artinya
`/dashboard`, `/payment`, `/approval`, `/scheduling/calendar` masing-masing
masih punya biaya backend spesifik-halaman di luar dua cache yang
ditambahkan hari ini. Menurunkan keempatnya ke bawah 1 detik berarti
profiling `ApprovalController`/`SchedulingController` dengan cara yang sama
seperti `DashboardService`/`PaymentService` — di luar lingkup yang
dikerjakan hari ini.

---

## Catatan Terbuka (butuh keputusan/aksi manual)

1. **Platform 1.4.7 belum ter-publish** ke Gitea Maven registry
   (`https://gitea.badaklng.com/api/packages/PTB_IT/maven`) — baru di-build
   dan di-install ke `.m2` lokal untuk keperluan verifikasi sesi ini. Build
   lain (CI, developer lain) akan gagal resolve `platform:1.4.7` sampai ada
   yang menjalankan `mvn deploy` dari `scratch/platform_repo` dengan hak
   akses registry.
2. **Migrasi `93_platform_147_and_payment_index.sql` belum dijalankan**
   terhadap ELLDEV — sengaja tidak dieksekusi tanpa konfirmasi eksplisit
   karena mengubah skema database bersama tim.
3. Login lokal `admin`/`admin123` (default di dokumentasi `run-local.md`)
   **tidak valid** di database ELLDEV saat ini; akun yang berhasil untuk
   sesi verifikasi ini berbeda dari dokumentasi.
