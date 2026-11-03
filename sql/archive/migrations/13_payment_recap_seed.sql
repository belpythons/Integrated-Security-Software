-- ============================================================
-- SES — Cash Inflow Recap seed — Oracle
-- Run AFTER 08_payment_ddl.sql (needs paid_amount populated
-- going forward -- declared directly on SES_REQUEST_PAYMENT there,
-- no separate alter in this production script set) and
-- 10_payment_admin_rbac_seed.sql (needs app_page ids
-- 34-37 / app_menu ids 22-23 already seeded, so this script's ids don't
-- collide).
--
-- Contents:
--   1. APP_PAGE registration for the read-only recap screen (page id 38
--      — next free after 09_payment_seed.sql's 34-37).
--   2. One APP_MENU leaf entry — "Cash Inflow Recap" under the existing
--      "Form & Approval (Admin)" group (menu_id 21), alongside "Payment
--      Verification" (menu id 24 — next free after 09_payment_seed.sql's
--      22-23).
--   3. APP_ROLE_PAGE / APP_ROLE_MENU grants for ADMIN/SYSADM — same
--      full-access pattern as 10_payment_admin_rbac_seed.sql. No
--      "everyone gets their own worklist" grant here (contrast page 34)
--      — this is an admin cash-recap report, not a requestor screen.
--
-- Read-only report: no "/payment/recap/form" virtual RBAC pair — there's
-- nothing to submit here, only READ.
--
-- Idempotent: NOT EXISTS-guarded per (page_url) / (menu_name,parent) /
-- (role_id,page_id) / (role_id,menu_id).
-- ============================================================

-- ---- APP_PAGE ----
INSERT INTO app_page (page_id, page_name, page_url, page_desc, active_flag, created_by, created_date)
SELECT 38, 'Cash Inflow Recap', '/payment/recap', 'Admin recap of cash inflow from verified payments (read)', 'Y', 'SYSTEM', SYSDATE
FROM dual WHERE NOT EXISTS (SELECT 1 FROM app_page WHERE page_url = '/payment/recap');

-- ---- APP_MENU ----
INSERT INTO app_menu (menu_id, menu_name, page_id, menu_icon, menu_order, active_flag, parent_menu_id, created_by, created_date)
SELECT 24, 'Cash Inflow Recap', p.page_id, 'bi-graph-up-arrow', 70, 'Y', 21, 'SYSTEM', SYSDATE
FROM app_page p WHERE p.page_url = '/payment/recap'
AND NOT EXISTS (SELECT 1 FROM app_menu WHERE menu_name = 'Cash Inflow Recap' AND parent_menu_id = 21);

-- ---- APP_ROLE_PAGE — ADMIN/SYSADM get READ on the recap page ----
INSERT INTO app_role_page (role_id, page_id, action_code, added_by, added_date)
SELECT r.role_id, p.page_id, 'READ', 'SYSTEM', SYSDATE
FROM app_role r, app_page p
WHERE r.role_name IN ('ADMIN', 'SYSADM')
AND p.page_url = '/payment/recap'
AND NOT EXISTS (
    SELECT 1 FROM app_role_page rp
    WHERE rp.role_id = r.role_id AND rp.page_id = p.page_id
);

-- ---- APP_ROLE_MENU — ADMIN/SYSADM see the new menu entry ----
INSERT INTO app_role_menu (role_id, menu_id, added_by, added_date)
SELECT r.role_id, m.menu_id, 'SYSTEM', SYSDATE
FROM app_role r, app_menu m
WHERE r.role_name IN ('ADMIN', 'SYSADM')
AND m.menu_id = 24
AND NOT EXISTS (
    SELECT 1 FROM app_role_menu rm
    WHERE rm.role_id = r.role_id AND rm.menu_id = m.menu_id
);

-- ---- Advance sequences past the highest seeded ID ----
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
