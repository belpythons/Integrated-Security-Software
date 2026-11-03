-- ============================================================
-- SES — Per-approver sign-offs (consensus steps) — Oracle 11g / 12c+
-- Run AFTER 02_approval_ddl.sql and 52_hybrid_routing_alter.sql.
--
-- [261019] Production copy of sql/archive/migrations/53_task_signoff_ddl.sql.
-- The archived version drops and recreates the table; this one only creates
-- what is missing, so re-running the 261019 package can never wipe the
-- consensus state of running approvals. DDL is otherwise identical.
--
-- Creates ONLY ses_approval_task_signoff. The dev archive's
-- 53_signoff_and_signature_ddl.sql also created ses_user_signature and a
-- signature_file_id column for a stored-digital-signature feature that was
-- WITHDRAWN on 2026-10-06; no Java code references either.
--
-- ses_approval_task_signoff is the consensus mechanism of the Additional
-- Routing step — one row per person that step waits on — and the Badge flow
-- cannot run without it.
--
-- Owned by com.badaklng.approval.
-- ============================================================

SET SERVEROUTPUT ON

DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count FROM user_sequences WHERE sequence_name = 'SEQ_SES_APPR_TASK_SIGNOFF';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'CREATE SEQUENCE SEQ_SES_APPR_TASK_SIGNOFF START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE';
    END IF;

    SELECT COUNT(*) INTO v_count FROM user_tables WHERE table_name = 'SES_APPROVAL_TASK_SIGNOFF';
    IF v_count > 0 THEN
        DBMS_OUTPUT.PUT_LINE('SES_APPROVAL_TASK_SIGNOFF: sudah ada, dilewati.');
        RETURN;
    END IF;

    -- UNIQUE(task_id, empl_id): one person signs off a given task once.
    -- ON DELETE CASCADE: sign-offs belong to their task, as the task's
    -- frozen assignee rows already do.
    EXECUTE IMMEDIATE '
        CREATE TABLE ses_approval_task_signoff (
            signoff_id        NUMBER        NOT NULL,
            task_id           NUMBER        NOT NULL,
            empl_id           VARCHAR2(20)  NOT NULL,
            user_id           VARCHAR2(36),
            display_name      VARCHAR2(200),
            pos_id            VARCHAR2(10),
            pos_title         VARCHAR2(100),
            status            VARCHAR2(20) DEFAULT ''PENDING'' NOT NULL,
            note              VARCHAR2(2000),
            completed_by      VARCHAR2(36),
            completed_date    DATE,
            order_by          NUMBER DEFAULT 0 NOT NULL,
            CONSTRAINT pk_ses_appr_task_signoff PRIMARY KEY (signoff_id),
            CONSTRAINT uq_ses_appr_task_signoff UNIQUE (task_id, empl_id),
            CONSTRAINT ck_ses_signoff_status CHECK (status IN (''PENDING'', ''APPROVED'', ''REJECTED'')),
            CONSTRAINT fk_ses_signoff_task FOREIGN KEY (task_id)
                REFERENCES ses_approval_task(task_id) ON DELETE CASCADE
        )';

    EXECUTE IMMEDIATE 'CREATE INDEX idx_ses_signoff_task ON ses_approval_task_signoff(task_id, status)';
    EXECUTE IMMEDIATE 'CREATE INDEX idx_ses_signoff_empl ON ses_approval_task_signoff(empl_id, status)';

    EXECUTE IMMEDIATE 'COMMENT ON TABLE ses_approval_task_signoff IS ''SES — satu baris per orang yang ditunggu pada tahap konsensus (completion_rule = ALL)''';
    EXECUTE IMMEDIATE 'COMMENT ON COLUMN ses_approval_task_signoff.empl_id IS ''Identitas pegawai; inilah yang dicocokkan oleh SES_APPROVAL_PENDING_VIEW''';
    EXECUTE IMMEDIATE 'COMMENT ON COLUMN ses_approval_task_signoff.status IS ''PENDING = belum memutuskan; baris APPROVED/REJECTED hilang dari antrean''';

    DBMS_OUTPUT.PUT_LINE('SES_APPROVAL_TASK_SIGNOFF: dibuat.');
END;
/

COMMIT;
