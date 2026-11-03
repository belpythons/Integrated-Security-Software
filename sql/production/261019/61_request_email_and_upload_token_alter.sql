-- ============================================================
-- SES — Email penerima dokumen + token unggah, status WAITING_FOR_UPLOAD — Oracle
-- Run AFTER 03_request_ddl.sql dan 21_request_draft_status_alter.sql.
--
-- ------------------------------------------------------------
-- MENGAPA
-- ------------------------------------------------------------
-- Dokumen pekerja dipegang KONTRAKTOR, bukan pemohon. Sampai sekarang pemohon
-- harus mengumpulkannya lewat jalur luar sistem sebelum bisa submit. Alur baru
-- membalik urutannya: pemohon submit tanpa dokumen, pengajuan ditahan di status
-- WAITING_FOR_UPLOAD, dan sebuah tautan ber-token dikirim ke email kontraktor.
-- Approval chain baru dipicu setelah dokumen lengkap.
--
-- Empat kolom baru pada ses_request:
--
--   email                VARCHAR2(150)  alamat tujuan tautan unggah. BUKAN email
--                                       pemohon — ini alamat pihak luar yang
--                                       memegang dokumennya.
--   upload_token         VARCHAR2(100)  64 karakter hex dari SecureRandom
--                                       (32 byte). Panjang kolom 100 memberi
--                                       ruang kalau panjangnya pernah dinaikkan.
--   upload_token_expiry  DATE           SYSDATE + 7 saat diterbitkan.
--   upload_token_used    CHAR(1)        'N' sampai dokumen disubmit, lalu 'Y'.
--
-- ⚠️ TOKEN ADALAH SATU-SATUNYA PENGAMAN halaman unggah — halaman itu dapat
-- diakses tanpa login, karena yang membukanya memang bukan pengguna SES.
-- Karena itu tokennya 256 bit dari SecureRandom, bukan UUID: UUIDv4 "hanya"
-- 122 bit dan beberapa generator UUID tidak kriptografis. Jangan pernah
-- menurunkan ini menjadi angka urut atau hash dari request_no.
--
-- ------------------------------------------------------------
-- STATUS BARU
-- ------------------------------------------------------------
-- CK_SES_REQUEST_STATUS di-DROP lalu di-ADD dengan satu nilai tambahan.
-- Mengikuti pola 21_request_draft_status_alter.sql persis; kolomnya
-- VARCHAR2(20) dan 'WAITING_FOR_UPLOAD' 18 karakter, jadi muat tanpa diperlebar.
--
-- ⚠️ TIDAK RE-RUNNABLE pada bagian constraint: DROP akan gagal kalau
-- constraint-nya sudah hilang. Bagian kolom dan index berpagar dan aman.
-- Dibuat begitu dengan sengaja — sesuai konvensi sql/README.md untuk
-- perubahan constraint.
--
-- ------------------------------------------------------------
-- JADWAL PEMBERSIH
-- ------------------------------------------------------------
-- Baris app_cat JOB_SCHEDULE/REQ_EXPIRY mendaftarkan jadwal untuk
-- RequestExpiryJob, yang membatalkan pengajuan WAITING_FOR_UPLOAD yang tidak
-- pernah diunggah. ⚠️ app.scheduler.auto-start=false di application.properties,
-- jadi job ini TIDAK berjalan sampai dinyalakan sekali di /admin/scheduler.
-- Ambang harinya dibaca dari app_parameter REQUEST_UPLOAD_EXPIRY_DAYS.
-- ============================================================

SET SERVEROUTPUT ON

-- ---- 1. Kolom baru (berpagar, aman di-rerun) ----
DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count FROM user_tab_columns
    WHERE table_name = 'SES_REQUEST' AND column_name = 'EMAIL';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE ses_request ADD email VARCHAR2(150)';
        DBMS_OUTPUT.PUT_LINE('ses_request.email: ditambahkan.');
    END IF;

    SELECT COUNT(*) INTO v_count FROM user_tab_columns
    WHERE table_name = 'SES_REQUEST' AND column_name = 'UPLOAD_TOKEN';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE ses_request ADD upload_token VARCHAR2(100)';
        DBMS_OUTPUT.PUT_LINE('ses_request.upload_token: ditambahkan.');
    END IF;

    SELECT COUNT(*) INTO v_count FROM user_tab_columns
    WHERE table_name = 'SES_REQUEST' AND column_name = 'UPLOAD_TOKEN_EXPIRY';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE ses_request ADD upload_token_expiry DATE';
        DBMS_OUTPUT.PUT_LINE('ses_request.upload_token_expiry: ditambahkan.');
    END IF;

    SELECT COUNT(*) INTO v_count FROM user_tab_columns
    WHERE table_name = 'SES_REQUEST' AND column_name = 'UPLOAD_TOKEN_USED';
    IF v_count = 0 THEN
        -- DEFAULT 'N' mengisi baris lama sekaligus; tanpa itu baris lama
        -- bernilai NULL dan setiap pemeriksaan "belum dipakai" harus
        -- mengurus NULL secara terpisah.
        EXECUTE IMMEDIATE 'ALTER TABLE ses_request ADD upload_token_used CHAR(1) DEFAULT ''N''';
        EXECUTE IMMEDIATE 'ALTER TABLE ses_request ADD CONSTRAINT ck_ses_request_tok_used'
                       || ' CHECK (upload_token_used IS NULL OR upload_token_used IN (''Y'', ''N''))';
        DBMS_OUTPUT.PUT_LINE('ses_request.upload_token_used: ditambahkan.');
    END IF;
END;
/

-- ---- 2. Index unik pada token ----
-- Jalur baca satu-satunya halaman unggah adalah "cari request dengan token
-- ini", jadi ia butuh index. UNIQUE karena dua pengajuan tidak boleh pernah
-- punya token yang sama.
--
-- Oracle tidak mengindeks baris yang SELURUH kolom indeksnya NULL, sehingga
-- index unik biasa di satu kolom nullable sudah otomatis mengabaikan baris
-- lama yang token-nya NULL. Itu persis yang diinginkan — tidak perlu
-- function-based index.
DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count FROM user_indexes WHERE index_name = 'UQ_SES_REQUEST_UPLOAD_TOKEN';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'CREATE UNIQUE INDEX uq_ses_request_upload_token'
                       || ' ON ses_request(upload_token)';
        DBMS_OUTPUT.PUT_LINE('uq_ses_request_upload_token: dibuat.');
    END IF;
END;
/

-- ---- 3. Status baru ----
-- [261019] Berpagar di salinan produksi ini: DROP hanya bila constraint-nya
-- ada, sehingga paket aman dijalankan ulang walau berhenti di antara keduanya.
DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count FROM user_constraints
    WHERE table_name = 'SES_REQUEST' AND constraint_name = 'CK_SES_REQUEST_STATUS';
    IF v_count > 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE ses_request DROP CONSTRAINT ck_ses_request_status';
    END IF;
    EXECUTE IMMEDIATE 'ALTER TABLE ses_request ADD CONSTRAINT ck_ses_request_status'
                   || ' CHECK (status IN (''DRAFT'', ''WAITING_FOR_UPLOAD'', ''PENDING'','
                   || ' ''APPROVED'', ''REJECTED'', ''CANCELLED''))';
END;
/

-- ---- 4. Komentar kolom ----
COMMENT ON COLUMN ses_request.email               IS 'Alamat tujuan tautan unggah dokumen — pihak luar/kontraktor, bukan pemohon';
COMMENT ON COLUMN ses_request.upload_token        IS '64 hex (SecureRandom 32 byte); satu-satunya pengaman halaman unggah tanpa login';
COMMENT ON COLUMN ses_request.upload_token_expiry IS 'Batas berlaku tautan, SYSDATE + 7 saat diterbitkan';
COMMENT ON COLUMN ses_request.upload_token_used   IS 'Y setelah dokumen disubmit; tautan hangus sekali pakai';
COMMENT ON COLUMN ses_request.status              IS 'DRAFT | WAITING_FOR_UPLOAD | PENDING | APPROVED | REJECTED | CANCELLED';

-- ---- 5. Jadwal pembersih + ambang harinya ----
DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count FROM app_cat
    WHERE cat_type = 'JOB_SCHEDULE' AND cat_value = 'REQ_EXPIRY';
    IF v_count = 0 THEN
        -- 01:00 setiap hari. Sengaja bukan tiap menit: ini sapuan sekali sehari
        -- atas pengajuan berumur puluhan hari, bukan antrean.
        INSERT INTO app_cat (cat_type, cat_value, cat_label, cat_other1, order_by, is_active)
        VALUES ('JOB_SCHEDULE', 'REQ_EXPIRY', 'Request Upload Expiry Sweeper', '0 0 1 * * ?', 8, 'Y');
        DBMS_OUTPUT.PUT_LINE('app_cat JOB_SCHEDULE/REQ_EXPIRY: ditambahkan.');
    END IF;

    SELECT COUNT(*) INTO v_count FROM app_parameter
    WHERE param_id = 'REQUEST_UPLOAD_EXPIRY_DAYS';
    IF v_count = 0 THEN
        INSERT INTO app_parameter (param_id, param_value, param_desc)
        VALUES ('REQUEST_UPLOAD_EXPIRY_DAYS', '30',
                'Umur hari sebelum pengajuan WAITING_FOR_UPLOAD dibatalkan otomatis');
        DBMS_OUTPUT.PUT_LINE('app_parameter REQUEST_UPLOAD_EXPIRY_DAYS: ditambahkan.');
    END IF;
END;
/

COMMIT;

-- Pemeriksaan sesudah:
-- SELECT column_name, data_type, data_length FROM user_tab_columns
-- WHERE table_name = 'SES_REQUEST'
--   AND column_name IN ('EMAIL','UPLOAD_TOKEN','UPLOAD_TOKEN_EXPIRY','UPLOAD_TOKEN_USED');
--
-- Dan constraint-nya harus memuat WAITING_FOR_UPLOAD:
-- SELECT constraint_name, search_condition FROM user_constraints
-- WHERE constraint_name = 'CK_SES_REQUEST_STATUS';
--
-- ⚠️ app_parameter di-cache di memori dan hanya dimuat ulang saat save() lewat
-- /admin/parameter. Setelah skrip ini, aplikasi perlu di-restart sekali agar
-- REQUEST_UPLOAD_EXPIRY_DAYS terbaca.
