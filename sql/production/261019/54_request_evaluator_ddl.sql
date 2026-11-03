-- ============================================================
-- SES — Staf Security (Evaluator) pilihan requestor — Oracle 11g / 12c+
-- Run AFTER 03_request_ddl.sql (which creates and owns ses_request).
--
-- [261019] Production copy of sql/archive/migrations/54_request_evaluator_ddl.sql.
-- The archived version drops and recreates the table; this one only creates
-- what is missing, so re-running the 261019 package can never wipe the
-- evaluator choices of submitted requests. DDL is otherwise identical.
--
-- Owned by com.badaklng.request.
--
-- The requestor picks one or more Staf Security on the new-badge form; step 1
-- of the approval chain then broadcasts to exactly those people and waits for
-- ALL of them (ses_approval_task_signoff, completion_rule = 'ALL').
--
-- This set is per SUBMISSION and chosen by the requestor, so it cannot live in
-- ses_approval_assignee, and it has to be readable BEFORE
-- ApprovalService#trigger() runs, because trigger() is what turns it into
-- sign-off rows. No FK to any APP_* object, matching the rule stated in
-- sql/README.md.
-- ============================================================

SET SERVEROUTPUT ON

DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count FROM user_sequences WHERE sequence_name = 'SEQ_SES_REQUEST_EVALUATOR';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'CREATE SEQUENCE SEQ_SES_REQUEST_EVALUATOR START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE';
    END IF;

    SELECT COUNT(*) INTO v_count FROM user_tables WHERE table_name = 'SES_REQUEST_EVALUATOR';
    IF v_count > 0 THEN
        DBMS_OUTPUT.PUT_LINE('SES_REQUEST_EVALUATOR: sudah ada, dilewati.');
        RETURN;
    END IF;

    -- UNIQUE(request_id, empl_id) blocks the same person being picked twice on
    -- one request, which would otherwise create two sign-off rows for one human
    -- and a consensus that can never complete.
    EXECUTE IMMEDIATE '
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
        )';

    EXECUTE IMMEDIATE 'CREATE INDEX idx_ses_evaluator_request ON ses_request_evaluator(request_id, line_no)';

    EXECUTE IMMEDIATE 'COMMENT ON TABLE ses_request_evaluator IS ''SES — Staf Security yang dipilih pemohon untuk tahap 1 (broadcast paralel, semua harus menyetujui)''';
    EXECUTE IMMEDIATE 'COMMENT ON COLUMN ses_request_evaluator.empl_id IS ''APP_POSITION_VIEW.HOLDER_EMPL_ID / EMPL_ID pada saat dipilih''';
    EXECUTE IMMEDIATE 'COMMENT ON COLUMN ses_request_evaluator.empl_name IS ''Disalin saat dipilih — pengajuan lama tetap bisa menyebut nama yang dituju meski org chart berubah''';

    DBMS_OUTPUT.PUT_LINE('SES_REQUEST_EVALUATOR: dibuat.');
END;
/

COMMIT;
