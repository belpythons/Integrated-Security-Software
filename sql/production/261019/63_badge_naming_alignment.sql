-- ============================================================
-- SES — Penyelarasan Standar Penamaan Modul Badge Karyawan — Oracle
-- Run AFTER 60_badge_chain_reshape_v2.sql.
--
-- ------------------------------------------------------------
-- MENGAPA SKRIP INI ADA
-- ------------------------------------------------------------
-- Modul Stiker Kendaraan menggunakan penamaan eksplisit dan konsisten
-- (misal: "Sticker Kendaraan", "Jenis Sticker", SES_STICKER_REQUEST),
-- sedangkan modul Badge sebelumnya memakai penamaan generik/ambigu
-- (misal: "New Request", "Master - Jenis Pengajuan", chain "DEFAULT"/"PAYMENT",
-- form_name "New Request").
--
-- Skrip ini menyelaraskan penamaan modul Badge di database:
--   1. APP_PAGE:
--      - '/request'               -> 'Badge Karyawan'
--      - '/request/form'          -> 'Form Badge Karyawan'
--      - '/admin/request-type'    -> 'Jenis Badge'
--      - '/admin/request-type/form' -> 'Form Jenis Badge'
--   2. APP_MENU:
--      - 'New Request'            -> 'Badge Karyawan'
--      - 'Master - Jenis Pengajuan' -> 'Jenis Badge'
--   3. SES_FORM:
--      - 'SES_REQUEST' form_name  -> 'Pengajuan Badge Karyawan'
--   4. SES_APPROVAL_CHAIN:
--      - chain_key 'PAYMENT'      -> 'BADGE_PAYMENT' (Badge Karyawan - Payment)
--      - chain_key 'DEFAULT'      -> 'BADGE_DEFAULT' (Badge Karyawan - Non-Payment)
--
-- ⚠️ 66_paid_types_to_payment_chain.sql and 64_ses_configuration_and_ext_upload.sql
-- also rename '/admin/request-type' to "Konfigurasi Jenis Pengajuan" — run
-- in numeric order (this script, then 64, then 66) so the later renames win,
-- same as they did on ELLDEV.
--
-- Idempotent & aman di-rerun berkali-kali.
-- ============================================================

SET DEFINE OFF;
SET SERVEROUTPUT ON;

-- 1. Penyelarasan APP_PAGE
UPDATE app_page
SET page_name = 'Badge Karyawan',
    page_desc = 'Badge request list (read)'
WHERE page_url = '/request';

UPDATE app_page
SET page_name = 'Form Badge Karyawan',
    page_desc = 'Create/edit a badge request'
WHERE page_url = '/request/form';

UPDATE app_page
SET page_name = 'Jenis Badge',
    page_desc = 'Badge request type master (read)'
WHERE page_url = '/admin/request-type';

UPDATE app_page
SET page_name = 'Form Jenis Badge',
    page_desc = 'Create/edit a badge request type'
WHERE page_url = '/admin/request-type/form';

-- 2. Penyelarasan APP_MENU
UPDATE app_menu
SET menu_name = 'Badge Karyawan'
WHERE page_id IN (SELECT page_id FROM app_page WHERE page_url = '/request')
   OR menu_name = 'New Request';

UPDATE app_menu
SET menu_name = 'Jenis Badge'
WHERE page_id IN (SELECT page_id FROM app_page WHERE page_url = '/admin/request-type')
   OR menu_name = 'Master - Jenis Pengajuan';

-- 3. Penyelarasan SES_FORM
UPDATE ses_form
SET form_name = 'Pengajuan Badge Karyawan'
WHERE form_code = 'SES_REQUEST';

-- 4. Penyelarasan SES_APPROVAL_CHAIN
UPDATE ses_approval_chain
SET chain_key = 'BADGE_PAYMENT',
    chain_name = 'Badge Karyawan (Payment)'
WHERE chain_key = 'PAYMENT' OR (chain_id = 2 AND chain_key NOT LIKE 'BADGE%');

UPDATE ses_approval_chain
SET chain_key = 'BADGE_DEFAULT',
    chain_name = 'Badge Karyawan (Non-Payment)'
WHERE chain_key = 'DEFAULT' OR (chain_id = 1 AND chain_key NOT LIKE 'BADGE%');

COMMIT;

BEGIN
    DBMS_OUTPUT.PUT_LINE('=== Penyelarasan Penamaan Modul Badge Berhasil Disimpan ===');
END;
/
