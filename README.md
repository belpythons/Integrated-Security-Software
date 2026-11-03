# SES — Secure Entry System (PT Badak LNG)

A web-based **Secure Entry System (SES)** for PT Badak LNG built on top of the Badak LNG **Platform** library (`com.badaklng:platform:1.4.7`). SES provides an enterprise workflow for managing personnel security badges, external contractor document verification, multi-tiered approval chains, worker screening scheduling, vehicle stickers, payments, and financial cash reports.

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
  - Generates standardized official permit PDF with PT Badak NGL letterhead (*Bontang, East Kalimantan, Indonesia — Security Suite*).
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
- **Dynamic Master Layout**: Decorated dynamically at SMTP dispatch time by `EmailLayoutDecorator` in `SesMailJob`, attaching the official PT Badak NGL logo via inline CID (`cid:badakLogo`) and dark-blue branding.
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

## 🚀 Running & Building

### 1. Rebuild `ses-core` Utility Script
For fast local iteration when updating shared entities or services in `ses-core`:
- Double-click **`rebuild-ses-core.bat`** in the project root, or execute:
  ```cmd
  rebuild-ses-core.bat
  ```
  This runs `mvn clean install -DskipTests` inside `ses-core` and installs the updated JAR into your local `.m2` repository without rebuilding the entire multi-module reactor.

### 2. Run Locally (Development)
Run Spring Boot applications locally across separate terminals:
```bash
# Internal Intranet App (Port 8082)
mvn spring-boot:run -pl ses-app

# External Gateway DMZ Portal (Port 8083)
mvn spring-boot:run -pl ses-ext
```
- **Internal App**: `http://localhost:8082` (Default local credentials: `admin` / `admin123`)
- **External Gateway**: `http://localhost:8083/ses-ext`

### 3. Automated Test Suite
```bash
mvn test
```

### 4. Build Multi-Module Production WAR Packages
```bash
mvn clean package -DskipTests
```
Produces two standalone WAR artifacts:
```
ses-app/target/ses-app.war   (Internal Application, context-root: /ses)
ses-ext/target/ses-ext.war   (External Gateway, context-root: /ses-ext)
```

### 5. Production Database Execution (ELLPRD)
From `sql/production/261019/`:
```bash
sqlplus ses@//10.10.217.252:1521/ELLPRD @INSTALL-261019.sql
```
Verifies all objects, applies migrations idempotently, compiles schema, and runs `99_verify.sql`.
