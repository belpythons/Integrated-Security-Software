-- ============================================================
-- SES — Scheduling module seed — Oracle
-- Run AFTER 15_scheduling_ddl.sql.
--
-- Contents:
--   1. APP_PAGE registration for the three scheduling screens
--      (page ids 39-41 — 13_payment_recap_seed.sql's Cash Inflow
--      Recap addition used 38, so 39 is next free).
--   2. Two APP_MENU leaf entries — "Scheduling Queue" (id 25) under
--      the existing "Form & Approval (Admin)" group (menu_id 21),
--      since only a scheduling manager works that operational
--      worklist; "Scheduling Calendar" (id 26) under the existing
--      "SES" group (menu_id 20) instead — unlike the queue, the
--      calendar is EVERY user's own view of their schedule (see
--      SchedulingController#calendar's isManager-scoped query), so it
--      belongs alongside "Pengajuan Baru", not the admin-only group.
--      (07_menu_seed.sql's highest menu id used was 21,
--      09_payment_seed.sql went to 23, so 25 is next free after that
--      group's 24 from 13_payment_recap_seed.sql).
--
-- NOT seeded here, on purpose (same convention as 09_payment_seed.sql):
--   * RBAC grants (app_role_page / app_role_menu) — see
--     17_scheduling_rbac_seed.sql.
--
-- All inserts are idempotent: app_page/app_menu via NOT EXISTS-guarded
-- INSERT...SELECT.
-- ============================================================

-- ------------------------------------------------------------
-- 1) APP_PAGE — standard RBAC pair convention: non-form URL grants
--    READ, <base>/form URL grants CRUD/SUBMIT. /scheduling/queue/form
--    is a virtual RBAC key covering create/reschedule/cancel/attend/
--    no-show on /scheduling/manage/{requestId}/{type} — same
--    convention as PaymentVerificationController's /payment/verify/form.
-- ------------------------------------------------------------
INSERT INTO app_page (page_id, page_name, page_url, page_desc, active_flag, created_by, created_date)
SELECT 39, 'Scheduling Queue', '/scheduling/queue', 'Admin queue of requests awaiting a schedule action (read)', 'Y', 'SYSTEM', SYSDATE
FROM dual WHERE NOT EXISTS (SELECT 1 FROM app_page WHERE page_url = '/scheduling/queue');

INSERT INTO app_page (page_id, page_name, page_url, page_desc, active_flag, created_by, created_date)
SELECT 40, 'Scheduling Manage', '/scheduling/queue/form', 'Set/reschedule/cancel a schedule and resolve attendance', 'Y', 'SYSTEM', SYSDATE
FROM dual WHERE NOT EXISTS (SELECT 1 FROM app_page WHERE page_url = '/scheduling/queue/form');

INSERT INTO app_page (page_id, page_name, page_url, page_desc, active_flag, created_by, created_date)
SELECT 41, 'Scheduling Calendar', '/scheduling/calendar', 'Month-grid calendar view — own schedule for a requestor, every schedule for a scheduling manager (read)', 'Y', 'SYSTEM', SYSDATE
FROM dual WHERE NOT EXISTS (SELECT 1 FROM app_page WHERE page_url = '/scheduling/calendar');

-- ------------------------------------------------------------
-- 2) APP_MENU — "Scheduling Queue" under "Form & Approval (Admin)"
--    (id 21, admin-only worklist); "Scheduling Calendar" under "SES"
--    (id 20, every user's own schedule view) — see header comment.
--    /scheduling/queue/form is a virtual action-grant URL,
--    intentionally NOT given its own menu entry (reached via the
--    "Manage" button on the queue page / by clicking a calendar
--    event), same convention as every other module's virtual /form
--    page.
-- ------------------------------------------------------------
INSERT INTO app_menu (menu_id, menu_name, page_id, menu_icon, menu_order, active_flag, parent_menu_id, created_by, created_date)
SELECT 25, 'Scheduling Queue', p.page_id, 'bi-calendar-check', 80, 'Y', 21, 'SYSTEM', SYSDATE
FROM app_page p WHERE p.page_url = '/scheduling/queue'
AND NOT EXISTS (SELECT 1 FROM app_menu WHERE menu_name = 'Scheduling Queue' AND parent_menu_id = 21);

INSERT INTO app_menu (menu_id, menu_name, page_id, menu_icon, menu_order, active_flag, parent_menu_id, created_by, created_date)
SELECT 26, 'My Schedule', p.page_id, 'bi-calendar3', 40, 'Y', 20, 'SYSTEM', SYSDATE
FROM app_page p WHERE p.page_url = '/scheduling/calendar'
AND NOT EXISTS (SELECT 1 FROM app_menu WHERE menu_name = 'My Schedule' AND parent_menu_id = 20);

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
