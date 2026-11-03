-- 34_app_user_cleanse_from_emp_info_view.sql
-- Cleansing APP_USER against the active-employee master APP_EMP_INFO_VIEW:
--   - every active employee gets an app_user row: auth_type='LDAP',
--     active_flag='Y', username=EMPLID, full_name/email refreshed live.
--   - non-employee/system accounts (username not a 6-digit badge, e.g.
--     admin/sysadm/test/test2) are NEVER touched by this script.
--   - app_user rows that used to represent an employee but no longer match
--     an active EMPLID (left the company, or were dev-seed noise) are
--     soft-deactivated (active_flag='N'), never deleted -- ses_user_ext /
--     ses_approval_task_assignee / ses_approval_assignee all point at
--     app_user.user_id with no DB-level FK (see sql/README.md), so a hard
--     delete or a change of user_id would silently orphan those rows.
--
-- Idempotent / re-runnable: MERGE + guarded UPDATE, safe to run again after
-- APP_EMP_INFO_VIEW data changes.
--
-- New rows get a deterministic user_id following the convention already
-- used by this table's existing LDAP rows: '00000000-0000-0000-0001-' ||
-- badge zero-padded to 12 digits. Re-running this script recomputes the
-- same id for the same badge, so it never creates a duplicate row for the
-- same employee.

MERGE INTO app_user u
USING (
    SELECT TRIM(emplid) AS emplid, name_display, email
    FROM app_emp_info_view
    WHERE TRIM(emplid) IS NOT NULL
) e
ON (u.username = e.emplid)
WHEN MATCHED THEN UPDATE SET
    u.auth_type    = 'LDAP',
    u.active_flag  = 'Y',
    u.full_name    = NVL(e.name_display, u.full_name),
    u.email        = NVL(e.email, u.email),
    u.updated_by   = 'CLEANSE_34',
    u.updated_date = SYSDATE
WHEN NOT MATCHED THEN INSERT (
    user_id, username, full_name, email, auth_type, active_flag,
    created_by, created_date, updated_by, updated_date
) VALUES (
    '00000000-0000-0000-0001-' || LPAD(e.emplid, 12, '0'),
    e.emplid, e.name_display, e.email, 'LDAP', 'Y',
    'CLEANSE_34', SYSDATE, 'CLEANSE_34', SYSDATE
);

-- Soft-deactivate app_user rows that look like an employee badge (6-digit
-- username) but no longer match an active EMPLID in APP_EMP_INFO_VIEW.
-- Never touches non-badge usernames (admin/sysadm/test/test2/...).
UPDATE app_user u
SET u.active_flag  = 'N',
    u.updated_by   = 'CLEANSE_34',
    u.updated_date = SYSDATE
WHERE REGEXP_LIKE(u.username, '^[0-9]{6}$')
  AND u.active_flag = 'Y'
  AND NOT EXISTS (
      SELECT 1 FROM app_emp_info_view e
      WHERE TRIM(e.emplid) = u.username
  );

COMMIT;
