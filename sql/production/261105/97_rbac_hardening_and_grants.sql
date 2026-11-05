-- ============================================================
-- PT Badak NGL - Secure Entry System (SES)
-- Production Deployment 261105 — 97_rbac_hardening_and_grants.sql
--
-- Pengerasan Hak Akses Role (RBAC Hardening):
-- 1. Cabut akses administrasi berbahaya dari REQUESTOR (ROLE_ID 4)
-- 2. Berikan hak operasional pos gerbang kilang ke SECURITY (ROLE_ID 5)
-- 3. Berikan hak modul baru ke ADMIN (1), SYSADM (2), dan VIEWER (3)
-- 4. Bersihkan penugasan role redundan pada akun Umar Hakim (136141)
--
-- Dirancang Idempoten menggunakan MERGE INTO APP_ROLE_PAGE.
-- ============================================================

SET SERVEROUTPUT ON SIZE UNLIMITED

DECLARE
    v_p_sticker_dash NUMBER;
    v_p_goods        NUMBER;
    v_del_req        NUMBER := 0;
    v_del_umar       NUMBER := 0;

    PROCEDURE grant_page(p_role_id IN NUMBER, p_page_id IN NUMBER, p_actions IN VARCHAR2) IS
    BEGIN
        MERGE INTO APP_ROLE_PAGE target
        USING (SELECT p_role_id AS ROLE_ID, p_page_id AS PAGE_ID, p_actions AS ACTION_CODE FROM DUAL) src
        ON (target.ROLE_ID = src.ROLE_ID AND target.PAGE_ID = src.PAGE_ID)
        WHEN MATCHED THEN
            UPDATE SET target.ACTION_CODE = src.ACTION_CODE
        WHEN NOT MATCHED THEN
            INSERT (ROLE_ID, PAGE_ID, ACTION_CODE, ADDED_BY, ADDED_DATE)
            VALUES (src.ROLE_ID, src.PAGE_ID, src.ACTION_CODE, 'SYSTEM', SYSDATE);
    END grant_page;

BEGIN
    DBMS_OUTPUT.PUT_LINE('--- Mengonfigurasi Izin Halaman dan Pengerasan RBAC ---');

    -- Ambil Page ID dinamis
    SELECT PAGE_ID INTO v_p_sticker_dash FROM APP_PAGE WHERE PAGE_URL = '/sticker/dashboard';
    SELECT PAGE_ID INTO v_p_goods FROM APP_PAGE WHERE PAGE_URL = '/goods-permit';

    -- 1. PENCABUTAN AKSES ADMIN DARI ROLE REQUESTOR (ROLE_ID = 4)
    --    Menghapus /admin/sticker-type (44, 45), /scheduling/queue (39, 40), dan /approval/form (20)
    DELETE FROM APP_ROLE_PAGE 
     WHERE ROLE_ID = 4 
       AND PAGE_ID IN (
           SELECT PAGE_ID FROM APP_PAGE 
            WHERE PAGE_URL IN ('/admin/sticker-type', '/admin/sticker-type/form', 
                               '/scheduling/queue', '/scheduling/queue/form', 
                               '/approval/form')
       );
    v_del_req := SQL%ROWCOUNT;
    DBMS_OUTPUT.PUT_LINE('Mencabut ' || v_del_req || ' izin admin dari role REQUESTOR.');

    -- 2. HAK AKSES ROLE REQUESTOR (ROLE_ID = 4)
    grant_page(4, v_p_goods, 'READ,CREATE,ADD,SUBMIT');
    DBMS_OUTPUT.PUT_LINE('Role REQUESTOR: Hak akses modul baru /goods-permit dikonfigurasi.');

    -- 3. HAK AKSES ROLE SECURITY (ROLE_ID = 5)
    -- Operasional Stiker Kendaraan di Pos Gerbang
    FOR r IN (SELECT PAGE_ID FROM APP_PAGE WHERE PAGE_URL IN ('/sticker', '/sticker/form')) LOOP
        grant_page(5, r.PAGE_ID, 'READ');
    END LOOP;
    grant_page(5, v_p_sticker_dash, 'READ,SUBMIT');

    -- Operasional Goods Entry Permit
    grant_page(5, v_p_goods, 'READ,CREATE,UPDATE,ADD,SUBMIT');

    -- Operasional Dashboard Utama & Riwayat
    FOR r IN (SELECT PAGE_ID FROM APP_PAGE WHERE PAGE_URL = '/dashboard') LOOP
        grant_page(5, r.PAGE_ID, 'READ,SUBMIT');
    END LOOP;
    FOR r IN (SELECT PAGE_ID FROM APP_PAGE WHERE PAGE_URL = '/history') LOOP
        grant_page(5, r.PAGE_ID, 'READ,PRINT,SUBMIT');
    END LOOP;

    -- Verifikasi Kas & Pembayaran (Dipertahankan)
    FOR r IN (SELECT PAGE_ID FROM APP_PAGE WHERE PAGE_URL = '/payment/verify') LOOP
        grant_page(5, r.PAGE_ID, 'READ');
    END LOOP;
    FOR r IN (SELECT PAGE_ID FROM APP_PAGE WHERE PAGE_URL = '/payment/verify/form') LOOP
        grant_page(5, r.PAGE_ID, 'READ,SUBMIT');
    END LOOP;

    DBMS_OUTPUT.PUT_LINE('Role SECURITY: Izin operasional gerbang & verifikasi dikonfigurasi.');

    -- 4. HAK AKSES ROLE ADMIN (ROLE_ID = 1) & SYSADM (ROLE_ID = 2)
    -- Akses Penuh ke Halaman Baru
    grant_page(1, v_p_sticker_dash, 'READ,CREATE,UPDATE,DELETE,ADD,REMOVE,SUBMIT');
    grant_page(1, v_p_goods,        'READ,CREATE,UPDATE,DELETE,ADD,REMOVE,SUBMIT');
    grant_page(2, v_p_sticker_dash, 'READ,CREATE,UPDATE,DELETE,ADD,REMOVE,SUBMIT');
    grant_page(2, v_p_goods,        'READ,CREATE,UPDATE,DELETE,ADD,REMOVE,SUBMIT');
    DBMS_OUTPUT.PUT_LINE('Role ADMIN & SYSADM: Akses penuh halaman baru dikonfigurasi.');

    -- 5. HAK AKSES ROLE VIEWER (ROLE_ID = 3)
    FOR r IN (SELECT PAGE_ID FROM APP_PAGE WHERE PAGE_URL IN ('/request', '/sticker')) LOOP
        grant_page(3, r.PAGE_ID, 'READ');
    END LOOP;
    grant_page(3, v_p_goods, 'READ');

    -- Cabut /scheduling/queue/form jika ada pada VIEWER
    DELETE FROM APP_ROLE_PAGE 
     WHERE ROLE_ID = 3 
       AND PAGE_ID IN (SELECT PAGE_ID FROM APP_PAGE WHERE PAGE_URL = '/scheduling/queue/form');

    DBMS_OUTPUT.PUT_LINE('Role VIEWER: Akses read-only monitoring dikonfigurasi.');

    -- 6. PEMBERSIHAN ROLE REDUNDAN PADA AKUN UMAR HAKIM (136141)
    DELETE FROM APP_USER_ROLE
     WHERE USER_ID = (SELECT USER_ID FROM APP_USER WHERE USERNAME = '136141')
       AND ROLE_ID = 4;
    v_del_umar := SQL%ROWCOUNT;
    IF v_del_umar > 0 THEN
        DBMS_OUTPUT.PUT_LINE('Membersihkan role REQUESTOR redundan dari akun Umar Hakim (136141).');
    ELSE
        DBMS_OUTPUT.PUT_LINE('Akun Umar Hakim (136141) sudah bersih (murni ADMIN).');
    END IF;

    COMMIT;
    DBMS_OUTPUT.PUT_LINE('Pengerasan RBAC dan penataan izin selesai dan telah di-COMMIT.');
END;
/
