-- ============================================================
-- SES — Hybrid approval routing: step roles, parallel groups, consensus rule
-- Oracle 11g / 12c+. Run AFTER 02_approval_ddl.sql and 48_mgr_approval_and_auth_type_alter.sql.
--
-- The approval engine was strictly serial: one PENDING task at a time
-- (ses_approval_instance.current_task_id), and the FIRST decision won the whole
-- task — several assignees on one step meant "anyone may", never "all must".
-- The Badge flow needs three things that shape cannot express:
--
--   Step 1  Staf Security        parallel broadcast, ALL must approve
--   Step 2  Manager Pemohon      \  both active at the same time,
--   Step 3  Manager Procurement  /  each skippable by its own request-type toggle
--   Step 4  Manager Security     serial, only after steps 2 and 3 settle
--
-- Three columns carry that, on ses_approval_step (the definition) and again on
-- ses_approval_task (the frozen copy taken at trigger()):
--
--   parallel_group   NUMBER       steps sharing a number activate together.
--                                 NULL = a group of its own = today's serial
--                                 behaviour, which is why NULL is the default.
--   completion_rule  VARCHAR2(10) ANY (today's behaviour, first decision wins)
--                                 or ALL (every ses_approval_task_signoff row
--                                 must approve). Default ANY.
--   step_role        VARCHAR2(30) WHICH well-known role this step plays, and
--                                 therefore which request-type toggle can skip
--                                 it. NULL = no role = never skipped.
--
-- WHY step_role IS A MARK, NOT A GUESS. The seeded chains put REQUESTOR_MANAGER
-- on the SAME step as a fixed USER = 'admin' fallback. A rule of "all assignees
-- are dynamic" would never match them, so the feature would be silently inert —
-- the most confusing possible failure mode. A rule of "any assignee is dynamic"
-- would throw away a substantive approver that merely happened to share the
-- step. Neither is salvageable, so the role is configured explicitly at
-- /approval/chain and the runtime only ever READS it.
--
-- NOTHING IS BACKFILLED ON ses_approval_task, deliberately. That is the whole
-- back-compat story: every in-flight instance keeps parallel_group IS NULL,
-- completion_rule 'ANY' and step_role NULL, which is precisely the serial,
-- first-decision-wins, never-skipped engine it was started under. No running
-- approval can change shape underneath the people working it.
--
-- NOTE on this production-fit copy: the CHECK constraint below is written
-- straight to its FINAL five-value shape (including PROCUREMENT_SUPERVISOR),
-- not the original four-value shape that 58_procurement_supervisor_role_alter.sql
-- widened on ELLDEV. There is nothing to migrate on a fresh schema — this
-- IS the fresh schema's starting shape. 58 is still ported separately for
-- numbering/diff parity with the dev script set, and is a guaranteed no-op
-- when run after this script (the columns/constraint already exist in their
-- final form).
--
-- This script does NOT create a signature_file_id column or any signature
-- table — that feature shipped briefly on ELLDEV and was withdrawn
-- 2026-10-06; see 53_task_signoff_ddl.sql's header for the full story.
--
-- Guarded with user_tab_columns — re-running is a no-op, not an ORA-01430.
-- ============================================================

-- ---- SES_APPROVAL_STEP: the definition ----
DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count FROM user_tab_columns
    WHERE table_name = 'SES_APPROVAL_STEP' AND column_name = 'STEP_ROLE';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE ses_approval_step ADD step_role VARCHAR2(30)';
        EXECUTE IMMEDIATE 'ALTER TABLE ses_approval_step ADD CONSTRAINT ck_ses_step_role'
                       || ' CHECK (step_role IS NULL OR step_role IN'
                       || ' (''SECURITY_STAFF'', ''PROCUREMENT_SUPERVISOR'', ''PROCUREMENT_MANAGER'','
                       || ' ''REQUESTOR_MANAGER'', ''SECURITY_MANAGER''))';
    END IF;

    SELECT COUNT(*) INTO v_count FROM user_tab_columns
    WHERE table_name = 'SES_APPROVAL_STEP' AND column_name = 'PARALLEL_GROUP';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE ses_approval_step ADD parallel_group NUMBER';
    END IF;

    SELECT COUNT(*) INTO v_count FROM user_tab_columns
    WHERE table_name = 'SES_APPROVAL_STEP' AND column_name = 'COMPLETION_RULE';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE ses_approval_step ADD completion_rule VARCHAR2(10) DEFAULT ''ANY'' NOT NULL';
        EXECUTE IMMEDIATE 'ALTER TABLE ses_approval_step ADD CONSTRAINT ck_ses_step_completion'
                       || ' CHECK (completion_rule IN (''ANY'', ''ALL''))';
    END IF;
END;
/

-- ---- SES_APPROVAL_TASK: the frozen copy ----
DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count FROM user_tab_columns
    WHERE table_name = 'SES_APPROVAL_TASK' AND column_name = 'STEP_ROLE';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE ses_approval_task ADD step_role VARCHAR2(30)';
        EXECUTE IMMEDIATE 'ALTER TABLE ses_approval_task ADD CONSTRAINT ck_ses_task_role'
                       || ' CHECK (step_role IS NULL OR step_role IN'
                       || ' (''SECURITY_STAFF'', ''PROCUREMENT_SUPERVISOR'', ''PROCUREMENT_MANAGER'','
                       || ' ''REQUESTOR_MANAGER'', ''SECURITY_MANAGER''))';
    END IF;

    SELECT COUNT(*) INTO v_count FROM user_tab_columns
    WHERE table_name = 'SES_APPROVAL_TASK' AND column_name = 'PARALLEL_GROUP';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE ses_approval_task ADD parallel_group NUMBER';
    END IF;

    SELECT COUNT(*) INTO v_count FROM user_tab_columns
    WHERE table_name = 'SES_APPROVAL_TASK' AND column_name = 'COMPLETION_RULE';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE ses_approval_task ADD completion_rule VARCHAR2(10) DEFAULT ''ANY'' NOT NULL';
        EXECUTE IMMEDIATE 'ALTER TABLE ses_approval_task ADD CONSTRAINT ck_ses_task_completion'
                       || ' CHECK (completion_rule IN (''ANY'', ''ALL''))';
    END IF;
END;
/

-- Supports advance(): "the lowest parallel_group of this instance that still
-- has a PENDING task", which is the single hottest query in the new engine.
DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count FROM user_indexes WHERE index_name = 'IDX_SES_TASK_GROUP';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'CREATE INDEX idx_ses_task_group ON ses_approval_task(instance_id, status, parallel_group)';
    END IF;
END;
/

COMMENT ON COLUMN ses_approval_step.step_role       IS 'Peran tahap: SECURITY_STAFF / PROCUREMENT_SUPERVISOR / PROCUREMENT_MANAGER / REQUESTOR_MANAGER / SECURITY_MANAGER; NULL = tanpa peran, tidak pernah dilewati';
COMMENT ON COLUMN ses_approval_step.parallel_group  IS 'Tahap bernomor sama aktif bersamaan; NULL = grup sendiri = serial';
COMMENT ON COLUMN ses_approval_step.completion_rule IS 'ANY = keputusan pertama menang; ALL = semua sign-off harus menyetujui';

COMMIT;

-- Rollback, for reference only — never run as part of a deploy.
--ALTER TABLE ses_approval_step DROP CONSTRAINT ck_ses_step_role;
--ALTER TABLE ses_approval_step DROP CONSTRAINT ck_ses_step_completion;
--ALTER TABLE ses_approval_step DROP (step_role, parallel_group, completion_rule);
--DROP INDEX idx_ses_task_group;
--ALTER TABLE ses_approval_task DROP CONSTRAINT ck_ses_task_role;
--ALTER TABLE ses_approval_task DROP CONSTRAINT ck_ses_task_completion;
--ALTER TABLE ses_approval_task DROP (step_role, parallel_group, completion_rule);
