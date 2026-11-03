-- ============================================================
-- SES 261023 — 00_precheck.sql
-- Stops the package before anything changes if the schema is not in the
-- state expected by the 261023 delta package:
-- Prerequisite: Deployment 261019 already applied on target schema.
-- Read-only: changes nothing.
-- ============================================================

SET SERVEROUTPUT ON

DECLARE
    v_count NUMBER;
    v_db    VARCHAR2(100);

    PROCEDURE require(p_ok IN BOOLEAN, p_msg IN VARCHAR2) IS
    BEGIN
        IF NOT p_ok THEN
            RAISE_APPLICATION_ERROR(-20001, 'Precheck 261023 gagal: ' || p_msg);
        END IF;
    END require;

    FUNCTION has_col(p_table IN VARCHAR2, p_col IN VARCHAR2) RETURN BOOLEAN IS
        v_c NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_c FROM user_tab_columns
         WHERE table_name = p_table AND column_name = p_col;
        RETURN v_c > 0;
    END has_col;

    FUNCTION has_tab(p_table IN VARCHAR2) RETURN BOOLEAN IS
        v_c NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_c FROM user_tables
         WHERE table_name = p_table;
        RETURN v_c > 0;
    END has_tab;
BEGIN
    SELECT SYS_CONTEXT('USERENV', 'DB_NAME') INTO v_db FROM dual;
    DBMS_OUTPUT.PUT_LINE('Database: ' || v_db || ', schema: ' || USER);

    -- 1. Verifikasi Prasyarat: Baseline 261019 sudah terpasang
    require(has_tab('SES_CASH_EXPENSE'), 'Tabel SES_CASH_EXPENSE tidak ditemukan (261019 migrasi 49 belum jalan).');
    require(has_tab('SES_APPROVAL_TASK_SIGNOFF'), 'Tabel SES_APPROVAL_TASK_SIGNOFF tidak ditemukan (261019 migrasi 53 belum jalan).');
    require(has_tab('SES_REQUEST_EVALUATOR'), 'Tabel SES_REQUEST_EVALUATOR tidak ditemukan (261019 migrasi 54 belum jalan).');
    require(has_col('SES_APPROVAL_STEP', 'STEP_ROLE'), 'Kolom SES_APPROVAL_STEP.STEP_ROLE tidak ditemukan (261019 migrasi 52 belum jalan).');

    -- Verifikasi parameter APP_LINK_EXT (migrasi 80)
    SELECT COUNT(*) INTO v_count FROM app_parameter WHERE param_id = 'APP_LINK_EXT';
    require(v_count > 0, 'Parameter APP_LINK_EXT belum terdaftar di APP_PARAMETER (261019 migrasi 80 belum jalan).');

    -- Verifikasi peran RBAC yang dibutuhkan
    SELECT COUNT(*) INTO v_count FROM app_role WHERE role_name = 'SECURITY';
    require(v_count = 1, 'Role SECURITY tidak ditemukan di APP_ROLE.');
    SELECT COUNT(*) INTO v_count FROM app_role WHERE role_name = 'REQUESTOR';
    require(v_count = 1, 'Role REQUESTOR tidak ditemukan di APP_ROLE.');

    -- Verifikasi struktur chain approval badge 7-tahap
    FOR c IN (SELECT chain_key FROM ses_approval_chain WHERE chain_key IN ('BADGE_DEFAULT', 'BADGE_PAYMENT')) LOOP
        SELECT COUNT(*) INTO v_count FROM ses_approval_step s
          JOIN ses_approval_chain ch ON ch.chain_id = s.chain_id
         WHERE ch.chain_key = c.chain_key;
        require(v_count = 7, 'Chain ' || c.chain_key || ' belum 7 tahap (261019 migrasi 60 belum jalan).');
    END LOOP;

    -- 2. Pemeriksaan Partial Re-run Delta 261023
    IF has_col('SES_STICKER_REQUEST', 'STICKER_TYPE_CODE') OR has_tab('SES_STICKER_VEH_DOC') THEN
        DBMS_OUTPUT.PUT_LINE('Catatan: Komponen skema 261023 terdeteksi sebagian. Paket ini aman dijalankan ulang (semua langkah delta idempoten).');
    END IF;

    DBMS_OUTPUT.PUT_LINE('Precheck 261023 OK: Baseline 261019 terverifikasi penuh.');
END;
/
