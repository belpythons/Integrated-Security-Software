-- ============================================================
-- SES 261019 — 00_precheck.sql
-- Stops the package before anything changes if this schema is not in the
-- state the package was built against (ELLPRD as of 2026-10-19: every
-- production script up to 47 applied, 48 onward not, or a previous partial
-- run of this package). Read-only: changes nothing.
-- ============================================================

SET SERVEROUTPUT ON

DECLARE
    v_count NUMBER;
    v_db    VARCHAR2(100);

    PROCEDURE require(p_ok IN BOOLEAN, p_msg IN VARCHAR2) IS
    BEGIN
        IF NOT p_ok THEN
            RAISE_APPLICATION_ERROR(-20001, 'Precheck 261019 gagal: ' || p_msg);
        END IF;
    END require;

    FUNCTION has_col(p_table IN VARCHAR2, p_col IN VARCHAR2) RETURN BOOLEAN IS
        v_c NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_c FROM user_tab_columns
         WHERE table_name = p_table AND column_name = p_col;
        RETURN v_c > 0;
    END has_col;
BEGIN
    SELECT SYS_CONTEXT('USERENV', 'DB_NAME') INTO v_db FROM dual;
    DBMS_OUTPUT.PUT_LINE('Database: ' || v_db || ', schema: ' || USER);

    -- Baseline that 48+ build on (scripts 26, 27, 29, 47).
    require(has_col('SES_REQUEST_PAYMENT', 'BUSINESS_TYPE'), 'SES_REQUEST_PAYMENT.BUSINESS_TYPE tidak ada (26 belum jalan).');
    SELECT COUNT(*) INTO v_count FROM user_tables WHERE table_name = 'SES_STICKER_REQUEST';
    require(v_count = 1, 'SES_STICKER_REQUEST tidak ada (27 belum jalan).');
    SELECT COUNT(*) INTO v_count FROM app_page WHERE page_url IN ('/history', '/dashboard');
    require(v_count = 2, 'Page /history dan /dashboard belum terdaftar (29 belum jalan).');
    SELECT COUNT(*) INTO v_count FROM user_views WHERE view_name = 'APP_POSITION_VIEW';
    require(v_count = 1, 'APP_POSITION_VIEW tidak ada.');

    -- 60 assigns the admin account as fallback approver.
    SELECT COUNT(*) INTO v_count FROM app_user WHERE username = 'admin';
    require(v_count = 1, 'User ''admin'' tidak ditemukan di APP_USER (dibutuhkan 60).');

    -- 74 would insert role_id 5 if no role is named SECURITY; on ELLPRD
    -- role_id 5 is already taken, so the role must already carry that name.
    SELECT COUNT(*) INTO v_count FROM app_role WHERE role_name = 'SECURITY';
    require(v_count = 1, 'Role SECURITY tidak ditemukan di APP_ROLE.');
    SELECT COUNT(*) INTO v_count FROM app_role WHERE role_name = 'REQUESTOR';
    require(v_count = 1, 'Role REQUESTOR tidak ditemukan di APP_ROLE.');

    -- 60 and 66 reshape / map exactly these two chains.
    SELECT COUNT(*) INTO v_count FROM ses_approval_chain
     WHERE chain_key IN ('DEFAULT', 'PAYMENT', 'BADGE_DEFAULT', 'BADGE_PAYMENT');
    require(v_count = 2, 'Chain DEFAULT/PAYMENT (atau BADGE_*) tidak ditemukan tepat 2.');

    -- Platform sequences that 50 draws page/menu ids from.
    SELECT COUNT(*) INTO v_count FROM user_sequences WHERE sequence_name IN ('SEQ_APP_PAGE', 'SEQ_APP_MENU');
    require(v_count = 2, 'SEQ_APP_PAGE / SEQ_APP_MENU tidak ada.');

    IF has_col('SES_APPROVAL_STEP', 'STEP_ROLE') THEN
        DBMS_OUTPUT.PUT_LINE('Catatan: STEP_ROLE sudah ada, paket ini pernah dijalankan sebagian; dilanjutkan (semua langkah idempoten).');
    END IF;

    DBMS_OUTPUT.PUT_LINE('Precheck 261019 OK.');
END;
/
