-- PT Badak NGL - Secure Entry System (SES)
-- Production deployment 261029 (29 Oktober 2026) — Integrasi BIMA Approval Package
--
-- Prerequisite: Deployment 261019 & 261023 already applied (SES_APPROVAL_PENDING_VIEW exists).
--
-- How to run (as the SES schema owner, from this directory):
--   sqlplus ses@//10.10.217.252:1521/ELLPRD @INSTALL-261029.sql
-- or on dev:
--   sqlplus ses/ses2026@//10.10.214.48:1521/ELLDEV @INSTALL-261029.sql

SET DEFINE OFF
SET SQLBLANKLINES ON
SET SERVEROUTPUT ON SIZE UNLIMITED
SET ECHO OFF
SET FEEDBACK ON
WHENEVER OSERROR EXIT FAILURE ROLLBACK
WHENEVER SQLERROR EXIT SQL.SQLCODE ROLLBACK

SPOOL install_261029.log

PROMPT === 56 SES BIMA Approval View & PL/SQL Package ===
@@56_ses_bima_approval_pkg.sql

PROMPT === Recompile invalid objects if any ===
EXEC DBMS_UTILITY.COMPILE_SCHEMA(schema => USER, compile_all => FALSE);

PROMPT === 99 Verify BIMA Approval Package & View ===
@@99_verify_bima_pkg.sql

PROMPT === Deployment SES 261029 selesai. ===
SPOOL OFF
