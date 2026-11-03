-- ============================================================
-- SES — Per-type approval toggles + Auth Type visibility — Oracle 11g / 12c+
-- Run AFTER 02_approval_ddl.sql and 03_request_ddl.sql (which create and own
-- ses_approval_instance and ses_request_type).
--
-- Backs three product features on the Badge request type:
--   1. "Needs Approval Request Manager"    -> ses_request_type.needs_mgr_approval_flag
--   2. "Needs Approval Procurement Manager"-> ses_request_type.needs_proc_approval_flag
--   3. Auth Type (ALL / LOCAL / LDAP)      -> ses_request_type.auth_type
-- plus the two per-instance FREEZES that make a running approval immune to a
-- later edit of the request type:
--   ses_approval_instance.needs_mgr_approval_flag
--   ses_approval_instance.needs_proc_approval_flag
--
-- WHICH step each toggle switches off is NOT recorded here — that is
-- ses_approval_step.step_role, added by 52_hybrid_routing_alter.sql. The two
-- scripts are deliberately separate: this one is per-type configuration, that
-- one is the chain mechanism, and a site could in principle want the first
-- without the second (the toggles are then simply inert).
--
-- EVERY DEFAULT IS LOAD-BEARING. 'Y' on all four flags and 'ALL' on auth_type
-- leave every existing row behaving exactly as it does today: approvals keep
-- going through both managers, and every request type stays visible to every
-- account. That is also what makes the admin checkboxes render CHECKED on a
-- brand new type, which is the requested default.
--
-- Guarded with user_tab_columns, so re-running is a no-op rather than an
-- ORA-01430. That differs from the plain-ADD convention of
-- 01_app_file_alter.sql on purpose: these columns ship together with a code
-- change, and a half-applied schema is a much worse failure than a script
-- that can be run twice.
-- ============================================================

-- ---- SES_REQUEST_TYPE: the two toggles + auth type ----
-- auth_type restricts which app_user.auth_type may SEE and CHOOSE this type on
-- the new-request form. Enforced in the application layer only
-- (RequestTypeService.visibleTo / assertSelectable) — the same posture this
-- schema already takes for every APP_USER reference, which has no FK either.
-- The admin master screen is never filtered: an LDAP admin must still be able
-- to open and edit a LOCAL-only type.
DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count FROM user_tab_columns
    WHERE table_name = 'SES_REQUEST_TYPE' AND column_name = 'NEEDS_MGR_APPROVAL_FLAG';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE ses_request_type ADD needs_mgr_approval_flag CHAR(1) DEFAULT ''Y'' NOT NULL';
        EXECUTE IMMEDIATE 'ALTER TABLE ses_request_type ADD CONSTRAINT ck_ses_request_type_mgr CHECK (needs_mgr_approval_flag IN (''Y'', ''N''))';
    END IF;

    SELECT COUNT(*) INTO v_count FROM user_tab_columns
    WHERE table_name = 'SES_REQUEST_TYPE' AND column_name = 'NEEDS_PROC_APPROVAL_FLAG';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE ses_request_type ADD needs_proc_approval_flag CHAR(1) DEFAULT ''Y'' NOT NULL';
        EXECUTE IMMEDIATE 'ALTER TABLE ses_request_type ADD CONSTRAINT ck_ses_request_type_proc CHECK (needs_proc_approval_flag IN (''Y'', ''N''))';
    END IF;

    SELECT COUNT(*) INTO v_count FROM user_tab_columns
    WHERE table_name = 'SES_REQUEST_TYPE' AND column_name = 'AUTH_TYPE';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE ses_request_type ADD auth_type VARCHAR2(10) DEFAULT ''ALL'' NOT NULL';
        EXECUTE IMMEDIATE 'ALTER TABLE ses_request_type ADD CONSTRAINT ck_ses_request_type_auth CHECK (auth_type IN (''ALL'', ''LOCAL'', ''LDAP''))';
    END IF;
END;
/

-- ---- SES_APPROVAL_INSTANCE: the two freezes ----
-- Written once at ApprovalService#trigger(), never again. They record what the
-- business type required AT SUBMISSION TIME. Turning a toggle off tomorrow must
-- not retroactively skip a step of an approval that is already running, and
-- turning one on must not resurrect a step that was already passed over.
DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count FROM user_tab_columns
    WHERE table_name = 'SES_APPROVAL_INSTANCE' AND column_name = 'NEEDS_MGR_APPROVAL_FLAG';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE ses_approval_instance ADD needs_mgr_approval_flag CHAR(1) DEFAULT ''Y'' NOT NULL';
        EXECUTE IMMEDIATE 'ALTER TABLE ses_approval_instance ADD CONSTRAINT ck_ses_instance_mgr_appr CHECK (needs_mgr_approval_flag IN (''Y'', ''N''))';
    END IF;

    SELECT COUNT(*) INTO v_count FROM user_tab_columns
    WHERE table_name = 'SES_APPROVAL_INSTANCE' AND column_name = 'NEEDS_PROC_APPROVAL_FLAG';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE ses_approval_instance ADD needs_proc_approval_flag CHAR(1) DEFAULT ''Y'' NOT NULL';
        EXECUTE IMMEDIATE 'ALTER TABLE ses_approval_instance ADD CONSTRAINT ck_ses_instance_proc_appr CHECK (needs_proc_approval_flag IN (''Y'', ''N''))';
    END IF;
END;
/

COMMENT ON COLUMN ses_request_type.needs_mgr_approval_flag  IS 'Y = alur melewati tahap Manager Pemohon; N = tahap itu dilewati';
COMMENT ON COLUMN ses_request_type.needs_proc_approval_flag IS 'Y = alur melewati tahap Manager Procurement; N = tahap itu dilewati';
COMMENT ON COLUMN ses_request_type.auth_type                IS 'ALL / LOCAL / LDAP — siapa yang boleh melihat dan memilih jenis ini';

COMMIT;

-- Rollback, for reference only — never run as part of a deploy.
--ALTER TABLE ses_request_type DROP CONSTRAINT ck_ses_request_type_mgr;
--ALTER TABLE ses_request_type DROP CONSTRAINT ck_ses_request_type_proc;
--ALTER TABLE ses_request_type DROP CONSTRAINT ck_ses_request_type_auth;
--ALTER TABLE ses_request_type DROP (needs_mgr_approval_flag, needs_proc_approval_flag, auth_type);
--ALTER TABLE ses_approval_instance DROP CONSTRAINT ck_ses_instance_mgr_appr;
--ALTER TABLE ses_approval_instance DROP CONSTRAINT ck_ses_instance_proc_appr;
--ALTER TABLE ses_approval_instance DROP (needs_mgr_approval_flag, needs_proc_approval_flag);
