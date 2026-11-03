-- 41_history_admin_view_grant.sql
-- Splits /history (Riwayat) into an org-wide view and a requestor's own view.
--
-- Same shape and same reasoning as 40_dashboard_admin_view_grant.sql, one
-- page further: HistoryController lists every module's submissions only for a
-- user who holds SUBMIT on page /history, and narrows the list to the caller's
-- own submissions for everyone else. SUBMIT is meaningless on a read-only
-- reporting page, which is what makes it available as the flag.
--
-- app_role_page.action_code is a COMMA-SEPARATED LIST in a single row per
-- (role, page) -- not one row per action. Hence the string surgery below
-- rather than an INSERT.
--
-- Idempotent: a role that already has SUBMIT is left alone.
-- Requires no restart -- PageAccessGuard reads grants per request.

UPDATE app_role_page rp
   SET rp.action_code = rp.action_code || ',SUBMIT'
 WHERE rp.page_id = (SELECT page_id FROM app_page WHERE page_url = '/history')
   AND rp.role_id IN (SELECT role_id FROM app_role WHERE role_name IN ('ADMIN', 'SYSADM'))
   AND ',' || rp.action_code || ',' NOT LIKE '%,SUBMIT,%';

-- A role with no row for this page at all (29 grants READ to ADMIN and
-- SYSADM, but a hand-edited schema might not have).
INSERT INTO app_role_page (role_id, page_id, action_code, added_by, added_date)
SELECT r.role_id, p.page_id, 'READ,SUBMIT', 'SYSTEM', SYSDATE
  FROM app_role r
 CROSS JOIN (SELECT page_id FROM app_page WHERE page_url = '/history') p
 WHERE r.role_name IN ('ADMIN', 'SYSADM')
   AND NOT EXISTS (SELECT 1 FROM app_role_page rp
                    WHERE rp.role_id = r.role_id AND rp.page_id = p.page_id);

COMMIT;

-- Verify -- ADMIN and SYSADM must list SUBMIT, no other role may:
-- SELECT r.role_name, rp.action_code
--   FROM app_role_page rp
--   JOIN app_role r ON r.role_id = rp.role_id
--   JOIN app_page p ON p.page_id = rp.page_id
--  WHERE p.page_url = '/history' ORDER BY r.role_name;
