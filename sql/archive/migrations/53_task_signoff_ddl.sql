-- ============================================================
-- SES — Per-approver sign-offs (consensus steps) — Oracle 11g / 12c+
-- Run AFTER 02_approval_ddl.sql and 52_hybrid_routing_alter.sql.
--
-- Named differently from the dev script set on purpose: the dev archive's
-- 53_signoff_and_signature_ddl.sql also created ses_user_signature and a
-- signature_file_id column for a stored-digital-signature feature. That
-- feature shipped briefly on ELLDEV and was WITHDRAWN on 2026-10-06 —
-- confirmed live: ses_user_signature does not exist, and
-- ses_approval_task.signature_file_id does not exist, on ELLDEV today, and
-- no Java code anywhere in this repo references either. This production
-- script creates ONLY the table that survived: ses_approval_task_signoff.
--
-- ses_approval_task_signoff is the consensus mechanism of the Additional
-- Routing step — one row per person that step waits on — not part of the
-- signature feature, and the Badge flow cannot run without it.
--
-- RE-RUNNABLE drop-first (same convention as 27_sticker_ddl.sql) — the
-- table is new, so there is nothing to lose on a first apply.
-- ⚠️ AFTER THE FIRST RELEASE, re-running this script DESTROYS the
-- consensus state of every running approval. Do not re-run in a populated
-- environment.
--
-- Owned by com.badaklng.approval.
-- ============================================================

-- ---- Reset ----
BEGIN
    FOR t IN (SELECT table_name FROM user_tables
              WHERE table_name = 'SES_APPROVAL_TASK_SIGNOFF') LOOP
        EXECUTE IMMEDIATE 'DROP TABLE ' || t.table_name || ' CASCADE CONSTRAINTS PURGE';
    END LOOP;
END;
/
BEGIN
    FOR s IN (SELECT sequence_name FROM user_sequences
              WHERE sequence_name = 'SEQ_SES_APPR_TASK_SIGNOFF') LOOP
        EXECUTE IMMEDIATE 'DROP SEQUENCE ' || s.sequence_name;
    END LOOP;
END;
/

-- ---- Sequence ----
-- Name abbreviated to stay inside Oracle 11g's 30-character identifier limit,
-- the same reason SEQ_SES_APPR_TASK_ASSIGNEE_DEF is spelled the way it is.
CREATE SEQUENCE SEQ_SES_APPR_TASK_SIGNOFF START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;

-- ---- SES_APPROVAL_TASK_SIGNOFF ----
-- One row per person a consensus step is waiting on. Only created for a task
-- whose completion_rule is 'ALL'; an 'ANY' task has no rows here at all and is
-- settled by ses_approval_task.status alone, exactly as before.
--
-- These rows are a freeze alongside the other ses_approval_task_* tables: the
-- set of people a consensus step waits for is fixed at activation. For step 1
-- that set comes from ses_request_evaluator — the staff the requestor picked
-- on the form — so re-resolving it later would silently change who a
-- submitted request was sent to.
--
-- empl_id, not user_id, is the identity that matters: it is what
-- APP_POSITION_VIEW and APP_EMP_INFO_VIEW key on, and what
-- SES_APPROVAL_PENDING_VIEW matches an inbox row against. user_id is carried
-- as a convenience for ses_approval_history and may be null for a person who
-- has never logged into this application.
--
-- signature_file_id does NOT exist on this table in production — see the
-- header above. A withdrawn feature is not recreated here.
--
-- There is no FK to ses_request_evaluator either. Once activation has copied
-- the names across, these rows must survive independently — deleting a draft's
-- evaluator list must never be able to rewrite the history of an approval that
-- already ran.
CREATE TABLE ses_approval_task_signoff (
    signoff_id        NUMBER        NOT NULL,
    task_id           NUMBER        NOT NULL,
    empl_id           VARCHAR2(20)  NOT NULL,
    user_id           VARCHAR2(36),
    display_name      VARCHAR2(200),
    pos_id            VARCHAR2(10),
    pos_title         VARCHAR2(100),
    status            VARCHAR2(20) DEFAULT 'PENDING' NOT NULL,
    note              VARCHAR2(2000),
    completed_by      VARCHAR2(36),
    completed_date    DATE,
    order_by          NUMBER DEFAULT 0 NOT NULL,
    CONSTRAINT pk_ses_appr_task_signoff PRIMARY KEY (signoff_id),
    CONSTRAINT uq_ses_appr_task_signoff UNIQUE (task_id, empl_id),
    CONSTRAINT ck_ses_signoff_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    CONSTRAINT fk_ses_signoff_task FOREIGN KEY (task_id)
        REFERENCES ses_approval_task(task_id) ON DELETE CASCADE
);

-- "Is this task still waiting on anyone?" and "which rows are still open?" are
-- the two questions asked on every approve; both filter task_id then status.
CREATE INDEX idx_ses_signoff_task ON ses_approval_task_signoff(task_id, status);
-- Backs SES_APPROVAL_PENDING_VIEW's NOT EXISTS, which matches on empl_id.
CREATE INDEX idx_ses_signoff_empl ON ses_approval_task_signoff(empl_id, status);

COMMENT ON TABLE  ses_approval_task_signoff          IS 'SES — satu baris per orang yang ditunggu pada tahap konsensus (completion_rule = ALL)';
COMMENT ON COLUMN ses_approval_task_signoff.empl_id  IS 'Identitas pegawai; inilah yang dicocokkan oleh SES_APPROVAL_PENDING_VIEW';
COMMENT ON COLUMN ses_approval_task_signoff.status   IS 'PENDING = belum memutuskan; baris APPROVED/REJECTED hilang dari antrean';

COMMIT;
