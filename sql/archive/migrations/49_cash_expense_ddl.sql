-- ============================================================
-- SES — Pengeluaran Kas (Cash Expense) module DDL — Oracle 11g / 12c+
-- Run as schema owner. Does not depend on any other script besides the
-- platform DDL.
--
-- This script is RE-RUNNABLE: existing objects are dropped first, then
-- recreated. Drop-first was chosen (same pattern as 27_sticker_ddl.sql)
-- because the table is brand new — no data to lose on a first release.
-- ⚠️ AFTER THE FIRST RELEASE, re-running this script DELETES the entire
--    cash-expense history. Do not re-run against a populated environment.
--
-- Owned by com.badaklng.cash. Records money LEAVING petty cash, the
-- counterpart to ses_request_payment, which records money COMING IN.
--
-- Metric definitions (product decision, 2026-10-05):
--   Total Kas Masuk = SUM(paid_amount) across every VERIFIED
--                     ses_request_payment row — the same definition
--                     already used by /payment/recap. There is no
--                     "disbursed" concept in this application.
--   Total Kas Keluar = SUM(amount) across every active ses_cash_expense row.
--   Balance          = Kas Masuk - Kas Keluar.
-- ============================================================

-- ---- Reset ----
BEGIN
    FOR t IN (SELECT table_name FROM user_tables WHERE table_name = 'SES_CASH_EXPENSE') LOOP
        EXECUTE IMMEDIATE 'DROP TABLE ' || t.table_name || ' CASCADE CONSTRAINTS PURGE';
    END LOOP;
END;
/
BEGIN
    FOR s IN (SELECT sequence_name FROM user_sequences WHERE sequence_name = 'SEQ_SES_CASH_EXPENSE') LOOP
        EXECUTE IMMEDIATE 'DROP SEQUENCE ' || s.sequence_name;
    END LOOP;
END;
/

-- ---- Sequence ----
CREATE SEQUENCE SEQ_SES_CASH_EXPENSE START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;

-- ---- SES_CASH_EXPENSE ----
-- amount NUMBER(15): IDR, whole rupiah, no decimals — consistent with
-- ses_request_payment.paid_amount and ses_request_type.payment_amount.
-- Storing both in the same unit and precision is what makes
-- Balance = Masuk - Keluar meaningful.
--
-- active_flag: deletion is SOFT. Cash history is a financial record; a row
-- entered in error is marked inactive and disappears from the list and
-- totals, but the row itself and its audit trail (who created it, who
-- deactivated it) remain.
CREATE TABLE ses_cash_expense (
    expense_id   NUMBER        NOT NULL,
    paid_to      VARCHAR2(200) NOT NULL,
    amount       NUMBER(15)    NOT NULL,
    notes        VARCHAR2(2000),
    expense_date DATE          NOT NULL,
    active_flag  CHAR(1)       DEFAULT 'Y' NOT NULL,
    created_by   VARCHAR2(50),
    created_date DATE,
    updated_by   VARCHAR2(50),
    updated_date DATE,
    CONSTRAINT pk_ses_cash_expense PRIMARY KEY (expense_id),
    CONSTRAINT ck_ses_cash_expense_amt CHECK (amount > 0),
    CONSTRAINT ck_ses_cash_expense_act CHECK (active_flag IN ('Y', 'N'))
);

-- Backs the list and aggregate queries, both of which filter active_flag first.
CREATE INDEX idx_ses_cash_expense_act ON ses_cash_expense(active_flag, expense_date);

COMMENT ON TABLE  ses_cash_expense              IS 'SES — histori pengeluaran kas (uang keluar); lawan dari ses_request_payment';
COMMENT ON COLUMN ses_cash_expense.paid_to      IS 'Dikeluarkan ke mana — penerima atau keperluan';
COMMENT ON COLUMN ses_cash_expense.amount       IS 'Nominal IDR, rupiah bulat, selalu > 0';
COMMENT ON COLUMN ses_cash_expense.active_flag  IS 'N = dihapus secara lunak; tidak masuk daftar maupun total';

COMMIT;
