-- ============================================================
-- SES — Payment amount columns: configured amount per request type +
-- officer-recorded actual amount received per payment/history row.
-- Run AFTER 03_request_ddl.sql / 08_payment_ddl.sql (which create +
-- own ses_request_type / ses_request_payment / ses_request_payment_history).
--
-- Plain ALTER TABLE ADD, same convention as 11_request_worker_alter.sql
-- — does NOT drop/recreate any of the three tables (03/08's drop-first
-- re-run behavior would be destructive to real submitted request/payment
-- data by the time this ships). Re-running against a schema that already
-- has these columns will fail with ORA-01430; that's expected — one-time
-- alter, not meant to be re-run once applied.
--
-- All three amounts are IDR, whole Rupiah, NUMBER(15) — no decimals,
-- matching everyday Rupiah usage (no circulating fractional denomination).
-- ============================================================

ALTER TABLE SES_REQUEST_TYPE ADD (
            payment_amount NUMBER(15)
        );

ALTER TABLE SES_REQUEST_PAYMENT ADD (
            paid_amount NUMBER(15)
        );

ALTER TABLE SES_REQUEST_PAYMENT_HISTORY ADD (
            amount NUMBER(15)
        );

--ALTER TABLE carsis.SES_REQUEST_TYPE DROP (payment_amount);
--ALTER TABLE carsis.SES_REQUEST_PAYMENT DROP (paid_amount);
--ALTER TABLE carsis.SES_REQUEST_PAYMENT_HISTORY DROP (amount);

COMMIT;
