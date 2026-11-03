-- ======================================================================
-- PT Badak NGL - Secure Entry System (SES)
-- Production deployment 261019 (19 Oktober 2026) — ELLPRD
--
-- Brings an ELLPRD schema that has every production script up to 47 to
-- what the current ses-app / ses-ext WARs map. Built by diffing ELLPRD
-- against ELLDEV (which runs this code) and against every @Entity in the
-- repository; 99_verify.sql re-checks that list at the end.
--
-- Scripts are the archived migrations (sql/archive/migrations), in their
-- original numbering so they stay diffable. Files marked [261019] in their
-- header were changed for production:
--   49, 53, 54  create-if-missing instead of drop-and-recreate
--   60          admin fallback looked up by username, not a seed UUID
--   61          status constraint swap guarded, so a re-run cannot fail on it
--   72          mail queue/log DELETEs removed
-- Deliberately NOT run:
--   39  APP_LINK on ELLPRD is already correct
--   47  ELLPRD already has this fix (a newer copy of it)
--   69  replaced by 80_app_link_ext.sql (69 only seeds a placeholder)
--
-- How to run (as the SES schema owner, from this directory):
--   sqlplus ses@//10.10.217.252:1521/ELLPRD @INSTALL-261019.sql
--
-- Every step is idempotent. It stops at the first error and writes
-- install_261019.log; fix the cause and run the whole file again.
-- Back up the schema first. Restart ses-app and ses-ext afterwards
-- (APP_PARAMETER is cached at startup).
-- ======================================================================

SET DEFINE OFF
SET SQLBLANKLINES ON
SET SERVEROUTPUT ON SIZE UNLIMITED
SET ECHO OFF
SET FEEDBACK ON
WHENEVER OSERROR EXIT FAILURE ROLLBACK
WHENEVER SQLERROR EXIT SQL.SQLCODE ROLLBACK

SPOOL install_261019.log

PROMPT === 00 Precheck ===
@@00_precheck.sql

PROMPT === 48 Manager/procurement approval flags, auth_type ===
@@48_mgr_approval_and_auth_type_alter.sql
PROMPT === 49 Cash expense table ===
@@49_cash_expense_ddl.sql
PROMPT === 50 Cash expense pages, menu, RBAC ===
@@50_cash_expense_seed.sql
PROMPT === 52 Hybrid routing columns ===
@@52_hybrid_routing_alter.sql
PROMPT === 53 Task sign-off table ===
@@53_task_signoff_ddl.sql
PROMPT === 54 Request evaluator table ===
@@54_request_evaluator_ddl.sql
PROMPT === 58 Procurement supervisor step role ===
@@58_procurement_supervisor_role_alter.sql
PROMPT === 55 SES_APPROVAL_PENDING_VIEW (parallel-aware) ===
@@55_approval_pending_view_parallel.sql
PROMPT === 36 APP_APPROVAL_VIEW ===
@@36_app_approval_view_ses.sql
PROMPT === 60 Badge chains to 7-step shape ===
@@60_badge_chain_reshape_v2.sql
PROMPT === 61 Request email / upload token, REQ_EXPIRY job ===
@@61_request_email_and_upload_token_alter.sql
PROMPT === 63 Badge naming ===
@@63_badge_naming_alignment.sql
PROMPT === 64 External upload flag, configuration menus ===
@@64_ses_configuration_and_ext_upload.sql
PROMPT === 65 External upload staging tables ===
@@65_ext_upload_staging_tables.sql
PROMPT === 66 Paid types to payment chain ===
@@66_paid_types_to_payment_chain.sql
PROMPT === 67 Worker screening status ===
@@67_worker_screening_status.sql
PROMPT === 70 Worker passport support ===
@@70_worker_passport_support.sql
PROMPT === 71 Worker screening columns ===
@@71_worker_screening_columns.sql
PROMPT === 72 Riwayat top-level menu (no mail cleanup) ===
@@72_riwayat_user_access_grant.sql
PROMPT === 74 SECURITY checkpoint grants ===
@@74_officer_checkpoint_rbac_grant.sql

PROMPT === 80 APP_LINK_EXT ===
@@80_app_link_ext.sql
PROMPT === 90 SECURITY role aligned to ELLDEV ===
@@90_security_rbac_align.sql

PROMPT === Recompile ===
EXEC DBMS_UTILITY.COMPILE_SCHEMA(schema => USER, compile_all => FALSE);

PROMPT === 99 Verify ===
@@99_verify.sql

PROMPT === Deployment SES 261019 selesai. Restart ses-app dan ses-ext. ===
SPOOL OFF
EXIT
