-- ============================================================
-- PT Badak NGL - Secure Entry System (SES)
-- Production Deployment 261105 — 99_verify.sql
--
-- Skrip Verifikasi Pasca-Deployment 261105 (READ-ONLY)
-- Memastikan integritas objek DDL, tabel APP_PAGE, pengerasan RBAC,
-- dan ketiadaan objek invalid pada skema database.
-- ============================================================

SET SERVEROUTPUT ON SIZE UNLIMITED

DECLARE
    v_count   NUMBER;
    v_errors  NUMBER := 0;

    PROCEDURE check_true(p_ok IN BOOLEAN, p_item IN VARCHAR2) IS
    BEGIN
        IF p_ok THEN
            DBMS_OUTPUT.PUT_LINE('  [PASS] ' || p_item);
        ELSE
            v_errors := v_errors + 1;
            DBMS_OUTPUT.PUT_LINE('  [FAIL] ' || p_item);
        END IF;
    END check_true;

    FUNCTION has_tab(p_table IN VARCHAR2) RETURN BOOLEAN IS
        v_c NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_c FROM user_tables WHERE table_name = UPPER(p_table);
        RETURN v_c > 0;
    END has_tab;

    FUNCTION has_seq(p_seq IN VARCHAR2) RETURN BOOLEAN IS
        v_c NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_c FROM user_sequences WHERE sequence_name = UPPER(p_seq);
        RETURN v_c > 0;
    END has_seq;

    FUNCTION has_page(p_url IN VARCHAR2) RETURN BOOLEAN IS
        v_c NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_c FROM app_page WHERE page_url = p_url AND active_flag = 'Y';
        RETURN v_c > 0;
    END has_page;

    FUNCTION role_has_grant(p_role_name IN VARCHAR2, p_url IN VARCHAR2) RETURN BOOLEAN IS
        v_c NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_c 
          FROM app_role_page rp
          JOIN app_role r ON rp.role_id = r.role_id
          JOIN app_page p ON rp.page_id = p.page_id
         WHERE r.role_name = p_role_name AND p.page_url = p_url;
        RETURN v_c > 0;
    END role_has_grant;

BEGIN
    DBMS_OUTPUT.PUT_LINE('====================================================');
    DBMS_OUTPUT.PUT_LINE('Laporan Verifikasi Pasca-Deployment 261105');
    DBMS_OUTPUT.PUT_LINE('====================================================');

    -- 1. Verifikasi Objek DDL Modul Baru
    DBMS_OUTPUT.PUT_LINE('1. Objek DDL Goods Entry Permit:');
    check_true(has_tab('SES_GOODS_DRIVER'), 'Tabel SES_GOODS_DRIVER terpasang');
    check_true(has_seq('SEQ_SES_GOODS_DRIVER'), 'Sequence SEQ_SES_GOODS_DRIVER terpasang');

    -- 2. Verifikasi Registrasi APP_PAGE
    DBMS_OUTPUT.PUT_LINE('2. Master Halaman APP_PAGE:');
    check_true(has_page('/sticker/dashboard'), 'Halaman /sticker/dashboard terdaftar aktif');
    check_true(has_page('/goods-permit'), 'Halaman /goods-permit terdaftar aktif');

    -- 3. Verifikasi Pengerasan RBAC REQUESTOR (Harus TIDAK Memiliki Izin Admin)
    DBMS_OUTPUT.PUT_LINE('3. Pengerasan Akses Role REQUESTOR (Zero Admin Leak):');
    check_true(NOT role_has_grant('REQUESTOR', '/admin/sticker-type'), 'Akses /admin/sticker-type tercabut dari REQUESTOR');
    check_true(NOT role_has_grant('REQUESTOR', '/admin/sticker-type/form'), 'Akses /admin/sticker-type/form tercabut dari REQUESTOR');
    check_true(NOT role_has_grant('REQUESTOR', '/scheduling/queue'), 'Akses /scheduling/queue tercabut dari REQUESTOR');
    check_true(NOT role_has_grant('REQUESTOR', '/scheduling/queue/form'), 'Akses /scheduling/queue/form tercabut dari REQUESTOR');
    check_true(NOT role_has_grant('REQUESTOR', '/approval/form'), 'Akses /approval/form tercabut dari REQUESTOR');
    check_true(role_has_grant('REQUESTOR', '/goods-permit'), 'Izin self-service /goods-permit terpasang untuk REQUESTOR');

    -- 4. Verifikasi Izin Operasional Role SECURITY
    DBMS_OUTPUT.PUT_LINE('4. Hak Operasional Role SECURITY:');
    check_true(role_has_grant('SECURITY', '/sticker/dashboard'), 'Akses /sticker/dashboard terpasang untuk SECURITY');
    check_true(role_has_grant('SECURITY', '/sticker'), 'Akses /sticker terpasang untuk SECURITY');
    check_true(role_has_grant('SECURITY', '/goods-permit'), 'Akses /goods-permit terpasang untuk SECURITY');
    check_true(role_has_grant('SECURITY', '/payment/verify'), 'Akses /payment/verify dipertahankan untuk SECURITY');
    check_true(role_has_grant('SECURITY', '/history'), 'Akses /history terpasang untuk SECURITY');

    -- 5. Verifikasi Izin Role ADMIN & SYSADM
    DBMS_OUTPUT.PUT_LINE('5. Hak Akses Role ADMIN & SYSADM:');
    check_true(role_has_grant('ADMIN', '/sticker/dashboard'), 'Akses /sticker/dashboard terpasang untuk ADMIN');
    check_true(role_has_grant('ADMIN', '/goods-permit'), 'Akses /goods-permit terpasang untuk ADMIN');
    check_true(role_has_grant('SYSADM', '/sticker/dashboard'), 'Akses /sticker/dashboard terpasang untuk SYSADM');
    check_true(role_has_grant('SYSADM', '/goods-permit'), 'Akses /goods-permit terpasang untuk SYSADM');

    -- 6. Verifikasi Akun Umar Hakim (136141)
    DBMS_OUTPUT.PUT_LINE('6. Integritas Akun Umar Hakim (136141):');
    SELECT COUNT(*) INTO v_count 
      FROM app_user_role ur
      JOIN app_user u ON ur.user_id = u.user_id
      JOIN app_role r ON ur.role_id = r.role_id
     WHERE u.username = '136141' AND r.role_name = 'REQUESTOR';
    check_true(v_count = 0, 'Role REQUESTOR tidak lagi melekat pada akun 136141 (murni ADMIN)');

    -- 7. Verifikasi Objek Invalid
    DBMS_OUTPUT.PUT_LINE('7. Status Validitas Objek Skema:');
    SELECT COUNT(*) INTO v_count FROM user_objects WHERE status != 'VALID';
    check_true(v_count = 0, 'Seluruh objek basis data berstatus VALID (' || v_count || ' invalid)');

    DBMS_OUTPUT.PUT_LINE('====================================================');
    IF v_errors = 0 THEN
        DBMS_OUTPUT.PUT_LINE('>> HASIL VERIFIKASI: 100% PASS (SEMUA PERSYARATAN TERPENUHI).');
        DBMS_OUTPUT.PUT_LINE('>> Paket Deployment SES 261105 VALID & SIAP PRODUKSI.');
    ELSE
        DBMS_OUTPUT.PUT_LINE('>> HASIL VERIFIKASI: DITEMUKAN ' || v_errors || ' KEGAGALAN!');
        RAISE_APPLICATION_ERROR(-20002, 'Verifikasi 261105 gagal dengan ' || v_errors || ' error.');
    END IF;
    DBMS_OUTPUT.PUT_LINE('====================================================');
END;
/
