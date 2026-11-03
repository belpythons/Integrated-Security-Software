-- ============================================================
-- SES — Remaining Menu Seed — Oracle
-- Run AFTER 04_seed.sql (needs app_page ids 19-32 and app_menu id 14)
-- and AFTER 06_admin_rbac_seed.sql or before it — order between 06/07
-- doesn't matter, but both need 04_seed.sql first.
--
-- 04_seed.sql only added one top-level app_menu entry ("New Request",
-- id 14, -> page 27 /request, parent_menu_id NULL). Every other SES
-- screen (Approvals worklist, Approval Chains, Approval Form Mapping,
-- Request Type Master) was reachable only by typing the URL directly
-- — no menu entry existed. This script adds the missing entries,
-- grouped under two new parent/section menus (page_id NULL = group,
-- same convention as platform's "Administration" > "System Adm."
-- group in platform/sql/02_seed_data.sql):
--   * "SES" (menu_id 20)                  -> parent of "Pengajuan Baru"
--   * "Form & Approval (Admin)" (id 21)   -> parent of "Approval
--     Chains", "Approval Form Mapping", "Master - Jenis Pengajuan"
-- "New Request" (menu_id 14) already exists with parent_menu_id NULL
-- from 04_seed.sql, so it's reparented AND renamed to "Pengajuan Baru"
-- here via UPDATE rather than re-inserted. Virtual `/form` pages are
-- intentionally NOT given their own menu entry (reached via buttons
-- on their list page), same as menu 14 only links /request, never
-- /request/form.
--
-- Contract Master intentionally has NO menu entry — the New Request
-- flow's contract picker reads CONTRACT_V, a real view granted
-- read-only from another database; there is no admin GUI for it,
-- nor could there be one.
--
-- Idempotent: NOT EXISTS-guarded on (menu_name, parent_menu_id IS NULL)
-- for group inserts, matching 04_seed.sql's convention for "New
-- Request"; the reparenting UPDATE is naturally idempotent (re-running
-- it just sets the same parent_menu_id again).
-- ============================================================

-- "Approvals" worklist (/approval, page_id 19) intentionally has NO menu
-- entry — moved to a static top-navbar link (layout/navbar.html), same
-- convention mansuite already uses. Its app_page/RBAC grants are untouched.

-- ---- Parent/section groups (page_id NULL) ----
SET DEFINE OFF;
INSERT INTO app_menu (menu_id, menu_name, page_id, menu_icon, menu_order, active_flag, parent_menu_id, created_by, created_date)
SELECT 20, 'SES', NULL, 'bi-clipboard-check', 20, 'Y', NULL, 'SYSTEM', SYSDATE
FROM dual WHERE NOT EXISTS (SELECT 1 FROM app_menu WHERE menu_name = 'SES' AND parent_menu_id IS NULL);

INSERT INTO app_menu (menu_id, menu_name, page_id, menu_icon, menu_order, active_flag, parent_menu_id, created_by, created_date)
SELECT 21, 'Form & Approval (Admin)', NULL, 'bi-diagram-3', 30, 'Y', NULL, 'SYSTEM', SYSDATE
FROM dual WHERE NOT EXISTS (SELECT 1 FROM app_menu WHERE menu_name = 'Form & Approval (Admin)' AND parent_menu_id IS NULL);

-- ---- Reparent + rename "New Request" (inserted by 04_seed.sql with parent NULL,
-- name 'New Request') under "SES", displayed as "Pengajuan Baru" ----
UPDATE app_menu SET menu_name = 'Pengajuan Baru', parent_menu_id = 20, updated_by = 'SYSTEM', updated_date = SYSDATE
WHERE menu_id = 14;

-- ---- Leaf entries, grouped under "Form & Approval (Admin)" (id 21) ----
INSERT INTO app_menu (menu_id, menu_name, page_id, menu_icon, menu_order, active_flag, parent_menu_id, created_by, created_date)
SELECT 16, 'Approval Chains', p.page_id, 'bi-diagram-3', 40, 'Y', 21, 'SYSTEM', SYSDATE
FROM app_page p WHERE p.page_url = '/approval/chain'
AND NOT EXISTS (SELECT 1 FROM app_menu WHERE menu_name = 'Approval Chains' AND parent_menu_id = 21);

INSERT INTO app_menu (menu_id, menu_name, page_id, menu_icon, menu_order, active_flag, parent_menu_id, created_by, created_date)
SELECT 17, 'Approval Form Mapping', p.page_id, 'bi-signpost-split', 50, 'Y', 21, 'SYSTEM', SYSDATE
FROM app_page p WHERE p.page_url = '/approval/map'
AND NOT EXISTS (SELECT 1 FROM app_menu WHERE menu_name = 'Approval Form Mapping' AND parent_menu_id = 21);

INSERT INTO app_menu (menu_id, menu_name, page_id, menu_icon, menu_order, active_flag, parent_menu_id, created_by, created_date)
SELECT 19, 'Master - Jenis Pengajuan', p.page_id, 'bi-list-check', 70, 'Y', 21, 'SYSTEM', SYSDATE
FROM app_page p WHERE p.page_url = '/admin/request-type'
AND NOT EXISTS (SELECT 1 FROM app_menu WHERE menu_name = 'Master - Jenis Pengajuan' AND parent_menu_id = 21);

-- ---- Grant the new/changed menu entries to ADMIN/SYSADM (same gap 06_admin_rbac_seed.sql closed for menu 14) ----
INSERT INTO app_role_menu (role_id, menu_id, added_by, added_date)
SELECT r.role_id, m.menu_id, 'SYSTEM', SYSDATE
FROM app_role r, app_menu m
WHERE r.role_name IN ('ADMIN', 'SYSADM')
AND m.menu_id IN (16, 17, 19, 20, 21)
AND NOT EXISTS (
    SELECT 1 FROM app_role_menu rm
    WHERE rm.role_id = r.role_id AND rm.menu_id = m.menu_id
);

-- ---- Advance SEQ_APP_MENU past the highest seeded ID ----
DECLARE v_max NUMBER;
BEGIN
    SELECT NVL(MAX(menu_id), 0) + 1 INTO v_max FROM app_menu;
    EXECUTE IMMEDIATE 'DROP SEQUENCE SEQ_APP_MENU';
    EXECUTE IMMEDIATE 'CREATE SEQUENCE SEQ_APP_MENU START WITH ' || v_max || ' INCREMENT BY 1 NOCACHE NOCYCLE';
END;
/

COMMIT;
