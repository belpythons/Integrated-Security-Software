-- ============================================================
-- SES — Staf Security (Evaluator) pilihan requestor — Oracle 11g / 12c+
-- Run AFTER 03_request_ddl.sql (which creates and owns ses_request).
--
-- RE-RUNNABLE drop-first (same convention as 27_sticker_ddl.sql) — the table is
-- brand new, so there is nothing to lose on a first apply.
-- WARNING: after the first release, re-running DESTROYS the evaluator choice of
-- every draft that has not yet been submitted.
--
-- Owned by com.badaklng.request.
--
-- The requestor picks one or more Staf Security on the new-badge form; step 1
-- of the approval chain then broadcasts to exactly those people and waits for
-- ALL of them (ses_approval_task_signoff, completion_rule = 'ALL').
--
-- WHY A TABLE AND NOT A CHAIN ASSIGNEE. Every other approver in this system is
-- configuration: an admin wires a position or a user into the chain at
-- /approval/chain, and it applies to every submission. This set is per
-- SUBMISSION and chosen by the requestor, so it cannot live in
-- ses_approval_assignee — and it has to be readable BEFORE
-- ApprovalService#trigger() runs, because trigger() is what turns it into
-- sign-off rows.
--
-- The candidate list comes from APP_POSITION_VIEW filtered to the Security
-- section, resolved live at form time (RequestController#securityEvaluators).
-- What is stored here is the CHOICE, with empl_name copied in: the org chart
-- may move someone out of Security tomorrow, and a submitted request must still
-- be able to say who it was sent to. No FK to any APP_* object, matching the
-- rule stated in sql/README.md — APP_USER / APP_POSITION_VIEW references in
-- this schema are plain pointers, enforced in the application layer.
-- ============================================================

-- ---- Reset ----
BEGIN
    FOR t IN (SELECT table_name FROM user_tables WHERE table_name = 'SES_REQUEST_EVALUATOR') LOOP
        EXECUTE IMMEDIATE 'DROP TABLE ' || t.table_name || ' CASCADE CONSTRAINTS PURGE';
    END LOOP;
END;
/
BEGIN
    FOR s IN (SELECT sequence_name FROM user_sequences WHERE sequence_name = 'SEQ_SES_REQUEST_EVALUATOR') LOOP
        EXECUTE IMMEDIATE 'DROP SEQUENCE ' || s.sequence_name;
    END LOOP;
END;
/

-- ---- Sequence ----
CREATE SEQUENCE SEQ_SES_REQUEST_EVALUATOR START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;

-- ---- SES_REQUEST_EVALUATOR ----
-- UNIQUE(request_id, empl_id) blocks the same person being picked twice on one
-- request, which would otherwise create two sign-off rows for one human and a
-- consensus that can never complete.
CREATE TABLE ses_request_evaluator (
    evaluator_id NUMBER        NOT NULL,
    request_id   NUMBER        NOT NULL,
    line_no      NUMBER        DEFAULT 0 NOT NULL,
    pos_id       VARCHAR2(10),
    pos_title    VARCHAR2(100),
    empl_id      VARCHAR2(20)  NOT NULL,
    empl_name    VARCHAR2(200),
    created_by   VARCHAR2(50),
    created_date DATE,
    CONSTRAINT pk_ses_request_evaluator PRIMARY KEY (evaluator_id),
    CONSTRAINT uq_ses_request_evaluator UNIQUE (request_id, empl_id),
    CONSTRAINT fk_ses_evaluator_request FOREIGN KEY (request_id)
        REFERENCES ses_request(request_id) ON DELETE CASCADE
);

CREATE INDEX idx_ses_evaluator_request ON ses_request_evaluator(request_id, line_no);

COMMENT ON TABLE  ses_request_evaluator           IS 'SES — Staf Security yang dipilih pemohon untuk tahap 1 (broadcast paralel, semua harus menyetujui)';
COMMENT ON COLUMN ses_request_evaluator.empl_id   IS 'APP_POSITION_VIEW.HOLDER_EMPL_ID / EMPL_ID pada saat dipilih';
COMMENT ON COLUMN ses_request_evaluator.empl_name IS 'Disalin saat dipilih — pengajuan lama tetap bisa menyebut nama yang dituju meski org chart berubah';

COMMIT;
