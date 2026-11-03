-- =============================================================================
-- 81_ses_text_parameter_seed.sql
--
-- Memindahkan seluruh narasi/label user-facing dari kode ke APP_PARAMETER,
-- sehingga perubahan kata tidak lagi memerlukan build + redeploy WAR.
--
-- CARA KERJA
--   Setiap call site di kode memanggil SesText.get("KEY", "<literal lama>"),
--   atau di Thymeleaf ${appParams['KEY'] ?: '<literal lama>'}. Literal lama
--   itu TETAP ADA sebagai fallback. Jadi:
--     * sebelum skrip ini dijalankan  -> aplikasi merender teks yang sama
--       persis seperti sebelum refactor (byte-per-byte);
--     * setelah dijalankan            -> nilai di tabel yang dipakai, dan
--       bisa diubah live di /admin/parameter.
--   Artinya skrip ini TIDAK wajib dijalankan bersamaan dengan deploy WAR, dan
--   APP_PARAMETER yang terhapus bukan insiden -- hanya kehilangan kemampuan
--   mengedit, bukan kehilangan teksnya.
--
-- IDEMPOTEN
--   MERGE per baris: aman dijalankan ulang, dan TIDAK menimpa nilai yang sudah
--   pernah diedit administrator (hanya mengisi baris yang belum ada). Itu
--   disengaja -- re-run paket deployment tidak boleh membatalkan perubahan
--   kata yang sudah disetujui.
--
-- CATATAN CACHE
--   AppParameterService menyimpan tabel ini di memori dan hanya memuat ulang
--   saat save() lewat UI. Setelah menjalankan skrip ini: RESTART aplikasi.
--   Perubahan berikutnya lewat /admin/parameter berlaku langsung.
--
-- BATASAN KOLOM
--   APP_PARAMETER.PARAM_VALUE adalah VARCHAR2(1000 CHAR); nilai terpanjang di
--   sini 268 karakter. PARAM_DESC VARCHAR2(500 CHAR). APP_PARAMETER
--   adalah tabel inti platform: skrip ini hanya INSERT baris, tidak pernah
--   ALTER/DROP.
--
--   Dihasilkan dari kode sumber (bukan ditulis tangan), sehingga nilai default
--   di sini dijamin identik dengan fallback di call site-nya.
--
-- TANDA ';' DI DALAM NILAI
--   Dua nilai memuat ';' sebagai tanda baca kalimat: SES_TXT_LETTER_BODY
--   (".. di bawah ini ;") dan SES_TXT_XLS_FOOTNOTE (".. pembayaran; .."). Itu
--   aman: SQL*Plus -- dan localdev/run_sql.py -- hanya memperlakukan ';'
--   sebagai akhir statement bila berada di AKHIR BARIS, sedangkan keduanya
--   berada di tengah baris literal. Jangan memformat ulang berkas ini
--   sehingga ';' tersebut jatuh di akhir baris.
-- =============================================================================

SET DEFINE OFF


-- ---------------------------------------------------------------------------
-- Identitas korporat lintas-area (letterhead, footer, kota, nama perusahaan)
-- ---------------------------------------------------------------------------

MERGE INTO app_parameter t
USING (SELECT 'SES_TXT_BRAND_APP_NAME' AS param_id FROM dual) s ON (t.param_id = s.param_id)
WHEN NOT MATCHED THEN INSERT (param_id, param_value, param_desc, updated_by, updated_date)
  VALUES ('SES_TXT_BRAND_APP_NAME',
          'Security Suite',
          'Nama aplikasi di footer. Tahun dan bullet ada di template, bukan di sini', 'SYSTEM', SYSDATE);

MERGE INTO app_parameter t
USING (SELECT 'SES_TXT_BRAND_CITY' AS param_id FROM dual) s ON (t.param_id = s.param_id)
WHEN NOT MATCHED THEN INSERT (param_id, param_value, param_desc, updated_by, updated_date)
  VALUES ('SES_TXT_BRAND_CITY',
          'Bontang',
          'Kota pada baris tanggal surat', 'SYSTEM', SYSDATE);

MERGE INTO app_parameter t
USING (SELECT 'SES_TXT_BRAND_COMPANY' AS param_id FROM dual) s ON (t.param_id = s.param_id)
WHEN NOT MATCHED THEN INSERT (param_id, param_value, param_desc, updated_by, updated_date)
  VALUES ('SES_TXT_BRAND_COMPANY',
          'PT Badak NGL',
          'Nama perusahaan, bentuk umum', 'SYSTEM', SYSDATE);

MERGE INTO app_parameter t
USING (SELECT 'SES_TXT_BRAND_COMPANY_FORMAL' AS param_id FROM dual) s ON (t.param_id = s.param_id)
WHEN NOT MATCHED THEN INSERT (param_id, param_value, param_desc, updated_by, updated_date)
  VALUES ('SES_TXT_BRAND_COMPANY_FORMAL',
          'PT. Badak NGL',
          'Nama perusahaan bentuk formal, dipakai di blok tanda tangan surat', 'SYSTEM', SYSDATE);

MERGE INTO app_parameter t
USING (SELECT 'SES_TXT_BRAND_COMPANY_SHORT' AS param_id FROM dual) s ON (t.param_id = s.param_id)
WHEN NOT MATCHED THEN INSERT (param_id, param_value, param_desc, updated_by, updated_date)
  VALUES ('SES_TXT_BRAND_COMPANY_SHORT',
          'PT BADAK NGL',
          'Nama perusahaan bentuk singkat/kapital untuk footer', 'SYSTEM', SYSDATE);

MERGE INTO app_parameter t
USING (SELECT 'SES_TXT_BRAND_TAGLINE' AS param_id FROM dual) s ON (t.param_id = s.param_id)
WHEN NOT MATCHED THEN INSERT (param_id, param_value, param_desc, updated_by, updated_date)
  VALUES ('SES_TXT_BRAND_TAGLINE',
          'Center of Excellence',
          'Tagline di bawah wordmark', 'SYSTEM', SYSDATE);

MERGE INTO app_parameter t
USING (SELECT 'SES_TXT_BRAND_WORDMARK' AS param_id FROM dual) s ON (t.param_id = s.param_id)
WHEN NOT MATCHED THEN INSERT (param_id, param_value, param_desc, updated_by, updated_date)
  VALUES ('SES_TXT_BRAND_WORDMARK',
          'Badak LNG',
          'Wordmark letterhead (halaman verifikasi + header email)', 'SYSTEM', SYSDATE);


-- ---------------------------------------------------------------------------
-- Surat permohonan badge -- PDF resmi (request/print_permohonan.html)
-- ---------------------------------------------------------------------------

MERGE INTO app_parameter t
USING (SELECT 'SES_TXT_LETTER_ADDRESSEE' AS param_id FROM dual) s ON (t.param_id = s.param_id)
WHEN NOT MATCHED THEN INSERT (param_id, param_value, param_desc, updated_by, updated_date)
  VALUES ('SES_TXT_LETTER_ADDRESSEE',
          'Manager Security',
          'Teks user-facing (lihat fallback di call site)', 'SYSTEM', SYSDATE);

MERGE INTO app_parameter t
USING (SELECT 'SES_TXT_LETTER_BODY' AS param_id FROM dual) s ON (t.param_id = s.param_id)
WHEN NOT MATCHED THEN INSERT (param_id, param_value, param_desc, updated_by, updated_date)
  VALUES ('SES_TXT_LETTER_BODY',
          'Sehubungan dengan pelaksanaan kontrak kerja sama dengan kami <span>{0}</span> terkait <strong>{1}</strong> bersama surat ini kami mengajukan permohonan penerbitan kartu identitas (badge) akses area kerja untuk personel pelaksana kami sebagaimana terinci di bawah ini ;',
          'Badan surat. {0}=nama kontraktor, {1}=label kontrak. Boleh memuat markup', 'SYSTEM', SYSDATE);

MERGE INTO app_parameter t
USING (SELECT 'SES_TXT_LETTER_CLOSING' AS param_id FROM dual) s ON (t.param_id = s.param_id)
WHEN NOT MATCHED THEN INSERT (param_id, param_value, param_desc, updated_by, updated_date)
  VALUES ('SES_TXT_LETTER_CLOSING',
          'Demikian surat permohonan ini kami sampaikan, atas bantuannya kami ucapkan terimakasih.',
          'Teks user-facing (lihat fallback di call site)', 'SYSTEM', SYSDATE);

MERGE INTO app_parameter t
USING (SELECT 'SES_TXT_LETTER_COL_ID' AS param_id FROM dual) s ON (t.param_id = s.param_id)
WHEN NOT MATCHED THEN INSERT (param_id, param_value, param_desc, updated_by, updated_date)
  VALUES ('SES_TXT_LETTER_COL_ID',
          'NO. IDENTITAS (KTP/PASSPORT)',
          'Teks user-facing (lihat fallback di call site)', 'SYSTEM', SYSDATE);

MERGE INTO app_parameter t
USING (SELECT 'SES_TXT_LETTER_COL_NAME' AS param_id FROM dual) s ON (t.param_id = s.param_id)
WHEN NOT MATCHED THEN INSERT (param_id, param_value, param_desc, updated_by, updated_date)
  VALUES ('SES_TXT_LETTER_COL_NAME',
          'NAMA',
          'Teks user-facing (lihat fallback di call site)', 'SYSTEM', SYSDATE);

MERGE INTO app_parameter t
USING (SELECT 'SES_TXT_LETTER_COL_NO' AS param_id FROM dual) s ON (t.param_id = s.param_id)
WHEN NOT MATCHED THEN INSERT (param_id, param_value, param_desc, updated_by, updated_date)
  VALUES ('SES_TXT_LETTER_COL_NO',
          'NO',
          'Teks user-facing (lihat fallback di call site)', 'SYSTEM', SYSDATE);

MERGE INTO app_parameter t
USING (SELECT 'SES_TXT_LETTER_COL_PERIOD' AS param_id FROM dual) s ON (t.param_id = s.param_id)
WHEN NOT MATCHED THEN INSERT (param_id, param_value, param_desc, updated_by, updated_date)
  VALUES ('SES_TXT_LETTER_COL_PERIOD',
          'PERIODE',
          'Teks user-facing (lihat fallback di call site)', 'SYSTEM', SYSDATE);

MERGE INTO app_parameter t
USING (SELECT 'SES_TXT_LETTER_COL_ROLE' AS param_id FROM dual) s ON (t.param_id = s.param_id)
WHEN NOT MATCHED THEN INSERT (param_id, param_value, param_desc, updated_by, updated_date)
  VALUES ('SES_TXT_LETTER_COL_ROLE',
          'JABATAN',
          'Teks user-facing (lihat fallback di call site)', 'SYSTEM', SYSDATE);

MERGE INTO app_parameter t
USING (SELECT 'SES_TXT_LETTER_COL_ZONE' AS param_id FROM dual) s ON (t.param_id = s.param_id)
WHEN NOT MATCHED THEN INSERT (param_id, param_value, param_desc, updated_by, updated_date)
  VALUES ('SES_TXT_LETTER_COL_ZONE',
          'ZONE',
          'Teks user-facing (lihat fallback di call site)', 'SYSTEM', SYSDATE);

MERGE INTO app_parameter t
USING (SELECT 'SES_TXT_LETTER_EMPTY_WORKERS' AS param_id FROM dual) s ON (t.param_id = s.param_id)
WHEN NOT MATCHED THEN INSERT (param_id, param_value, param_desc, updated_by, updated_date)
  VALUES ('SES_TXT_LETTER_EMPTY_WORKERS',
          'Tidak ada data pekerja.',
          'Teks user-facing (lihat fallback di call site)', 'SYSTEM', SYSDATE);

MERGE INTO app_parameter t
USING (SELECT 'SES_TXT_LETTER_LABEL_ATTACHMENT' AS param_id FROM dual) s ON (t.param_id = s.param_id)
WHEN NOT MATCHED THEN INSERT (param_id, param_value, param_desc, updated_by, updated_date)
  VALUES ('SES_TXT_LETTER_LABEL_ATTACHMENT',
          'Lampiran',
          'Teks user-facing (lihat fallback di call site)', 'SYSTEM', SYSDATE);

MERGE INTO app_parameter t
USING (SELECT 'SES_TXT_LETTER_LABEL_FROM' AS param_id FROM dual) s ON (t.param_id = s.param_id)
WHEN NOT MATCHED THEN INSERT (param_id, param_value, param_desc, updated_by, updated_date)
  VALUES ('SES_TXT_LETTER_LABEL_FROM',
          'Dari',
          'Teks user-facing (lihat fallback di call site)', 'SYSTEM', SYSDATE);

MERGE INTO app_parameter t
USING (SELECT 'SES_TXT_LETTER_LABEL_SUBJECT' AS param_id FROM dual) s ON (t.param_id = s.param_id)
WHEN NOT MATCHED THEN INSERT (param_id, param_value, param_desc, updated_by, updated_date)
  VALUES ('SES_TXT_LETTER_LABEL_SUBJECT',
          'Perihal',
          'Teks user-facing (lihat fallback di call site)', 'SYSTEM', SYSDATE);

MERGE INTO app_parameter t
USING (SELECT 'SES_TXT_LETTER_LABEL_TO' AS param_id FROM dual) s ON (t.param_id = s.param_id)
WHEN NOT MATCHED THEN INSERT (param_id, param_value, param_desc, updated_by, updated_date)
  VALUES ('SES_TXT_LETTER_LABEL_TO',
          'Kepada',
          'Teks user-facing (lihat fallback di call site)', 'SYSTEM', SYSDATE);

MERGE INTO app_parameter t
USING (SELECT 'SES_TXT_LETTER_NOT_PRINTABLE' AS param_id FROM dual) s ON (t.param_id = s.param_id)
WHEN NOT MATCHED THEN INSERT (param_id, param_value, param_desc, updated_by, updated_date)
  VALUES ('SES_TXT_LETTER_NOT_PRINTABLE',
          'Dokumen hanya dapat dicetak setelah seluruh tahap persetujuan selesai.',
          'Teks user-facing (lihat fallback di call site)', 'SYSTEM', SYSDATE);

MERGE INTO app_parameter t
USING (SELECT 'SES_TXT_LETTER_QR_CAPTION' AS param_id FROM dual) s ON (t.param_id = s.param_id)
WHEN NOT MATCHED THEN INSERT (param_id, param_value, param_desc, updated_by, updated_date)
  VALUES ('SES_TXT_LETTER_QR_CAPTION',
          'Scan untuk Verifikasi Dokumen',
          'Teks user-facing (lihat fallback di call site)', 'SYSTEM', SYSDATE);

MERGE INTO app_parameter t
USING (SELECT 'SES_TXT_LETTER_SIGN_CAPTION' AS param_id FROM dual) s ON (t.param_id = s.param_id)
WHEN NOT MATCHED THEN INSERT (param_id, param_value, param_desc, updated_by, updated_date)
  VALUES ('SES_TXT_LETTER_SIGN_CAPTION',
          'Mengetahui,',
          'Teks user-facing (lihat fallback di call site)', 'SYSTEM', SYSDATE);

MERGE INTO app_parameter t
USING (SELECT 'SES_TXT_LETTER_SIGN_PLACEHOLDER' AS param_id FROM dual) s ON (t.param_id = s.param_id)
WHEN NOT MATCHED THEN INSERT (param_id, param_value, param_desc, updated_by, updated_date)
  VALUES ('SES_TXT_LETTER_SIGN_PLACEHOLDER',
          '( ................... )',
          'Teks user-facing (lihat fallback di call site)', 'SYSTEM', SYSDATE);

MERGE INTO app_parameter t
USING (SELECT 'SES_TXT_LETTER_SIGN_ROLE_FALLBACK' AS param_id FROM dual) s ON (t.param_id = s.param_id)
WHEN NOT MATCHED THEN INSERT (param_id, param_value, param_desc, updated_by, updated_date)
  VALUES ('SES_TXT_LETTER_SIGN_ROLE_FALLBACK',
          'Approver',
          'Teks user-facing (lihat fallback di call site)', 'SYSTEM', SYSDATE);

MERGE INTO app_parameter t
USING (SELECT 'SES_TXT_LETTER_SUBJECT' AS param_id FROM dual) s ON (t.param_id = s.param_id)
WHEN NOT MATCHED THEN INSERT (param_id, param_value, param_desc, updated_by, updated_date)
  VALUES ('SES_TXT_LETTER_SUBJECT',
          'Permohonan Badge Kerja {0}',
          'Perihal surat. {0}=nama kontraktor', 'SYSTEM', SYSDATE);


-- ---------------------------------------------------------------------------
-- Halaman verifikasi QR publik di ses-ext (request/verify.html)
-- ---------------------------------------------------------------------------

MERGE INTO app_parameter t
USING (SELECT 'SES_TXT_VERIFY_APPROVED_NOTE' AS param_id FROM dual) s ON (t.param_id = s.param_id)
WHEN NOT MATCHED THEN INSERT (param_id, param_value, param_desc, updated_by, updated_date)
  VALUES ('SES_TXT_VERIFY_APPROVED_NOTE',
          'Pengajuan ini telah menyelesaikan seluruh tahapan verifikasi dan persetujuan resmi.',
          'Teks user-facing (lihat fallback di call site)', 'SYSTEM', SYSDATE);

MERGE INTO app_parameter t
USING (SELECT 'SES_TXT_VERIFY_ATTESTATION' AS param_id FROM dual) s ON (t.param_id = s.param_id)
WHEN NOT MATCHED THEN INSERT (param_id, param_value, param_desc, updated_by, updated_date)
  VALUES ('SES_TXT_VERIFY_ATTESTATION',
          'menyatakan bahwa dokumen berikut tercatat dalam sistem:',
          'Kalimat atestasi halaman publik', 'SYSTEM', SYSDATE);

MERGE INTO app_parameter t
USING (SELECT 'SES_TXT_VERIFY_AUDIT_NOTE' AS param_id FROM dual) s ON (t.param_id = s.param_id)
WHEN NOT MATCHED THEN INSERT (param_id, param_value, param_desc, updated_by, updated_date)
  VALUES ('SES_TXT_VERIFY_AUDIT_NOTE',
          'Dokumen ini benar dan tercatat dalam audit trail kami',
          'Narasi audit trail halaman publik', 'SYSTEM', SYSDATE);

MERGE INTO app_parameter t
USING (SELECT 'SES_TXT_VERIFY_BADGE_NOT_VALID' AS param_id FROM dual) s ON (t.param_id = s.param_id)
WHEN NOT MATCHED THEN INSERT (param_id, param_value, param_desc, updated_by, updated_date)
  VALUES ('SES_TXT_VERIFY_BADGE_NOT_VALID',
          'Belum sah',
          'Teks user-facing (lihat fallback di call site)', 'SYSTEM', SYSDATE);

MERGE INTO app_parameter t
USING (SELECT 'SES_TXT_VERIFY_BADGE_VALID' AS param_id FROM dual) s ON (t.param_id = s.param_id)
WHEN NOT MATCHED THEN INSERT (param_id, param_value, param_desc, updated_by, updated_date)
  VALUES ('SES_TXT_VERIFY_BADGE_VALID',
          'Valid',
          'Teks user-facing (lihat fallback di call site)', 'SYSTEM', SYSDATE);

MERGE INTO app_parameter t
USING (SELECT 'SES_TXT_VERIFY_DISCLAIMER' AS param_id FROM dual) s ON (t.param_id = s.param_id)
WHEN NOT MATCHED THEN INSERT (param_id, param_value, param_desc, updated_by, updated_date)
  VALUES ('SES_TXT_VERIFY_DISCLAIMER',
          'Dokumen ini <strong>belum</strong> berstatus disetujui. Lembar yang menyertakan kode ini tidak berlaku sebagai izin sah.',
          'DISCLAIMER HUKUM halaman publik. Boleh memuat markup', 'SYSTEM', SYSDATE);

MERGE INTO app_parameter t
USING (SELECT 'SES_TXT_VERIFY_NOT_FOUND' AS param_id FROM dual) s ON (t.param_id = s.param_id)
WHEN NOT MATCHED THEN INSERT (param_id, param_value, param_desc, updated_by, updated_date)
  VALUES ('SES_TXT_VERIFY_NOT_FOUND',
          'tidak terdaftar pada sistem SES.',
          'Teks user-facing (lihat fallback di call site)', 'SYSTEM', SYSDATE);

MERGE INTO app_parameter t
USING (SELECT 'SES_TXT_VERIFY_NOT_FOUND_HINT' AS param_id FROM dual) s ON (t.param_id = s.param_id)
WHEN NOT MATCHED THEN INSERT (param_id, param_value, param_desc, updated_by, updated_date)
  VALUES ('SES_TXT_VERIFY_NOT_FOUND_HINT',
          'Periksa kembali nomor dokumen yang tercetak, atau hubungi Security PT Badak NGL.',
          'Teks user-facing (lihat fallback di call site)', 'SYSTEM', SYSDATE);

MERGE INTO app_parameter t
USING (SELECT 'SES_TXT_VERIFY_PRIVACY_NOTE' AS param_id FROM dual) s ON (t.param_id = s.param_id)
WHEN NOT MATCHED THEN INSERT (param_id, param_value, param_desc, updated_by, updated_date)
  VALUES ('SES_TXT_VERIFY_PRIVACY_NOTE',
          'Nomor identitas pribadi (KTP/NIK) tidak ditampilkan untuk perlindungan data publik.',
          'Teks user-facing (lihat fallback di call site)', 'SYSTEM', SYSDATE);

MERGE INTO app_parameter t
USING (SELECT 'SES_TXT_VERIFY_ROLE_LEFT' AS param_id FROM dual) s ON (t.param_id = s.param_id)
WHEN NOT MATCHED THEN INSERT (param_id, param_value, param_desc, updated_by, updated_date)
  VALUES ('SES_TXT_VERIFY_ROLE_LEFT',
          'Procurement Manager',
          'Teks user-facing (lihat fallback di call site)', 'SYSTEM', SYSDATE);

MERGE INTO app_parameter t
USING (SELECT 'SES_TXT_VERIFY_ROLE_RIGHT' AS param_id FROM dual) s ON (t.param_id = s.param_id)
WHEN NOT MATCHED THEN INSERT (param_id, param_value, param_desc, updated_by, updated_date)
  VALUES ('SES_TXT_VERIFY_ROLE_RIGHT',
          'Site Manager',
          'Teks user-facing (lihat fallback di call site)', 'SYSTEM', SYSDATE);

MERGE INTO app_parameter t
USING (SELECT 'SES_TXT_VERIFY_SIGN_LEFT' AS param_id FROM dual) s ON (t.param_id = s.param_id)
WHEN NOT MATCHED THEN INSERT (param_id, param_value, param_desc, updated_by, updated_date)
  VALUES ('SES_TXT_VERIFY_SIGN_LEFT',
          'Mengetahui (PT Badak NGL)',
          'Teks user-facing (lihat fallback di call site)', 'SYSTEM', SYSDATE);

MERGE INTO app_parameter t
USING (SELECT 'SES_TXT_VERIFY_SIGN_RIGHT' AS param_id FROM dual) s ON (t.param_id = s.param_id)
WHEN NOT MATCHED THEN INSERT (param_id, param_value, param_desc, updated_by, updated_date)
  VALUES ('SES_TXT_VERIFY_SIGN_RIGHT',
          'Hormat Kami (Pemohon)',
          'Teks user-facing (lihat fallback di call site)', 'SYSTEM', SYSDATE);

MERGE INTO app_parameter t
USING (SELECT 'SES_TXT_VERIFY_STATUS_APPROVED' AS param_id FROM dual) s ON (t.param_id = s.param_id)
WHEN NOT MATCHED THEN INSERT (param_id, param_value, param_desc, updated_by, updated_date)
  VALUES ('SES_TXT_VERIFY_STATUS_APPROVED',
          'Disetujui',
          'Teks user-facing (lihat fallback di call site)', 'SYSTEM', SYSDATE);

MERGE INTO app_parameter t
USING (SELECT 'SES_TXT_VERIFY_STATUS_CANCELLED' AS param_id FROM dual) s ON (t.param_id = s.param_id)
WHEN NOT MATCHED THEN INSERT (param_id, param_value, param_desc, updated_by, updated_date)
  VALUES ('SES_TXT_VERIFY_STATUS_CANCELLED',
          'Dibatalkan',
          'Teks user-facing (lihat fallback di call site)', 'SYSTEM', SYSDATE);

MERGE INTO app_parameter t
USING (SELECT 'SES_TXT_VERIFY_STATUS_DRAFT' AS param_id FROM dual) s ON (t.param_id = s.param_id)
WHEN NOT MATCHED THEN INSERT (param_id, param_value, param_desc, updated_by, updated_date)
  VALUES ('SES_TXT_VERIFY_STATUS_DRAFT',
          'Draft',
          'Teks user-facing (lihat fallback di call site)', 'SYSTEM', SYSDATE);

MERGE INTO app_parameter t
USING (SELECT 'SES_TXT_VERIFY_STATUS_PENDING' AS param_id FROM dual) s ON (t.param_id = s.param_id)
WHEN NOT MATCHED THEN INSERT (param_id, param_value, param_desc, updated_by, updated_date)
  VALUES ('SES_TXT_VERIFY_STATUS_PENDING',
          'Dalam Proses',
          'Teks user-facing (lihat fallback di call site)', 'SYSTEM', SYSDATE);

MERGE INTO app_parameter t
USING (SELECT 'SES_TXT_VERIFY_STATUS_REJECTED' AS param_id FROM dual) s ON (t.param_id = s.param_id)
WHEN NOT MATCHED THEN INSERT (param_id, param_value, param_desc, updated_by, updated_date)
  VALUES ('SES_TXT_VERIFY_STATUS_REJECTED',
          'Ditolak',
          'Teks user-facing (lihat fallback di call site)', 'SYSTEM', SYSDATE);

MERGE INTO app_parameter t
USING (SELECT 'SES_TXT_VERIFY_VERIFIED_FALLBACK' AS param_id FROM dual) s ON (t.param_id = s.param_id)
WHEN NOT MATCHED THEN INSERT (param_id, param_value, param_desc, updated_by, updated_date)
  VALUES ('SES_TXT_VERIFY_VERIFIED_FALLBACK',
          '(Telah diverifikasi sesuai prosedur)',
          'Teks user-facing (lihat fallback di call site)', 'SYSTEM', SYSDATE);


-- ---------------------------------------------------------------------------
-- Notifikasi email (subject, narasi, label kartu, shell layout)
-- ---------------------------------------------------------------------------

MERGE INTO app_parameter t
USING (SELECT 'SES_TXT_MAIL_BUTTON' AS param_id FROM dual) s ON (t.param_id = s.param_id)
WHEN NOT MATCHED THEN INSERT (param_id, param_value, param_desc, updated_by, updated_date)
  VALUES ('SES_TXT_MAIL_BUTTON',
          'Lihat Pengajuan',
          'Teks user-facing (lihat fallback di call site)', 'SYSTEM', SYSDATE);

MERGE INTO app_parameter t
USING (SELECT 'SES_TXT_MAIL_FOOTER' AS param_id FROM dual) s ON (t.param_id = s.param_id)
WHEN NOT MATCHED THEN INSERT (param_id, param_value, param_desc, updated_by, updated_date)
  VALUES ('SES_TXT_MAIL_FOOTER',
          'PT Badak NGL. All rights reserved.',
          'Teks user-facing (lihat fallback di call site)', 'SYSTEM', SYSDATE);

MERGE INTO app_parameter t
USING (SELECT 'SES_TXT_MAIL_GREETING' AS param_id FROM dual) s ON (t.param_id = s.param_id)
WHEN NOT MATCHED THEN INSERT (param_id, param_value, param_desc, updated_by, updated_date)
  VALUES ('SES_TXT_MAIL_GREETING',
          'Halo',
          'Teks user-facing (lihat fallback di call site)', 'SYSTEM', SYSDATE);

MERGE INTO app_parameter t
USING (SELECT 'SES_TXT_MAIL_HEADLINE_APPROVED' AS param_id FROM dual) s ON (t.param_id = s.param_id)
WHEN NOT MATCHED THEN INSERT (param_id, param_value, param_desc, updated_by, updated_date)
  VALUES ('SES_TXT_MAIL_HEADLINE_APPROVED',
          'Pengajuan Disetujui',
          'Teks user-facing (lihat fallback di call site)', 'SYSTEM', SYSDATE);

MERGE INTO app_parameter t
USING (SELECT 'SES_TXT_MAIL_HEADLINE_PENDING' AS param_id FROM dual) s ON (t.param_id = s.param_id)
WHEN NOT MATCHED THEN INSERT (param_id, param_value, param_desc, updated_by, updated_date)
  VALUES ('SES_TXT_MAIL_HEADLINE_PENDING',
          'Persetujuan Diperlukan',
          'Teks user-facing (lihat fallback di call site)', 'SYSTEM', SYSDATE);

MERGE INTO app_parameter t
USING (SELECT 'SES_TXT_MAIL_HEADLINE_REJECTED' AS param_id FROM dual) s ON (t.param_id = s.param_id)
WHEN NOT MATCHED THEN INSERT (param_id, param_value, param_desc, updated_by, updated_date)
  VALUES ('SES_TXT_MAIL_HEADLINE_REJECTED',
          'Pengajuan Ditolak',
          'Teks user-facing (lihat fallback di call site)', 'SYSTEM', SYSDATE);

MERGE INTO app_parameter t
USING (SELECT 'SES_TXT_MAIL_INTRO_APPROVED' AS param_id FROM dual) s ON (t.param_id = s.param_id)
WHEN NOT MATCHED THEN INSERT (param_id, param_value, param_desc, updated_by, updated_date)
  VALUES ('SES_TXT_MAIL_INTRO_APPROVED',
          'Pengajuan Anda telah <b>disetujui</b>.',
          'Teks user-facing (lihat fallback di call site)', 'SYSTEM', SYSDATE);

MERGE INTO app_parameter t
USING (SELECT 'SES_TXT_MAIL_INTRO_PENDING' AS param_id FROM dual) s ON (t.param_id = s.param_id)
WHEN NOT MATCHED THEN INSERT (param_id, param_value, param_desc, updated_by, updated_date)
  VALUES ('SES_TXT_MAIL_INTRO_PENDING',
          'Pengajuan berikut <b>menunggu persetujuan Anda</b>.',
          'Teks user-facing (lihat fallback di call site)', 'SYSTEM', SYSDATE);

MERGE INTO app_parameter t
USING (SELECT 'SES_TXT_MAIL_INTRO_REJECTED' AS param_id FROM dual) s ON (t.param_id = s.param_id)
WHEN NOT MATCHED THEN INSERT (param_id, param_value, param_desc, updated_by, updated_date)
  VALUES ('SES_TXT_MAIL_INTRO_REJECTED',
          'Pengajuan Anda <b>ditolak</b>.',
          'Teks user-facing (lihat fallback di call site)', 'SYSTEM', SYSDATE);

MERGE INTO app_parameter t
USING (SELECT 'SES_TXT_MAIL_NO_REPLY' AS param_id FROM dual) s ON (t.param_id = s.param_id)
WHEN NOT MATCHED THEN INSERT (param_id, param_value, param_desc, updated_by, updated_date)
  VALUES ('SES_TXT_MAIL_NO_REPLY',
          'This is an automated notification from Security Entry System (SES). Please do not reply directly to this email.',
          'Teks user-facing (lihat fallback di call site)', 'SYSTEM', SYSDATE);

MERGE INTO app_parameter t
USING (SELECT 'SES_TXT_MAIL_ROW_APPROVED_BY' AS param_id FROM dual) s ON (t.param_id = s.param_id)
WHEN NOT MATCHED THEN INSERT (param_id, param_value, param_desc, updated_by, updated_date)
  VALUES ('SES_TXT_MAIL_ROW_APPROVED_BY',
          'Disetujui Oleh',
          'Teks user-facing (lihat fallback di call site)', 'SYSTEM', SYSDATE);

MERGE INTO app_parameter t
USING (SELECT 'SES_TXT_MAIL_ROW_CURRENT_STEP' AS param_id FROM dual) s ON (t.param_id = s.param_id)
WHEN NOT MATCHED THEN INSERT (param_id, param_value, param_desc, updated_by, updated_date)
  VALUES ('SES_TXT_MAIL_ROW_CURRENT_STEP',
          'Tahap Saat Ini',
          'Teks user-facing (lihat fallback di call site)', 'SYSTEM', SYSDATE);

MERGE INTO app_parameter t
USING (SELECT 'SES_TXT_MAIL_ROW_NOTE' AS param_id FROM dual) s ON (t.param_id = s.param_id)
WHEN NOT MATCHED THEN INSERT (param_id, param_value, param_desc, updated_by, updated_date)
  VALUES ('SES_TXT_MAIL_ROW_NOTE',
          'Catatan',
          'Teks user-facing (lihat fallback di call site)', 'SYSTEM', SYSDATE);

MERGE INTO app_parameter t
USING (SELECT 'SES_TXT_MAIL_ROW_REJECTED_BY' AS param_id FROM dual) s ON (t.param_id = s.param_id)
WHEN NOT MATCHED THEN INSERT (param_id, param_value, param_desc, updated_by, updated_date)
  VALUES ('SES_TXT_MAIL_ROW_REJECTED_BY',
          'Ditolak Oleh',
          'Teks user-facing (lihat fallback di call site)', 'SYSTEM', SYSDATE);

MERGE INTO app_parameter t
USING (SELECT 'SES_TXT_MAIL_ROW_REQUEST_NO' AS param_id FROM dual) s ON (t.param_id = s.param_id)
WHEN NOT MATCHED THEN INSERT (param_id, param_value, param_desc, updated_by, updated_date)
  VALUES ('SES_TXT_MAIL_ROW_REQUEST_NO',
          'Nomor Pengajuan',
          'Teks user-facing (lihat fallback di call site)', 'SYSTEM', SYSDATE);

MERGE INTO app_parameter t
USING (SELECT 'SES_TXT_MAIL_ROW_REQUEST_TYPE' AS param_id FROM dual) s ON (t.param_id = s.param_id)
WHEN NOT MATCHED THEN INSERT (param_id, param_value, param_desc, updated_by, updated_date)
  VALUES ('SES_TXT_MAIL_ROW_REQUEST_TYPE',
          'Jenis Pengajuan',
          'Teks user-facing (lihat fallback di call site)', 'SYSTEM', SYSDATE);

MERGE INTO app_parameter t
USING (SELECT 'SES_TXT_MAIL_ROW_STATUS' AS param_id FROM dual) s ON (t.param_id = s.param_id)
WHEN NOT MATCHED THEN INSERT (param_id, param_value, param_desc, updated_by, updated_date)
  VALUES ('SES_TXT_MAIL_ROW_STATUS',
          'Status',
          'Teks user-facing (lihat fallback di call site)', 'SYSTEM', SYSDATE);

MERGE INTO app_parameter t
USING (SELECT 'SES_TXT_MAIL_ROW_SUBMITTED_BY' AS param_id FROM dual) s ON (t.param_id = s.param_id)
WHEN NOT MATCHED THEN INSERT (param_id, param_value, param_desc, updated_by, updated_date)
  VALUES ('SES_TXT_MAIL_ROW_SUBMITTED_BY',
          'Diajukan Oleh',
          'Teks user-facing (lihat fallback di call site)', 'SYSTEM', SYSDATE);

MERGE INTO app_parameter t
USING (SELECT 'SES_TXT_MAIL_STATUS_APPROVED' AS param_id FROM dual) s ON (t.param_id = s.param_id)
WHEN NOT MATCHED THEN INSERT (param_id, param_value, param_desc, updated_by, updated_date)
  VALUES ('SES_TXT_MAIL_STATUS_APPROVED',
          'DISETUJUI',
          'Teks user-facing (lihat fallback di call site)', 'SYSTEM', SYSDATE);

MERGE INTO app_parameter t
USING (SELECT 'SES_TXT_MAIL_STATUS_PENDING' AS param_id FROM dual) s ON (t.param_id = s.param_id)
WHEN NOT MATCHED THEN INSERT (param_id, param_value, param_desc, updated_by, updated_date)
  VALUES ('SES_TXT_MAIL_STATUS_PENDING',
          'MENUNGGU PERSETUJUAN ANDA',
          'Teks user-facing (lihat fallback di call site)', 'SYSTEM', SYSDATE);

MERGE INTO app_parameter t
USING (SELECT 'SES_TXT_MAIL_STATUS_REJECTED' AS param_id FROM dual) s ON (t.param_id = s.param_id)
WHEN NOT MATCHED THEN INSERT (param_id, param_value, param_desc, updated_by, updated_date)
  VALUES ('SES_TXT_MAIL_STATUS_REJECTED',
          'DITOLAK',
          'Teks user-facing (lihat fallback di call site)', 'SYSTEM', SYSDATE);

MERGE INTO app_parameter t
USING (SELECT 'SES_TXT_MAIL_SUBJECT_ASSIGNED' AS param_id FROM dual) s ON (t.param_id = s.param_id)
WHEN NOT MATCHED THEN INSERT (param_id, param_value, param_desc, updated_by, updated_date)
  VALUES ('SES_TXT_MAIL_SUBJECT_ASSIGNED',
          '[SES] Persetujuan diperlukan - {0}{1}',
          'Subject penugasan. {0}=no transaksi, {1}=sufiks '' dari <nama>''', 'SYSTEM', SYSDATE);

MERGE INTO app_parameter t
USING (SELECT 'SES_TXT_MAIL_SUBJECT_INFO' AS param_id FROM dual) s ON (t.param_id = s.param_id)
WHEN NOT MATCHED THEN INSERT (param_id, param_value, param_desc, updated_by, updated_date)
  VALUES ('SES_TXT_MAIL_SUBJECT_INFO',
          '[SES] {0} - {1}',
          'Subject informasi. {0}=headline, {1}=no pengajuan', 'SYSTEM', SYSDATE);

MERGE INTO app_parameter t
USING (SELECT 'SES_TXT_MAIL_SUBJECT_OUTCOME' AS param_id FROM dual) s ON (t.param_id = s.param_id)
WHEN NOT MATCHED THEN INSERT (param_id, param_value, param_desc, updated_by, updated_date)
  VALUES ('SES_TXT_MAIL_SUBJECT_OUTCOME',
          '[SES] Pengajuan {0} {1}',
          'Subject hasil. {0}=no pengajuan, {1}=kata disetujui/ditolak', 'SYSTEM', SYSDATE);

MERGE INTO app_parameter t
USING (SELECT 'SES_TXT_MAIL_SUBJECT_UPLOAD' AS param_id FROM dual) s ON (t.param_id = s.param_id)
WHEN NOT MATCHED THEN INSERT (param_id, param_value, param_desc, updated_by, updated_date)
  VALUES ('SES_TXT_MAIL_SUBJECT_UPLOAD',
          '[SES] Badge Request - Documents Required - {0}',
          'Subject permintaan unggah dokumen. {0}=no pengajuan', 'SYSTEM', SYSDATE);

MERGE INTO app_parameter t
USING (SELECT 'SES_TXT_MAIL_SUBJECT_WORD_APPROVED' AS param_id FROM dual) s ON (t.param_id = s.param_id)
WHEN NOT MATCHED THEN INSERT (param_id, param_value, param_desc, updated_by, updated_date)
  VALUES ('SES_TXT_MAIL_SUBJECT_WORD_APPROVED',
          'disetujui',
          'Teks user-facing (lihat fallback di call site)', 'SYSTEM', SYSDATE);

MERGE INTO app_parameter t
USING (SELECT 'SES_TXT_MAIL_SUBJECT_WORD_REJECTED' AS param_id FROM dual) s ON (t.param_id = s.param_id)
WHEN NOT MATCHED THEN INSERT (param_id, param_value, param_desc, updated_by, updated_date)
  VALUES ('SES_TXT_MAIL_SUBJECT_WORD_REJECTED',
          'ditolak',
          'Teks user-facing (lihat fallback di call site)', 'SYSTEM', SYSDATE);

MERGE INTO app_parameter t
USING (SELECT 'SES_TXT_MAIL_UPLOAD_BUTTON' AS param_id FROM dual) s ON (t.param_id = s.param_id)
WHEN NOT MATCHED THEN INSERT (param_id, param_value, param_desc, updated_by, updated_date)
  VALUES ('SES_TXT_MAIL_UPLOAD_BUTTON',
          'Unggah Dokumen / Upload Documents',
          'Teks user-facing (lihat fallback di call site)', 'SYSTEM', SYSDATE);

MERGE INTO app_parameter t
USING (SELECT 'SES_TXT_MAIL_UPLOAD_CTA_EN' AS param_id FROM dual) s ON (t.param_id = s.param_id)
WHEN NOT MATCHED THEN INSERT (param_id, param_value, param_desc, updated_by, updated_date)
  VALUES ('SES_TXT_MAIL_UPLOAD_CTA_EN',
          'Please click the button below to upload the required documents:',
          'Teks user-facing (lihat fallback di call site)', 'SYSTEM', SYSDATE);

MERGE INTO app_parameter t
USING (SELECT 'SES_TXT_MAIL_UPLOAD_CTA_ID' AS param_id FROM dual) s ON (t.param_id = s.param_id)
WHEN NOT MATCHED THEN INSERT (param_id, param_value, param_desc, updated_by, updated_date)
  VALUES ('SES_TXT_MAIL_UPLOAD_CTA_ID',
          'Silakan klik tombol di bawah ini untuk mengunggah berkas persyaratan:',
          'Teks user-facing (lihat fallback di call site)', 'SYSTEM', SYSDATE);

MERGE INTO app_parameter t
USING (SELECT 'SES_TXT_MAIL_UPLOAD_FALLBACK_EN' AS param_id FROM dual) s ON (t.param_id = s.param_id)
WHEN NOT MATCHED THEN INSERT (param_id, param_value, param_desc, updated_by, updated_date)
  VALUES ('SES_TXT_MAIL_UPLOAD_FALLBACK_EN',
          'If the button does not work, open:',
          'Teks user-facing (lihat fallback di call site)', 'SYSTEM', SYSDATE);

MERGE INTO app_parameter t
USING (SELECT 'SES_TXT_MAIL_UPLOAD_FALLBACK_ID' AS param_id FROM dual) s ON (t.param_id = s.param_id)
WHEN NOT MATCHED THEN INSERT (param_id, param_value, param_desc, updated_by, updated_date)
  VALUES ('SES_TXT_MAIL_UPLOAD_FALLBACK_ID',
          'Jika tombol di atas tidak dapat diklik, buka tautan berikut:',
          'Teks user-facing (lihat fallback di call site)', 'SYSTEM', SYSDATE);

MERGE INTO app_parameter t
USING (SELECT 'SES_TXT_MAIL_UPLOAD_GREETING' AS param_id FROM dual) s ON (t.param_id = s.param_id)
WHEN NOT MATCHED THEN INSERT (param_id, param_value, param_desc, updated_by, updated_date)
  VALUES ('SES_TXT_MAIL_UPLOAD_GREETING',
          'Kepada / <i>Dear</i>',
          'Teks user-facing (lihat fallback di call site)', 'SYSTEM', SYSDATE);

MERGE INTO app_parameter t
USING (SELECT 'SES_TXT_MAIL_UPLOAD_GREETING_TEXT' AS param_id FROM dual) s ON (t.param_id = s.param_id)
WHEN NOT MATCHED THEN INSERT (param_id, param_value, param_desc, updated_by, updated_date)
  VALUES ('SES_TXT_MAIL_UPLOAD_GREETING_TEXT',
          'Dear Bapak/Ibu',
          'Teks user-facing (lihat fallback di call site)', 'SYSTEM', SYSDATE);

MERGE INTO app_parameter t
USING (SELECT 'SES_TXT_MAIL_UPLOAD_LABEL_CONTRACT_NO' AS param_id FROM dual) s ON (t.param_id = s.param_id)
WHEN NOT MATCHED THEN INSERT (param_id, param_value, param_desc, updated_by, updated_date)
  VALUES ('SES_TXT_MAIL_UPLOAD_LABEL_CONTRACT_NO',
          'No. Kontrak / <i>Contract No.</i>',
          'Teks user-facing (lihat fallback di call site)', 'SYSTEM', SYSDATE);

MERGE INTO app_parameter t
USING (SELECT 'SES_TXT_MAIL_UPLOAD_LABEL_CONTRACT_PERIOD' AS param_id FROM dual) s ON (t.param_id = s.param_id)
WHEN NOT MATCHED THEN INSERT (param_id, param_value, param_desc, updated_by, updated_date)
  VALUES ('SES_TXT_MAIL_UPLOAD_LABEL_CONTRACT_PERIOD',
          'Masa Berlaku / <i>Contract Period</i>',
          'Teks user-facing (lihat fallback di call site)', 'SYSTEM', SYSDATE);

MERGE INTO app_parameter t
USING (SELECT 'SES_TXT_MAIL_UPLOAD_LABEL_CONTRACT_TITLE' AS param_id FROM dual) s ON (t.param_id = s.param_id)
WHEN NOT MATCHED THEN INSERT (param_id, param_value, param_desc, updated_by, updated_date)
  VALUES ('SES_TXT_MAIL_UPLOAD_LABEL_CONTRACT_TITLE',
          'Judul Kontrak / <i>Contract Title</i>',
          'Teks user-facing (lihat fallback di call site)', 'SYSTEM', SYSDATE);

MERGE INTO app_parameter t
USING (SELECT 'SES_TXT_MAIL_UPLOAD_LABEL_CONTRACT_TYPE' AS param_id FROM dual) s ON (t.param_id = s.param_id)
WHEN NOT MATCHED THEN INSERT (param_id, param_value, param_desc, updated_by, updated_date)
  VALUES ('SES_TXT_MAIL_UPLOAD_LABEL_CONTRACT_TYPE',
          'Jenis Kontrak / <i>Contract Type</i>',
          'Teks user-facing (lihat fallback di call site)', 'SYSTEM', SYSDATE);

MERGE INTO app_parameter t
USING (SELECT 'SES_TXT_MAIL_UPLOAD_LABEL_REQUEST_NO' AS param_id FROM dual) s ON (t.param_id = s.param_id)
WHEN NOT MATCHED THEN INSERT (param_id, param_value, param_desc, updated_by, updated_date)
  VALUES ('SES_TXT_MAIL_UPLOAD_LABEL_REQUEST_NO',
          'No. Pengajuan / <i>Request No.</i>',
          'Teks user-facing (lihat fallback di call site)', 'SYSTEM', SYSDATE);

MERGE INTO app_parameter t
USING (SELECT 'SES_TXT_MAIL_UPLOAD_LABEL_REQUEST_TYPE' AS param_id FROM dual) s ON (t.param_id = s.param_id)
WHEN NOT MATCHED THEN INSERT (param_id, param_value, param_desc, updated_by, updated_date)
  VALUES ('SES_TXT_MAIL_UPLOAD_LABEL_REQUEST_TYPE',
          'Jenis Permohonan / <i>Request Type</i>',
          'Teks user-facing (lihat fallback di call site)', 'SYSTEM', SYSDATE);

MERGE INTO app_parameter t
USING (SELECT 'SES_TXT_MAIL_UPLOAD_LEAD_EN' AS param_id FROM dual) s ON (t.param_id = s.param_id)
WHEN NOT MATCHED THEN INSERT (param_id, param_value, param_desc, updated_by, updated_date)
  VALUES ('SES_TXT_MAIL_UPLOAD_LEAD_EN',
          'Your badge request has been submitted and requires supporting worker documents before the approval process can proceed.',
          'Teks user-facing (lihat fallback di call site)', 'SYSTEM', SYSDATE);

MERGE INTO app_parameter t
USING (SELECT 'SES_TXT_MAIL_UPLOAD_LEAD_ID' AS param_id FROM dual) s ON (t.param_id = s.param_id)
WHEN NOT MATCHED THEN INSERT (param_id, param_value, param_desc, updated_by, updated_date)
  VALUES ('SES_TXT_MAIL_UPLOAD_LEAD_ID',
          'Pengajuan badge Anda telah diterima dan memerlukan kelengkapan dokumen pendukung pekerja sebelum proses persetujuan dapat dilanjutkan.',
          'Teks user-facing (lihat fallback di call site)', 'SYSTEM', SYSDATE);

MERGE INTO app_parameter t
USING (SELECT 'SES_TXT_MAIL_UPLOAD_TITLE_EN' AS param_id FROM dual) s ON (t.param_id = s.param_id)
WHEN NOT MATCHED THEN INSERT (param_id, param_value, param_desc, updated_by, updated_date)
  VALUES ('SES_TXT_MAIL_UPLOAD_TITLE_EN',
          'Badge Request - Document Upload Required',
          'Teks user-facing (lihat fallback di call site)', 'SYSTEM', SYSDATE);

MERGE INTO app_parameter t
USING (SELECT 'SES_TXT_MAIL_UPLOAD_TITLE_ID' AS param_id FROM dual) s ON (t.param_id = s.param_id)
WHEN NOT MATCHED THEN INSERT (param_id, param_value, param_desc, updated_by, updated_date)
  VALUES ('SES_TXT_MAIL_UPLOAD_TITLE_ID',
          'Unggah Dokumen Permohonan Badge',
          'Teks user-facing (lihat fallback di call site)', 'SYSTEM', SYSDATE);

MERGE INTO app_parameter t
USING (SELECT 'SES_TXT_MAIL_UPLOAD_TTL_EN' AS param_id FROM dual) s ON (t.param_id = s.param_id)
WHEN NOT MATCHED THEN INSERT (param_id, param_value, param_desc, updated_by, updated_date)
  VALUES ('SES_TXT_MAIL_UPLOAD_TTL_EN',
          '* The link is valid for {0} days and only valid for 1 upload session. Please ensure all mandatory documents are uploaded before closing the page.',
          'Catatan masa berlaku tautan (EN). {0}=jumlah hari', 'SYSTEM', SYSDATE);

MERGE INTO app_parameter t
USING (SELECT 'SES_TXT_MAIL_UPLOAD_TTL_ID' AS param_id FROM dual) s ON (t.param_id = s.param_id)
WHEN NOT MATCHED THEN INSERT (param_id, param_value, param_desc, updated_by, updated_date)
  VALUES ('SES_TXT_MAIL_UPLOAD_TTL_ID',
          '* Tautan berlaku selama {0} hari dan hanya berlaku untuk 1 sesi pengunggahan. Harap pastikan seluruh dokumen wajib telah diunggah sebelum menutup halaman.',
          'Catatan masa berlaku tautan. {0}=jumlah hari', 'SYSTEM', SYSDATE);


-- ---------------------------------------------------------------------------
-- Laporan keuangan Excel (nama sheet, header kolom, baris total)
-- ---------------------------------------------------------------------------

MERGE INTO app_parameter t
USING (SELECT 'SES_TXT_XLS_COL_AMOUNT' AS param_id FROM dual) s ON (t.param_id = s.param_id)
WHEN NOT MATCHED THEN INSERT (param_id, param_value, param_desc, updated_by, updated_date)
  VALUES ('SES_TXT_XLS_COL_AMOUNT',
          'Nominal (Rp)',
          'Teks user-facing (lihat fallback di call site)', 'SYSTEM', SYSDATE);

MERGE INTO app_parameter t
USING (SELECT 'SES_TXT_XLS_FILENAME_PREFIX' AS param_id FROM dual) s ON (t.param_id = s.param_id)
WHEN NOT MATCHED THEN INSERT (param_id, param_value, param_desc, updated_by, updated_date)
  VALUES ('SES_TXT_XLS_FILENAME_PREFIX',
          'Laporan-Keuangan',
          'Teks user-facing (lihat fallback di call site)', 'SYSTEM', SYSDATE);

MERGE INTO app_parameter t
USING (SELECT 'SES_TXT_XLS_FOOTNOTE' AS param_id FROM dual) s ON (t.param_id = s.param_id)
WHEN NOT MATCHED THEN INSERT (param_id, param_value, param_desc, updated_by, updated_date)
  VALUES ('SES_TXT_XLS_FOOTNOTE',
          'Pemasukan dihitung dari tanggal VERIFIKASI pembayaran; pengeluaran dari TANGGAL KAS, bukan tanggal entri.',
          'Teks user-facing (lihat fallback di call site)', 'SYSTEM', SYSDATE);

MERGE INTO app_parameter t
USING (SELECT 'SES_TXT_XLS_IN_COL_REQUESTOR' AS param_id FROM dual) s ON (t.param_id = s.param_id)
WHEN NOT MATCHED THEN INSERT (param_id, param_value, param_desc, updated_by, updated_date)
  VALUES ('SES_TXT_XLS_IN_COL_REQUESTOR',
          'Pemohon',
          'Teks user-facing (lihat fallback di call site)', 'SYSTEM', SYSDATE);

MERGE INTO app_parameter t
USING (SELECT 'SES_TXT_XLS_IN_COL_REQUEST_NO' AS param_id FROM dual) s ON (t.param_id = s.param_id)
WHEN NOT MATCHED THEN INSERT (param_id, param_value, param_desc, updated_by, updated_date)
  VALUES ('SES_TXT_XLS_IN_COL_REQUEST_NO',
          'No. Pengajuan',
          'Teks user-facing (lihat fallback di call site)', 'SYSTEM', SYSDATE);

MERGE INTO app_parameter t
USING (SELECT 'SES_TXT_XLS_IN_COL_TRX_NO' AS param_id FROM dual) s ON (t.param_id = s.param_id)
WHEN NOT MATCHED THEN INSERT (param_id, param_value, param_desc, updated_by, updated_date)
  VALUES ('SES_TXT_XLS_IN_COL_TRX_NO',
          'No. Transaksi',
          'Teks user-facing (lihat fallback di call site)', 'SYSTEM', SYSDATE);

MERGE INTO app_parameter t
USING (SELECT 'SES_TXT_XLS_IN_COL_TYPE' AS param_id FROM dual) s ON (t.param_id = s.param_id)
WHEN NOT MATCHED THEN INSERT (param_id, param_value, param_desc, updated_by, updated_date)
  VALUES ('SES_TXT_XLS_IN_COL_TYPE',
          'Jenis',
          'Teks user-facing (lihat fallback di call site)', 'SYSTEM', SYSDATE);

MERGE INTO app_parameter t
USING (SELECT 'SES_TXT_XLS_IN_COL_VERIFIED_BY' AS param_id FROM dual) s ON (t.param_id = s.param_id)
WHEN NOT MATCHED THEN INSERT (param_id, param_value, param_desc, updated_by, updated_date)
  VALUES ('SES_TXT_XLS_IN_COL_VERIFIED_BY',
          'Diverifikasi Oleh',
          'Teks user-facing (lihat fallback di call site)', 'SYSTEM', SYSDATE);

MERGE INTO app_parameter t
USING (SELECT 'SES_TXT_XLS_IN_COL_VERIFIED_DATE' AS param_id FROM dual) s ON (t.param_id = s.param_id)
WHEN NOT MATCHED THEN INSERT (param_id, param_value, param_desc, updated_by, updated_date)
  VALUES ('SES_TXT_XLS_IN_COL_VERIFIED_DATE',
          'Tanggal Verifikasi',
          'Teks user-facing (lihat fallback di call site)', 'SYSTEM', SYSDATE);

MERGE INTO app_parameter t
USING (SELECT 'SES_TXT_XLS_LABEL_PERIOD' AS param_id FROM dual) s ON (t.param_id = s.param_id)
WHEN NOT MATCHED THEN INSERT (param_id, param_value, param_desc, updated_by, updated_date)
  VALUES ('SES_TXT_XLS_LABEL_PERIOD',
          'Periode',
          'Teks user-facing (lihat fallback di call site)', 'SYSTEM', SYSDATE);

MERGE INTO app_parameter t
USING (SELECT 'SES_TXT_XLS_OUT_COL_CREATED_BY' AS param_id FROM dual) s ON (t.param_id = s.param_id)
WHEN NOT MATCHED THEN INSERT (param_id, param_value, param_desc, updated_by, updated_date)
  VALUES ('SES_TXT_XLS_OUT_COL_CREATED_BY',
          'Dibuat Oleh',
          'Teks user-facing (lihat fallback di call site)', 'SYSTEM', SYSDATE);

MERGE INTO app_parameter t
USING (SELECT 'SES_TXT_XLS_OUT_COL_DATE' AS param_id FROM dual) s ON (t.param_id = s.param_id)
WHEN NOT MATCHED THEN INSERT (param_id, param_value, param_desc, updated_by, updated_date)
  VALUES ('SES_TXT_XLS_OUT_COL_DATE',
          'Tanggal Kas',
          'Teks user-facing (lihat fallback di call site)', 'SYSTEM', SYSDATE);

MERGE INTO app_parameter t
USING (SELECT 'SES_TXT_XLS_OUT_COL_ID' AS param_id FROM dual) s ON (t.param_id = s.param_id)
WHEN NOT MATCHED THEN INSERT (param_id, param_value, param_desc, updated_by, updated_date)
  VALUES ('SES_TXT_XLS_OUT_COL_ID',
          'ID',
          'Teks user-facing (lihat fallback di call site)', 'SYSTEM', SYSDATE);

MERGE INTO app_parameter t
USING (SELECT 'SES_TXT_XLS_OUT_COL_NOTES' AS param_id FROM dual) s ON (t.param_id = s.param_id)
WHEN NOT MATCHED THEN INSERT (param_id, param_value, param_desc, updated_by, updated_date)
  VALUES ('SES_TXT_XLS_OUT_COL_NOTES',
          'Keperluan / Catatan',
          'Teks user-facing (lihat fallback di call site)', 'SYSTEM', SYSDATE);

MERGE INTO app_parameter t
USING (SELECT 'SES_TXT_XLS_OUT_COL_PAID_TO' AS param_id FROM dual) s ON (t.param_id = s.param_id)
WHEN NOT MATCHED THEN INSERT (param_id, param_value, param_desc, updated_by, updated_date)
  VALUES ('SES_TXT_XLS_OUT_COL_PAID_TO',
          'Dibayarkan Kepada',
          'Teks user-facing (lihat fallback di call site)', 'SYSTEM', SYSDATE);

MERGE INTO app_parameter t
USING (SELECT 'SES_TXT_XLS_PERIOD_ALL' AS param_id FROM dual) s ON (t.param_id = s.param_id)
WHEN NOT MATCHED THEN INSERT (param_id, param_value, param_desc, updated_by, updated_date)
  VALUES ('SES_TXT_XLS_PERIOD_ALL',
          'Seluruh data',
          'Teks user-facing (lihat fallback di call site)', 'SYSTEM', SYSDATE);

MERGE INTO app_parameter t
USING (SELECT 'SES_TXT_XLS_PERIOD_OPEN_END' AS param_id FROM dual) s ON (t.param_id = s.param_id)
WHEN NOT MATCHED THEN INSERT (param_id, param_value, param_desc, updated_by, updated_date)
  VALUES ('SES_TXT_XLS_PERIOD_OPEN_END',
          'kini',
          'Teks user-facing (lihat fallback di call site)', 'SYSTEM', SYSDATE);

MERGE INTO app_parameter t
USING (SELECT 'SES_TXT_XLS_PERIOD_OPEN_START' AS param_id FROM dual) s ON (t.param_id = s.param_id)
WHEN NOT MATCHED THEN INSERT (param_id, param_value, param_desc, updated_by, updated_date)
  VALUES ('SES_TXT_XLS_PERIOD_OPEN_START',
          'awal',
          'Teks user-facing (lihat fallback di call site)', 'SYSTEM', SYSDATE);

MERGE INTO app_parameter t
USING (SELECT 'SES_TXT_XLS_PERIOD_RANGE' AS param_id FROM dual) s ON (t.param_id = s.param_id)
WHEN NOT MATCHED THEN INSERT (param_id, param_value, param_desc, updated_by, updated_date)
  VALUES ('SES_TXT_XLS_PERIOD_RANGE',
          '{0} s/d {1}',
          'Rentang periode. {0}=awal, {1}=akhir', 'SYSTEM', SYSDATE);

MERGE INTO app_parameter t
USING (SELECT 'SES_TXT_XLS_SHEET_EXPENSE' AS param_id FROM dual) s ON (t.param_id = s.param_id)
WHEN NOT MATCHED THEN INSERT (param_id, param_value, param_desc, updated_by, updated_date)
  VALUES ('SES_TXT_XLS_SHEET_EXPENSE',
          'Pengeluaran',
          'Teks user-facing (lihat fallback di call site)', 'SYSTEM', SYSDATE);

MERGE INTO app_parameter t
USING (SELECT 'SES_TXT_XLS_SHEET_INCOME' AS param_id FROM dual) s ON (t.param_id = s.param_id)
WHEN NOT MATCHED THEN INSERT (param_id, param_value, param_desc, updated_by, updated_date)
  VALUES ('SES_TXT_XLS_SHEET_INCOME',
          'Pemasukan',
          'Teks user-facing (lihat fallback di call site)', 'SYSTEM', SYSDATE);

MERGE INTO app_parameter t
USING (SELECT 'SES_TXT_XLS_SHEET_SUMMARY' AS param_id FROM dual) s ON (t.param_id = s.param_id)
WHEN NOT MATCHED THEN INSERT (param_id, param_value, param_desc, updated_by, updated_date)
  VALUES ('SES_TXT_XLS_SHEET_SUMMARY',
          'Ringkasan',
          'Teks user-facing (lihat fallback di call site)', 'SYSTEM', SYSDATE);

MERGE INTO app_parameter t
USING (SELECT 'SES_TXT_XLS_SUM_COL_COUNT' AS param_id FROM dual) s ON (t.param_id = s.param_id)
WHEN NOT MATCHED THEN INSERT (param_id, param_value, param_desc, updated_by, updated_date)
  VALUES ('SES_TXT_XLS_SUM_COL_COUNT',
          'Jumlah Transaksi',
          'Teks user-facing (lihat fallback di call site)', 'SYSTEM', SYSDATE);

MERGE INTO app_parameter t
USING (SELECT 'SES_TXT_XLS_SUM_COL_DESC' AS param_id FROM dual) s ON (t.param_id = s.param_id)
WHEN NOT MATCHED THEN INSERT (param_id, param_value, param_desc, updated_by, updated_date)
  VALUES ('SES_TXT_XLS_SUM_COL_DESC',
          'Keterangan',
          'Teks user-facing (lihat fallback di call site)', 'SYSTEM', SYSDATE);

MERGE INTO app_parameter t
USING (SELECT 'SES_TXT_XLS_SUM_ROW_BALANCE' AS param_id FROM dual) s ON (t.param_id = s.param_id)
WHEN NOT MATCHED THEN INSERT (param_id, param_value, param_desc, updated_by, updated_date)
  VALUES ('SES_TXT_XLS_SUM_ROW_BALANCE',
          'SALDO KAS BERSIH',
          'Teks user-facing (lihat fallback di call site)', 'SYSTEM', SYSDATE);

MERGE INTO app_parameter t
USING (SELECT 'SES_TXT_XLS_SUM_ROW_EXPENSE' AS param_id FROM dual) s ON (t.param_id = s.param_id)
WHEN NOT MATCHED THEN INSERT (param_id, param_value, param_desc, updated_by, updated_date)
  VALUES ('SES_TXT_XLS_SUM_ROW_EXPENSE',
          'Pengeluaran (kas keluar)',
          'Teks user-facing (lihat fallback di call site)', 'SYSTEM', SYSDATE);

MERGE INTO app_parameter t
USING (SELECT 'SES_TXT_XLS_SUM_ROW_INCOME' AS param_id FROM dual) s ON (t.param_id = s.param_id)
WHEN NOT MATCHED THEN INSERT (param_id, param_value, param_desc, updated_by, updated_date)
  VALUES ('SES_TXT_XLS_SUM_ROW_INCOME',
          'Pemasukan (pembayaran badge terverifikasi)',
          'Teks user-facing (lihat fallback di call site)', 'SYSTEM', SYSDATE);

MERGE INTO app_parameter t
USING (SELECT 'SES_TXT_XLS_TITLE' AS param_id FROM dual) s ON (t.param_id = s.param_id)
WHEN NOT MATCHED THEN INSERT (param_id, param_value, param_desc, updated_by, updated_date)
  VALUES ('SES_TXT_XLS_TITLE',
          'LAPORAN KEUANGAN SES',
          'Teks user-facing (lihat fallback di call site)', 'SYSTEM', SYSDATE);

MERGE INTO app_parameter t
USING (SELECT 'SES_TXT_XLS_TOTAL_EXPENSE' AS param_id FROM dual) s ON (t.param_id = s.param_id)
WHEN NOT MATCHED THEN INSERT (param_id, param_value, param_desc, updated_by, updated_date)
  VALUES ('SES_TXT_XLS_TOTAL_EXPENSE',
          'TOTAL PENGELUARAN',
          'Teks user-facing (lihat fallback di call site)', 'SYSTEM', SYSDATE);

MERGE INTO app_parameter t
USING (SELECT 'SES_TXT_XLS_TOTAL_INCOME' AS param_id FROM dual) s ON (t.param_id = s.param_id)
WHEN NOT MATCHED THEN INSERT (param_id, param_value, param_desc, updated_by, updated_date)
  VALUES ('SES_TXT_XLS_TOTAL_INCOME',
          'TOTAL PEMASUKAN',
          'Teks user-facing (lihat fallback di call site)', 'SYSTEM', SYSDATE);


-- ---------------------------------------------------------------------------
-- Pernyataan pemohon dan instruksi pembayaran di formulir
-- ---------------------------------------------------------------------------

MERGE INTO app_parameter t
USING (SELECT 'SES_TXT_FORM_QRIS_HELP' AS param_id FROM dual) s ON (t.param_id = s.param_id)
WHEN NOT MATCHED THEN INSERT (param_id, param_value, param_desc, updated_by, updated_date)
  VALUES ('SES_TXT_FORM_QRIS_HELP',
          'Silakan scan kode QRIS untuk pembayaran biaya permohonan stiker kendaraan (Loket Security PTB). Klik tombol <strong>Perbesar QRIS</strong> di atas agar kode QR ditampilkan dalam ukuran besar untuk memudahkan scanner kamera ponsel.',
          'Instruksi QRIS pada formulir stiker. Boleh memuat markup', 'SYSTEM', SYSDATE);

MERGE INTO app_parameter t
USING (SELECT 'SES_TXT_FORM_QRIS_MODAL_HELP' AS param_id FROM dual) s ON (t.param_id = s.param_id)
WHEN NOT MATCHED THEN INSERT (param_id, param_value, param_desc, updated_by, updated_date)
  VALUES ('SES_TXT_FORM_QRIS_MODAL_HELP',
          'Arahkan kamera aplikasi pembayaran (GoPay, OVO, Dana, BCA, Livin, dll) ke kode QR di atas. Setelah pembayaran berhasil, simpan bukti transfer dan unggah pada kolom Bukti Pembayaran.',
          'Teks user-facing (lihat fallback di call site)', 'SYSTEM', SYSDATE);

MERGE INTO app_parameter t
USING (SELECT 'SES_TXT_FORM_REQUEST_DECLARATION' AS param_id FROM dual) s ON (t.param_id = s.param_id)
WHEN NOT MATCHED THEN INSERT (param_id, param_value, param_desc, updated_by, updated_date)
  VALUES ('SES_TXT_FORM_REQUEST_DECLARATION',
          'Dengan mengirimkan formulir ini, Anda menyatakan bahwa data yang diisi adalah benar dan mematuhi seluruh protokol keamanan dan K3 Kawasan Kilang PT Badak NGL.',
          'Teks user-facing (lihat fallback di call site)', 'SYSTEM', SYSDATE);

MERGE INTO app_parameter t
USING (SELECT 'SES_TXT_FORM_STICKER_DECLARATION' AS param_id FROM dual) s ON (t.param_id = s.param_id)
WHEN NOT MATCHED THEN INSERT (param_id, param_value, param_desc, updated_by, updated_date)
  VALUES ('SES_TXT_FORM_STICKER_DECLARATION',
          'Dengan mengirimkan formulir ini, Anda menyatakan bahwa data identitas dan seluruh kendaraan serta pengemudi yang didaftarkan adalah benar, sah, serta mematuhi seluruh protokol keselamatan dan keamanan berkendara di area PT Badak NGL.',
          'Teks user-facing (lihat fallback di call site)', 'SYSTEM', SYSDATE);


-- ---------------------------------------------------------------------------
-- Nama modul yang tampil di worklist persetujuan dan daftar pembayaran
-- ---------------------------------------------------------------------------

MERGE INTO app_parameter t
USING (SELECT 'SES_TXT_MODULE_REQUEST_MATCHKEY' AS param_id FROM dual) s ON (t.param_id = s.param_id)
WHEN NOT MATCHED THEN INSERT (param_id, param_value, param_desc, updated_by, updated_date)
  VALUES ('SES_TXT_MODULE_REQUEST_MATCHKEY',
          'Jenis Badge',
          'Teks user-facing (lihat fallback di call site)', 'SYSTEM', SYSDATE);

MERGE INTO app_parameter t
USING (SELECT 'SES_TXT_MODULE_REQUEST_NAME' AS param_id FROM dual) s ON (t.param_id = s.param_id)
WHEN NOT MATCHED THEN INSERT (param_id, param_value, param_desc, updated_by, updated_date)
  VALUES ('SES_TXT_MODULE_REQUEST_NAME',
          'Pengajuan Badge Karyawan',
          'Teks user-facing (lihat fallback di call site)', 'SYSTEM', SYSDATE);

MERGE INTO app_parameter t
USING (SELECT 'SES_TXT_MODULE_REQUEST_PAYMENT_NAME' AS param_id FROM dual) s ON (t.param_id = s.param_id)
WHEN NOT MATCHED THEN INSERT (param_id, param_value, param_desc, updated_by, updated_date)
  VALUES ('SES_TXT_MODULE_REQUEST_PAYMENT_NAME',
          'Badge Karyawan',
          'Teks user-facing (lihat fallback di call site)', 'SYSTEM', SYSDATE);

MERGE INTO app_parameter t
USING (SELECT 'SES_TXT_MODULE_STICKER_NAME' AS param_id FROM dual) s ON (t.param_id = s.param_id)
WHEN NOT MATCHED THEN INSERT (param_id, param_value, param_desc, updated_by, updated_date)
  VALUES ('SES_TXT_MODULE_STICKER_NAME',
          'Stiker Kendaraan',
          'Teks user-facing (lihat fallback di call site)', 'SYSTEM', SYSDATE);


COMMIT;

-- Verifikasi: harus mengembalikan 130
-- SELECT COUNT(*) FROM app_parameter WHERE param_id LIKE 'SES_TXT_%';
--
-- Verifikasi: tidak ada nilai yang terpotong
-- SELECT param_id, LENGTH(param_value) FROM app_parameter
--  WHERE param_id LIKE 'SES_TXT_%' AND LENGTH(param_value) >= 1000;
--
-- Mengembalikan satu baris ke default bawaan kode: hapus barisnya.
-- DELETE FROM app_parameter WHERE param_id = 'SES_TXT_...';
