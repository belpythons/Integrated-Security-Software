-- ============================================================
-- SES — Payment Verification Admin RBAC Seed — Oracle
-- Run AFTER 09_payment_seed.sql (needs app_page ids 34-37 and
-- app_menu ids 22-23 to already exist) and after the platform seed
-- (needs ADMIN/SYSADM roles in app_role).
--
-- 09_payment_seed.sql deliberately does NOT grant app_role_page /
-- app_role_menu for the pages/menu it registers (see its header
-- comment) — this script closes that gap, following the same
-- full-access pattern 06_admin_rbac_seed.sql used for the request
-- module's pages.
--
-- Idempotent: NOT EXISTS-guarded per (role_id, page_id) /
-- (role_id, menu_id).
-- ============================================================

-- ---- APP_ROLE_PAGE — ADMIN/SYSADM get every payment page, all actions ----
INSERT INTO app_role_page (role_id, page_id, action_code, added_by, added_date)
SELECT r.role_id, p.page_id, 'READ,CREATE,UPDATE,DELETE,ADD,REMOVE,SUBMIT', 'SYSTEM', SYSDATE
FROM app_role r, app_page p
WHERE r.role_name IN ('ADMIN', 'SYSADM')
AND p.page_id BETWEEN 34 AND 37
AND NOT EXISTS (
    SELECT 1 FROM app_role_page rp
    WHERE rp.role_id = r.role_id AND rp.page_id = p.page_id
);

-- ---- APP_ROLE_MENU — ADMIN/SYSADM see both new menu entries ----
INSERT INTO app_role_menu (role_id, menu_id, added_by, added_date)
SELECT r.role_id, m.menu_id, 'SYSTEM', SYSDATE
FROM app_role r, app_menu m
WHERE r.role_name IN ('ADMIN', 'SYSADM')
AND m.menu_id IN (22, 23)
AND NOT EXISTS (
    SELECT 1 FROM app_role_menu rm
    WHERE rm.role_id = r.role_id AND rm.menu_id = m.menu_id
);

-- ---- APP_ROLE_PAGE — every role gets its own worklist on "My Payments" (page 34) ----
-- Any requestor may end up with a payment-required request, same
-- "everyone gets their own worklist" treatment 06_admin_rbac_seed.sql
-- gave the Approvals page (19). The detail/upload action page (35)
-- is NOT included here — SUBMIT on the upload action is opt-in per
-- role like every other write action, this only guarantees the list
-- is reachable.
INSERT INTO app_role_page (role_id, page_id, action_code, added_by, added_date)
SELECT r.role_id, 34, 'READ,SUBMIT', 'SYSTEM', SYSDATE
FROM app_role r
WHERE NOT EXISTS (
    SELECT 1 FROM app_role_page rp
    WHERE rp.role_id = r.role_id AND rp.page_id = 34
);
INSERT INTO app_role_page (role_id, page_id, action_code, added_by, added_date)
SELECT r.role_id, 35, 'READ,SUBMIT', 'SYSTEM', SYSDATE
FROM app_role r
WHERE NOT EXISTS (
    SELECT 1 FROM app_role_page rp
    WHERE rp.role_id = r.role_id AND rp.page_id = 35
);

COMMIT;
