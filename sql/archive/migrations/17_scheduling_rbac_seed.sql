-- ============================================================
-- SES — Scheduling RBAC Seed — Oracle
-- Run AFTER 16_scheduling_seed.sql (needs app_page ids 39-41 and
-- app_menu ids 25-26 to already exist) and after the platform seed
-- (needs ADMIN/SYSADM roles in app_role).
--
-- 16_scheduling_seed.sql deliberately does NOT grant app_role_page /
-- app_role_menu for the pages/menu it registers (see its header
-- comment) — this script closes that gap.
--
-- Unlike every other SES module screen, the calendar (page 41) and
-- the manage/detail page (page 40) are NOT admin-only: every user
-- needs to reach them read-only to see their own schedule (page 40
-- is also where a calendar event links to) — SchedulingController
-- scopes what a non-manager actually sees by ownership/hasAction, a
-- plain page grant doesn't do that scoping itself. Only the
-- SUBMIT action (create/reschedule/cancel/attend/no-show) and the
-- operational queue (page 39, menu 25) stay ADMIN/SYSADM-only.
--
-- Idempotent: NOT EXISTS-guarded per (role_id, page_id) /
-- (role_id, menu_id).
-- ============================================================

-- ---- APP_ROLE_PAGE — ADMIN/SYSADM get every scheduling page, all actions ----
INSERT INTO app_role_page (role_id, page_id, action_code, added_by, added_date)
SELECT r.role_id, p.page_id, 'READ,CREATE,UPDATE,DELETE,ADD,REMOVE,SUBMIT', 'SYSTEM', SYSDATE
FROM app_role r, app_page p
WHERE r.role_name IN ('ADMIN', 'SYSADM')
AND p.page_id BETWEEN 39 AND 41
AND NOT EXISTS (
    SELECT 1 FROM app_role_page rp
    WHERE rp.role_id = r.role_id AND rp.page_id = p.page_id
);

-- ---- APP_ROLE_PAGE — every role gets READ on the calendar (41) and the
-- manage/detail page (40), so any requestor can see their own schedule
-- via the calendar and click through to a detail page. NOT SUBMIT — only
-- a scheduling manager may create/reschedule/cancel/attend/no-show
-- (enforced both server-side via pageAccessGuard.requireAction and by
-- SchedulingController's ownership check on GET /scheduling/manage/**).
-- ADMIN/SYSADM already have a row from the block above, so this is a
-- no-op for them.
INSERT INTO app_role_page (role_id, page_id, action_code, added_by, added_date)
SELECT r.role_id, 40, 'READ', 'SYSTEM', SYSDATE
FROM app_role r
WHERE NOT EXISTS (
    SELECT 1 FROM app_role_page rp
    WHERE rp.role_id = r.role_id AND rp.page_id = 40
);
INSERT INTO app_role_page (role_id, page_id, action_code, added_by, added_date)
SELECT r.role_id, 41, 'READ', 'SYSTEM', SYSDATE
FROM app_role r
WHERE NOT EXISTS (
    SELECT 1 FROM app_role_page rp
    WHERE rp.role_id = r.role_id AND rp.page_id = 41
);

-- ---- APP_ROLE_MENU — "Scheduling Queue" (25) stays ADMIN/SYSADM-only ----
INSERT INTO app_role_menu (role_id, menu_id, added_by, added_date)
SELECT r.role_id, m.menu_id, 'SYSTEM', SYSDATE
FROM app_role r, app_menu m
WHERE r.role_name IN ('ADMIN', 'SYSADM')
AND m.menu_id = 25
AND NOT EXISTS (
    SELECT 1 FROM app_role_menu rm
    WHERE rm.role_id = r.role_id AND rm.menu_id = m.menu_id
);

-- ---- APP_ROLE_MENU — "My Schedule" (26) goes to every role ----
INSERT INTO app_role_menu (role_id, menu_id, added_by, added_date)
SELECT r.role_id, 26, 'SYSTEM', SYSDATE
FROM app_role r
WHERE NOT EXISTS (
    SELECT 1 FROM app_role_menu rm
    WHERE rm.role_id = r.role_id AND rm.menu_id = 26
);

COMMIT;
