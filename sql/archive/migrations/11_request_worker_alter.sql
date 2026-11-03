-- ============================================================
-- SES — ses_request_worker: badge / department / section columns
-- Run AFTER 03_request_ddl.sql (which creates + owns ses_request_worker).
--
-- Adds three free-text columns to ses_request_worker: badge (employee
-- badge/id no.), department and section — all optionally autofilled
-- client-side from APP_POSITION_VIEW (HOLDER_EMPL_ID, DEPT_DESC,
-- SECTION_DESC) but always plain free-text, never FKs (see
-- ses/sql/README.md "SES-owned tables" — APP_POSITION_VIEW integrity is
-- app-layer only). This is a plain ALTER TABLE ADD, same convention as
-- 01_app_file_alter.sql — does NOT drop or recreate the table
-- (03_request_ddl.sql's drop-first re-run behavior would be destructive
-- to any real submitted request data by the time this ships). Re-running
-- this script against a schema that already has the columns will fail
-- with ORA-01430; that's expected — it's a one-time alter, not meant to
-- be re-run once applied.
-- ============================================================

ALTER TABLE SES_REQUEST_WORKER ADD (
            badge      VARCHAR2(50),
            department VARCHAR2(200),
            section    VARCHAR2(200)
        );

--ALTER TABLE SES_REQUEST_WORKER DROP (
--            badge,
--            department,
--            section
--        );

COMMIT;
