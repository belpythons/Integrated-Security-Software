-- PT Badak NGL - Secure Entry System (SES)
-- Production deployment 261023 (23 Oktober 2026) — ELLPRD
--
-- Prerequisite: Deployment 261019 already applied.
--
-- How to run (as the SES schema owner, from this directory):
--   sqlplus ses@//10.10.217.252:1521/ELLPRD @INSTALL-261023.sql

SET DEFINE OFF
SET SQLBLANKLINES ON
SET SERVEROUTPUT ON SIZE UNLIMITED
SET ECHO OFF
SET FEEDBACK ON
WHENEVER OSERROR EXIT FAILURE ROLLBACK
WHENEVER SQLERROR EXIT SQL.SQLCODE ROLLBACK

SPOOL install_261023.log

PROMPT === 00 Precheck 261023 ===
@@00_precheck.sql

PROMPT === 81 Teks user-facing ke APP_PARAMETER ===
@@81_ses_text_parameter_seed.sql

PROMPT === 82 SES_HISTORY_V (View Dasar) ===
@@82_ses_history_view.sql

PROMPT === 91 Sticker request type code & vehicle documents ===
@@91_sticker_request_type_code.sql

PROMPT === 92 Performance indexes ===
@@92_performance_indexes.sql

PROMPT === 93 SES_HISTORY_V stage resolution view ===
@@93_history_stage_resolution_view.sql

PROMPT === 94 Platform 1.4.7 alignment & payment index ===
@@94_platform_147_and_payment_index.sql

PROMPT === Recompile ===
EXEC DBMS_UTILITY.COMPILE_SCHEMA(schema => USER, compile_all => FALSE);

PROMPT === 99 Verify 261023 ===
@@99_verify.sql

PROMPT === Deployment SES 261023 selesai. Restart ses-app dan ses-ext. ===
SPOOL OFF
EXIT
