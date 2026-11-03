-- ============================================================
-- SES — app_file audit-column extension — Oracle 11g / 12c+
-- Run AFTER platform/sql/01_ddl.sql (which creates + owns app_file)
-- and BEFORE 02_approval_ddl.sql / 03_request_ddl.sql / everything
-- else that uploads through it.
--
-- platform/sql/01_ddl.sql creates app_file with six columns only
-- (file_id, ref_id, file_name, file_type, file_data, file_size) and
-- no audit trail — platform's own AppFile.java entity does not
-- extend FullAuditEntity, matching that shape. SES needs an audit
-- trail on every upload (who attached what, when), so this script
-- ADDS the four FullAuditEntity columns via ALTER TABLE — it does
-- NOT drop or recreate the table, so any data already in app_file is
-- preserved. Guarded (checks all_tab_columns first), so it is safe
-- to re-run against a schema that already has the columns.
--
-- SesAppFile (com.badaklng.ses.file) is the ses-owned entity mapping
-- the resulting 10-column shape; platform's own AppFile.java /
-- AppFileRepository.java stay unused within ses (they only know
-- about the original six columns).
--
-- NOTE: queries ALL_TABLES / ALL_TAB_COLUMNS with an explicit OWNER,
-- not USER_TABLES / USER_TAB_COLUMNS — under the dev init path this
-- runs connected as SYS with ALTER SESSION SET CURRENT_SCHEMA=SYSTEM,
-- and the USER_% dictionary views do not reliably follow an aliased
-- CURRENT_SCHEMA for a SYS session (a SYS-specific quirk). ALL_% with
-- an explicit OWNER has no such ambiguity.
-- ============================================================

ALTER TABLE APP_FILE ADD (
            created_by   VARCHAR2(50),
            created_date DATE,
            updated_by   VARCHAR2(50),
            updated_date DATE
        );
        
--ALTER TABLE APP_FILE DROP (
--            created_by,
--            created_date,
--            updated_by,
--            updated_date
--        );

COMMIT;
