-- ============================================================
-- SES — REQUESTOR role seed — Oracle
-- Run AFTER platform/sql/02_seed_data.sql (needs app_role -- ADMIN=1,
-- SYSADM=2, VIEWER=3, so this seeds id 4) and AFTER 04_seed.sql (needs
-- app_page ids 27/28 -- /request, /request/form).
--
-- Adds a baseline self-service role for anyone filing SES requests --
-- distinct from VIEWER (read-only) and ADMIN/SYSADM (full admin).
-- Grants READ,CREATE,SUBMIT on the request module only (pages 27/28).
--
-- Every other SES worklist page a requestor also needs (Approvals
-- 19/20, My Payments 34/35, My Schedule 41) is already granted to
-- "every role" by 06_admin_rbac_seed.sql / 10_payment_admin_rbac_seed.sql
-- / 17_scheduling_rbac_seed.sql, via a role-name-UNFILTERED
-- INSERT...SELECT FROM app_role r (no WHERE r.role_name IN (...)).
-- Those three scripts are idempotent (NOT EXISTS-guarded per
-- role_id/page_id) and safe to re-run — re-running them AFTER this
-- script backfills REQUESTOR into every one of those "every role"
-- grants automatically. No separate grant is written here for those
-- pages; re-run 06/10/17 to pick them up.
--
-- Idempotent: role insert guarded on role_name, page grant guarded on
-- (role_id, page_id).
-- ============================================================

INSERT INTO app_role (role_id, role_name, role_desc, active_flag, created_by, created_date)
SELECT 4, 'REQUESTOR', 'Baseline self-service requestor', 'Y', 'SYSTEM', SYSDATE
FROM dual
WHERE NOT EXISTS (SELECT 1 FROM app_role WHERE role_name = 'REQUESTOR');

INSERT INTO app_role_page (role_id, page_id, action_code, added_by, added_date)
SELECT r.role_id, p.page_id, 'READ,CREATE,SUBMIT', 'SYSTEM', SYSDATE
FROM app_role r, app_page p
WHERE r.role_name = 'REQUESTOR'
AND p.page_id IN (27, 28)
AND NOT EXISTS (
    SELECT 1 FROM app_role_page rp
    WHERE rp.role_id = r.role_id AND rp.page_id = p.page_id
);

-- ---- Advance SEQ_APP_ROLE past the newly seeded id, same convention
-- as 06_admin_rbac_seed.sql's SEQ_APP_PAGE re-advance ----
DECLARE v_max NUMBER;
BEGIN
    SELECT NVL(MAX(role_id), 0) + 1 INTO v_max FROM app_role;
    EXECUTE IMMEDIATE 'DROP SEQUENCE SEQ_APP_ROLE';
    EXECUTE IMMEDIATE 'CREATE SEQUENCE SEQ_APP_ROLE START WITH ' || v_max || ' INCREMENT BY 1 NOCACHE NOCYCLE';
END;
/

COMMIT;
