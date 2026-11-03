-- ============================================================
-- SES — Generalize ses_request_payment to a soft business pointer
-- Run AFTER 25_payment_status_cancelled_alter.sql.
--
-- Prior to this script ses_request_payment.request_id was a REAL FK to
-- ses_request — intentional per PaymentService's class javadoc, since only
-- one business module (com.badaklng.request) ever used the payment engine.
-- Now a second module (com.badaklng.sticker, "Stiker Kendaraan") needs the
-- same submit/verify/pay-on-behalf machinery, so the pointer is widened to
-- the same soft (business_type, business_ref_id) shape the approval module
-- already uses for SesApprovalInstance — no FK, resolved at the application
-- layer via com.badaklng.payment.spi.PaymentBusinessDescriptor.
--
-- Idempotent-ish: guarded so re-running after the columns already exist is
-- a no-op rather than an error.
-- ============================================================

-- ---- Add the new soft-pointer columns ----
DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count FROM user_tab_columns
    WHERE table_name = 'SES_REQUEST_PAYMENT' AND column_name = 'BUSINESS_TYPE';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE ses_request_payment ADD business_type VARCHAR2(30)';
        EXECUTE IMMEDIATE 'ALTER TABLE ses_request_payment ADD business_ref_id NUMBER';
    END IF;
END;
/

-- ---- Backfill every existing row as SES_REQUEST ----
UPDATE ses_request_payment SET business_type = 'SES_REQUEST', business_ref_id = request_id
WHERE business_type IS NULL;
COMMIT;

-- ---- Drop the old FK + column, enforce NOT NULL on the new pair ----
DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count FROM user_constraints
    WHERE table_name = 'SES_REQUEST_PAYMENT' AND constraint_name = 'FK_SES_PAYMENT_REQUEST';
    IF v_count > 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE ses_request_payment DROP CONSTRAINT fk_ses_payment_request';
    END IF;

    SELECT COUNT(*) INTO v_count FROM user_tab_columns
    WHERE table_name = 'SES_REQUEST_PAYMENT' AND column_name = 'REQUEST_ID';
    IF v_count > 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE ses_request_payment DROP COLUMN request_id';
    END IF;

    EXECUTE IMMEDIATE 'ALTER TABLE ses_request_payment MODIFY business_type VARCHAR2(30) NOT NULL';
    EXECUTE IMMEDIATE 'ALTER TABLE ses_request_payment MODIFY business_ref_id NUMBER NOT NULL';
END;
/

DROP INDEX idx_ses_payment_request;
CREATE INDEX idx_ses_payment_business ON ses_request_payment(business_type, business_ref_id);

-- ---- Widen the payment-history action CHECK to match PaymentHistoryAction —
-- PaymentService#onRequestRejected has been writing CANCELLED/REFUND_FLAGGED
-- since 25_payment_status_cancelled_alter.sql shipped, but this constraint
-- (from 08_payment_ddl.sql) was never updated alongside it, so any request
-- rejection with a live payment item has been throwing a constraint
-- violation. Also adds SUBMITTED_ON_BEHALF for the new officer pay-on-behalf
-- flow. ----
ALTER TABLE ses_request_payment_history DROP CONSTRAINT ck_ses_pay_hist_action;

ALTER TABLE ses_request_payment_history ADD CONSTRAINT ck_ses_pay_hist_action
    CHECK (action IN ('SUBMITTED', 'SUBMITTED_ON_BEHALF', 'APPROVED', 'REJECTED', 'CANCELLED', 'REFUND_FLAGGED'));

COMMIT;
