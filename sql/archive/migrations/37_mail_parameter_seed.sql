-- ============================================================
-- SES — Mail parameters in APP_PARAMETER (TESTING_MODE & friends)
--
-- Moves the mail dev-redirect switch out of Spring properties and into
-- APP_PARAMETER, editable live at /admin/parameter. Consumed by
-- com.badaklng.ses.mail.SesMailJob, which replaces the platform's MailJob
-- for schedule key 'MAIL' and therefore applies the redirect to EVERY
-- outbound mail, including the platform's own unhandled-exception mail.
--
-- Key names deliberately match the IT Suite (ITS) application's parameter
-- set so an operator who knows one app knows the other.
--
-- Semantics implemented by SesMailJob:
--   redirect when  TESTING_MODE = 'ON'  OR  REDIRECT_MAIL = 'YES'
--   banner   when  TESTING_MODE = 'ON'   (prefixed to the subject)
--   FAIL-CLOSED: if a redirect is called for but ADMIN_EMAIL is blank,
--   NOTHING is sent — the queue stays pending and an AppError row is
--   written.
--
-- Idempotent: re-running refreshes PARAM_DESC but never overwrites a
-- PARAM_VALUE an operator has since changed via /admin/parameter.
--
-- !! PRODUCTION DEFAULTS — this is NOT the dev seed. TESTING_MODE seeds as
-- !! 'OFF' here: a production schema carries real employee addresses from
-- !! day one (see 34_app_user_cleanse_from_emp_info_view.sql), so every
-- !! notification is meant to actually reach its recipient. ADMIN_EMAIL
-- !! seeds blank — SET IT at /admin/parameter before switching TESTING_MODE
-- !! back ON for any reason, or a misconfigured redirect will fail-closed
-- !! and silently stop every notification. APP_LINK below is a placeholder;
-- !! 39_app_link_production.sql sets the real deployed URL — adjust it for
-- !! wherever this schema is actually deployed.
--
-- IMPORTANT — AppParameterService caches the whole table in memory and
-- only reloads on save(). After running this script the app MUST be
-- restarted once. Later changes made through /admin/parameter take
-- effect immediately (that path calls save()); later changes made by
-- direct SQL do NOT.
--
-- Related: docs/implementasi-email-stage-1-testing-mode-parameter.md
-- ============================================================

MERGE INTO app_parameter t
USING (
    SELECT 'TESTING_MODE'         AS param_id,
           'OFF'                  AS param_value,   -- PRODUCTION default — do not set to ON without ADMIN_EMAIL configured
           'Testing mode notification display on e-mail' AS param_desc
      FROM dual
    UNION ALL
    SELECT 'TESTING_BANNER',
           '**TESTING MODE, PLEASE IGNORE**',
           'Testing message on e-mail'
      FROM dual
    UNION ALL
    SELECT 'REDIRECT_MAIL',
           'NO',
           '(YES / NO) Redirect All Mail to Admin'
      FROM dual
    UNION ALL
    -- CHANGE ME: set the real operator inbox before ever enabling a redirect.
    SELECT 'ADMIN_EMAIL',
           '',
           'Employee Email that assigned as Administrator'
      FROM dual
    UNION ALL
    -- CHANGE ME per environment. No trailing slash. Used for the
    -- "Lihat Pengajuan" button and the logo <img> inside approval mail.
    -- 39_app_link_production.sql overrides this to the real deployed URL.
    SELECT 'APP_LINK',
           'http://CHANGE-ME:8080',
           'Application current link'
      FROM dual
    UNION ALL
    SELECT 'APP_MAIL_SUBJ_PREFIX',
           '[SES]',
           'E-mail subject prefix'
      FROM dual
) s
ON (t.param_id = s.param_id)
WHEN MATCHED THEN
    UPDATE SET t.param_desc = s.param_desc,
               t.param_value = NVL(t.param_value, s.param_value)
WHEN NOT MATCHED THEN
    INSERT (param_id, param_value, param_desc, updated_by, updated_date)
    VALUES (s.param_id, s.param_value, s.param_desc, 'SYSTEM', SYSTIMESTAMP);

COMMIT;

-- Verify:
-- SELECT param_id, param_value, param_desc FROM app_parameter
--  WHERE param_id IN ('TESTING_MODE','TESTING_BANNER','REDIRECT_MAIL',
--                     'ADMIN_EMAIL','APP_LINK','APP_MAIL_SUBJ_PREFIX',
--                     'MAIL_FROM','MAIL_SERVER')
--  ORDER BY param_id;
