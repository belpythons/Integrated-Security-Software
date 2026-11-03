-- ============================================================
-- SES — Pembersihan Total Seluruh Artefak & Konfigurasi Modul Izin Masuk Barang (Goods) — Oracle
-- Run as schema owner on ELLDEV / production.
--
-- ------------------------------------------------------------
-- MENGAPA SKRIP INI ADA (HASIL TRACE-BACK REPO & DATABASE)
-- ------------------------------------------------------------
-- Modul Izin Masuk Barang (Goods Permit) sebelumnya di-commit pada
-- f971ca7 (sql/45, sql/46, sql/49) lalu di-revert pada commit fbd7120.
-- Di database ELLDEV, artefak-artefak berikut dibersihkan:
--
-- 1. TABEL & SEQUENCE:
--    - SES_GOODS_SCREENING_RECORD, SES_GOODS_SCREENING_MASTER,
--      SES_GOODS_ITEM, SES_GOODS_REQUEST
--    - Sequences: SEQ_SES_GOODS_REQ, SEQ_SES_GOODS_REQ_NO, SEQ_SES_GOODS_ITEM,
--                SEQ_SES_GOODS_SCR_MASTER, SEQ_SES_GOODS_SCR_REC
--
-- 2. APP_PAGE (6 halaman):
--    - /goods, /goods/form, /admin/goods-screening, /admin/goods-screening/form,
--      /goods/inspection, /goods/inspection/form
--
-- 3. APP_MENU (4 menu):
--    - 'Izin Masuk Barang', 'Request Izin Barang',
--      'Konfigurasi Screening Barang', 'Inspeksi Kelaikan Kendaraan'
--
-- 4. RBAC (APP_ROLE_PAGE & APP_ROLE_MENU):
--    - Hak akses ADMIN, SYSADM, REQUESTOR pada seluruh halaman & menu di atas.
--
-- 5. APPROVAL ENGINE CONFIGURATION:
--    - SES_FORM: 'SES_GOODS_REQUEST'
--    - SES_FORM_APPROVAL_MAP: form_code 'SES_GOODS_REQUEST' / 'GOODS%'
--    - SES_APPROVAL_CHAIN: 'GOODS_DEFAULT' / 'Izin Masuk Barang' (jika tidak ada instance aktif).
--
-- Idempotent & aman di-rerun berkali-kali.
-- ============================================================

SET DEFINE OFF;
SET SERVEROUTPUT ON;

-- ============================================================
-- BAGIAN 1: PEMBERSIHAN MENU & RBAC
-- ============================================================

-- 1a. Hapus APP_ROLE_MENU terkait Goods / Izin Masuk Barang / Screening / Inspeksi
DELETE FROM app_role_menu
WHERE menu_id IN (
    SELECT menu_id FROM app_menu
    WHERE page_id IN (
        SELECT page_id FROM app_page
        WHERE LOWER(page_url) LIKE '/goods%'
           OR LOWER(page_url) LIKE '/admin/goods-screening%'
    )
    OR LOWER(menu_name) LIKE '%izin%barang%'
    OR LOWER(menu_name) LIKE '%request izin barang%'
    OR LOWER(menu_name) LIKE '%screening barang%'
    OR LOWER(menu_name) LIKE '%inspeksi kelaikan kendaraan%'
    OR LOWER(menu_name) LIKE '%goods%'
);

-- 1b. Hapus APP_MENU terkait Goods / Izin Masuk Barang / Screening / Inspeksi
DELETE FROM app_menu
WHERE page_id IN (
    SELECT page_id FROM app_page
    WHERE LOWER(page_url) LIKE '/goods%'
       OR LOWER(page_url) LIKE '/admin/goods-screening%'
)
OR LOWER(menu_name) LIKE '%izin%barang%'
OR LOWER(menu_name) LIKE '%request izin barang%'
OR LOWER(menu_name) LIKE '%screening barang%'
OR LOWER(menu_name) LIKE '%inspeksi kelaikan kendaraan%'
OR LOWER(menu_name) LIKE '%goods%';

-- 1c. Hapus APP_ROLE_PAGE terkait halaman Goods
DELETE FROM app_role_page
WHERE page_id IN (
    SELECT page_id FROM app_page
    WHERE LOWER(page_url) LIKE '/goods%'
       OR LOWER(page_url) LIKE '/admin/goods-screening%'
);

-- 1d. Hapus APP_PAGE terkait halaman Goods
DELETE FROM app_page
WHERE LOWER(page_url) LIKE '/goods%'
   OR LOWER(page_url) LIKE '/admin/goods-screening%';


-- ============================================================
-- BAGIAN 2: PEMBERSIHAN APPROVAL ENGINE CONFIGURATION
-- ============================================================

-- 2a. Hapus Mapping Form Approval Goods
DELETE FROM ses_form_approval_map
WHERE LOWER(form_code) LIKE 'goods%'
   OR LOWER(form_code) LIKE 'ses_goods%';

-- 2b. Hapus Registrasi SES_FORM Goods
DELETE FROM ses_form
WHERE LOWER(form_code) LIKE 'goods%'
   OR LOWER(form_code) LIKE 'ses_goods%';

-- 2c. Hapus Chain Goods jika ada dan tidak dipakai oleh instance aktif
-- (ON DELETE CASCADE pada ses_approval_assignee dan ses_approval_step_task akan otomatis membersihkan anaknya)
DELETE FROM ses_approval_step
WHERE chain_id IN (
    SELECT chain_id FROM ses_approval_chain
    WHERE (LOWER(chain_name) LIKE 'goods%' OR LOWER(chain_key) LIKE 'goods%' OR LOWER(chain_name) LIKE '%izin masuk barang%')
      AND chain_id NOT IN (SELECT DISTINCT chain_id FROM ses_approval_instance WHERE chain_id IS NOT NULL)
);

DELETE FROM ses_approval_chain
WHERE (LOWER(chain_name) LIKE 'goods%' OR LOWER(chain_key) LIKE 'goods%' OR LOWER(chain_name) LIKE '%izin masuk barang%')
  AND chain_id NOT IN (SELECT DISTINCT chain_id FROM ses_approval_instance WHERE chain_id IS NOT NULL);


-- ============================================================
-- BAGIAN 3: DROP TABEL & SEQUENCE MODUL GOODS
-- ============================================================

BEGIN
    -- Drop tables if exist
    FOR t IN (
        SELECT table_name FROM user_tables
        WHERE table_name IN (
            'SES_GOODS_SCREENING_RECORD',
            'SES_GOODS_SCREENING_MASTER',
            'SES_GOODS_ITEM',
            'SES_GOODS_REQUEST'
        )
    ) LOOP
        EXECUTE IMMEDIATE 'DROP TABLE ' || t.table_name || ' CASCADE CONSTRAINTS PURGE';
        DBMS_OUTPUT.PUT_LINE('Table ' || t.table_name || ' dropped.');
    END LOOP;

    -- Drop sequences if exist
    FOR s IN (
        SELECT sequence_name FROM user_sequences
        WHERE sequence_name IN (
            'SEQ_SES_GOODS_REQ',
            'SEQ_SES_GOODS_REQ_NO',
            'SEQ_SES_GOODS_ITEM',
            'SEQ_SES_GOODS_SCR_MASTER',
            'SEQ_SES_GOODS_SCR_REC'
        )
    ) LOOP
        EXECUTE IMMEDIATE 'DROP SEQUENCE ' || s.sequence_name;
        DBMS_OUTPUT.PUT_LINE('Sequence ' || s.sequence_name || ' dropped.');
    END LOOP;
END;
/

COMMIT;
