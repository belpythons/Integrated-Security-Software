-- ============================================================
-- SES — Payment Verification module DDL — Oracle 11g / 12c+
-- Run as schema owner, AFTER 03_request_ddl.sql.
--
-- This script is RE-RUNNABLE: it drops existing SES payment
-- objects first, then recreates them.
--
-- Owned by com.badaklng.payment. Adds a two-sided verification
-- workflow on top of ses_request: a payment item per request that
-- needs one (ses_request_type.needs_payment_flag = 'Y'), moving
-- PENDING -> VERIFYING -> VERIFIED/REJECTED as the requestor
-- uploads proof and an admin reviews it. Approving unlocks a
-- checkpoint gate in the approval-chain mechanism (see
-- com.badaklng.approval.spi.CheckpointTaskType and
-- ApprovalService.completeCheckpointByKey — checkpoint key
-- "PAYMENT_VERIFICATION", registered by
-- com.badaklng.payment.spi.PaymentApprovalBridge).
--
-- Proof-of-payment files go through the shared APP_FILE table via
-- AppFileService, same convention as ses_request/ses_request_worker_doc
-- — proof_file_id columns below are plain VARCHAR2(36) pointers, not FKs.
-- ============================================================

-- ---- Reset: drop existing SES payment tables & sequences ----
BEGIN
    FOR t IN (
        SELECT table_name FROM user_tables WHERE table_name IN (
            'SES_REQUEST_PAYMENT_HISTORY', 'SES_REQUEST_PAYMENT'
        )
    ) LOOP
        EXECUTE IMMEDIATE 'DROP TABLE ' || t.table_name || ' CASCADE CONSTRAINTS PURGE';
    END LOOP;
END;
/
BEGIN
    FOR s IN (
        SELECT sequence_name FROM user_sequences WHERE sequence_name IN (
            'SEQ_SES_REQUEST_PAYMENT', 'SEQ_SES_REQUEST_PAY_HISTORY'
        )
    ) LOOP
        EXECUTE IMMEDIATE 'DROP SEQUENCE ' || s.sequence_name;
    END LOOP;
END;
/

-- ---- Sequences ----
CREATE SEQUENCE SEQ_SES_REQUEST_PAYMENT     START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;
CREATE SEQUENCE SEQ_SES_REQUEST_PAY_HISTORY START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;

-- ---- SES_REQUEST_PAYMENT ----
-- Current-state row per request. request_id is a REAL FK (unlike most
-- SES pointer columns) because this table is intentionally request-
-- specific, not a generic soft-pointer design like the approval module's
-- business_ref_id — see com.badaklng.payment.service.PaymentService's
-- class javadoc. Deliberately NOT UNIQUE(request_id): one request
-- conceptually has one "current" payment item today, enforced only in
-- PaymentService.ensurePayment() at the application layer, so a future
-- multi-payment-per-request model change never needs a constraint drop.
CREATE TABLE ses_request_payment (
    payment_id       NUMBER        NOT NULL,
    request_id       NUMBER        NOT NULL,
    status           VARCHAR2(20) DEFAULT 'PENDING' NOT NULL,
    proof_file_id    VARCHAR2(36),           -- -> app_file.file_id, no FK (shared table)
    paid_amount      NUMBER(15),             -- officer-recorded actual amount (IDR), set on approve
    rejection_reason VARCHAR2(500),
    reviewed_by      VARCHAR2(36),           -- -> app_user.user_id, no FK (platform-owned)
    reviewed_date    DATE,
    created_by       VARCHAR2(50),
    created_date     DATE,
    updated_by       VARCHAR2(50),
    updated_date     DATE,
    CONSTRAINT pk_ses_request_payment PRIMARY KEY (payment_id),
    CONSTRAINT fk_ses_payment_request FOREIGN KEY (request_id) REFERENCES ses_request(request_id),
    CONSTRAINT ck_ses_payment_status CHECK (status IN ('PENDING', 'VERIFYING', 'VERIFIED', 'REJECTED'))
);

-- ---- SES_REQUEST_PAYMENT_HISTORY ----
-- Append-only log of every submit/approve/reject cycle for a payment
-- item — backs the detail page's "history card" instead of creating a
-- new SES_REQUEST_PAYMENT row (and a new list entry) per rejection.
CREATE TABLE ses_request_payment_history (
    history_id     NUMBER        NOT NULL,
    payment_id     NUMBER        NOT NULL,
    action         VARCHAR2(20)  NOT NULL,
    proof_file_id  VARCHAR2(36),           -- snapshot at SUBMITTED time; null for APPROVED/REJECTED rows
    reason         VARCHAR2(500),          -- rejection reason snapshot, REJECTED rows only
    amount         NUMBER(15),             -- officer-recorded amount snapshot, APPROVED rows only
    actor_user_id  VARCHAR2(36)  NOT NULL, -- -> app_user.user_id, no FK
    actor_date     DATE          NOT NULL,
    created_by     VARCHAR2(50),
    created_date   DATE,
    updated_by     VARCHAR2(50),
    updated_date   DATE,
    CONSTRAINT pk_ses_request_payment_history PRIMARY KEY (history_id),
    CONSTRAINT fk_ses_pay_hist_payment FOREIGN KEY (payment_id) REFERENCES ses_request_payment(payment_id) ON DELETE CASCADE,
    CONSTRAINT ck_ses_pay_hist_action CHECK (action IN ('SUBMITTED', 'APPROVED', 'REJECTED'))
);

-- ---- Indexes ----
CREATE INDEX idx_ses_payment_request  ON ses_request_payment(request_id);
CREATE INDEX idx_ses_payment_status   ON ses_request_payment(status);
CREATE INDEX idx_ses_pay_hist_payment ON ses_request_payment_history(payment_id);

COMMIT;
