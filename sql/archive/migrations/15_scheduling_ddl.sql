-- ============================================================
-- SES — Scheduling module DDL — Oracle 11g / 12c+
-- Run as schema owner, AFTER 03_request_ddl.sql.
--
-- This script is RE-RUNNABLE: it drops existing SES scheduling
-- objects first, then recreates them.
--
-- Owned by com.badaklng.scheduling. Adds an admin-driven scheduling
-- workflow on top of ses_request: a schedule item per (request,
-- schedule_type) — SCREENING (the screening itself happens outside
-- the system) or BADGE_RETRIEVAL (last-step badge pickup) — moving
-- SCHEDULED -> ATTENDED/NO_SHOW/CANCELLED as an admin puts a
-- date/time on the calendar and later attests whether the requestor
-- actually showed up. Marking ATTENDED unlocks a checkpoint gate in
-- the approval-chain mechanism (see
-- com.badaklng.approval.spi.CheckpointTaskType and
-- ApprovalService.completeCheckpointByKey — checkpoint keys
-- "SCREENING_SCHEDULE" / "BADGE_RETRIEVAL_SCHEDULE", registered by
-- com.badaklng.scheduling.spi.SchedulingApprovalBridge). A NO_SHOW
-- deliberately leaves the checkpoint PENDING — the system never
-- auto-resolves attendance, the admin must reschedule or cancel.
-- ============================================================

-- ---- Reset: drop existing SES scheduling tables & sequences ----
BEGIN
    FOR t IN (
        SELECT table_name FROM user_tables WHERE table_name IN (
            'SES_REQUEST_SCHEDULE_HISTORY', 'SES_REQUEST_SCHEDULE'
        )
    ) LOOP
        EXECUTE IMMEDIATE 'DROP TABLE ' || t.table_name || ' CASCADE CONSTRAINTS PURGE';
    END LOOP;
END;
/
BEGIN
    FOR s IN (
        SELECT sequence_name FROM user_sequences WHERE sequence_name IN (
            'SEQ_SES_REQUEST_SCHEDULE', 'SEQ_SES_REQUEST_SCHED_HIST'
        )
    ) LOOP
        EXECUTE IMMEDIATE 'DROP SEQUENCE ' || s.sequence_name;
    END LOOP;
END;
/

-- ---- Sequences ----
CREATE SEQUENCE SEQ_SES_REQUEST_SCHEDULE   START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;
CREATE SEQUENCE SEQ_SES_REQUEST_SCHED_HIST START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;

-- ---- SES_REQUEST_SCHEDULE ----
-- One mutable row per (request_id, schedule_type) for its whole lifecycle —
-- create/reschedule/cancel/attend/no-show all update this same row (never
-- insert a new one); every transition is appended to
-- SES_REQUEST_SCHEDULE_HISTORY instead. request_id is a REAL FK (like
-- ses_request_payment) — this table is intentionally request-specific, not
-- a generic soft-pointer design.
CREATE TABLE ses_request_schedule (
    schedule_id       NUMBER        NOT NULL,
    request_id        NUMBER        NOT NULL,
    schedule_type      VARCHAR2(20) NOT NULL,
    status             VARCHAR2(20) DEFAULT 'SCHEDULED' NOT NULL,
    scheduled_date     DATE          NOT NULL,
    note               VARCHAR2(500),
    resolution_reason  VARCHAR2(500),          -- mandatory reason snapshot for the latest CANCELLED/NO_SHOW
    resolved_by        VARCHAR2(36),           -- -> app_user.user_id, no FK (platform-owned)
    resolved_date      DATE,
    created_by         VARCHAR2(50),
    created_date       DATE,
    updated_by         VARCHAR2(50),
    updated_date       DATE,
    CONSTRAINT pk_ses_request_schedule PRIMARY KEY (schedule_id),
    CONSTRAINT fk_ses_schedule_request FOREIGN KEY (request_id) REFERENCES ses_request(request_id),
    CONSTRAINT ck_ses_schedule_type CHECK (schedule_type IN ('SCREENING', 'BADGE_RETRIEVAL')),
    CONSTRAINT ck_ses_schedule_status CHECK (status IN ('SCHEDULED', 'ATTENDED', 'NO_SHOW', 'CANCELLED')),
    CONSTRAINT uq_ses_schedule_req_type UNIQUE (request_id, schedule_type)
);

-- ---- SES_REQUEST_SCHEDULE_HISTORY ----
-- Append-only log of every create/reschedule/cancel/attend/no-show cycle
-- for a schedule item — backs the manage page's "History" card instead of
-- creating a new SES_REQUEST_SCHEDULE row per cycle.
CREATE TABLE ses_request_schedule_history (
    history_id      NUMBER        NOT NULL,
    schedule_id     NUMBER        NOT NULL,
    action          VARCHAR2(20)  NOT NULL,
    scheduled_date  DATE,                    -- snapshot for CREATED/RESCHEDULED rows; null otherwise
    reason          VARCHAR2(500),           -- note (CREATED/RESCHEDULED) or mandatory reason (CANCELLED/NO_SHOW)
    actor_user_id   VARCHAR2(36)  NOT NULL,  -- -> app_user.user_id, no FK
    actor_date      DATE          NOT NULL,
    created_by      VARCHAR2(50),
    created_date    DATE,
    updated_by      VARCHAR2(50),
    updated_date    DATE,
    CONSTRAINT pk_ses_request_schedule_hist PRIMARY KEY (history_id),
    CONSTRAINT fk_ses_sched_hist_schedule FOREIGN KEY (schedule_id) REFERENCES ses_request_schedule(schedule_id) ON DELETE CASCADE,
    CONSTRAINT ck_ses_sched_hist_action CHECK (action IN ('CREATED', 'RESCHEDULED', 'CANCELLED', 'ATTENDED', 'NO_SHOW'))
);

-- ---- Indexes ----
CREATE INDEX idx_ses_schedule_request  ON ses_request_schedule(request_id);
CREATE INDEX idx_ses_schedule_date     ON ses_request_schedule(scheduled_date);
CREATE INDEX idx_ses_sched_hist_sched  ON ses_request_schedule_history(schedule_id);

COMMIT;
