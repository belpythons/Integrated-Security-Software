-- ============================================================
-- SES — Admin RBAC Seed — Oracle
-- Run AFTER 04_seed.sql (needs app_page ids 19-32 and app_menu id 14
-- to already exist) and after the platform seed (needs ADMIN/SYSADM
-- roles in app_role).
--
-- 04_seed.sql deliberately does NOT grant app_role_page /
-- app_role_menu for the SES pages/menu it registers (see its header
-- comment) — the platform's own RBAC seed only ran against the pages
-- that existed at that time (ids 1-18), so SES pages 19-32 and menu
-- 14 ("New Request") were never granted to ADMIN/SYSADM. This script
-- closes that gap, following the same full-access pattern platform
-- used for ADMIN/SYSADM in 02_seed_data.sql.
--
-- Idempotent: NOT EXISTS-guarded per (role_id, page_id) /
-- (role_id, menu_id).
-- ============================================================

-- ---- APP_ROLE_PAGE — ADMIN/SYSADM get every SES page, all actions ----
INSERT INTO app_role_page (role_id, page_id, action_code, added_by, added_date)
SELECT r.role_id, p.page_id, 'READ,CREATE,UPDATE,DELETE,ADD,REMOVE,SUBMIT', 'SYSTEM', SYSDATE
FROM app_role r, app_page p
WHERE r.role_name IN ('ADMIN', 'SYSADM')
AND p.page_id BETWEEN 19 AND 32
AND NOT EXISTS (
    SELECT 1 FROM app_role_page rp
    WHERE rp.role_id = r.role_id AND rp.page_id = p.page_id
);

-- ---- APP_ROLE_MENU — ADMIN/SYSADM see the "New Request" menu entry ----
INSERT INTO app_role_menu (role_id, menu_id, added_by, added_date)
SELECT r.role_id, m.menu_id, 'SYSTEM', SYSDATE
FROM app_role r, app_menu m
WHERE r.role_name IN ('ADMIN', 'SYSADM')
AND m.menu_id = 14
AND NOT EXISTS (
    SELECT 1 FROM app_role_menu rm
    WHERE rm.role_id = r.role_id AND rm.menu_id = m.menu_id
);

-- ---- APP_ROLE_PAGE — every role gets its own worklist on "Approvals" (page 19) ----
-- Approval is now surfaced as a static navbar link, reachable by any
-- authenticated user (ApprovalController scopes rows to the current
-- username). ADMIN/SYSADM already have the fuller action set from the
-- BETWEEN 19-32 grant above; the NOT EXISTS guard means this only fills
-- in the other roles, it never downgrades ADMIN/SYSADM.
INSERT INTO app_role_page (role_id, page_id, action_code, added_by, added_date)
SELECT r.role_id, 19, 'READ,SUBMIT', 'SYSTEM', SYSDATE
FROM app_role r
WHERE NOT EXISTS (
    SELECT 1 FROM app_role_page rp
    WHERE rp.role_id = r.role_id AND rp.page_id = 19
);

-- ---- APP_PAGE — Switch User (com.badaklng.switchuser) ----
-- Live grant (unlike mansuite's commented-out draft) — ses actively enables
-- this feature. Single READ action code; no app_menu row (top navbar link,
-- not sidebar). page_id 33 is the next free id after 04_seed.sql's 19-32
-- block.
INSERT INTO app_page (page_id, page_name, page_url, page_desc, active_flag, created_by, created_date)
SELECT 33, 'Switch User', '/switchuser', 'Impersonate another active user', 'Y', 'SYSTEM', SYSDATE
FROM dual
WHERE NOT EXISTS (SELECT 1 FROM app_page WHERE page_url = '/switchuser');

INSERT INTO app_role_page (role_id, page_id, action_code, added_by, added_date)
SELECT r.role_id, p.page_id, 'READ', 'SYSTEM', SYSDATE
FROM app_role r, app_page p
WHERE r.role_name IN ('ADMIN', 'SYSADM')
AND p.page_url = '/switchuser'
AND NOT EXISTS (
    SELECT 1 FROM app_role_page rp
    WHERE rp.role_id = r.role_id AND rp.page_id = p.page_id
);

-- ---- Advance SEQ_APP_PAGE past the highest seeded ID (04_seed.sql already
-- advanced it to 33 based on its own max of 32 — the page_id 33 insert
-- above needs a re-advance so future NEXTVAL calls don't collide) ----
DECLARE v_max NUMBER;
BEGIN
    SELECT NVL(MAX(page_id), 0) + 1 INTO v_max FROM app_page;
    EXECUTE IMMEDIATE 'DROP SEQUENCE SEQ_APP_PAGE';
    EXECUTE IMMEDIATE 'CREATE SEQUENCE SEQ_APP_PAGE START WITH ' || v_max || ' INCREMENT BY 1 NOCACHE NOCYCLE';
END;
/

COMMIT;
