-- ============================================================
-- SES — Add CANCELLED to ses_request_payment.status
-- A payment item still PENDING/VERIFYING when its request's approval
-- ends REJECTED is auto-cancelled (see PaymentService#onRequestRejected)
-- so it drops out of the officer's /payment/verify review queue instead
-- of sitting there for a request that's already dead. VERIFIED payments
-- are left untouched (money already changed hands) and only get a
-- history entry flagging them for manual refund review.
--
-- NOT RE-RUNNABLE on a schema where it already applied (plain DROP
-- CONSTRAINT), same convention as 21_request_draft_status_alter.sql.
-- ============================================================

ALTER TABLE ses_request_payment DROP CONSTRAINT ck_ses_payment_status;

ALTER TABLE ses_request_payment ADD CONSTRAINT ck_ses_payment_status
    CHECK (status IN ('PENDING', 'VERIFYING', 'VERIFIED', 'REJECTED', 'CANCELLED'));
