-- ============================================================
-- SES — Shared Seed Data — Oracle
-- Run AFTER 03_request_ddl.sql (and after the platform seed).
--
-- Contents:
--   1. APP_PAGE registration for every SES screen (page ids 19-32 —
--      platform seed uses 1-18, so no collision).
--   2. One APP_MENU entry for "New Request".
--   3. Request-type / document-requirement / zone master seed.
--
-- NOT seeded here, on purpose:
--   * Approval chains, steps, assignees — fully GUI-created now
--     (ApprovalChainService). A seeded chain would just be
--     something an admin has to find and understand before they
--     even start; better to have them build the first one live.
--   * SES_FORM / SES_FORM_APPROVAL_MAP rows — SES_FORM is
--     populated at startup from ApprovalFormDescriptor beans
--     (ApprovalFormRegistrySync); a seeded row with no descriptor
--     bean would be deactivated as an orphan on first boot.
--   * RBAC grants (app_role_page / app_role_menu) — intentionally
--     NOT seeded; wired manually per environment.
--
-- All inserts are idempotent: app_page/app_menu via
-- NOT EXISTS-guarded INSERT...SELECT, master data via the same
-- pattern keyed on its natural code column.
-- ============================================================

-- ------------------------------------------------------------
-- 1) APP_PAGE — standard RBAC pair convention: non-form URL grants
--    READ, <base>/form URL grants CRUD/SUBMIT. The form URL is a
--    virtual RBAC key where noted — no controller mapping needs to
--    exist there, only the app_page row.
-- ------------------------------------------------------------

INSERT INTO app_page (page_id, page_name, page_url, page_desc, active_flag, created_by, created_date)
SELECT 19, 'Approvals', '/approval', 'Approval worklist, my requests, and detail (read)', 'Y', 'SYSTEM', SYSDATE
FROM dual WHERE NOT EXISTS (SELECT 1 FROM app_page WHERE page_url = '/approval');

INSERT INTO app_page (page_id, page_name, page_url, page_desc, active_flag, created_by, created_date)
SELECT 20, 'Approval Action', '/approval/form', 'Approve/reject actions on the current task', 'Y', 'SYSTEM', SYSDATE
FROM dual WHERE NOT EXISTS (SELECT 1 FROM app_page WHERE page_url = '/approval/form');

INSERT INTO app_page (page_id, page_name, page_url, page_desc, active_flag, created_by, created_date)
SELECT 23, 'Approval Chains', '/approval/chain', 'Approval chain list (read)', 'Y', 'SYSTEM', SYSDATE
FROM dual WHERE NOT EXISTS (SELECT 1 FROM app_page WHERE page_url = '/approval/chain');

INSERT INTO app_page (page_id, page_name, page_url, page_desc, active_flag, created_by, created_date)
SELECT 24, 'Approval Chain Form', '/approval/chain/form', 'Create/edit/reorder/delete chains, steps, and assignees', 'Y', 'SYSTEM', SYSDATE
FROM dual WHERE NOT EXISTS (SELECT 1 FROM app_page WHERE page_url = '/approval/chain/form');

INSERT INTO app_page (page_id, page_name, page_url, page_desc, active_flag, created_by, created_date)
SELECT 25, 'Approval Form Mapping', '/approval/map', 'Form-to-chain mapping list (read)', 'Y', 'SYSTEM', SYSDATE
FROM dual WHERE NOT EXISTS (SELECT 1 FROM app_page WHERE page_url = '/approval/map');

INSERT INTO app_page (page_id, page_name, page_url, page_desc, active_flag, created_by, created_date)
SELECT 26, 'Approval Form Mapping Form', '/approval/map/form', 'Edit a form''s default chain and overrides', 'Y', 'SYSTEM', SYSDATE
FROM dual WHERE NOT EXISTS (SELECT 1 FROM app_page WHERE page_url = '/approval/map/form');

INSERT INTO app_page (page_id, page_name, page_url, page_desc, active_flag, created_by, created_date)
SELECT 27, 'New Request', '/request', 'My requests list (read)', 'Y', 'SYSTEM', SYSDATE
FROM dual WHERE NOT EXISTS (SELECT 1 FROM app_page WHERE page_url = '/request');

INSERT INTO app_page (page_id, page_name, page_url, page_desc, active_flag, created_by, created_date)
SELECT 28, 'New Request Form', '/request/form', 'Create/edit/cancel a request', 'Y', 'SYSTEM', SYSDATE
FROM dual WHERE NOT EXISTS (SELECT 1 FROM app_page WHERE page_url = '/request/form');

INSERT INTO app_page (page_id, page_name, page_url, page_desc, active_flag, created_by, created_date)
SELECT 29, 'Contract Master', '/admin/contract', 'Contract list (read)', 'Y', 'SYSTEM', SYSDATE
FROM dual WHERE NOT EXISTS (SELECT 1 FROM app_page WHERE page_url = '/admin/contract');

INSERT INTO app_page (page_id, page_name, page_url, page_desc, active_flag, created_by, created_date)
SELECT 30, 'Contract Master Form', '/admin/contract/form', 'Create/edit a contract', 'Y', 'SYSTEM', SYSDATE
FROM dual WHERE NOT EXISTS (SELECT 1 FROM app_page WHERE page_url = '/admin/contract/form');

INSERT INTO app_page (page_id, page_name, page_url, page_desc, active_flag, created_by, created_date)
SELECT 31, 'Request Type Master', '/admin/request-type', 'Request type list (read)', 'Y', 'SYSTEM', SYSDATE
FROM dual WHERE NOT EXISTS (SELECT 1 FROM app_page WHERE page_url = '/admin/request-type');

INSERT INTO app_page (page_id, page_name, page_url, page_desc, active_flag, created_by, created_date)
SELECT 32, 'Request Type Master Form', '/admin/request-type/form', 'Create/edit a request type and its document requirements', 'Y', 'SYSTEM', SYSDATE
FROM dual WHERE NOT EXISTS (SELECT 1 FROM app_page WHERE page_url = '/admin/request-type/form');

-- ------------------------------------------------------------
-- 2) APP_MENU — "New Request" top-level entry, linked via page_id
--    (AppMenu.getMenuUrl() derives the URL from the linked
--    APP_PAGE, so this never needs to change even if the URL does).
--    Existing platform seed uses menu_id 1-13; SEQ_APP_MENU is
--    re-advanced below same as SEQ_APP_PAGE.
-- ------------------------------------------------------------

INSERT INTO app_menu (menu_id, menu_name, page_id, menu_icon, menu_order, active_flag, parent_menu_id, created_by, created_date)
SELECT 14, 'New Request', p.page_id, 'bi-file-earmark-plus', 20, 'Y', NULL, 'SYSTEM', SYSDATE
FROM app_page p WHERE p.page_url = '/request'
AND NOT EXISTS (SELECT 1 FROM app_menu WHERE menu_name = 'New Request' AND parent_menu_id IS NULL);

-- ------------------------------------------------------------
-- 3) Request module master data
-- ------------------------------------------------------------

-- ---- SES_ZONE ----
INSERT INTO ses_zone (zone_code, zone_name, order_by, active_flag, created_by, created_date)
SELECT 'ZONE_01', 'Zone 01', 10, 'Y', 'SYSTEM', SYSDATE FROM dual WHERE NOT EXISTS (SELECT 1 FROM ses_zone WHERE zone_code = 'ZONE_01');
INSERT INTO ses_zone (zone_code, zone_name, order_by, active_flag, created_by, created_date)
SELECT 'ZONE_02', 'Zone 02', 20, 'Y', 'SYSTEM', SYSDATE FROM dual WHERE NOT EXISTS (SELECT 1 FROM ses_zone WHERE zone_code = 'ZONE_02');
INSERT INTO ses_zone (zone_code, zone_name, order_by, active_flag, created_by, created_date)
SELECT 'ZONE_03', 'Zone 03', 30, 'Y', 'SYSTEM', SYSDATE FROM dual WHERE NOT EXISTS (SELECT 1 FROM ses_zone WHERE zone_code = 'ZONE_03');

-- ---- SES_DOC_REQUIREMENT ----
INSERT INTO ses_doc_requirement (doc_code, doc_name, doc_desc, active_flag, created_by, created_date)
SELECT 'KTP', 'National ID Card (KTP)', 'Kartu Tanda Penduduk — Indonesian national ID card', 'Y', 'SYSTEM', SYSDATE
FROM dual WHERE NOT EXISTS (SELECT 1 FROM ses_doc_requirement WHERE doc_code = 'KTP');
INSERT INTO ses_doc_requirement (doc_code, doc_name, doc_desc, active_flag, created_by, created_date)
SELECT 'KK', 'Family Card (Kartu Keluarga)', 'Kartu Keluarga — Indonesian family registration card', 'Y', 'SYSTEM', SYSDATE
FROM dual WHERE NOT EXISTS (SELECT 1 FROM ses_doc_requirement WHERE doc_code = 'KK');

-- ---- SES_REQUEST_TYPE ----
-- BADGE_NEW and BADGE_EXT need Proof of Payment; SCREENING does not.
INSERT INTO ses_request_type (type_code, type_name, needs_payment_flag, order_by, active_flag, created_by, created_date)
SELECT 'BADGE_NEW', 'Tanda Pengenal Pekerja (new badge)', 'Y', 10, 'Y', 'SYSTEM', SYSDATE
FROM dual WHERE NOT EXISTS (SELECT 1 FROM ses_request_type WHERE type_code = 'BADGE_NEW');
INSERT INTO ses_request_type (type_code, type_name, needs_payment_flag, order_by, active_flag, created_by, created_date)
SELECT 'BADGE_EXT', 'Tanda Pengenal Pekerja (extension)', 'Y', 20, 'Y', 'SYSTEM', SYSDATE
FROM dual WHERE NOT EXISTS (SELECT 1 FROM ses_request_type WHERE type_code = 'BADGE_EXT');
INSERT INTO ses_request_type (type_code, type_name, needs_payment_flag, order_by, active_flag, created_by, created_date)
SELECT 'SCREENING', 'Screening', 'N', 30, 'Y', 'SYSTEM', SYSDATE
FROM dual WHERE NOT EXISTS (SELECT 1 FROM ses_request_type WHERE type_code = 'SCREENING');

-- ---- SES_REQUEST_TYPE_DOC ----
-- Deliberately different sets per type so the dynamic per-worker document
-- column is visibly exercised: BADGE_NEW needs KTP+KK, BADGE_EXT needs
-- KTP+KK too, SCREENING needs KTP only.
INSERT INTO ses_request_type_doc (type_code, doc_code, mandatory_flag, order_by, created_by, created_date)
SELECT 'BADGE_NEW', 'KTP', 'Y', 10, 'SYSTEM', SYSDATE FROM dual
WHERE NOT EXISTS (SELECT 1 FROM ses_request_type_doc WHERE type_code = 'BADGE_NEW' AND doc_code = 'KTP');
INSERT INTO ses_request_type_doc (type_code, doc_code, mandatory_flag, order_by, created_by, created_date)
SELECT 'BADGE_NEW', 'KK', 'Y', 20, 'SYSTEM', SYSDATE FROM dual
WHERE NOT EXISTS (SELECT 1 FROM ses_request_type_doc WHERE type_code = 'BADGE_NEW' AND doc_code = 'KK');

INSERT INTO ses_request_type_doc (type_code, doc_code, mandatory_flag, order_by, created_by, created_date)
SELECT 'BADGE_EXT', 'KTP', 'Y', 10, 'SYSTEM', SYSDATE FROM dual
WHERE NOT EXISTS (SELECT 1 FROM ses_request_type_doc WHERE type_code = 'BADGE_EXT' AND doc_code = 'KTP');
INSERT INTO ses_request_type_doc (type_code, doc_code, mandatory_flag, order_by, created_by, created_date)
SELECT 'BADGE_EXT', 'KK', 'Y', 20, 'SYSTEM', SYSDATE FROM dual
WHERE NOT EXISTS (SELECT 1 FROM ses_request_type_doc WHERE type_code = 'BADGE_EXT' AND doc_code = 'KK');

INSERT INTO ses_request_type_doc (type_code, doc_code, mandatory_flag, order_by, created_by, created_date)
SELECT 'SCREENING', 'KTP', 'Y', 10, 'SYSTEM', SYSDATE FROM dual
WHERE NOT EXISTS (SELECT 1 FROM ses_request_type_doc WHERE type_code = 'SCREENING' AND doc_code = 'KTP');

-- ------------------------------------------------------------
-- 4) Advance sequences past the highest seeded ID
-- ------------------------------------------------------------
DECLARE v_max NUMBER;
BEGIN
    SELECT NVL(MAX(page_id), 0) + 1 INTO v_max FROM app_page;
    EXECUTE IMMEDIATE 'DROP SEQUENCE SEQ_APP_PAGE';
    EXECUTE IMMEDIATE 'CREATE SEQUENCE SEQ_APP_PAGE START WITH ' || v_max || ' INCREMENT BY 1 NOCACHE NOCYCLE';

    SELECT NVL(MAX(menu_id), 0) + 1 INTO v_max FROM app_menu;
    EXECUTE IMMEDIATE 'DROP SEQUENCE SEQ_APP_MENU';
    EXECUTE IMMEDIATE 'CREATE SEQUENCE SEQ_APP_MENU START WITH ' || v_max || ' INCREMENT BY 1 NOCACHE NOCYCLE';
END;
/

COMMIT;
