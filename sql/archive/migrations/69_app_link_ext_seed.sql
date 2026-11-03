-- ============================================================
-- SES — Seed APP_LINK_EXT Parameter — Oracle
-- Run as schema owner, AFTER 37_mail_parameter_seed.sql.
--
-- ------------------------------------------------------------
-- MENGAPA SKRIP INI ADA
-- ------------------------------------------------------------
-- The external-facing public pages (barcode verification, the contractor
-- document-upload link in RequestMailService / RequestQrCodeService) read
-- APP_LINK_EXT first, falling back to APP_LINK, so that a link scanned or
-- opened by an external device points at the publicly reachable gateway
-- address rather than an internal-only intranet one.
--
-- ⚠️ CHANGE THE VALUE BELOW before relying on it — the placeholder here is
-- NOT a real address. If this deployment has no separate public-facing
-- gateway/DMZ distinct from APP_LINK, set this to the same value as
-- APP_LINK instead of leaving the placeholder.
--
-- Idempotent: re-running just re-asserts the value.
--
-- ⚠️ CATATAN CACHE: AppParameterService me-load app_parameter ke memori
-- saat startup dan hanya me-reload saat ada pemanggilan save() di GUI
-- (/admin/parameter). Setelah menjalankan skrip ini, RESTART aplikasi agar
-- nilai baru terbaca.
-- ============================================================

MERGE INTO app_parameter t
USING (
    SELECT 'APP_LINK_EXT' AS param_id,
           'http://CHANGE-ME-EXTERNAL-GATEWAY' AS param_value,
           'External Upload Application Link' AS param_desc,
           'SYSTEM' AS updated_by
      FROM dual
) s
ON (t.param_id = s.param_id)
WHEN MATCHED THEN
    UPDATE SET t.param_desc  = s.param_desc,
               t.updated_by  = s.updated_by,
               t.updated_date = SYSTIMESTAMP,
               t.param_value = NVL(t.param_value, s.param_value)
WHEN NOT MATCHED THEN
    INSERT (param_id, param_value, param_desc, updated_by, updated_date)
    VALUES (s.param_id, s.param_value, s.param_desc, s.updated_by, SYSTIMESTAMP);

COMMIT;

-- Verifikasi:
-- SELECT param_id, param_value FROM app_parameter WHERE param_id = 'APP_LINK_EXT';
