-- ============================================================================
-- 93_platform_147_and_payment_index.sql
-- Platform 1.4.7 alignment (APP_JOB.ACTIVE_FLAG) + payment officer-queue index
-- ============================================================================

-- 1. APP_JOB.ACTIVE_FLAG (Platform 1.4.7 — Job Management on/off switch)
DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count
      FROM user_tab_cols
     WHERE table_name = 'APP_JOB' AND column_name = 'ACTIVE_FLAG';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE APP_JOB ADD (ACTIVE_FLAG CHAR(1) DEFAULT ''Y'' NOT NULL)';
    END IF;
END;
/

-- 2. Index for PaymentService.findForOfficer (STATUS IN (...) ORDER BY PAYMENT_ID DESC) —
--    migration 92 only covers the CREATED_DATE DESC ordering used elsewhere.
DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count FROM user_indexes WHERE index_name = 'IDX_SES_PAYMENT_STAT_ID';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'CREATE INDEX IDX_SES_PAYMENT_STAT_ID ON SES_REQUEST_PAYMENT (STATUS, PAYMENT_ID DESC)';
    END IF;
END;
/
