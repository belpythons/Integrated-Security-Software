-- ============================================================
-- PT Badak NGL - Secure Entry System (SES)
-- Production Deployment 261105 — 00_precheck.sql
--
-- Memastikan skema target telah menjalankan paket 261019, 261023,
-- dan 261029 (BIMA Approval Package & Unified History).
-- Skrip ini berstatus READ-ONLY (tidak melakukan perubahan data/skema).
-- ============================================================

SET SERVEROUTPUT ON SIZE UNLIMITED

DECLARE
    v_count NUMBER;
    v_db    VARCHAR2(100);

    PROCEDURE require(p_ok IN BOOLEAN, p_msg IN VARCHAR2) IS
    BEGIN
        IF NOT p_ok THEN
            RAISE_APPLICATION_ERROR(-20001, 'Precheck 261105 GAGAL: ' || p_msg);
        END IF;
    END require;

    FUNCTION has_tab(p_table IN VARCHAR2) RETURN BOOLEAN IS
        v_c NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_c FROM user_tables WHERE table_name = UPPER(p_table);
        RETURN v_c > 0;
    END has_tab;

    FUNCTION has_view(p_view IN VARCHAR2) RETURN BOOLEAN IS
        v_c NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_c FROM user_views WHERE view_name = UPPER(p_view);
        RETURN v_c > 0;
    END has_view;

    FUNCTION has_pkg(p_pkg IN VARCHAR2) RETURN BOOLEAN IS
        v_c NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_c FROM user_objects 
         WHERE object_name = UPPER(p_pkg) AND object_type IN ('PACKAGE', 'PACKAGE BODY') AND status = 'VALID';
        RETURN v_c = 2;
    END has_pkg;

    FUNCTION has_col(p_table IN VARCHAR2, p_col IN VARCHAR2) RETURN BOOLEAN IS
        v_c NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_c FROM user_tab_columns
         WHERE table_name = UPPER(p_table) AND column_name = UPPER(p_col);
        RETURN v_c > 0;
    END has_col;
BEGIN
    SELECT SYS_CONTEXT('USERENV', 'DB_NAME') INTO v_db FROM dual;
    DBMS_OUTPUT.PUT_LINE('====================================================');
    DBMS_OUTPUT.PUT_LINE('Menjalankan Precheck Deployment 261105');
    DBMS_OUTPUT.PUT_LINE('Database Target : ' || v_db || ' | Schema: ' || USER);
    DBMS_OUTPUT.PUT_LINE('====================================================');

    -- 1. Verifikasi Prasyarat Rilis 261019
    require(has_tab('SES_CASH_EXPENSE'), 'Tabel SES_CASH_EXPENSE belum ada (Paket 261019 belum diterapkan).');
    require(has_tab('SES_APPROVAL_TASK_SIGNOFF'), 'Tabel SES_APPROVAL_TASK_SIGNOFF belum ada (Paket 261019 belum diterapkan).');
    require(has_col('SES_APPROVAL_STEP', 'STEP_ROLE'), 'Kolom SES_APPROVAL_STEP.STEP_ROLE belum ada (Paket 261019 belum diterapkan).');

    -- 2. Verifikasi Prasyarat Rilis 261023
    require(has_view('SES_HISTORY_V'), 'View SES_HISTORY_V belum ada (Paket 261023 belum diterapkan).');
    require(has_col('SES_STICKER_REQUEST', 'STICKER_TYPE_CODE'), 'Kolom SES_STICKER_REQUEST.STICKER_TYPE_CODE belum ada (Paket 261023 belum diterapkan).');

    -- 3. Verifikasi Prasyarat Rilis 261029 (BIMA Package)
    require(has_view('SES_BIMA_APPROVAL_V'), 'View SES_BIMA_APPROVAL_V belum ada (Paket 261029 belum diterapkan).');
    require(has_pkg('PKG_SES_BIMA_APPROVAL'), 'Package PKG_SES_BIMA_APPROVAL (Spec & Body) belum VALID (Paket 261029 belum diterapkan).');

    -- 4. Verifikasi Keberadaan Master RBAC SES
    require(has_tab('APP_PAGE'), 'Tabel master APP_PAGE tidak ditemukan.');
    require(has_tab('APP_ROLE'), 'Tabel master APP_ROLE tidak ditemukan.');
    require(has_tab('APP_ROLE_PAGE'), 'Tabel relasi APP_ROLE_PAGE tidak ditemukan.');
    require(has_tab('APP_USER'), 'Tabel master APP_USER tidak ditemukan.');
    require(has_tab('APP_USER_ROLE'), 'Tabel relasi APP_USER_ROLE tidak ditemukan.');

    DBMS_OUTPUT.PUT_LINE('>> PRECHECK 261105 BERHASIL (100% PASS).');
    DBMS_OUTPUT.PUT_LINE('>> Skema memenuhi seluruh baseline prasyarat untuk menerima delta 261105.');
    DBMS_OUTPUT.PUT_LINE('====================================================');
END;
/
