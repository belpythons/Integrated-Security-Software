-- ============================================================
-- SES — Scheduling seed reset — Oracle
-- One-off corrective script. Run ONLY if you already applied an
-- earlier version of 16_scheduling_seed.sql / 17_scheduling_rbac_seed.sql
-- (menu 26 named 'Scheduling Calendar' under parent 21, admin-only
-- RBAC) BEFORE the calendar-for-everyone rework (menu 26 renamed
-- 'My Schedule', reparented under 20, opened READ-only to every role).
--
-- Re-running the current 16/17 as-is would fail: 16's app_menu insert
-- uses a literal menu_id (26) that already exists with the old
-- name/parent, so it collides on primary key instead of being skipped
-- by its NOT EXISTS guard (which checks the NEW name/parent, not the
-- old row).
--
-- Deletes ONLY the exact rows 16_scheduling_seed.sql /
-- 17_scheduling_rbac_seed.sql created — app_page ids 39-41, app_menu
-- ids 25-26, and their app_role_page / app_role_menu grants. Every
-- other row in these shared platform tables (every other module's
-- pages/menus/grants) is untouched — this is NOT a TRUNCATE.
--
-- After running this, re-run 15_scheduling_ddl.sql (if you also want
-- a clean ses_request_schedule/history — optional, that script is
-- already independently re-runnable), then 16_scheduling_seed.sql,
-- then 17_scheduling_rbac_seed.sql.
--
-- Safe to re-run this reset script itself — plain DELETE ... WHERE,
-- no-ops once the rows are gone.
-- ============================================================

-- ---- Children first (FK to app_menu / app_page) ----
--DELETE FROM app_role_menu WHERE menu_id IN (25, 26);
--DELETE FROM app_role_page WHERE page_id BETWEEN 39 AND 41;

-- ---- Then the menu/page rows themselves ----
--DELETE FROM app_menu WHERE menu_id IN (25, 26);
--DELETE FROM app_page WHERE page_id BETWEEN 39 AND 41;

COMMIT;
