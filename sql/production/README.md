# SES — Database Production Deployments (`sql/production/`)

Direktori ini berisi paket deployment database resmi untuk lingkungan **Production (PT Badak LNG — ELLPRD)**.

---

## 📜 Standar Operasional Prosedur (SOP) & Aturan Versioning Database Produksi

Demi menjamin keandalan, keterlacakan riwayat (*auditability*), kemudahan dokumentasi, serta keamanan eksekusi di lingkungan produksi, setiap perubahan skema database SES wajib mematuhi standar berikut:

### 1. Prinsip Satu Folder Per Tanggal Perubahan (`YYMMDD/`)
* Setiap paket rilis/perubahan skema di produksi **WAJIB dialokasikan ke dalam subfolder tersendiri berdasarkan tanggal rilisnya** dengan format `YYMMDD` (misal: `261019/`, `261023/`, dst.).
* **DILARANG** mencampur perubahan beda tanggal rilis ke dalam folder yang sama.

### 2. Aturan Immutabilitas Mutlak (*Strict Freeze*)
* Subfolder rilis yang telah diterapkan atau berstatus rilis masa lalu (seperti `261019/`) bersifat **BEKU / IMMUTABLE**.
* **DILARANG KERAS** memodifikasi file yang ada atau menambahkan skrip migrasi baru ke dalam folder rilis lama yang sudah lewat.
* Semua kebutuhan migrasi baru, patch, atau perbaikan skema harus dibuatkan folder bertanggal baru (`YYMMDD/`).

### 3. Struktur Standar Setiap Folder Rilis
Setiap folder rilis `YYMMDD/` wajib memiliki struktur mandiri sebagai berikut:
```
sql/production/YYMMDD/
├── 00_precheck.sql          # Prasyarat read-only: mengecek baseline rilis sebelumnya
├── NN_<nama_migrasi>.sql    # Skrip modular DDL/DML/Seed (nomor sesuai arsip)
├── 99_verify.sql            # Verifikasi read-only: memastikan seluruh kolom, tabel, index, view VALID
└── INSTALL-YYMMDD.sql       # Master runner SQL*Plus berurutan dengan spool log
```

### 4. Idempotensi & Keamanan Eksekusi
* Seluruh skrip wajib dirancang **idempoten** (aman dijalankan ulang jika eksekusi terputus di tengah jalan).
* Master runner wajib menyertakan:
  ```sql
  WHENEVER OSERROR EXIT FAILURE ROLLBACK
  WHENEVER SQLERROR EXIT SQL.SQLCODE ROLLBACK
  SPOOL install_YYMMDD.log
  ```
* Nomor berkas migrasi diselaraskan dengan [`sql/archive/migrations/`](../archive/migrations/) agar mudah di-diff terhadap riwayat development.

---

## 📦 Daftar Rilis Produksi

```
sql/production/
├── 261019/                      <-- Rilis Baseline (19 Oktober 2026) [BEKU / IMMUTABLE]
├── 261023/                      <-- Rilis Delta (23 Oktober 2026) [BEKU / IMMUTABLE]
├── 261029/                      <-- Rilis Delta (29 Oktober 2026) [TERBARU: Integrasi BIMA Approval]
└── README.md
```

---

## 📦 Rilis Baseline: `261019` (19 Oktober 2026) — *BEKU*

### Baseline Awal
ELLPRD per 19 Oktober 2026 telah menjalankan seluruh skrip produksi **sampai 47**; skrip **48 s/d 90** diterapkan melalui paket ini.

### Urutan Eksekusi `261019`
| Langkah | File | Isi Ringkas |
| :--- | :--- | :--- |
| 00 | `00_precheck.sql` | Berhenti sebelum mengubah apa pun bila skema bukan baseline yang diharapkan (read-only). |
| 48 | `48_mgr_approval_and_auth_type_alter.sql` | Flag approval manajer/procurement, `auth_type`. |
| 49 | `49_cash_expense_ddl.sql` | Tabel `SES_CASH_EXPENSE`. |
| 50 | `50_cash_expense_seed.sql` | Page, menu, RBAC Pengeluaran Kas. |
| 52 | `52_hybrid_routing_alter.sql` | Kolom `step_role` / `parallel_group` / `completion_rule`. |
| 53 | `53_task_signoff_ddl.sql` | Tabel `SES_APPROVAL_TASK_SIGNOFF`. |
| 54 | `54_request_evaluator_ddl.sql` | Tabel `SES_REQUEST_EVALUATOR`. |
| 58 | `58_procurement_supervisor_role_alter.sql` | Peran `PROCUREMENT_SUPERVISOR`. |
| 55 | `55_approval_pending_view_parallel.sql` | `SES_APPROVAL_PENDING_VIEW` paralel. |
| 36 | `36_app_approval_view_ses.sql` | `APP_APPROVAL_VIEW` (setelah 55). |
| 60 | `60_badge_chain_reshape_v2.sql` | Chain DEFAULT/PAYMENT ke bentuk 7 tahap. |
| 61 | `61_request_email_and_upload_token_alter.sql` | Email/token unggah, status `WAITING_FOR_UPLOAD`, job `REQ_EXPIRY`. |
| 63 | `63_badge_naming_alignment.sql` | Penamaan modul Badge, chain key `BADGE_*`. |
| 64 | `64_ses_configuration_and_ext_upload.sql` | Flag `need_ext_page_upload`, menu konfigurasi. |
| 65 | `65_ext_upload_staging_tables.sql` | Tabel staging unggah eksternal. |
| 66 | `66_paid_types_to_payment_chain.sql` | Jenis berbayar ke chain pembayaran. |
| 67, 70, 71 | `67_…`, `70_…`, `71_…` | Status/kolom screening per pekerja, dukungan Passport. |
| 72 | `72_riwayat_user_access_grant.sql` | Riwayat ke top-level RBAC. |
| 74 | `74_officer_checkpoint_rbac_grant.sql` | Grant checkpoint role SECURITY. |
| 80 | `80_app_link_ext.sql` | `APP_LINK_EXT = http://apps.badaklng.home:8005/ses-ext`. |
| 90 | `90_security_rbac_align.sql` | Role SECURITY disamakan persis dengan ELLDEV. |
| 99 | `99_verify.sql` | Verifikasi 431 kolom, 26 sequence, objek VALID, chain badge 7 tahap (read-only). |

---

## 📦 Rilis Delta: `261023` (23 Oktober 2026) — *RILIS AKTIF*

### Prasyarat
Paket ini merupakan paket delta yang dijalankan di atas skema yang **telah berhasil menjalankan rilis `261019`**.

### Urutan Eksekusi `261023`
| Langkah | File | Isi Ringkas |
| :--- | :--- | :--- |
| 00 | `00_precheck.sql` | Memvalidasi keberadaan tabel & kolom baseline `261019` (`SES_CASH_EXPENSE`, `SES_APPROVAL_STEP.STEP_ROLE`, `APP_LINK_EXT`, chain badge 7-tahap). |
| 81 | `81_ses_text_parameter_seed.sql` | Seeding teks user-facing (disclaimer hukum, warning kuota, template email) ke `APP_PARAMETER` (`SES_TXT_*`). |
| 82 | `82_ses_history_view.sql` | View dasar `SES_HISTORY_V` untuk paginasi riwayat lintas modul. |
| 91 | `91_sticker_request_type_code.sql` | Kolom `sticker_type_code`, `contract_no`, `contract_expiry_date`, `requestor_type` pada `SES_STICKER_REQUEST` dan tabel & sequence `SES_STICKER_VEH_DOC`. |
| 92 | `92_performance_indexes.sql` | Indeks performa untuk `APP_USER`, `SES_REQUEST`, dan `SES_STICKER_REQUEST`. |
| 93 | `93_history_stage_resolution_view.sql` | Resolusi status tahap aktif (`WAITING_PAYMENT`, `WAITING_SCREENING`, dll.) pada `SES_HISTORY_V`. |
| 94 | `94_platform_147_and_payment_index.sql` | Kolom `APP_JOB.ACTIVE_FLAG` (Platform 1.4.7) dan indeks antrean petugas `IDX_SES_PAYMENT_STAT_ID`. |
| -- | Recompile | `DBMS_UTILITY.COMPILE_SCHEMA` untuk menyegarkan dependensi objek. |
| 99 | `99_verify.sql` | Verifikasi menyeluruh: 440+ kolom, 27 sequence, 14 indeks performa, parameter `SES_TXT_*`, view `SES_HISTORY_V` VALID. |

---

## 🚀 Panduan Eksekusi Deployment di ELLPRD

### Eksekusi Paket Delta `261023`:
1. **Backup** skema `ses` di ELLPRD sebelum eksekusi.
2. Dari direktori `sql/production/261023/`, login sebagai pemilik skema SES lalu jalankan:
   ```cmd
   cd sql/production/261023
   sqlplus ses@//10.10.217.252:1521/ELLPRD @INSTALL-261023.sql
   ```
3. Pantau log output pada file `install_261023.log`.
4. Pastikan baris akhir log memunculkan:
   ```
   Verifikasi 261023 OK: 442 kolom, 27 sequence, 14 indeks performa, semua objek SES/APP VALID, chain badge 7 tahap.
   === Deployment SES 261023 selesai. Restart ses-app dan ses-ext. ===
   ```
5. Deploy WAR `ses-app` dan `ses-ext` yang baru ke WebLogic Server 12c, lalu **restart kedua managed server** (agar cache `APP_PARAMETER` di memori disegarkan).


---

## 📦 Rilis Delta: 261029 (29 Oktober 2026) — *RILIS AKTIF*

### Prasyarat
Paket ini merupakan paket integrasi BIMA yang dijalankan di atas skema yang **telah berhasil menjalankan rilis 261019 & 261023** (SES_APPROVAL_PENDING_VIEW telah aktif).

### Urutan Eksekusi 261029
| Langkah | File | Isi Ringkas |
| :--- | :--- | :--- |
| 56 | 56_ses_bima_approval_pkg.sql | View SES_BIMA_APPROVAL_V (9 kolom standar BIMA) dan Package PKG_SES_BIMA_APPROVAL (Spec & Body). |
| -- | Recompile | DBMS_UTILITY.COMPILE_SCHEMA untuk menyegarkan dependensi objek. |
| 99 | 99_verify_bima_pkg.sql | Verifikasi status VALID untuk View, Package Spec, Package Body, serta uji eksekusi fungsi/cursor. |
