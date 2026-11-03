-- ============================================================
-- SES — Dukungan Kolom Screening Tanggal, Alasan & Status untuk Pekerja — Oracle
-- Run as schema owner on ELLDEV / production, AFTER 70_worker_passport_support.sql.
--
-- ------------------------------------------------------------
-- MENGAPA SKRIP INI ADA
-- ------------------------------------------------------------
-- Fitur checklist kelulusan screening pekerja (SchedulingService &
-- RequestDetail) mencatat tanggal kelulusan pekerja (SCREENING_DATE)
-- dan alasan tidak lulus (SCREENING_REASON).
--
-- Selain itu, status screening diperluas dengan 'ACCEPTED' (Lulus)
-- dan 'REJECTED' (Tidak Lulus), sehingga constraint CK_SES_WORKER_SCR_STATUS
-- perlu diperbarui agar mendukung status-status tersebut.
-- ============================================================

SET DEFINE OFF;
SET SERVEROUTPUT ON;

-- 1. Tambahkan kolom SCREENING_DATE ke SES_REQUEST_WORKER jika belum ada.
DECLARE
    v_col_exists NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_col_exists
    FROM user_tab_cols
    WHERE table_name = 'SES_REQUEST_WORKER' AND column_name = 'SCREENING_DATE';

    IF v_col_exists = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE ses_request_worker ADD (screening_date DATE)';
        DBMS_OUTPUT.PUT_LINE('Kolom SCREENING_DATE ditambahkan ke SES_REQUEST_WORKER.');
    ELSE
        DBMS_OUTPUT.PUT_LINE('Kolom SCREENING_DATE sudah ada.');
    END IF;
END;
/

-- 2. Tambahkan kolom SCREENING_REASON ke SES_REQUEST_WORKER jika belum ada.
DECLARE
    v_col_exists NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_col_exists
    FROM user_tab_cols
    WHERE table_name = 'SES_REQUEST_WORKER' AND column_name = 'SCREENING_REASON';

    IF v_col_exists = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE ses_request_worker ADD (screening_reason VARCHAR2(500))';
        DBMS_OUTPUT.PUT_LINE('Kolom SCREENING_REASON ditambahkan ke SES_REQUEST_WORKER.');
    ELSE
        DBMS_OUTPUT.PUT_LINE('Kolom SCREENING_REASON sudah ada.');
    END IF;
END;
/

COMMENT ON COLUMN ses_request_worker.screening_date IS 'Tanggal kelulusan screening pekerja.';
COMMENT ON COLUMN ses_request_worker.screening_reason IS 'Alasan tidak lulus screening pekerja.';

-- 3. Perbarui check constraint CK_SES_WORKER_SCR_STATUS agar mendukung ACCEPTED dan REJECTED.
DECLARE
    v_cons_exists NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_cons_exists
    FROM user_constraints
    WHERE table_name = 'SES_REQUEST_WORKER' AND constraint_name = 'CK_SES_WORKER_SCR_STATUS';

    IF v_cons_exists > 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE ses_request_worker DROP CONSTRAINT ck_ses_worker_scr_status';
        DBMS_OUTPUT.PUT_LINE('Constraint lama CK_SES_WORKER_SCR_STATUS di-drop.');
    END IF;

    EXECUTE IMMEDIATE 'ALTER TABLE ses_request_worker ADD CONSTRAINT ck_ses_worker_scr_status '
                   || 'CHECK (screening_status IN (''PENDING'', ''ATTENDED'', ''NO_SHOW'', ''FAILED'', ''ACCEPTED'', ''REJECTED''))';
    DBMS_OUTPUT.PUT_LINE('Constraint CK_SES_WORKER_SCR_STATUS diperbarui.');
END;
/

COMMIT;
