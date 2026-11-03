-- ============================================================
-- SES — Pengeluaran Kas (Cash Expense) module DDL — Oracle 11g / 12c+
-- Run as schema owner. Does not depend on any other script besides the
-- platform DDL.
--
-- [261019] Production copy of sql/archive/migrations/49_cash_expense_ddl.sql.
-- The archived version drops and recreates the table; this one only creates
-- what is missing, so re-running the 261019 package after go-live can never
-- delete cash-expense history. DDL is otherwise identical.
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

SET SERVEROUTPUT ON

DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count FROM user_sequences WHERE sequence_name = 'SEQ_SES_CASH_EXPENSE';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'CREATE SEQUENCE SEQ_SES_CASH_EXPENSE START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE';
    END IF;

    SELECT COUNT(*) INTO v_count FROM user_tables WHERE table_name = 'SES_CASH_EXPENSE';
    IF v_count > 0 THEN
        DBMS_OUTPUT.PUT_LINE('SES_CASH_EXPENSE: sudah ada, dilewati.');
        RETURN;
    END IF;

    -- amount NUMBER(15): IDR, whole rupiah, no decimals — consistent with
    -- ses_request_payment.paid_amount and ses_request_type.payment_amount.
    --
    -- active_flag: deletion is SOFT. Cash history is a financial record; a row
    -- entered in error is marked inactive and disappears from the list and
    -- totals, but the row itself and its audit trail remain.
    EXECUTE IMMEDIATE '
        CREATE TABLE ses_cash_expense (
            expense_id   NUMBER        NOT NULL,
            paid_to      VARCHAR2(200) NOT NULL,
            amount       NUMBER(15)    NOT NULL,
            notes        VARCHAR2(2000),
            expense_date DATE          NOT NULL,
            active_flag  CHAR(1)       DEFAULT ''Y'' NOT NULL,
            created_by   VARCHAR2(50),
            created_date DATE,
            updated_by   VARCHAR2(50),
            updated_date DATE,
            CONSTRAINT pk_ses_cash_expense PRIMARY KEY (expense_id),
            CONSTRAINT ck_ses_cash_expense_amt CHECK (amount > 0),
            CONSTRAINT ck_ses_cash_expense_act CHECK (active_flag IN (''Y'', ''N''))
        )';

    -- Backs the list and aggregate queries, both of which filter active_flag first.
    EXECUTE IMMEDIATE 'CREATE INDEX idx_ses_cash_expense_act ON ses_cash_expense(active_flag, expense_date)';

    EXECUTE IMMEDIATE 'COMMENT ON TABLE ses_cash_expense IS ''SES — histori pengeluaran kas (uang keluar); lawan dari ses_request_payment''';
    EXECUTE IMMEDIATE 'COMMENT ON COLUMN ses_cash_expense.paid_to IS ''Dikeluarkan ke mana — penerima atau keperluan''';
    EXECUTE IMMEDIATE 'COMMENT ON COLUMN ses_cash_expense.amount IS ''Nominal IDR, rupiah bulat, selalu > 0''';
    EXECUTE IMMEDIATE 'COMMENT ON COLUMN ses_cash_expense.active_flag IS ''N = dihapus secara lunak; tidak masuk daftar maupun total''';

    DBMS_OUTPUT.PUT_LINE('SES_CASH_EXPENSE: dibuat.');
END;
/

COMMIT;
