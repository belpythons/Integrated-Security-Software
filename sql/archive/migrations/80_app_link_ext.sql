-- ============================================================
-- SES 261019 — 80_app_link_ext.sql
-- Replaces 69_app_link_ext_seed.sql, which only seeds a CHANGE-ME
-- placeholder. Sets APP_LINK_EXT to the public ses-ext gateway; contractor
-- upload links and QR verification URLs are built from it
-- (RequestMailService / RequestQrCodeService), falling back to APP_LINK
-- when it is blank.
--
-- APP_LINK itself is left untouched (69's companion 39 is not run).
--
-- AppParameterService caches APP_PARAMETER at startup: restart ses-app and
-- ses-ext after this runs, or edit the value at /admin/parameter instead.
-- Idempotent.
-- ============================================================

SET DEFINE OFF

MERGE INTO app_parameter t
USING (
    SELECT 'APP_LINK_EXT' AS param_id,
           'http://apps.badaklng.home:8005/ses-ext' AS param_value,
           'External Upload Application Link' AS param_desc
      FROM dual
) s
ON (t.param_id = s.param_id)
WHEN MATCHED THEN
    UPDATE SET t.param_value  = s.param_value,
               t.param_desc   = s.param_desc,
               t.updated_by   = 'SYSTEM',
               t.updated_date = SYSTIMESTAMP
WHEN NOT MATCHED THEN
    INSERT (param_id, param_value, param_desc, updated_by, updated_date)
    VALUES (s.param_id, s.param_value, s.param_desc, 'SYSTEM', SYSTIMESTAMP);

COMMIT;
