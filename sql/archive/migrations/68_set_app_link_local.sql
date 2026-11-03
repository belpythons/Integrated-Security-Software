-- ============================================================
-- SES — Set APP_LINK to local test instance — Oracle
-- Run as schema owner on local / test environment.
--
-- ------------------------------------------------------------
-- MENGAPA SKRIP INI ADA
-- ------------------------------------------------------------
-- Email tautan unggah dokumen (upload token link) dan email notifikasi
-- approval membaca parameter app_parameter.APP_LINK lebih dulu sebelum
-- fallback ke properti ses.app.base-url.
--
-- Nilai sebelumnya menunjuk ke http://apps.badaklng.home:8005/ses.
-- Skrip ini menyetel APP_LINK ke instance lokal:
-- http://localhost:8082
--
-- ⚠️ CATATAN CACHE:
-- AppParameterService me-load app_parameter ke memori saat startup
-- dan hanya me-reload saat ada pemanggilan save() di GUI (/admin/parameter).
-- Setelah menjalankan skrip ini, RESTART aplikasi agar nilai baru terbaca.
-- ============================================================

UPDATE app_parameter
   SET param_value  = 'http://localhost:8082',
       param_desc   = 'Application current link (Local Testing)',
       updated_by   = 'SYSTEM',
       updated_date = SYSTIMESTAMP
 WHERE param_id = 'APP_LINK';

INSERT INTO app_parameter (param_id, param_value, param_desc, updated_by, updated_date)
SELECT 'APP_LINK', 'http://localhost:8082', 'Application current link (Local Testing)',
       'SYSTEM', SYSTIMESTAMP
  FROM dual
 WHERE NOT EXISTS (SELECT 1 FROM app_parameter WHERE param_id = 'APP_LINK');

COMMIT;

-- Verifikasi:
-- SELECT param_id, param_value FROM app_parameter WHERE param_id = 'APP_LINK';
