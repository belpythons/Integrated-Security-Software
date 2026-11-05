-- ============================================================
-- PT Badak NGL - Secure Entry System (SES)
-- Production Deployment 261105 — 96_app_page_new_modules.sql
--
-- Pendaftaran Halaman Baru ke Tabel Master APP_PAGE:
-- 1. /sticker/dashboard  - Sticker Security Dashboard
-- 2. /goods-permit       - Goods Entry Permit Portal
--
-- Dirancang Idempoten menggunakan MERGE INTO APP_PAGE.
-- ============================================================

SET SERVEROUTPUT ON SIZE UNLIMITED

DECLARE
    v_rows NUMBER := 0;
BEGIN
    DBMS_OUTPUT.PUT_LINE('--- Mendaftarkan Halaman Baru ke APP_PAGE ---');

    -- 1. Halaman /sticker/dashboard (PAGE_ID 58)
    MERGE INTO APP_PAGE target
    USING (
        SELECT 58 AS PAGE_ID, 
               'Sticker Security Dashboard' AS PAGE_NAME, 
               '/sticker/dashboard' AS PAGE_URL, 
               'Vehicle Sticker Security Dashboard & Checkpoint' AS PAGE_DESC, 
               'Y' AS ACTIVE_FLAG 
        FROM DUAL
    ) src ON (target.PAGE_URL = src.PAGE_URL)
    WHEN MATCHED THEN
        UPDATE SET target.PAGE_NAME   = src.PAGE_NAME,
                   target.PAGE_DESC   = src.PAGE_DESC,
                   target.ACTIVE_FLAG = src.ACTIVE_FLAG,
                   target.UPDATED_BY  = 'SYSTEM',
                   target.UPDATED_DATE= SYSDATE
    WHEN NOT MATCHED THEN
        INSERT (PAGE_ID, PAGE_NAME, PAGE_URL, PAGE_DESC, ACTIVE_FLAG, CREATED_BY, CREATED_DATE)
        VALUES (src.PAGE_ID, src.PAGE_NAME, src.PAGE_URL, src.PAGE_DESC, src.ACTIVE_FLAG, 'SYSTEM', SYSDATE);
    
    DBMS_OUTPUT.PUT_LINE('Halaman /sticker/dashboard berhasil didaftarkan/diperbarui.');

    -- 2. Halaman /goods-permit (PAGE_ID 59)
    MERGE INTO APP_PAGE target
    USING (
        SELECT 59 AS PAGE_ID, 
               'Goods Entry Permit' AS PAGE_NAME, 
               '/goods-permit' AS PAGE_URL, 
               'Goods Entry Permit (Izin Masuk Barang) Portal' AS PAGE_DESC, 
               'Y' AS ACTIVE_FLAG 
        FROM DUAL
    ) src ON (target.PAGE_URL = src.PAGE_URL)
    WHEN MATCHED THEN
        UPDATE SET target.PAGE_NAME   = src.PAGE_NAME,
                   target.PAGE_DESC   = src.PAGE_DESC,
                   target.ACTIVE_FLAG = src.ACTIVE_FLAG,
                   target.UPDATED_BY  = 'SYSTEM',
                   target.UPDATED_DATE= SYSDATE
    WHEN NOT MATCHED THEN
        INSERT (PAGE_ID, PAGE_NAME, PAGE_URL, PAGE_DESC, ACTIVE_FLAG, CREATED_BY, CREATED_DATE)
        VALUES (src.PAGE_ID, src.PAGE_NAME, src.PAGE_URL, src.PAGE_DESC, src.ACTIVE_FLAG, 'SYSTEM', SYSDATE);

    DBMS_OUTPUT.PUT_LINE('Halaman /goods-permit berhasil didaftarkan/diperbarui.');

    COMMIT;
    DBMS_OUTPUT.PUT_LINE('Pendaftaran APP_PAGE selesai dan telah di-COMMIT.');
END;
/
