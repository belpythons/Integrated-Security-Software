-- ============================================================
-- SES — Payment Verification module seed — Oracle
-- Run AFTER 08_payment_ddl.sql.
--
-- Contents:
--   1. APP_PAGE registration for the two payment screens (page ids
--      34-37 — 04_seed.sql used 19-32, 06_admin_rbac_seed.sql's
--      Switch User addition used 33, so 34 is next free).
--   2. Two APP_MENU leaf entries — "My Payments" under the existing
--      "SES" group (menu_id 20), "Payment Verification" under the
--      existing "Form & Approval (Admin)" group (menu_id 21).
--
-- NOT seeded here, on purpose (same convention as 04_seed.sql):
--   * RBAC grants (app_role_page / app_role_menu) — see
--     10_payment_admin_rbac_seed.sql.
--
-- All inserts are idempotent: app_page/app_menu via NOT EXISTS-guarded
-- INSERT...SELECT.
-- ============================================================

-- ------------------------------------------------------------
-- 1) APP_PAGE — standard RBAC pair convention: non-form URL grants
--    READ, <base>/form URL grants CRUD/SUBMIT. Both /payment/form and
--    /payment/verify/form are virtual RBAC keys — no controller mapping
--    exists there, only the app_page row (same convention as
--    ApprovalChainController's /approval/chain/form).
-- ------------------------------------------------------------
INSERT INTO app_page (page_id, page_name, page_url, page_desc, active_flag, created_by, created_date)
SELECT 34, 'My Payments', '/payment', 'Requestor payment list (read)', 'Y', 'SYSTEM', SYSDATE
FROM dual WHERE NOT EXISTS (SELECT 1 FROM app_page WHERE page_url = '/payment');

INSERT INTO app_page (page_id, page_name, page_url, page_desc, active_flag, created_by, created_date)
SELECT 35, 'My Payments Detail', '/payment/form', 'View a payment item and upload proof of payment', 'Y', 'SYSTEM', SYSDATE
FROM dual WHERE NOT EXISTS (SELECT 1 FROM app_page WHERE page_url = '/payment/form');

INSERT INTO app_page (page_id, page_name, page_url, page_desc, active_flag, created_by, created_date)
SELECT 36, 'Payment Verification', '/payment/verify', 'Admin payment verification queue (read)', 'Y', 'SYSTEM', SYSDATE
FROM dual WHERE NOT EXISTS (SELECT 1 FROM app_page WHERE page_url = '/payment/verify');

INSERT INTO app_page (page_id, page_name, page_url, page_desc, active_flag, created_by, created_date)
SELECT 37, 'Payment Verification Detail', '/payment/verify/form', 'Approve/reject a submitted proof of payment', 'Y', 'SYSTEM', SYSDATE
FROM dual WHERE NOT EXISTS (SELECT 1 FROM app_page WHERE page_url = '/payment/verify/form');

-- ------------------------------------------------------------
-- 2) APP_MENU — leaf entries under the existing "SES" (20) and
--    "Form & Approval (Admin)" (21) groups from 07_menu_seed.sql.
--    Next free menu_id after that script's highest (21) is 22.
-- ------------------------------------------------------------
INSERT INTO app_menu (menu_id, menu_name, page_id, menu_icon, menu_order, active_flag, parent_menu_id, created_by, created_date)
SELECT 22, 'My Payments', p.page_id, 'bi-credit-card', 30, 'Y', 20, 'SYSTEM', SYSDATE
FROM app_page p WHERE p.page_url = '/payment'
AND NOT EXISTS (SELECT 1 FROM app_menu WHERE menu_name = 'My Payments' AND parent_menu_id = 20);

INSERT INTO app_menu (menu_id, menu_name, page_id, menu_icon, menu_order, active_flag, parent_menu_id, created_by, created_date)
SELECT 23, 'Payment Verification', p.page_id, 'bi-cash-stack', 60, 'Y', 21, 'SYSTEM', SYSDATE
FROM app_page p WHERE p.page_url = '/payment/verify'
AND NOT EXISTS (SELECT 1 FROM app_menu WHERE menu_name = 'Payment Verification' AND parent_menu_id = 21);

-- ------------------------------------------------------------
-- 3) Advance sequences past the highest seeded ID
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
