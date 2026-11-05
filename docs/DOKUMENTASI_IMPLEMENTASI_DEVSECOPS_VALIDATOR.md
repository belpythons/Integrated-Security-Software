# Dokumentasi Teknis: Implementasi DevSecOps Bean Validation & Hibernate Validator

**Aplikasi:** Secure Entry System (SES) • PT SETERAH (PT. S)  
**Standar Keamanan:** OWASP Top 10 (A03:2021 – Injection & Boundary Input Control)  
**Versi Platform:** Spring Boot 2.7.18 • Java 8 • Hibernate Validator 6.2.5.Final (JSR-380)  
**Status:** Terverifikasi & Aktif  

---

## 1. Latar Belakang & Landasan DevSecOps

Dalam arsitektur sistem perizinan dan identitas perusahaan seperti SES, integritas data pada *trust boundary* (lapisan penerimaan input dari pengguna dan gateway eksternal) merupakan garda pertahanan utama. Implementasi **Bean Validation (JSR-380)** dengan mesin **Hibernate Validator** dirancang untuk menutup celah keamanan berikut:

1. **Cross-Site Scripting (XSS) & Stored Script Injection**:
   - Menolak teks yang mengandung tag skrip yang dapat dieksekusi (`<script>...</script>`), pseudo-protokol (`javascript:...`, `vbscript:...`), inline event handler (`onerror=`, `onload=`, `onclick=`), atau elemen berbahaya (`<iframe>`, `<object>`, `<embed>`).
2. **Buffer / Column Exhaustion**:
   - Membatasi panjang teks (`@Size(max = ...)`) secara presisi sesuai kapasitas kolom database Oracle (mencegah kegagalan fatal saat commit runtime: `ORA-12899: value too large for column`).
3. **Parameter Injection & Special Character Pollution**:
   - Membatasi ID badge, nomor polisi, dan kode jenis pengajuan hanya pada pola karakter alfanumerik yang valid melalui ekspresi reguler (`@Pattern`).
4. **Arbitrary File Upload Vulnerability**:
   - Membatasi berkas lampiran (bukti transfer, STNK, dokumen pekerja) hanya pada ekstensi dan MIME type yang diizinkan (`.pdf`, `.jpg`, `.jpeg`, `.png`).
5. **Information Disclosure Prevention**:
   - Menghindari kebocoran informasi sistem sensitif (internal stack trace, query SQL, nama kelas, atau struktur tabel) kepada pengguna dengan menyediakan penanganan error terpusat (`SesExceptionHandler`) berstandar RFC-7807.

---

## 2. Arsitektur & Alur Validasi

Alur penegakan validasi input diatur secara deklaratif pada lapisan web dan diproses sebelum masuk ke logika bisnis service atau repository:

```mermaid
flowchart TD
    User([Pengguna / Klien / REST API]) -->|POST / PUT Request| Controller[Spring MVC / REST Controller]
    Controller -->|@Validated ValidationGroup| Engine[Hibernate Validator Engine]
    
    subgraph Validation Engine
        Engine --> JSR380[JSR-380 Standar<br/>@NotBlank, @Size, @Pattern, @Email, @Positive]
        Engine --> CustomSec[DevSecOps Constraints<br/>@SafeText (Anti-XSS)<br/>@ValidFileExtension]
    end

    Engine -->|Validasi Sukses| ServiceLayer[Service Layer & Database Persistence]
    Engine -->|Validasi Gagal (Form MVC)| BindingResult[Controller BindingResult]
    BindingResult -->|Redirect / Render Kembali| FormView[Form View dengan Field Error Highlight]
    
    Engine -->|Validasi Gagal (REST / Exception)| GlobalHandler[SesExceptionHandler]
    GlobalHandler -->|RFC-7807 JSON 400| SecureJSON[Response JSON 400 Bersih<br/>Tanpa Kebocoran Stack Trace]
```

---

## 3. Komponen Utama

### 3.1. Validation Groups (JSR-380)

Untuk mendukung alur bisnis di mana pemohon dapat menyimpan draft sementara (yang belum lengkap) dan mengirim pengajuan final (yang harus lengkap dan valid), dibuat dua marker interface:

1. **`DraftGroup`** ([`DraftGroup.java`](file:///d:/GIT/Gitea/ses/ses-core/src/main/java/com/badaklng/ses/validation/DraftGroup.java)):
   - Menegakkan aturan minimal (seperti format nomor badge yang valid jika diisi), tetapi memperbolehkan field dokumen atau field wajib tertentu kosong.
2. **`SubmitGroup`** ([`SubmitGroup.java`](file:///d:/GIT/Gitea/ses/ses-core/src/main/java/com/badaklng/ses/validation/SubmitGroup.java)):
   - Menegakkan seluruh aturan secara ketat sebelum permohonan masuk ke rantai approval.

```java
package com.badaklng.ses.validation;

import javax.validation.groups.Default;

public interface DraftGroup extends Default {}
public interface SubmitGroup extends Default {}
```

---

### 3.2. Custom DevSecOps Constraints

#### A. `@SafeText` & `SafeTextValidator`
- **Tujuan**: Mencegah serangan injeksi skrip HTML/JavaScript pada input catatan, nama, keterangan, dan alamat.
- **Lokasi**:
  - Annotation: [`SafeText.java`](file:///d:/GIT/Gitea/ses/ses-core/src/main/java/com/badaklng/ses/validation/SafeText.java)
  - Validator: [`SafeTextValidator.java`](file:///d:/GIT/Gitea/ses/ses-core/src/main/java/com/badaklng/ses/validation/SafeTextValidator.java)
- **Pola Deteksi**:
  ```java
  private static final Pattern DANGEROUS_PATTERNS = Pattern.compile(
      "(?i)(" +
      "<\\s*script[^>]*>" +
      "|<\\s*/\\s*script\\s*>" +
      "|javascript\\s*:" +
      "|vbscript\\s*:" +
      "|data\\s*:\\s*text/html" +
      "|\\bon[a-zA-Z]+\\s*=" +
      "|<\\s*(iframe|object|embed|applet|meta|link)[^>]*>" +
      "|<\\s*svg[^>]*on[a-zA-Z]+\\s*=" +
      "|<\\s*img[^>]*on[a-zA-Z]+\\s*=" +
      "|<\\s*body[^>]*on[a-zA-Z]+\\s*=" +
      ")",
      Pattern.DOTALL
  );
  ```

#### B. `@ValidFileExtension` & `ValidFileExtensionValidator`
- **Tujuan**: Memastikan berkas yang diunggah memiliki ekstensi resmi sesuai allowlist keamanan.
- **Lokasi**:
  - Annotation: [`ValidFileExtension.java`](file:///d:/GIT/Gitea/ses/ses-core/src/main/java/com/badaklng/ses/validation/ValidFileExtension.java)
  - Validator: [`ValidFileExtensionValidator.java`](file:///d:/GIT/Gitea/ses/ses-core/src/main/java/com/badaklng/ses/validation/ValidFileExtensionValidator.java)
- **Ekstensi Diizinkan (Default)**: `.pdf`, `.jpg`, `.jpeg`, `.png`.

---

### 3.3. Centralized Exception Handling (`SesExceptionHandler`)

[`SesExceptionHandler.java`](file:///d:/GIT/Gitea/ses/ses-core/src/main/java/com/badaklng/ses/web/SesExceptionHandler.java) menangani tiga jenis kegagalan validasi utama:
1. `MethodArgumentNotValidException`: Validasi pada `@RequestBody` endpoint REST API.
2. `BindException`: Validasi pada form submission Spring MVC jika `BindingResult` tidak ditangani manual.
3. `ConstraintViolationException`: Validasi level method / query parameter pada service yang dianotasi `@Validated`.

**Format JSON Terstandarisasi (RFC-7807)**:
```json
{
  "timestamp": "2026-10-21T07:47:38Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validasi formulir gagal",
  "path": "/sticker/save",
  "fieldErrors": {
    "badge": "Format badge hanya boleh huruf, angka, dan strip",
    "vehicles[0].policeNo": "Nomor polisi wajib diisi",
    "requestorName": "Teks mengandung tag atau skrip yang berisiko keamanan (XSS)"
  }
}
```

---

## 4. Matriks Validasi Form & Model

### 4.1. Stiker Kendaraan (`StickerEditForm` & `VehicleRow`)

| Field | Tipe | Constraints | Keterangan / DevSecOps |
| :--- | :--- | :--- | :--- |
| `requestorName` | String | `@NotBlank(SubmitGroup)`<br>`@Size(max = 100)`<br>`@SafeText` | Wajib saat submit, maks 100 char, anti-XSS |
| `badge` | String | `@NotBlank(SubmitGroup)`<br>`@Pattern(^[A-Za-z0-9_-]+$)`<br>`@Size(max = 30)` | Pola alfanumerik + strip, maks 30 char |
| `department` | String | `@Size(max = 100)`<br>`@SafeText` | Maks 100 char, anti-XSS |
| `section` | String | `@Size(max = 100)`<br>`@SafeText` | Maks 100 char, anti-XSS |
| `instance` | String | `@Size(max = 100)`<br>`@SafeText` | Maks 100 char, anti-XSS |
| `phoneNo` | String | `@Pattern(^[0-9+()\\-\\s]*$)`<br>`@Size(max = 30)` | Format nomor telepon valid |
| `workPhoneNo` | String | `@Pattern(^[0-9+()\\-\\s]*$)`<br>`@Size(max = 30)` | Format nomor telepon kantor valid |
| `address` | String | `@Size(max = 255)`<br>`@SafeText` | Maks 255 char, anti-XSS |
| `paymentFile` | MultipartFile | `@ValidFileExtension` | Ekstensi berkas PDF, JPG, PNG |
| `vehicles` | List | `@Valid`<br>`@NotEmpty(SubmitGroup)` | Minimal 1 kendaraan terdaftar saat submit |
| **VehicleRow** | | | |
| `stickerTypeCode` | String | `@NotBlank(SubmitGroup)`<br>`@Size(max = 20)` | Jenis stiker wajib dipilih |
| `policeNo` | String | `@NotBlank(SubmitGroup)`<br>`@Pattern(^[A-Za-z0-9\\s]+$)`<br>`@Size(max = 20)` | Hanya huruf, angka, spasi |
| `vehicleType` | String | `@Size(max = 50)`<br>`@SafeText` | Maks 50 char, anti-XSS |
| `ownerName` | String | `@Size(max = 100)`<br>`@SafeText` | Maks 100 char, anti-XSS |
| `brand` | String | `@Size(max = 50)`<br>`@SafeText` | Maks 50 char, anti-XSS |
| `color` | String | `@Size(max = 30)`<br>`@SafeText` | Maks 30 char, anti-XSS |
| `yearMade` | String | `@Pattern(^(19\|20)\\d{2}$)` | Tahun pembuatan 4 digit (misal: 2024) |
| `letterFile` | MultipartFile | `@ValidFileExtension` | Ekstensi STNK PDF, JPG, PNG |

---

### 4.2. Tanda Pengenal / Badge (`RequestEditForm` & `WorkerRow`)

| Field | Tipe | Constraints | Keterangan / DevSecOps |
| :--- | :--- | :--- | :--- |
| `typeCode` | String | `@NotBlank`<br>`@Size(max = 30)` | Jenis pengajuan wajib dipilih |
| `contractNo` | String | `@Size(max = 100)`<br>`@SafeText` | Nomor kontrak aman |
| `contractTitle` | String | `@Size(max = 255)`<br>`@SafeText` | Judul kontrak aman |
| `email` | String | `@Email`<br>`@Size(max = 100)` | Format email vendor / kontraktor valid |
| `paymentFile` | MultipartFile | `@ValidFileExtension` | Ekstensi berkas bukti pembayaran |
| `workers` | List | `@Valid` | Validasi cascading ke daftar pekerja |
| **WorkerRow** | | | |
| `workerName` | String | `@NotBlank`<br>`@Size(max = 100)`<br>`@SafeText` | Nama pekerja wajib & aman |
| `nik` | String | `@NotBlank`<br>`@Size(min = 3, max = 30)` | Nomor identitas pekerja |
| `badge` | String | `@Pattern(^[A-Za-z0-9_-]+$)`<br>`@Size(max = 30)` | Nomor badge alfanumerik |
| `gender` | String | `@NotBlank` | Jenis kelamin wajib (L/P) |
| `screeningReason` | String | `@Size(max = 500)`<br>`@SafeText` | Catatan screening aman |
| `WorkerDocRow.file`| MultipartFile | `@ValidFileExtension` | Berkas dokumen pekerja PDF, JPG, PNG |

---

### 4.3. Persetujuan & Kas (`ApprovalActionForm` & `CashExpenseForm`)

- **`ApprovalActionForm`**:
  - `instanceId`: `@NotNull`
  - `action`: `@NotBlank`, `@Pattern(regexp = "^(?i)(approve|reject|revise)$")`
  - `note`: `@Size(max = 500)`, `@SafeText` (mencegah XSS pada catatan approval)
- **`CashExpenseForm`**:
  - `paidTo`: `@NotBlank`, `@Size(max = 100)`, `@SafeText`
  - `amount`: `@NotNull`, `@Positive` (nominal harus > 0)
  - `expenseDate`: `@NotNull`
  - `notes`: `@Size(max = 500)`, `@SafeText`

---

## 5. Panduan Developer: Cara Menggunakan Validator

### Menambahkan Validasi pada Endpoint Controller Baru

```java
@PostMapping("/save")
public String save(
        @Validated(SubmitGroup.class) @ModelAttribute("formName") MyForm form,
        BindingResult bindingResult,
        Model model,
        RedirectAttributes ra) {

    // 1. Periksa apakah validasi gagal
    if (bindingResult.hasErrors()) {
        model.addAttribute("errorMsg", "Mohon periksa kembali isian formulir.");
        model.addAttribute("formName", form);
        return "my-module/form"; // Kembalikan ke halaman form tanpa menghilangkan isian pengguna
    }

    // 2. Lanjutkan ke service layer jika data sudah valid
    myService.save(form);
    ra.addFlashAttribute("successMsg", "Data berhasil disimpan.");
    return "redirect:/my-module/list";
}
```

---

## 6. Verifikasi & Hasil Pengujian

Suite pengujian unit test telah diintegrasikan pada `ses-core` dan diverifikasi dengan Maven:

```powershell
mvn test -pl ses-core '-Dtest=SafeTextValidatorTest,StickerEditFormValidationTest,RequestFormValidationTest'
```

### Hasil Test Suite:
```
[INFO] Running com.badaklng.request.model.RequestFormValidationTest
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.badaklng.ses.validation.SafeTextValidatorTest
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.badaklng.sticker.model.StickerEditFormValidationTest
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0
[INFO] 
[INFO] Results:
[INFO] Tests run: 15, Failures: 0, Errors: 0, Skipped: 0
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
```

Kompilasi multi-modul `mvn test-compile` pada seluruh modul (`ses-parent`, `ses-core`, `ses-app`, `ses-ext`) berstatus **BUILD SUCCESS**.
