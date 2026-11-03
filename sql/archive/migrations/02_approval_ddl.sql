-- ============================================================
-- SES — Approval Chain & Runtime DDL — Oracle 11g / 12c+
-- Run as schema owner (e.g. ses_user), AFTER 01_app_tables.sql.
--
-- This script is RE-RUNNABLE: it drops existing SES approval
-- objects first, then recreates them.
--
-- Chains, steps, and assignees are fully GUI/DB-driven — see
-- ApprovalChainService. Nothing here is code-defined anymore.
-- A form is bound to a chain through SES_FORM_APPROVAL_MAP, keyed
-- by a form-owned form_code + match_key (never an enum value the
-- approval module has to know the meaning of).
--
-- NOTE on foreign keys:
--   * APP_USER / APP_POSITION_VIEW are NOT referenced by DB FK
--     constraints on purpose:
--       - APP_USER lives in the platform schema (shared library).
--       - APP_POSITION_VIEW is a READ-ONLY view in production,
--         owned by another schema/app. We never write to it.
--     Referential integrity is enforced in the application layer.
-- ============================================================

-- ---- Reset: drop existing SES approval tables & sequences ----
BEGIN
    FOR t IN (
        SELECT table_name FROM user_tables WHERE table_name IN (
            'SES_APPROVAL_HISTORY', 'SES_APPROVAL_TASK_ASSIGNEE',
            'SES_APPROVAL_TASK_ASSIGNEE_DEF', 'SES_APPROVAL_TASK_CHKP',
            'SES_APPROVAL_TASK',
            'SES_APPROVAL_INSTANCE', 'SES_FORM_APPROVAL_MAP', 'SES_FORM',
            'SES_APPROVAL_ASSIGNEE', 'SES_APPROVAL_STEP_TASK', 'SES_APPROVAL_STEP',
            'SES_APPROVAL_CHAIN', 'SES_USER_EXT'
        )
    ) LOOP
        EXECUTE IMMEDIATE 'DROP TABLE ' || t.table_name || ' CASCADE CONSTRAINTS PURGE';
    END LOOP;
END;
/
BEGIN
    FOR s IN (
        SELECT sequence_name FROM user_sequences WHERE sequence_name IN (
            'SEQ_SES_APPROVAL_CHAIN', 'SEQ_SES_APPROVAL_STEP',
            'SEQ_SES_APPROVAL_ASSIGNEE', 'SEQ_SES_APPROVAL_INSTANCE',
            'SEQ_SES_APPROVAL_TASK', 'SEQ_SES_APPROVAL_TASK_ASSIGNEE',
            'SEQ_SES_APPR_TASK_ASSIGNEE_DEF', 'SEQ_SES_APPROVAL_HISTORY',
            'SEQ_SES_USER_EXT', 'SEQ_SES_FORM', 'SEQ_SES_FORM_APPROVAL_MAP',
            'SEQ_SES_APPROVAL_STEP_TASK', 'SEQ_SES_APPROVAL_TASK_CHKP'
        )
    ) LOOP
        EXECUTE IMMEDIATE 'DROP SEQUENCE ' || s.sequence_name;
    END LOOP;
END;
/

-- ---- Sequences ----
CREATE SEQUENCE SEQ_SES_APPROVAL_CHAIN         START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;
CREATE SEQUENCE SEQ_SES_APPROVAL_STEP          START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;
CREATE SEQUENCE SEQ_SES_APPROVAL_ASSIGNEE      START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;
CREATE SEQUENCE SEQ_SES_APPROVAL_INSTANCE      START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;
CREATE SEQUENCE SEQ_SES_APPROVAL_TASK          START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;
CREATE SEQUENCE SEQ_SES_APPROVAL_TASK_ASSIGNEE START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;
CREATE SEQUENCE SEQ_SES_APPR_TASK_ASSIGNEE_DEF START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;
CREATE SEQUENCE SEQ_SES_APPROVAL_HISTORY       START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;
CREATE SEQUENCE SEQ_SES_USER_EXT               START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;
CREATE SEQUENCE SEQ_SES_FORM                   START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;
CREATE SEQUENCE SEQ_SES_FORM_APPROVAL_MAP      START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;
CREATE SEQUENCE SEQ_SES_APPROVAL_STEP_TASK     START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;
CREATE SEQUENCE SEQ_SES_APPROVAL_TASK_CHKP     START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;

-- ---- SES_APPROVAL_CHAIN ----
-- One row per approval chain. Fully GUI-managed: admins create, rename,
-- activate/deactivate, and delete chains (guarded — see
-- SES_FORM_APPROVAL_MAP.fk_ses_fam_chain and ApprovalChainService.delete).
-- chain_key is admin-entered on create, normalised UPPER(TRIM()), and
-- IMMUTABLE afterwards — SES_APPROVAL_INSTANCE keeps a string snapshot of
-- it, so changing it would orphan historical instances. Renames go through
-- chain_name instead.
CREATE TABLE ses_approval_chain (
    chain_id      NUMBER        NOT NULL,
    chain_key     VARCHAR2(100) NOT NULL,
    chain_name    VARCHAR2(200) NOT NULL,
    description   VARCHAR2(500),
    active_flag   CHAR(1) DEFAULT 'Y' NOT NULL,
    created_by    VARCHAR2(50),
    created_date  DATE,
    updated_by    VARCHAR2(50),
    updated_date  DATE,
    CONSTRAINT pk_ses_approval_chain PRIMARY KEY (chain_id),
    CONSTRAINT uq_ses_approval_chain_key UNIQUE (chain_key)
);

-- ---- SES_APPROVAL_STEP ----
-- Ordered steps of a chain. Fully GUI-managed: add/rename/reorder/delete.
--
-- uq_ses_approval_step is DEFERRABLE INITIALLY DEFERRED on purpose: a
-- reorder (or a mid-list insert/delete) renumbers every step's step_order
-- from the submitted array position in one transaction, which passes
-- through illegal intermediate duplicates before the final state is
-- reached. Deferring the check to COMMIT makes that legal; a violation
-- then surfaces as TransactionSystemException, not
-- DataIntegrityViolationException. See ApprovalChainService.createOrUpdate.
-- (11g note: deferrable constraints are supported since 8i. If a DBA
-- disallows them, the fallback is a two-phase renumber — negate every
-- step_order for the chain, flush, then assign the final 1..n, flush —
-- since negative values never collide with positive ones under the
-- immediate constraint.)
CREATE TABLE ses_approval_step (
    step_id     NUMBER        NOT NULL,
    chain_id    NUMBER        NOT NULL,
    step_order  NUMBER        NOT NULL,
    step_name   VARCHAR2(200) NOT NULL,
    created_by  VARCHAR2(50),
    created_date DATE,
    updated_by  VARCHAR2(50),
    updated_date DATE,
    CONSTRAINT pk_ses_approval_step PRIMARY KEY (step_id),
    CONSTRAINT uq_ses_approval_step UNIQUE (chain_id, step_order) DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT fk_ses_step_chain FOREIGN KEY (chain_id) REFERENCES ses_approval_chain(chain_id) ON DELETE CASCADE
);

-- ---- SES_APPROVAL_ASSIGNEE ----
-- WHO may approve a step. A step may hold MANY assignees, freely mixing
-- all four types; whoever acts first resolves the step (ANY semantics,
-- first-come-first-serve — no quorum/ALL mode).
--   assignee_type: POSITION | USER | REQUESTOR_MANAGER | REPORT_TO
--   POSITION          -> pos_id  (APP_POSITION_VIEW.POS_ID, no FK)
--   USER              -> user_id (APP_USER.USER_ID, no FK)
--   REQUESTOR_MANAGER -> dynamic resolution at approval time: section head
--                        (PB_SECTION), else department head (DEPTID), else
--                        up APP_POSITION_VIEW.SUPERIOR_ID. The requestor is
--                        excluded from every rung, so a section head
--                        escalates instead of approving their own request.
--                        See ApprovalResolverService.managerLadder.
--   REPORT_TO         -> dynamic: SES_USER_EXT.report_to_pos_id targeted
--                        directly, no manager-lookup/fallback chain
-- order_by is display order in the chain editor only; it has no bearing
-- on resolution (first ACTOR wins, not first row).
CREATE TABLE ses_approval_assignee (
    assignee_id   NUMBER        NOT NULL,
    step_id       NUMBER        NOT NULL,
    assignee_type VARCHAR2(20)  NOT NULL,
    pos_id        VARCHAR2(10),
    user_id       VARCHAR2(36),
    order_by      NUMBER DEFAULT 0 NOT NULL,
    created_by    VARCHAR2(50),
    created_date  DATE,
    updated_by    VARCHAR2(50),
    updated_date  DATE,
    CONSTRAINT pk_ses_approval_assignee PRIMARY KEY (assignee_id),
    CONSTRAINT ck_ses_assignee_type CHECK (assignee_type IN ('POSITION', 'USER', 'REQUESTOR_MANAGER', 'REPORT_TO')),
    CONSTRAINT fk_ses_assignee_step FOREIGN KEY (step_id) REFERENCES ses_approval_step(step_id) ON DELETE CASCADE
);

-- ---- SES_APPROVAL_STEP_TASK ----
-- Definition of a CHECKPOINT (gated task) required on a step before it can
-- be approved. Fully GUI-managed, sibling of SES_APPROVAL_ASSIGNEE — a step
-- with zero rows here has no gate (unchanged legacy behaviour). A step with
-- N rows requires all N checkpoints resolved (DONE or SKIPPED) before
-- Approve is allowed on that step; Reject is never gated.
--
-- checkpoint_key is an OPTIONAL free-text convention key, same pattern as
-- SES_FORM.form_code / SES_FORM_APPROVAL_MAP.match_key: no enum, no
-- compile-time dependency on any business module. A checkpoint left purely
-- manual (ticked by the approver) leaves this null. A checkpoint a business
-- module drives auto-completion for (e.g. a screening-schedule module
-- calling ApprovalService.completeCheckpointByKey) carries the key the
-- module and the chain admin agreed on by convention.
CREATE TABLE ses_approval_step_task (
    step_task_id   NUMBER        NOT NULL,
    step_id        NUMBER        NOT NULL,
    task_label     VARCHAR2(200) NOT NULL,
    checkpoint_key VARCHAR2(50),
    order_by       NUMBER DEFAULT 0 NOT NULL,
    created_by     VARCHAR2(50),
    created_date   DATE,
    updated_by     VARCHAR2(50),
    updated_date   DATE,
    CONSTRAINT pk_ses_approval_step_task PRIMARY KEY (step_task_id),
    CONSTRAINT fk_ses_step_task_step FOREIGN KEY (step_id) REFERENCES ses_approval_step(step_id) ON DELETE CASCADE
);

-- ---- SES_FORM ----
-- Registry of form modules that use the approval engine. Rows are
-- upserted at startup from every ApprovalFormDescriptor bean found on the
-- classpath (ApprovalFormRegistrySync) — the approval module has ZERO
-- compile-time dependency on any form module, only on that interface.
-- All columns below are descriptor-synced and therefore READ-ONLY in the
-- GUI: code always wins on the next boot.
--
-- active_flag='N' marks an ORPHAN — a row whose descriptor bean has
-- disappeared (module removed/disabled). Orphans are never auto-deleted:
-- their SES_FORM_APPROVAL_MAP rows are left intact so re-adding the module
-- restores configuration untouched. resolveChain() refuses to resolve for
-- an inactive form.
CREATE TABLE ses_form (
    form_id         NUMBER        NOT NULL,
    form_code       VARCHAR2(100) NOT NULL,
    form_name       VARCHAR2(200) NOT NULL,
    match_key_label VARCHAR2(200),
    active_flag     CHAR(1) DEFAULT 'Y' NOT NULL,
    last_seen_date  DATE,
    created_by      VARCHAR2(50),
    created_date    DATE,
    updated_by      VARCHAR2(50),
    updated_date    DATE,
    CONSTRAINT pk_ses_form PRIMARY KEY (form_id),
    CONSTRAINT uq_ses_form_code UNIQUE (form_code)
);

-- ---- SES_FORM_APPROVAL_MAP ----
-- Binds a form to a chain. match_key NULL = the form's DEFAULT chain; a
-- non-null match_key OVERRIDES the default for one routing value (e.g. one
-- request type). The routing value itself is never an approval-module
-- concept: it is whatever code the form module's OWN table uses (see
-- ApprovalFormDescriptor.matchKeys()) or free text entered by the admin.
--
-- fk_ses_fam_chain deliberately carries NO "ON DELETE" clause: it is the
-- DB-level backstop for the chain delete-guard in ApprovalChainService —
-- a chain referenced by a mapping cannot be hard-deleted even if the
-- service-layer check were bypassed.
--
-- A plain UNIQUE(form_code, match_key) would NOT stop two default rows
-- per form: Oracle treats NULLs as distinct in index key comparison. Use
-- a function-based unique index instead (11g-compatible).
CREATE TABLE ses_form_approval_map (
    map_id      NUMBER        NOT NULL,
    form_code   VARCHAR2(100) NOT NULL,
    match_key   VARCHAR2(50),
    chain_id    NUMBER        NOT NULL,
    active_flag CHAR(1) DEFAULT 'Y' NOT NULL,
    created_by  VARCHAR2(50),
    created_date DATE,
    updated_by  VARCHAR2(50),
    updated_date DATE,
    CONSTRAINT pk_ses_form_approval_map PRIMARY KEY (map_id),
    CONSTRAINT fk_ses_fam_form  FOREIGN KEY (form_code) REFERENCES ses_form(form_code),
    CONSTRAINT fk_ses_fam_chain FOREIGN KEY (chain_id)  REFERENCES ses_approval_chain(chain_id)
);
CREATE UNIQUE INDEX ux_ses_fam_key ON ses_form_approval_map (form_code, NVL(match_key, '~DEFAULT~'));

-- ---- SES_APPROVAL_INSTANCE ----
-- One approval process per business submission. References the business
-- record only by type+id (loosely coupled; the approval module never
-- joins module tables). Also snapshots which form/match_key/tier produced
-- the chain, for traceability on the detail screen.
CREATE TABLE ses_approval_instance (
    instance_id        NUMBER        NOT NULL,
    chain_id            NUMBER,                    -- snapshot pointer, NO FK: the chain may be
                                                     -- deactivated/renamed later without breaking this row
    chain_key          VARCHAR2(100) NOT NULL,
    chain_name          VARCHAR2(200) NOT NULL,
    form_code            VARCHAR2(100),
    match_key            VARCHAR2(50),
    resolution_tier      VARCHAR2(10),
    business_ref_type  VARCHAR2(100) NOT NULL,
    business_ref_id    NUMBER        NOT NULL,
    requestor_user_id  VARCHAR2(36)  NOT NULL,
    current_task_id    NUMBER,
    status             VARCHAR2(20) DEFAULT 'PENDING' NOT NULL,
    created_by         VARCHAR2(50),
    created_date       DATE,
    updated_by         VARCHAR2(50),
    updated_date       DATE,
    CONSTRAINT pk_ses_approval_instance PRIMARY KEY (instance_id),
    CONSTRAINT ck_ses_instance_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED')),
    CONSTRAINT ck_ses_instance_tier CHECK (resolution_tier IN ('OVERRIDE', 'DEFAULT'))
);

-- ---- SES_APPROVAL_TASK ----
-- Per-instance snapshot of one chain step.
CREATE TABLE ses_approval_task (
    task_id           NUMBER        NOT NULL,
    instance_id       NUMBER        NOT NULL,
    step_definition_id NUMBER,
    step_order        NUMBER        NOT NULL,
    step_name         VARCHAR2(200) NOT NULL,
    status            VARCHAR2(20) DEFAULT 'PENDING' NOT NULL,
    completed_by      VARCHAR2(36),
    completed_date    DATE,
    note              VARCHAR2(2000),
    CONSTRAINT pk_ses_approval_task PRIMARY KEY (task_id),
    CONSTRAINT uq_ses_approval_task UNIQUE (instance_id, step_order),
    CONSTRAINT ck_ses_task_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'SKIPPED')),
    CONSTRAINT fk_ses_task_instance FOREIGN KEY (instance_id) REFERENCES ses_approval_instance(instance_id) ON DELETE CASCADE
);

-- ---- SES_APPROVAL_TASK_CHKP ----
-- Per-instance FROZEN snapshot of SES_APPROVAL_STEP_TASK, same freeze
-- reasoning as SES_APPROVAL_TASK_ASSIGNEE_DEF: an admin editing/deleting a
-- step's checkpoints mid-flight must never retroactively change a running
-- instance. Copied into existence at ApprovalService#trigger(), one row per
-- SES_APPROVAL_STEP_TASK definition, status PENDING.
--
--   status: PENDING | DONE | SKIPPED
--     DONE    -> the task actually happened, either a manual tick
--                (source_ref NULL) or a business module reporting it via
--                ApprovalService#completeCheckpointByKey (source_ref set,
--                e.g. "SES_SCREENING_SCHEDULE:1234") — the distinction is
--                for tracking/audit, NOT for gating: both DONE and SKIPPED
--                clear the approve-gate identically.
--     SKIPPED -> explicit human override ("done outside the system, can't
--                prove it, proceed anyway") — note is required at the app
--                layer for this path, never for DONE.
--
-- Approve gate (ApprovalService#act): a task with ANY checkpoint still
-- PENDING cannot be approved. Reject is never gated by this table.
CREATE TABLE ses_approval_task_chkp (
    checkpoint_id  NUMBER        NOT NULL,
    task_id        NUMBER        NOT NULL,
    task_label     VARCHAR2(200) NOT NULL,
    checkpoint_key VARCHAR2(50),
    status         VARCHAR2(20) DEFAULT 'PENDING' NOT NULL,
    note           VARCHAR2(2000),
    source_ref     VARCHAR2(100),
    completed_by   VARCHAR2(36),
    completed_date DATE,
    CONSTRAINT pk_ses_approval_task_chkp PRIMARY KEY (checkpoint_id),
    CONSTRAINT ck_ses_checkpoint_status CHECK (status IN ('PENDING', 'DONE', 'SKIPPED')),
    CONSTRAINT fk_ses_checkpoint_task FOREIGN KEY (task_id) REFERENCES ses_approval_task(task_id) ON DELETE CASCADE
);

-- ---- SES_APPROVAL_TASK_ASSIGNEE_DEF ----
-- Per-task snapshot of the STEP'S ASSIGNEE DEFINITIONS (not the resolved
-- people), copied from SES_APPROVAL_ASSIGNEE at trigger time. This is
-- what keeps a running instance honest when an admin later edits or
-- deletes a step: activateTask() reads from here, never from the live
-- SES_APPROVAL_ASSIGNEE rows. Person/holder resolution still happens at
-- ACTIVATION time against this frozen configuration, so a POSITION still
-- resolves to whoever holds it *then*, REQUESTOR_MANAGER still traverses
-- the live org tree, and REPORT_TO still targets the live
-- SES_USER_EXT.report_to_pos_id — only the step's shape is frozen, not
-- the people.
CREATE TABLE ses_approval_task_assignee_def (
    task_assignee_def_id NUMBER        NOT NULL,
    task_id               NUMBER        NOT NULL,
    assignee_type         VARCHAR2(20)  NOT NULL,
    pos_id                VARCHAR2(10),
    user_id               VARCHAR2(36),
    order_by              NUMBER DEFAULT 0 NOT NULL,
    CONSTRAINT pk_ses_appr_task_assignee_def PRIMARY KEY (task_assignee_def_id),
    CONSTRAINT ck_ses_tadef_type CHECK (assignee_type IN ('POSITION', 'USER', 'REQUESTOR_MANAGER', 'REPORT_TO')),
    CONSTRAINT fk_ses_tadef_task FOREIGN KEY (task_id) REFERENCES ses_approval_task(task_id) ON DELETE CASCADE
);

-- ---- SES_APPROVAL_TASK_ASSIGNEE ----
-- Resolved assignees of a task (frozen snapshot).
--   empl_id           = the resolved person (holder of the position,
--                       or the direct user's username/empl id)
--   resolution_source = STATIC_POSITION | STATIC_USER |
--                       DYNAMIC_MANAGER | REPORT_TO_FALLBACK |
--                       NO_POSITION_FALLBACK | MANAGER_SKIP
CREATE TABLE ses_approval_task_assignee (
    task_assignee_id  NUMBER        NOT NULL,
    task_id           NUMBER        NOT NULL,
    assignee_type     VARCHAR2(20)  NOT NULL,
    pos_id            VARCHAR2(10),
    user_id           VARCHAR2(36),
    empl_id           VARCHAR2(20),
    display_name      VARCHAR2(200),
    email             VARCHAR2(100),
    resolution_source VARCHAR2(30),
    CONSTRAINT pk_ses_approval_task_assignee PRIMARY KEY (task_assignee_id),
    CONSTRAINT fk_ses_task_assignee_task FOREIGN KEY (task_id) REFERENCES ses_approval_task(task_id) ON DELETE CASCADE
);

-- ---- SES_APPROVAL_HISTORY ----
-- Append-only audit trail of every action on an approval instance.
CREATE TABLE ses_approval_history (
    history_id      NUMBER        NOT NULL,
    instance_id     NUMBER        NOT NULL,
    task_id         NUMBER,
    action          VARCHAR2(20)  NOT NULL,
    actor_user_id   VARCHAR2(36),
    actor_empl_id   VARCHAR2(20),
    from_step_order NUMBER,
    to_step_order   NUMBER,
    note            VARCHAR2(2000),
    created_date    DATE          NOT NULL,
    CONSTRAINT pk_ses_approval_history PRIMARY KEY (history_id),
    CONSTRAINT ck_ses_history_action CHECK (action IN ('START', 'APPROVE', 'REJECT', 'SKIP', 'ADVANCE', 'COMPLETE', 'CANCEL', 'TASK_COMPLETE', 'TASK_SKIP')),
    CONSTRAINT fk_ses_history_instance FOREIGN KEY (instance_id) REFERENCES ses_approval_instance(instance_id) ON DELETE CASCADE
);

-- ---- SES_USER_EXT ----
-- SES-owned extension of APP_USER (platform table, read-only here).
-- Holds the "report to who" position used as fallback for dynamic
-- (REQUESTOR_MANAGER) resolution and for external/local users with
-- no position in APP_POSITION_VIEW.
CREATE TABLE ses_user_ext (
    user_id         VARCHAR2(36) NOT NULL,
    report_to_pos_id VARCHAR2(10),
    created_by      VARCHAR2(50),
    created_date    DATE,
    updated_by      VARCHAR2(50),
    updated_date    DATE,
    CONSTRAINT pk_ses_user_ext PRIMARY KEY (user_id)
);

-- ---- Indexes ----
CREATE INDEX idx_ses_assignee_step    ON ses_approval_assignee(step_id);
CREATE INDEX idx_ses_step_chain       ON ses_approval_step(chain_id);
CREATE INDEX idx_ses_step_task_step   ON ses_approval_step_task(step_id);
CREATE INDEX idx_ses_checkpoint_task  ON ses_approval_task_chkp(task_id);
CREATE INDEX idx_ses_fam_form         ON ses_form_approval_map(form_code);
CREATE INDEX idx_ses_fam_chain        ON ses_form_approval_map(chain_id);
CREATE INDEX idx_ses_instance_ref     ON ses_approval_instance(business_ref_type, business_ref_id);
CREATE INDEX idx_ses_instance_chain   ON ses_approval_instance(chain_key);
CREATE INDEX idx_ses_instance_status  ON ses_approval_instance(status);
CREATE INDEX idx_ses_instance_form    ON ses_approval_instance(form_code, match_key);
CREATE INDEX idx_ses_task_instance    ON ses_approval_task(instance_id);
CREATE INDEX idx_ses_task_status      ON ses_approval_task(status);
CREATE INDEX idx_ses_tadef_task       ON ses_approval_task_assignee_def(task_id);
CREATE INDEX idx_ses_tassignee_task   ON ses_approval_task_assignee(task_id);
CREATE INDEX idx_ses_history_instance ON ses_approval_history(instance_id);
CREATE INDEX idx_ses_history_created  ON ses_approval_history(created_date);

COMMIT;
