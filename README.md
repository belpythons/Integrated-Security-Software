# SES — Secure Entry System (PT Seterah / PT. S)

A web-based **Secure Entry System (SES)** for PT Seterah (PT. S) built on top of the enterprise **Platform** library (`com.badaklng:platform:1.4.7`). SES provides an enterprise workflow for managing personnel security badges, external contractor document verification, multi-tiered approval chains, worker screening scheduling, vehicle stickers, payments, and financial cash reports.

---

## 🛠️ Technology Stack

- **Java**: Java 8 / 11 / 17 compatible (`javax.*` namespace matching WebLogic 12c & Platform library)
- **Framework**: Spring Boot `2.7.18` & Spring Security `5`
- **Platform Foundation**: `com.badaklng:platform:1.4.7` (RBAC, Audit, User/Role/Menu management, Mail, Scheduler)
- **Database**: Oracle Database 10g/11g/19c (`Oracle12cDialect`, `ddl-auto=none`)
- **Template Engine**: Thymeleaf + Thymeleaf Layout Dialect + Spring Security Extras + Lucide Icons
- **Job Scheduling**: Quartz Scheduler (`memory` store)
- **Reporting**: Apache POI (Multi-sheet Excel Workbook generation for financial reports)
- **Deployment**: Executable WAR (Supports local embedded Tomcat via `mvn spring-boot:run` and Oracle WebLogic Server 12c deployment)

---

## 📦 Core Business Modules & Key Enhancements

### 1. Badge Request (`/request`) & External Contractor Upload
- **Request Submission**: Submit badge requests for workers with contract metadata, access zone, validity period, and required documents.
- **Dynamic Request Types (`/admin/request-type`)**: Configurable document requirements, base pricing per worker, and dynamic approval requirements (*Needs Approval Request Manager*, *Needs Approval Procurement Manager*).
- **External Contractor 1-Session Document Upload (`/request/upload-docs?token=...`)**:
  - Secure token lifecycle with 7-day validity.
  - Allows external contractors to upload required documents without full portal login credentials.
  - Completing upload locks the token and triggers the internal approval engine.
- **Worker Auto-Lookup**: Search existing worker profiles (`/request/workers/search`) with NIK/Passport and badge validation.
- **Official Permit Document Generation (`/request/{id}/document.pdf`)**:
  - Generates standardized official permit PDF with PT. Seterah letterhead (*Bontang, East Kalimantan, Indonesia — Security Suite*).
  - Embeds dual verification barcodes (QR Codes) for **Procurement Manager** and **Site Manager** linking to external verification page (`/verify/{requestNo}`) via `APP_LINK_EXT`.
  - Accessible directly for approved requests (`APPROVED`) from Request View, Request List, Admin History (`/history`), and Approval Detail (`/approval/detail`).
- **Public Verification Page (`/verify/{requestNo}`)**:
  - Standalone mobile-friendly public landing page accessible without login (`permitAll`).
  - Displays work contract summary and worker roster while hiding sensitive personal data (NIK/KTP).

### 2. Approval Engine & Checkpoint Management (`/approval`)
- **Dynamic Multi-Tier Chains**: Configurable approval chains with parallel/sequential steps, evaluators, and department manager routing (`SesApprovalInstance`, `SesApprovalTask`, `SesApprovalAssignee`).
- **Streamlined Approver Inbox (`/approval`)**: Dedicated interface focused on pending actions with responsive filter bar, status badges, and direct detail routing.
- **Interactive Checkpoints**:
  - `PAYMENT_VERIFICATION`: Blocks final approval until payment is verified by finance officer.
  - `SCREENING_SCHEDULE`: Blocks progress until security screening is scheduled and executed.
  - `BADGE_ISSUANCE`: Manages clearance for badge printing and collection.
- **Automated Email Notifications**: Formatted HTML notification emails with direct action links.
- **Security Validation (`SafeTextValidator`)**: Automatic sanitization on notes (approval/rejection/skip) preventing script and HTML injections.

### 3. Scheduling & Per-Worker Screening (`/scheduling`)
- **Scheduling Queue (`/scheduling/queue`)**: Filterable queue of requests awaiting screening or badge retrieval with instant interactive client-side filtering.
- **Per-Worker Attendance & Outcome Decision**:
  - Attendance checklist per individual worker: **Hadir / Lolos** (`ATTENDED`), **Tidak Hadir** (`NO_SHOW`), or **Tidak Lulus** (`FAILED`).
  - **Save as Draft Screening (`POST /{scheduleId}/screening-draft`)**: Allows officers to save partial attendance records without finalizing the schedule or advancing the approval checkpoint.
  - Gating badge retrieval: only cleared workers (`ATTENDED`) proceed to badge printing and collection.

### 4. Payment Management & Financial Reports (`/payment`)
- **Daftar Tagihan & Pembayaran (`/payment`)**: Unified filter bar card with instant module and status dropdown filtering (`PENDING`, `VERIFYING`, `VERIFIED`, `REJECTED`, `CANCELLED`).
- **Payment Verification (`/payment/verify`)**:
  - Upload payment proof by requestor or direct *"Bayarkan atas nama pemohon"* by finance officers.
  - Instant module filtering (`Badge Karyawan`, `Stiker Kendaraan`).
  - Full synchronization of payment verification status, approved amounts, and rejection notes across **Request Detail** (`/request/view/{id}`) and **Approval Detail** (`/approval/detail/{id}`).
- **Cash-Inflow Recap (`/payment/recap`)**: Filter verified revenue by date range (`from` and `to`), search keywords, and export to Excel.
- **Multi-Sheet Financial Report Export (`/payment/recap/report.xlsx`)**: 
  - Download comprehensive 3-sheet Excel report covering **Pemasukan (Cash Inflow)**, **Pengeluaran (Cash Expense)**, and **Ringkasan (Net Balance Summary)**.

### 5. Cash Expense (`/admin/cash-expense`)
- Record and track petty cash expenses, receipt attachments, categories, and real-time remaining cash balances with instant search and monthly Excel export.

### 6. Vehicle Sticker Request (`/sticker`)
- Request vehicle security stickers for regular and guest vehicles with police number validation and integrated approval/payment tracking.
- Dedicated document management for vehicles (`SES_STICKER_VEH_DOC`) supporting STNK, SIM, and KIR attachments.

### 7. Unified History & Tracking (`/history`)
- Centralized tracking across badge requests, vehicle stickers, payments, and approvals powered by database view `SES_HISTORY_V`.
- **Hierarchical Data Scoping (Section / Department / Org-Wide)**:
  - Regular users (`REQUESTOR`) view submissions belonging to colleagues within their **Section** (if assigned) or their entire **Department** (if section is blank), or personal submissions.
  - Administrators and officers (`SUBMIT` action) maintain full visibility across the entire organization.
  - In-scope colleagues can view application details (`/request/{id}/view`, `/sticker/{id}/view`) and download approved permit PDFs (`/request/{id}/document.pdf`).

### 8. Modular Push Email SMTP & Lightweight Content Storage
- **Clean Content Snippets in DB**: `APP_MAIL.MAIL_BODY` stores only clean, semantic HTML fragments (~1 KB) without bloated boilerplate `<html>`, `<body>`, or image tags.
- **Dynamic Master Layout**: Decorated dynamically at SMTP dispatch time by `EmailLayoutDecorator` in `SesMailJob`, attaching the official PT. S logo via inline CID (`cid:badakLogo`) and dark-blue branding.
- **Bilingual Notifications**: Document upload notifications (`ext upload-docs`) are formatted bilingually (Indonesian & English) with direct action buttons and copyable fallback links.

---

## 🗄️ Database Architecture & Migrations

### Database Connection (ELLDEV)
| Parameter        | Value                                          |
| :--------------- | :--------------------------------------------- |
| **Host**         | `10.10.214.48`                                 |
| **Port**         | `1521`                                         |
| **Service Name** | `ELLDEV`                                       |
| **Username**     | `ses`                                          |
| **Password**     | `ses2026`                                      |
| **JDBC URL**     | `jdbc:oracle:thin:@//10.10.214.48:1521/ELLDEV` |

### Synchronized Database Migrations & Versioning SOP
Migrations are organized in [`sql/archive/migrations/`](sql/archive/migrations) and packaged for production in [`sql/production/`](sql/production/) under immutable date-based folders (`YYMMDD`):
- **`sql/production/261019/` (Baseline Release — Immutable)**:
  - Migrations 48 through 80 & 90: Core approval chains, cash expense, external upload staging, worker passport support, and security RBAC alignment.
  - Verified by `INSTALL-261019.sql` and `99_verify.sql` (431 columns, 26 sequences).
- **`sql/production/261023/` (Delta Release — Latest)**:
  - `81_ses_text_parameter_seed.sql`: Seeds user-facing legal and warning text into `APP_PARAMETER` (`SES_TXT_*`).
  - `82_ses_history_view.sql`: Creates `SES_HISTORY_V` for unified pagination and filtering.
  - `91_sticker_request_type_code.sql`: Adds sticker type and contract metadata to `SES_STICKER_REQUEST` and creates `SES_STICKER_VEH_DOC` table & sequence.
  - `92_performance_indexes.sql`: High-performance index optimization for Oracle 19c.
  - `93_history_stage_resolution_view.sql`: Precise stage status resolution view for `/history`.
  - `94_platform_147_and_payment_index.sql`: Platform 1.4.7 compatibility (`APP_JOB.ACTIVE_FLAG`) and payment queue indexing.
  - Verified by `INSTALL-261023.sql` and `99_verify.sql` (442 columns, 27 sequences, 14 indexes).

---

## ⚙️ Application Parameters (`APP_PARAMETER`)

Configured via `/admin/parameter` or seeded through SQL scripts:
- **`APP_LINK`**: Base URL for internal links in email notifications (e.g. `http://localhost:8082`).
- **`APP_LINK_EXT`**: Base URL for external contractor document upload links (e.g. `http://localhost:8083/ses-ext`).
- **`MAIL_SERVER`**: Outbound SMTP server configuration string (e.g. `host=10.10.214.48,port=25`).
- **`TESTING_MODE`**: Set to `ON` during testing/staging to redirect all outbound emails to `ADMIN_EMAIL`.
- **`ADMIN_EMAIL`**: Recipient email address for redirected test emails.
- **`SES_TXT_*`**: Customizable user-facing notification texts and disclaimers.

---

## 🏗️ 3-Tier Enterprise Codebase Architecture

Struktur proyek SES dipisahkan secara fisik menjadi 3 tier arsitektur mandiri:

```
ses/
├── platform/               # [TIER 1] Domain Entities, Repositories & Platform Abstraction
│   └── ses-core/           # Shared business entities, repositories, workflow & file service
├── intranet/               # [TIER 2] Internal Corporate Network Applications
│   └── ses-app/            # Intranet Web Backoffice (Port 8082, LDAP + Local Auth)
├── internet/               # [TIER 3] External DMZ, Public Gateway & Mobile
│   ├── ses-ext/            # External Vendor Portal & Public Verification Gateway (Port 8083)
│   ├── ses-api/            # Stateless RESTful API Gateway (Port 8084)
│   └── ses-mobo/           # Flutter Mobile Application (Gate Security & Field POSKO)
├── docs/                   # Dokumentasi Resmi, FDD, Arsitektur, dan Audit Schema
├── tests/                  # Kebutuhan Pengujian, Testing Mock & Automation Scripts
├── localdev/               # Tooling & Konfigurasi Development Lokal (ELLDEV & Offline)
└── ses.bat                 # Master Control Script Windows (Menu Interaktif [1]-[10])
```

---

## 🚀 Master Control Script (`ses.bat`)

Seluruh operasional disatukan ke dalam **1 skrip tunggal** di root direktori: **[`ses.bat`](file:///d:/GIT/Gitea/Integrated-Security-Software/ses.bat)**.
Cukup klik ganda (double-click) **`ses.bat`**, lalu ketik nomor menu `1`, `2`, `3`, dst.:

- **`[1]`** Jalankan Intranet Backoffice (`ses-app` : Port `8082`)
- **`[2]`** Jalankan Internet Extranet Portal (`ses-ext` : Port `8083`)
- **`[3]`** Jalankan RESTful API Gateway (`ses-api` : Port `8084`)
- **`[4]`** Jalankan Mobile App di Android Emulator (`ses-mobo`)
- **`[5]`** **LAUNCH SEMUA SERVICE WEB** (Membuka 3 jendela otomatis: :8082, :8083, :8084)
- **`[6]`** Build Seluruh Proyek (Java Web Modules + Flutter APK)
- **`[7]`** Build Cepat Java Web Saja (`mvn clean install -DskipTests`)
- **`[8]`** Build APK Android Debug (`flutter build apk`)
- **`[9]`** Rebuild Pustaka Bersama (`platform/ses-core`)
- **`[10]`** Uji Koneksi Database Oracle ELLDEV (`10.10.214.48:1521`)

---

## 🚀 Running & Building (CLI Manual)

### 1. Run Locally via Maven
```bash
# Internal Intranet App (Port 8082)
mvn spring-boot:run -pl intranet/ses-app "-Dspring-boot.run.profiles=local"

# External Gateway DMZ Portal (Port 8083)
mvn spring-boot:run -pl internet/ses-ext "-Dspring-boot.run.profiles=local"

# RESTful API Gateway (Port 8084)
mvn spring-boot:run -pl internet/ses-api "-Dspring-boot.run.profiles=local"
```

### 2. Build Multi-Module Production Packages
```bash
mvn clean package -DskipTests
```
Menghasilkan artefak WAR/JAR:
```
platform/ses-core/target/ses-core.jar
intranet/ses-app/target/ses-app.war     (Internal Application, context-root: /ses)
internet/ses-ext/target/ses-ext.war     (External Gateway, context-root: /ses-ext)
internet/ses-api/target/ses-api.war     (RESTful API Gateway, context-root: /api)
```
