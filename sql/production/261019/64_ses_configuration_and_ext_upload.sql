-- ============================================================
-- SES — Penyelarasan Menu SES Configuration & Toggle EXT Upload — Oracle
-- Run AFTER 63_badge_naming_alignment.sql.
--
-- ------------------------------------------------------------
-- MENGAPA SKRIP INI ADA
-- ------------------------------------------------------------
-- 1. Penyelarasan penamaan menu "Konfigurasi Jenis Pengajuan"
--    (APP_PAGE & APP_MENU untuk /admin/request-type).
-- 2. Penyelarasan penamaan menu "Konfigurasi Jenis Sticker"
--    (APP_PAGE & APP_MENU untuk /admin/sticker-type).
-- 3. Tambah kolom NEED_EXT_PAGE_UPLOAD pada tabel SES_REQUEST_TYPE
--    untuk mengatur apakah jenis pengajuan memerlukan alur email link
--    upload dokumen eksternal ('Y') atau upload langsung di modal ('N').
--
-- Idempotent & aman di-rerun berkali-kali.
-- ============================================================

SET DEFINE OFF;
SET SERVEROUTPUT ON;

-- 1. Tambah kolom NEED_EXT_PAGE_UPLOAD pada SES_REQUEST_TYPE
DECLARE
    v_col_exists NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_col_exists
    FROM user_tab_cols
    WHERE table_name = 'SES_REQUEST_TYPE' AND column_name = 'NEED_EXT_PAGE_UPLOAD';

    IF v_col_exists = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE ses_request_type ADD (need_ext_page_upload CHAR(1) DEFAULT ''N'' NOT NULL)';
        EXECUTE IMMEDIATE 'ALTER TABLE ses_request_type ADD CONSTRAINT ck_ses_req_type_ext_upload CHECK (need_ext_page_upload IN (''Y'', ''N''))';
        DBMS_OUTPUT.PUT_LINE('Column NEED_EXT_PAGE_UPLOAD added to SES_REQUEST_TYPE.');
    ELSE
        DBMS_OUTPUT.PUT_LINE('Column NEED_EXT_PAGE_UPLOAD already exists.');
    END IF;
END;
/

COMMENT ON COLUMN ses_request_type.need_ext_page_upload IS 'Y = upload dokumen via email link eksternal; N = upload langsung pada form/modal (default)';

-- 2. Penyelarasan APP_PAGE
UPDATE app_page
SET page_name = 'Konfigurasi Jenis Pengajuan',
    page_desc = 'Konfigurasi jenis pengajuan master (read)'
WHERE page_url = '/admin/request-type';

UPDATE app_page
SET page_name = 'Form Konfigurasi Jenis Pengajuan',
    page_desc = 'Create/edit konfigurasi jenis pengajuan'
WHERE page_url = '/admin/request-type/form';

-- 3. Penyelarasan APP_MENU ("Jenis Badge" -> "Konfigurasi Jenis Pengajuan")
UPDATE app_menu
SET menu_name = 'Konfigurasi Jenis Pengajuan'
WHERE page_id IN (SELECT page_id FROM app_page WHERE page_url = '/admin/request-type')
   OR menu_name IN ('Jenis Badge', 'Master - Jenis Pengajuan');

-- 4. Penyelarasan APP_PAGE & APP_MENU untuk Konfigurasi Jenis Sticker
UPDATE app_page
SET page_name = 'Konfigurasi Jenis Sticker',
    page_desc = 'Konfigurasi jenis sticker master (read)'
WHERE page_url = '/admin/sticker-type';

UPDATE app_page
SET page_name = 'Form Konfigurasi Jenis Sticker',
    page_desc = 'Create/edit konfigurasi jenis sticker'
WHERE page_url = '/admin/sticker-type/form';

UPDATE app_menu
SET menu_name = 'Konfigurasi Jenis Sticker'
WHERE page_id IN (SELECT page_id FROM app_page WHERE page_url = '/admin/sticker-type')
   OR menu_name IN ('Jenis Sticker', 'Master Kendaraan');

-- Pastikan menu Konfigurasi Jenis Sticker terdaftar di app_menu jika belum ada
INSERT INTO app_menu (menu_id, menu_name, page_id, menu_icon, menu_order, active_flag, parent_menu_id, created_by, created_date)
SELECT NVL((SELECT MAX(menu_id) + 1 FROM app_menu), 28),
       'Konfigurasi Jenis Sticker',
       p.page_id,
       'bi-tag',
       4,
       'Y',
       NVL((SELECT menu_id FROM app_menu WHERE menu_name = 'SES Form Configuration' AND parent_menu_id IS NULL AND ROWNUM = 1), 21),
       'SYSTEM',
       SYSDATE
FROM app_page p
WHERE p.page_url = '/admin/sticker-type'
  AND NOT EXISTS (
      SELECT 1 FROM app_menu m
      WHERE m.page_id = p.page_id OR m.menu_name = 'Konfigurasi Jenis Sticker'
  );

-- Pastikan RBAC app_role_menu untuk Konfigurasi Jenis Sticker diberikan ke ADMIN dan SYSADM
INSERT INTO app_role_menu (role_id, menu_id, added_by, added_date)
SELECT r.role_id, m.menu_id, 'SYSTEM', SYSDATE
FROM app_role r, app_menu m
WHERE r.role_name IN ('ADMIN', 'SYSADM')
  AND m.page_id IN (SELECT page_id FROM app_page WHERE page_url = '/admin/sticker-type')
  AND NOT EXISTS (
      SELECT 1 FROM app_role_menu rm
      WHERE rm.role_id = r.role_id AND rm.menu_id = m.menu_id
  );

-- 5. Penyelarasan Urutan Menu (menu_order) SES Form Configuration:
--    1. Approval Chains
--    2. Approval Form Mapping
--    3. Konfigurasi Jenis Pengajuan
--    4. Konfigurasi Jenis Sticker
DECLARE
    v_parent_id NUMBER;
BEGIN
    SELECT menu_id INTO v_parent_id
    FROM app_menu
    WHERE menu_name = 'SES Form Configuration' AND parent_menu_id IS NULL AND ROWNUM = 1;

    -- 1. Approval Chains
    UPDATE app_menu
    SET menu_order = 1, parent_menu_id = v_parent_id, updated_by = 'SYSTEM', updated_date = SYSDATE
    WHERE page_id IN (SELECT page_id FROM app_page WHERE page_url = '/approval/chain')
       OR menu_name = 'Approval Chains';

    -- 2. Approval Form Mapping
    UPDATE app_menu
    SET menu_order = 2, parent_menu_id = v_parent_id, updated_by = 'SYSTEM', updated_date = SYSDATE
    WHERE page_id IN (SELECT page_id FROM app_page WHERE page_url = '/approval/map')
       OR menu_name = 'Approval Form Mapping';

    -- 3. Konfigurasi Jenis Pengajuan
    UPDATE app_menu
    SET menu_order = 3, parent_menu_id = v_parent_id, updated_by = 'SYSTEM', updated_date = SYSDATE
    WHERE page_id IN (SELECT page_id FROM app_page WHERE page_url = '/admin/request-type')
       OR menu_name = 'Konfigurasi Jenis Pengajuan';

    -- 4. Konfigurasi Jenis Sticker
    UPDATE app_menu
    SET menu_order = 4, parent_menu_id = v_parent_id, updated_by = 'SYSTEM', updated_date = SYSDATE
    WHERE page_id IN (SELECT page_id FROM app_page WHERE page_url = '/admin/sticker-type')
       OR menu_name = 'Konfigurasi Jenis Sticker';

    DBMS_OUTPUT.PUT_LINE('Urutan SES Form Configuration berhasil diperbarui.');
EXCEPTION
    WHEN NO_DATA_FOUND THEN
        DBMS_OUTPUT.PUT_LINE('Grup menu SES Form Configuration tidak ditemukan, fallback ke urutan per page.');
        UPDATE app_menu SET menu_order = 1 WHERE page_id IN (SELECT page_id FROM app_page WHERE page_url = '/approval/chain');
        UPDATE app_menu SET menu_order = 2 WHERE page_id IN (SELECT page_id FROM app_page WHERE page_url = '/approval/map');
        UPDATE app_menu SET menu_order = 3 WHERE page_id IN (SELECT page_id FROM app_page WHERE page_url = '/admin/request-type');
        UPDATE app_menu SET menu_order = 4 WHERE page_id IN (SELECT page_id FROM app_page WHERE page_url = '/admin/sticker-type');
END;
/

COMMIT;

BEGIN
    DBMS_OUTPUT.PUT_LINE('=== Penyelarasan Menu SES Configuration & Toggle EXT Upload Selesai ===');
END;
/
