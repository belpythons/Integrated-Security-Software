-- ============================================================
-- SES — Dukungan Passport untuk Pekerja — Oracle
-- Run as schema owner on ELLDEV / production, AFTER 67_worker_screening_status.sql.
--
-- ------------------------------------------------------------
-- MENGAPA SKRIP INI ADA
-- ------------------------------------------------------------
-- SES_REQUEST_WORKER.NIK selama ini mengasumsikan setiap pekerja punya NIK
-- 16 digit. Pekerja asing hanya punya nomor Passport (alfanumerik, sampai
-- 20 karakter), jadi kolom perlu diperlebar dan diberi penanda jenis
-- identitas agar validasi (RequestService) dan tampilan tahu format mana
-- yang berlaku untuk baris itu.
--
-- NIK tetap default: baris lama otomatis menjadi ID_TYPE = 'NIK' tanpa
-- perlu backfill manual.
-- ============================================================

SET DEFINE OFF;
SET SERVEROUTPUT ON;

-- 1. Lebarkan NIK agar menampung Passport (alfanumerik, maks 20 karakter;
--    30 diberi ruang lebih supaya tidak perlu ALTER lagi).
DECLARE
    v_len NUMBER;
BEGIN
    SELECT data_length INTO v_len
    FROM user_tab_columns
    WHERE table_name = 'SES_REQUEST_WORKER' AND column_name = 'NIK';

    IF v_len < 30 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE ses_request_worker MODIFY (nik VARCHAR2(30))';
        DBMS_OUTPUT.PUT_LINE('Kolom NIK dilebarkan ke VARCHAR2(30).');
    ELSE
        DBMS_OUTPUT.PUT_LINE('Kolom NIK sudah >= VARCHAR2(30).');
    END IF;
END;
/

-- 2. Tambahkan ID_TYPE.
DECLARE
    v_col_exists NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_col_exists
    FROM user_tab_cols
    WHERE table_name = 'SES_REQUEST_WORKER' AND column_name = 'ID_TYPE';

    IF v_col_exists = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE ses_request_worker ADD '
                       || '(id_type VARCHAR2(10) DEFAULT ''NIK'' NOT NULL)';
        EXECUTE IMMEDIATE 'ALTER TABLE ses_request_worker ADD CONSTRAINT ck_ses_worker_id_type '
                       || 'CHECK (id_type IN (''NIK'', ''PASSPORT''))';
        DBMS_OUTPUT.PUT_LINE('Kolom ID_TYPE ditambahkan ke SES_REQUEST_WORKER.');
    ELSE
        DBMS_OUTPUT.PUT_LINE('Kolom ID_TYPE sudah ada.');
    END IF;
END;
/

COMMENT ON COLUMN ses_request_worker.id_type IS 'Jenis identitas pada kolom NIK: NIK (16 digit angka) | PASSPORT (alfanumerik 6-20 karakter).';

COMMIT;

BEGIN
    DBMS_OUTPUT.PUT_LINE('=== Dukungan Passport untuk pekerja selesai ===');
END;
/

-- ---- Verifikasi (jalankan manual bila perlu) ----
-- SELECT column_name, data_type, data_length, nullable, data_default
--   FROM user_tab_columns
--  WHERE table_name = 'SES_REQUEST_WORKER' AND column_name IN ('NIK', 'ID_TYPE');
--
-- SELECT constraint_name, search_condition FROM user_constraints
--  WHERE constraint_name = 'CK_SES_WORKER_ID_TYPE';
