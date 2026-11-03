-- ============================================================
-- SES — Menu Reorg + RBAC Gap Sync — Oracle
-- Run AFTER 29_dashboard_history_seed.sql.
--
-- ELLDEV was hand-edited via the Menu Management GUI (/admin/menu)
-- after 06/07/10/17/28/29 already ran, diverging from what those
-- scripts alone would produce on a fresh environment. Found by
-- connecting to ELLDEV (SID) directly and diffing app_menu/app_role_menu
-- against disk on 2026-08-27. This script codifies that live state so
-- re-running the full chain on a NEW environment reproduces it:
--
--   1. Menu 21 renamed "Form & Approval (Admin)" -> "SES (Admin)".
--   2. New group menu 31 "SES Form Configuration" (page_id NULL,
--      parent NULL) split out of menu 21 — now holds the four
--      form/workflow-config screens: Approval Chains (16), Approval
--      Form Mapping (17), Konfigurasi Jenis Pengajuan (19, formerly
--      "Master - Jenis Pengajuan"), Konfigurasi Jenis Sticker (28).
--      Menu 21 keeps the operational-admin screens: Verifikasi
--      Pembayaran (23), Penjadwalan (25), Pemasukan Kas (24), Riwayat
--      (29).
--   3. menu_order renumbered to small sequential values per group
--      (top-level groups 1-4; each group's children 1-5) instead of
--      the original tens-spaced values — cosmetic, done via the GUI.
--   4. REQUESTOR was manually granted app_role_menu for 14 (Pengajuan
--      Baru), 20 (SES group), 22 (Pembayaran) — filling a real gap:
--      19_requestor_role_seed.sql / 10_payment_admin_rbac_seed.sql
--      already grant REQUESTOR the underlying app_page rows (27/28,
--      34/35) but never the app_menu rows needed to reach them from
--      the sidebar. Backfilled here.
--
--   5. REQUESTOR's action_code on pages 19/20 (/approval,
--      /approval/form) reverted from an ad-hoc manual grant of
--      'READ,CREATE,UPDATE,DELETE,ADD,REMOVE,SUBMIT' (added_by='admin',
--      2026-08-27) back to the intended baseline READ,SUBMIT — same
--      as every other role gets on page 19 via 06_admin_rbac_seed.sql.
--      Note page 20's action_code no longer gates anything: the SUBMIT
--      check on approve/reject/checkpoint actions was removed from
--      ApprovalController (see below), so this is cosmetic/hygiene,
--      not a security fix by itself.
--
-- ALSO CHANGED (code, not SQL): ApprovalController's approve/reject/
-- checkpoint-complete/checkpoint-skip endpoints used to call
-- pageAccessGuard.requireAction(ApprovalTaskForm.FORM_URL, "SUBMIT")
-- (page 20) before acting. Removed at the user's request so every user
-- can reach the approval action endpoints regardless of RBAC grant.
-- This is safe because ApprovalService.act() independently and
-- unconditionally checks resolverService.canAct(task, username, userId)
-- and throws SecurityException if the caller is not an actual assignee
-- of the current step — that check was always the real authorization,
-- the RBAC page grant was only a coarse pre-check.
--
-- NOT touched by this script (flagged for review, not auto-fixed):
--   * ADMIN and SYSADM may diverge on menu 31 depending on what has been
--     granted manually via the GUI on this environment — this script
--     grants menu 31 to ADMIN only, on purpose, not backfilling SYSADM.
--     If SYSADM should also see "SES Form Configuration", grant it
--     separately.
--   * VIEWER holding app_role_menu for 26 (Jadwal) and 30 (Dashboard)
--     without their parent group 20 (SES) is a known ELLDEV divergence,
--     left as-is here too.
--
-- Idempotent: renames/reparents/reorders are plain UPDATEs (safe to
-- re-run, same convention as 07_menu_seed.sql's menu-14 reparent);
-- inserts are NOT EXISTS-guarded per row.
-- ============================================================

SET DEFINE OFF;

-- ---- 1) Rename menu 21 ----
UPDATE app_menu SET menu_name = 'SES (Admin)', updated_by = 'SYSTEM', updated_date = SYSDATE
WHERE menu_id = 21 AND menu_name != 'SES (Admin)';

-- ---- 2) New group "SES Form Configuration" ----
INSERT INTO app_menu (menu_id, menu_name, page_id, menu_icon, menu_order, active_flag, parent_menu_id, created_by, created_date)
SELECT 31, 'SES Form Configuration', NULL, 'bi bi-gear-fill', 4, 'Y', NULL, 'SYSTEM', SYSDATE
FROM dual WHERE NOT EXISTS (SELECT 1 FROM app_menu WHERE menu_name = 'SES Form Configuration' AND parent_menu_id IS NULL);

-- ---- 3) Reparent the four form/workflow-config leaves onto menu 31 ----
UPDATE app_menu SET parent_menu_id = 31, updated_by = 'SYSTEM', updated_date = SYSDATE
WHERE menu_id IN (16, 17, 19, 28) AND parent_menu_id != 31;

-- ---- 4) menu_order sync (top-level groups) ----
UPDATE app_menu SET menu_order = 2, updated_by = 'SYSTEM', updated_date = SYSDATE WHERE menu_id = 20 AND menu_order != 2;
UPDATE app_menu SET menu_order = 3, updated_by = 'SYSTEM', updated_date = SYSDATE WHERE menu_id = 21 AND menu_order != 3;

-- ---- 5) menu_order sync ("SES" group children, parent 20) ----
UPDATE app_menu SET menu_order = 1, updated_by = 'SYSTEM', updated_date = SYSDATE WHERE menu_id = 30 AND menu_order != 1; -- Dashboard
UPDATE app_menu SET menu_order = 2, updated_by = 'SYSTEM', updated_date = SYSDATE WHERE menu_id = 14 AND menu_order != 2; -- Pengajuan Baru
UPDATE app_menu SET menu_order = 3, updated_by = 'SYSTEM', updated_date = SYSDATE WHERE menu_id = 27 AND menu_order != 3; -- Sticker Kendaraan
UPDATE app_menu SET menu_order = 4, updated_by = 'SYSTEM', updated_date = SYSDATE WHERE menu_id = 22 AND menu_order != 4; -- Pembayaran
UPDATE app_menu SET menu_order = 5, updated_by = 'SYSTEM', updated_date = SYSDATE WHERE menu_id = 26 AND menu_order != 5; -- Jadwal

-- ---- 6) menu_order sync ("SES (Admin)" group children, parent 21) ----
UPDATE app_menu SET menu_order = 1, updated_by = 'SYSTEM', updated_date = SYSDATE WHERE menu_id = 23 AND menu_order != 1; -- Verifikasi Pembayaran
UPDATE app_menu SET menu_order = 2, updated_by = 'SYSTEM', updated_date = SYSDATE WHERE menu_id = 25 AND menu_order != 2; -- Penjadwalan
UPDATE app_menu SET menu_order = 3, updated_by = 'SYSTEM', updated_date = SYSDATE WHERE menu_id = 24 AND menu_order != 3; -- Pemasukan Kas
UPDATE app_menu SET menu_order = 4, updated_by = 'SYSTEM', updated_date = SYSDATE WHERE menu_id = 29 AND menu_order != 4; -- Riwayat

-- ---- 7) Grant menu 31 to ADMIN only ----
INSERT INTO app_role_menu (role_id, menu_id, added_by, added_date)
SELECT r.role_id, 31, 'SYSTEM', SYSDATE
FROM app_role r
WHERE r.role_name = 'ADMIN'
AND NOT EXISTS (
    SELECT 1 FROM app_role_menu rm
    WHERE rm.role_id = r.role_id AND rm.menu_id = 31
);

-- ---- 8) Backfill REQUESTOR menu access for pages it already holds ----
INSERT INTO app_role_menu (role_id, menu_id, added_by, added_date)
SELECT r.role_id, m.menu_id, 'SYSTEM', SYSDATE
FROM app_role r, app_menu m
WHERE r.role_name = 'REQUESTOR'
AND m.menu_id IN (14, 20, 22)
AND NOT EXISTS (
    SELECT 1 FROM app_role_menu rm
    WHERE rm.role_id = r.role_id AND rm.menu_id = m.menu_id
);

-- ---- 9) Revert REQUESTOR's ad-hoc full-CRUD grant on Approval pages
-- back to the READ,SUBMIT baseline (page 19) / no baseline (page 20 —
-- deleted, no script anywhere grants it to a non-admin role) ----
UPDATE app_role_page SET action_code = 'READ,SUBMIT'
WHERE page_id = 19 AND role_id = (SELECT role_id FROM app_role WHERE role_name = 'REQUESTOR')
AND action_code != 'READ,SUBMIT';

DELETE FROM app_role_page
WHERE page_id = 20 AND role_id = (SELECT role_id FROM app_role WHERE role_name = 'REQUESTOR');

-- ---- Advance SEQ_APP_MENU past the highest seeded ID ----
DECLARE v_max NUMBER;
BEGIN
    SELECT NVL(MAX(menu_id), 0) + 1 INTO v_max FROM app_menu;
    EXECUTE IMMEDIATE 'DROP SEQUENCE SEQ_APP_MENU';
    EXECUTE IMMEDIATE 'CREATE SEQUENCE SEQ_APP_MENU START WITH ' || v_max || ' INCREMENT BY 1 NOCACHE NOCYCLE';
END;
/

COMMIT;
