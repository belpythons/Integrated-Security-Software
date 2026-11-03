-- 39_app_link_production.sql
-- Points approval mail (and the QR/verify link) at the real application,
-- not at localhost or the 37_mail_parameter_seed.sql placeholder.
--
-- Background: ApprovalMailService / RequestQrCodeService build their
-- absolute base URL from APP_LINK first, falling back to the Spring
-- property ses.app.base-url (committed value http://localhost:${server.port})
-- only if APP_LINK is blank.
--
-- The deployment context segment (e.g. /ses) is REQUIRED if this app has
-- one: it sets no server.servlet.context-path of its own — the context
-- comes from the WAR's deployment name on the application server, so an
-- absolute URL built outside a request has to carry it. No trailing slash
-- — the template strips one if present, but the seeded value should be
-- clean.
--
-- ⚠️ CHANGE THE VALUE BELOW to match wherever this schema is actually
-- deployed before running — the literal here (apps.badaklng.home:8005/ses)
-- is the address this application has been deployed to; it is not a
-- generic placeholder.
--
-- Idempotent: re-running just re-asserts the value.
--
-- NOTE ON CACHING: AppParameterService loads app_parameter into memory at
-- startup and only reloads on save(). Running this script does NOT take
-- effect until the application restarts. To change it live instead, edit
-- APP_LINK at /admin/parameter — that path calls save() and the new value
-- applies on the next queued mail / QR render.

UPDATE app_parameter
   SET param_value  = 'http://apps.badaklng.home:8005/ses',
       param_desc   = 'Application current link',
       updated_by   = 'SYSTEM',
       updated_date = SYSTIMESTAMP
 WHERE param_id = 'APP_LINK';

-- Safety net for a schema where 37_mail_parameter_seed.sql was never run.
INSERT INTO app_parameter (param_id, param_value, param_desc, updated_by, updated_date)
SELECT 'APP_LINK', 'http://apps.badaklng.home:8005/ses', 'Application current link',
       'SYSTEM', SYSTIMESTAMP
  FROM dual
 WHERE NOT EXISTS (SELECT 1 FROM app_parameter WHERE param_id = 'APP_LINK');

COMMIT;

-- Verify:
-- SELECT param_id, param_value FROM app_parameter WHERE param_id = 'APP_LINK';
