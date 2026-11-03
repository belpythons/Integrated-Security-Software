-- ============================================================
-- SES — Stiker Kendaraan module seed — Oracle
-- Run AFTER 27_sticker_ddl.sql.
--
-- Contents:
--   1. APP_PAGE registration for the four sticker screens (page ids
--      42-45 — scheduling's 16_scheduling_seed.sql used 39-41, so 42 is
--      next free per ses/sql/README.md's running tally).
--   2. Two APP_MENU leaf entries — "Sticker Kendaraan" under the existing
--      "SES" group (menu_id 20), "Jenis Sticker" under the existing
--      "Form & Approval (Admin)" group (menu_id 21), same convention
--      07_menu_seed.sql used for "Master - Jenis Pengajuan".
--   3. RBAC grants — ADMIN/SYSADM get full CRUD on all four pages;
--      REQUESTOR gets READ,CREATE,SUBMIT on /sticker + /sticker/form,
--      same scoped-to-REQUESTOR pattern 19_requestor_role_seed.sql used
--      for /request + /request/form (creation is opt-in per role, unlike
--      the "every role" worklist grants on Approvals/My Payments).
--
-- NOT seeded here, on purpose:
--   * The SES_STICKER_REQUEST approval chain + its /approval/map default
--     mapping — there is no chain to point at yet (the real chains on
--     this environment are hand-built via the GUI, see
--     20_approval_chain_seed.sql's header). An admin must create a chain
--     at /approval/chain and map it as the default for form
--     "SES_STICKER_REQUEST" at /approval/map — both screens are already
--     generic and need no code change for this new form (SES_FORM is
--     auto-registered by ApprovalFormRegistrySync from the
--     StickerApprovalBridge bean on startup).
--   * SES_STICKER_BASE_AMOUNT app_parameter row — add it via the existing
--     Parameter admin screen (defaults to 0 / "not configured" until an
--     admin sets it, same as SesRequestType.paymentAmount before it's set).
--
-- Idempotent: NOT EXISTS-guarded per row, same convention as every other
-- seed script in this directory.
-- ============================================================

-- ------------------------------------------------------------
-- 1) APP_PAGE
-- ------------------------------------------------------------
INSERT INTO app_page (page_id, page_name, page_url, page_desc, active_flag, created_by, created_date)
SELECT 42, 'Sticker Kendaraan', '/sticker', 'Vehicle sticker request list (read)', 'Y', 'SYSTEM', SYSDATE
FROM dual WHERE NOT EXISTS (SELECT 1 FROM app_page WHERE page_url = '/sticker');

INSERT INTO app_page (page_id, page_name, page_url, page_desc, active_flag, created_by, created_date)
SELECT 43, 'Sticker Kendaraan Form', '/sticker/form', 'Create/edit a vehicle sticker request', 'Y', 'SYSTEM', SYSDATE
FROM dual WHERE NOT EXISTS (SELECT 1 FROM app_page WHERE page_url = '/sticker/form');

INSERT INTO app_page (page_id, page_name, page_url, page_desc, active_flag, created_by, created_date)
SELECT 44, 'Jenis Sticker', '/admin/sticker-type', 'Vehicle sticker type master (read)', 'Y', 'SYSTEM', SYSDATE
FROM dual WHERE NOT EXISTS (SELECT 1 FROM app_page WHERE page_url = '/admin/sticker-type');

INSERT INTO app_page (page_id, page_name, page_url, page_desc, active_flag, created_by, created_date)
SELECT 45, 'Jenis Sticker Form', '/admin/sticker-type/form', 'Create/edit a vehicle sticker type', 'Y', 'SYSTEM', SYSDATE
FROM dual WHERE NOT EXISTS (SELECT 1 FROM app_page WHERE page_url = '/admin/sticker-type/form');

-- ------------------------------------------------------------
-- 2) APP_MENU
-- ------------------------------------------------------------
INSERT INTO app_menu (menu_id, menu_name, page_id, menu_icon, menu_order, active_flag, parent_menu_id, created_by, created_date)
SELECT 27, 'Sticker Kendaraan', p.page_id, 'bi-car-front', 35, 'Y', 20, 'SYSTEM', SYSDATE
FROM app_page p WHERE p.page_url = '/sticker'
AND NOT EXISTS (SELECT 1 FROM app_menu WHERE menu_name = 'Sticker Kendaraan' AND parent_menu_id = 20);

INSERT INTO app_menu (menu_id, menu_name, page_id, menu_icon, menu_order, active_flag, parent_menu_id, created_by, created_date)
SELECT 28, 'Jenis Sticker', p.page_id, 'bi-tag', 75, 'Y', 21, 'SYSTEM', SYSDATE
FROM app_page p WHERE p.page_url = '/admin/sticker-type'
AND NOT EXISTS (SELECT 1 FROM app_menu WHERE menu_name = 'Jenis Sticker' AND parent_menu_id = 21);

-- ------------------------------------------------------------
-- 3) RBAC
-- ------------------------------------------------------------
-- ADMIN/SYSADM — full CRUD on all four pages.
INSERT INTO app_role_page (role_id, page_id, action_code, added_by, added_date)
SELECT r.role_id, p.page_id, 'READ,CREATE,UPDATE,DELETE,ADD,REMOVE,SUBMIT', 'SYSTEM', SYSDATE
FROM app_role r, app_page p
WHERE r.role_name IN ('ADMIN', 'SYSADM')
AND p.page_id BETWEEN 42 AND 45
AND NOT EXISTS (
    SELECT 1 FROM app_role_page rp
    WHERE rp.role_id = r.role_id AND rp.page_id = p.page_id
);

INSERT INTO app_role_menu (role_id, menu_id, added_by, added_date)
SELECT r.role_id, m.menu_id, 'SYSTEM', SYSDATE
FROM app_role r, app_menu m
WHERE r.role_name IN ('ADMIN', 'SYSADM')
AND m.menu_id IN (27, 28)
AND NOT EXISTS (
    SELECT 1 FROM app_role_menu rm
    WHERE rm.role_id = r.role_id AND rm.menu_id = m.menu_id
);

-- REQUESTOR gets READ,CREATE,SUBMIT on /sticker + /sticker/form (pages
-- 42-43) — same scoped-to-REQUESTOR pattern 19_requestor_role_seed.sql
-- used for /request + /request/form, NOT a blanket "every role" grant:
-- request/sticker CREATION is opt-in per role (unlike passively viewing
-- one's own worklist on Approvals/My Payments/My Schedule, which every
-- role gets). The master pages (44-45) are untouched here — admin-only.
INSERT INTO app_role_page (role_id, page_id, action_code, added_by, added_date)
SELECT r.role_id, p.page_id, 'READ,CREATE,SUBMIT', 'SYSTEM', SYSDATE
FROM app_role r, app_page p
WHERE r.role_name = 'REQUESTOR'
AND p.page_id IN (42, 43)
AND NOT EXISTS (
    SELECT 1 FROM app_role_page rp
    WHERE rp.role_id = r.role_id AND rp.page_id = p.page_id
);
INSERT INTO app_role_menu (role_id, menu_id, added_by, added_date)
SELECT r.role_id, 27, 'SYSTEM', SYSDATE
FROM app_role r
WHERE r.role_name = 'REQUESTOR'
AND NOT EXISTS (
    SELECT 1 FROM app_role_menu rm
    WHERE rm.role_id = r.role_id AND rm.menu_id = 27
);

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
