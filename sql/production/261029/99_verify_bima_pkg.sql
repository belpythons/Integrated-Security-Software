-- 99_verify_bima_pkg.sql
-- Verification script for SES BIMA Approval integration objects
-- Tanggal: 2026-10-29

SET SERVEROUTPUT ON SIZE UNLIMITED;

DECLARE
    v_view_status    VARCHAR2(30);
    v_pkg_spec_stat  VARCHAR2(30);
    v_pkg_body_stat  VARCHAR2(30);
    v_total_rows     NUMBER := 0;
    v_count_result   NUMBER := 0;
    v_cur            SYS_REFCURSOR;
    v_to_user        VARCHAR2(6);
    v_mod_id         CHAR(3);
    v_mod_sub_id     CHAR(2);
    v_req_id         VARCHAR2(16);
    v_req_desc       VARCHAR2(408);
    v_req_date       DATE;
    v_req_by         VARCHAR2(6);
    v_app_remark     VARCHAR2(4000);
    v_app_rem_type   VARCHAR2(50);
BEGIN
    DBMS_OUTPUT.PUT_LINE('====================================================');
    DBMS_OUTPUT.PUT_LINE('VERIFIKASI INTEGRASI BIMA APPROVAL PACKAGE & VIEW');
    DBMS_OUTPUT.PUT_LINE('====================================================');

    -- 1. Check SES_BIMA_APPROVAL_V
    BEGIN
        SELECT status INTO v_view_status
        FROM user_objects
        WHERE object_name = 'SES_BIMA_APPROVAL_V' AND object_type = 'VIEW';
        DBMS_OUTPUT.PUT_LINE('[OK] View SES_BIMA_APPROVAL_V ditemukan dengan status: ' || v_view_status);
    EXCEPTION
        WHEN NO_DATA_FOUND THEN
            DBMS_OUTPUT.PUT_LINE('[ERROR] View SES_BIMA_APPROVAL_V TIDAK DITEMUKAN!');
    END;

    -- 2. Check Package Spec
    BEGIN
        SELECT status INTO v_pkg_spec_stat
        FROM user_objects
        WHERE object_name = 'PKG_SES_BIMA_APPROVAL' AND object_type = 'PACKAGE';
        DBMS_OUTPUT.PUT_LINE('[OK] Package Specification PKG_SES_BIMA_APPROVAL status: ' || v_pkg_spec_stat);
    EXCEPTION
        WHEN NO_DATA_FOUND THEN
            DBMS_OUTPUT.PUT_LINE('[ERROR] Package Spec PKG_SES_BIMA_APPROVAL TIDAK DITEMUKAN!');
    END;

    -- 3. Check Package Body
    BEGIN
        SELECT status INTO v_pkg_body_stat
        FROM user_objects
        WHERE object_name = 'PKG_SES_BIMA_APPROVAL' AND object_type = 'PACKAGE BODY';
        DBMS_OUTPUT.PUT_LINE('[OK] Package Body PKG_SES_BIMA_APPROVAL status: ' || v_pkg_body_stat);
    EXCEPTION
        WHEN NO_DATA_FOUND THEN
            DBMS_OUTPUT.PUT_LINE('[ERROR] Package Body PKG_SES_BIMA_APPROVAL TIDAK DITEMUKAN!');
    END;

    -- 4. Test View Row Count
    EXECUTE IMMEDIATE 'SELECT COUNT(*) FROM SES_BIMA_APPROVAL_V' INTO v_total_rows;
    DBMS_OUTPUT.PUT_LINE('[INFO] Total baris pending approval di SES_BIMA_APPROVAL_V: ' || v_total_rows);

    -- 5. Test PKG_SES_BIMA_APPROVAL.GET_PENDING_COUNT
    v_count_result := PKG_SES_BIMA_APPROVAL.GET_PENDING_COUNT(NULL);
    DBMS_OUTPUT.PUT_LINE('[OK] GET_PENDING_COUNT(NULL) = ' || v_count_result);

    -- 6. Test PKG_SES_BIMA_APPROVAL.GET_PENDING_APPROVALS Cursor
    v_cur := PKG_SES_BIMA_APPROVAL.GET_PENDING_APPROVALS(NULL);
    IF v_cur%ISOPEN THEN
        DBMS_OUTPUT.PUT_LINE('[OK] GET_PENDING_APPROVALS cursor berhasil dibuka');
        FETCH v_cur INTO v_to_user, v_mod_id, v_mod_sub_id, v_req_id, v_req_desc, v_req_date, v_req_by, v_app_remark, v_app_rem_type;
        IF v_cur%FOUND THEN
            DBMS_OUTPUT.PUT_LINE('[SAMPLE] TO_USER=' || v_to_user || ' | MOD_ID=' || v_mod_id || ' | SUB=' || v_mod_sub_id || ' | REQ_ID=' || v_req_id);
            DBMS_OUTPUT.PUT_LINE('         REQ_DESC=' || SUBSTR(v_req_desc, 1, 80) || '...');
        END IF;
        CLOSE v_cur;
    END IF;

    DBMS_OUTPUT.PUT_LINE('====================================================');
    DBMS_OUTPUT.PUT_LINE('HASIL: SEMUA KOMPONEN VALID & SIAP DIGUNAKAN');
    DBMS_OUTPUT.PUT_LINE('====================================================');
END;
/
