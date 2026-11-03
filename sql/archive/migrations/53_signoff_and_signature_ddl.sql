-- ============================================================
-- SES — Per-approver sign-offs + stored signature specimens — Oracle 11g / 12c+
--
-- ⚠️ SUPERSEDED IN PART. Everything here concerning SIGNATURES was withdrawn
-- on 2026-10-06: ses_user_signature and the signature_file_id column are
-- dropped again by 59_drop_digital_signature.sql.
--
-- ses_approval_task_signoff itself STAYS. It is the consensus mechanism of the
-- Additional Routing step — one row per person that step waits on — not part
-- of the signature feature, and the Badge flow cannot run without it.
--
-- Left as the record of what was applied to ELLDEV on 2026-10-06. A fresh dev
-- install may run this and then 59; production takes
-- production/53_task_signoff_ddl.sql instead, which never creates the
-- signature objects at all.
-- Run AFTER 02_approval_ddl.sql and 52_hybrid_routing_alter.sql.
--
-- Two tables, both new, so this script is RE-RUNNABLE drop-first (same
-- convention as 27_sticker_ddl.sql).
-- WARNING: after the first release, re-running this DESTROYS the consensus
-- state of every running approval and every stored signature. Do not re-run in
-- a populated environment.
--
-- Owned by com.badaklng.approval.
-- ============================================================

-- ---- Reset ----
BEGIN
    FOR t IN (SELECT table_name FROM user_tables
              WHERE table_name IN ('SES_APPROVAL_TASK_SIGNOFF', 'SES_USER_SIGNATURE')) LOOP
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
-- These rows are a FOURTH freeze, alongside the three ses_approval_task_*
-- tables: the set of people a consensus step waits for is fixed at activation.
-- For step 1 that set comes from ses_request_evaluator — the staff the
-- requestor picked on the form — so re-resolving it later would silently
-- change who a submitted request was sent to.
--
-- empl_id, not user_id, is the identity that matters: it is what
-- APP_POSITION_VIEW and APP_EMP_INFO_VIEW key on, and what
-- SES_APPROVAL_PENDING_VIEW matches an inbox row against. user_id is carried
-- as a convenience for ses_approval_history and may be null for a person who
-- has never logged into this application.
--
-- signature_file_id -> app_file.file_id, no FK: the same soft-pointer
-- convention every APP_FILE reference in this schema uses.
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
    signature_file_id VARCHAR2(36),
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

-- ---- SES_USER_SIGNATURE ----
-- One stored signature specimen per user, written when the approver ticks
-- "simpan sebagai TTD default saya" in the Approve modal.
--
-- This is a CONVENIENCE, never the audit record. Each approval still stores its
-- own signature image against its own task or sign-off row, so a specimen that
-- is later replaced cannot retroactively change what was signed on a document
-- approved last month. That separation is the entire reason this is a second
-- table rather than a column read at print time.
--
-- user_id is the APP_USER UUID (not the username) — it is what
-- ses_approval_task.completed_by holds, so the print path can join without a
-- second lookup.
CREATE TABLE ses_user_signature (
    user_id      VARCHAR2(36)  NOT NULL,
    file_id      VARCHAR2(36)  NOT NULL,
    created_by   VARCHAR2(50),
    created_date DATE,
    updated_by   VARCHAR2(50),
    updated_date DATE,
    CONSTRAINT pk_ses_user_signature PRIMARY KEY (user_id)
);

COMMENT ON TABLE  ses_user_signature         IS 'SES — spesimen tanda tangan per pengguna; kenyamanan, bukan catatan audit';
COMMENT ON COLUMN ses_user_signature.file_id IS 'app_file.file_id berisi gambar tanda tangan (PNG)';

COMMIT;
