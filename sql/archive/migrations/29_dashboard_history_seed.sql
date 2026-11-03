-- ============================================================
-- SES — Dashboard + Riwayat (History) seed — Oracle
-- Run AFTER 28_sticker_seed.sql.
--
-- Contents:
--   1. APP_PAGE 46 (/history, admin/officer-only) and 47 (/dashboard,
--      every role — a read-only reporting landing page).
--   2. APP_MENU 29 ("Riwayat", under "Form & Approval (Admin)" group id
--      21 — same admin-only grouping as the payment verification/recap
--      screens) and 30 ("Dashboard", top-level, parent NULL, menu_order 0
--      so it sits first in the sidebar).
--   3. RBAC — /history + its menu entry go to ADMIN/SYSADM only (no
--      distinct "OFFICER" role exists yet in this seed data; the only
--      users currently capable of the officer-only payment actions this
--      spec ties "officer" to ARE ADMIN/SYSADM via
--      10_payment_admin_rbac_seed.sql, so this satisfies "admin/officer
--      eyes only" as-is — if a dedicated OFFICER role is created later,
--      grant it /history via the GUI, same as any other admin screen).
--      /dashboard + its menu entry go to every role (unfiltered), same
--      "every role" convention 06_admin_rbac_seed.sql used for Approvals.
--
-- Idempotent: NOT EXISTS-guarded per row, same convention as every other
-- seed script in this directory.
-- ============================================================

-- ------------------------------------------------------------
-- 1) APP_PAGE
-- ------------------------------------------------------------
INSERT INTO app_page (page_id, page_name, page_url, page_desc, active_flag, created_by, created_date)
SELECT 46, 'Riwayat', '/history', 'Unified history of every request across every module (admin/officer only, read-only)', 'Y', 'SYSTEM', SYSDATE
FROM dual WHERE NOT EXISTS (SELECT 1 FROM app_page WHERE page_url = '/history');

INSERT INTO app_page (page_id, page_name, page_url, page_desc, active_flag, created_by, created_date)
SELECT 47, 'Dashboard', '/dashboard', 'Summary cards, trend/composition/cash-inflow charts, 5 newest requests (read-only)', 'Y', 'SYSTEM', SYSDATE
FROM dual WHERE NOT EXISTS (SELECT 1 FROM app_page WHERE page_url = '/dashboard');

-- ------------------------------------------------------------
-- 2) APP_MENU
-- ------------------------------------------------------------
INSERT INTO app_menu (menu_id, menu_name, page_id, menu_icon, menu_order, active_flag, parent_menu_id, created_by, created_date)
SELECT 29, 'Riwayat', p.page_id, 'bi-clock-history', 80, 'Y', 21, 'SYSTEM', SYSDATE
FROM app_page p WHERE p.page_url = '/history'
AND NOT EXISTS (SELECT 1 FROM app_menu WHERE menu_name = 'Riwayat' AND parent_menu_id = 21);

INSERT INTO app_menu (menu_id, menu_name, page_id, menu_icon, menu_order, active_flag, parent_menu_id, created_by, created_date)
SELECT 30, 'Dashboard', p.page_id, 'bi-speedometer2', 0, 'Y', NULL, 'SYSTEM', SYSDATE
FROM app_page p WHERE p.page_url = '/dashboard'
AND NOT EXISTS (SELECT 1 FROM app_menu WHERE menu_name = 'Dashboard' AND parent_menu_id IS NULL);

-- ------------------------------------------------------------
-- 3) RBAC
-- ------------------------------------------------------------
-- Riwayat — ADMIN/SYSADM only.
INSERT INTO app_role_page (role_id, page_id, action_code, added_by, added_date)
SELECT r.role_id, 46, 'READ', 'SYSTEM', SYSDATE
FROM app_role r
WHERE r.role_name IN ('ADMIN', 'SYSADM')
AND NOT EXISTS (
    SELECT 1 FROM app_role_page rp
    WHERE rp.role_id = r.role_id AND rp.page_id = 46
);
INSERT INTO app_role_menu (role_id, menu_id, added_by, added_date)
SELECT r.role_id, 29, 'SYSTEM', SYSDATE
FROM app_role r
WHERE r.role_name IN ('ADMIN', 'SYSADM')
AND NOT EXISTS (
    SELECT 1 FROM app_role_menu rm
    WHERE rm.role_id = r.role_id AND rm.menu_id = 29
);

-- Dashboard — every role (unfiltered), same convention as Approvals (page 19).
INSERT INTO app_role_page (role_id, page_id, action_code, added_by, added_date)
SELECT r.role_id, 47, 'READ', 'SYSTEM', SYSDATE
FROM app_role r
WHERE NOT EXISTS (
    SELECT 1 FROM app_role_page rp
    WHERE rp.role_id = r.role_id AND rp.page_id = 47
);
INSERT INTO app_role_menu (role_id, menu_id, added_by, added_date)
SELECT r.role_id, 30, 'SYSTEM', SYSDATE
FROM app_role r
WHERE NOT EXISTS (
    SELECT 1 FROM app_role_menu rm
    WHERE rm.role_id = r.role_id AND rm.menu_id = 30
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
