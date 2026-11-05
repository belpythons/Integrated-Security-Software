-- PT Badak NGL - Secure Entry System (SES)
-- Production deployment 261105 (05 November 2026) — Modul Baru & RBAC Hardening
--
-- Prerequisite: Deployment 261019, 261023, & 261029 already applied on target schema.
--
-- How to run (as the SES schema owner, from this directory):
--   sqlplus ses@//10.10.217.252:1521/ELLPRD @INSTALL-261105.sql
-- or on dev:
--   sqlplus ses/ses2026@//10.10.214.48:1521/ELLDEV @INSTALL-261105.sql

SET DEFINE OFF
SET SQLBLANKLINES ON
SET SERVEROUTPUT ON SIZE UNLIMITED
SET ECHO OFF
SET FEEDBACK ON
WHENEVER OSERROR EXIT FAILURE ROLLBACK
WHENEVER SQLERROR EXIT SQL.SQLCODE ROLLBACK

SPOOL install_261105.log

PROMPT === 00 Precheck: Validasi Prasyarat Baseline Skema ===
@@00_precheck.sql

PROMPT === 95 DDL Tabel Pendukung Modul Goods Entry Permit ===
@@95_goods_driver_ddl.sql

PROMPT === 96 Registrasi Halaman Modul Baru ke APP_PAGE ===
@@96_app_page_new_modules.sql

PROMPT === 97 Pengerasan Hak Akses Role (RBAC Hardening) & Penataan Akun ===
@@97_rbac_hardening_and_grants.sql

PROMPT === Recompile invalid objects if any ===
EXEC DBMS_UTILITY.COMPILE_SCHEMA(schema => USER, compile_all => FALSE);

PROMPT === 99 Verifikasi Menyeluruh Pasca-Deployment 261105 ===
@@99_verify.sql

PROMPT === Deployment SES 261105 Selesai dengan SUKSES. Restart ses-app. ===
SPOOL OFF
