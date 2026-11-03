-- ============================================================================
-- 92_performance_indexes.sql
-- Optimasi Indeks Performa Data Retrieval untuk SES Oracle 19c ELLDEV
-- Mengeliminasi Full Table Scans dan in-memory sorting pada tabel transaksi & auth
-- ============================================================================

-- 1. Function-Based Index pada APP_USER untuk pencarian login & username case-insensitive
DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count FROM user_indexes WHERE index_name = 'IDX_APP_USER_UPPER_NAME';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'CREATE INDEX IDX_APP_USER_UPPER_NAME ON APP_USER (UPPER(USERNAME))';
    END IF;
END;
/

-- 2. Indeks SES_REQUEST untuk Riwayat & Dashboard Sort
DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count FROM user_indexes WHERE index_name = 'IDX_SES_REQ_CREATED';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'CREATE INDEX IDX_SES_REQ_CREATED ON SES_REQUEST (CREATED_DATE DESC)';
    END IF;

    SELECT COUNT(*) INTO v_count FROM user_indexes WHERE index_name = 'IDX_SES_REQ_STAT_CREATED';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'CREATE INDEX IDX_SES_REQ_STAT_CREATED ON SES_REQUEST (STATUS, CREATED_DATE DESC)';
    END IF;

    SELECT COUNT(*) INTO v_count FROM user_indexes WHERE index_name = 'IDX_SES_REQ_USER_CREATED';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'CREATE INDEX IDX_SES_REQ_USER_CREATED ON SES_REQUEST (REQUESTOR_USER_ID, CREATED_DATE DESC)';
    END IF;
END;
/

-- 3. Indeks SES_STICKER_REQUEST untuk Riwayat & Status Filter
DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count FROM user_indexes WHERE index_name = 'IDX_SES_STK_STATUS';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'CREATE INDEX IDX_SES_STK_STATUS ON SES_STICKER_REQUEST (STATUS)';
    END IF;

    SELECT COUNT(*) INTO v_count FROM user_indexes WHERE index_name = 'IDX_SES_STK_CREATED';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'CREATE INDEX IDX_SES_STK_CREATED ON SES_STICKER_REQUEST (CREATED_DATE DESC)';
    END IF;

    SELECT COUNT(*) INTO v_count FROM user_indexes WHERE index_name = 'IDX_SES_STK_STAT_CREATED';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'CREATE INDEX IDX_SES_STK_STAT_CREATED ON SES_STICKER_REQUEST (STATUS, CREATED_DATE DESC)';
    END IF;

    SELECT COUNT(*) INTO v_count FROM user_indexes WHERE index_name = 'IDX_SES_STK_USER_CREATED';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'CREATE INDEX IDX_SES_STK_USER_CREATED ON SES_STICKER_REQUEST (REQUESTOR_USER_ID, CREATED_DATE DESC)';
    END IF;
END;
/

-- 4. Indeks Komposit SES_APPROVAL_INSTANCE (Pending Worklist)
DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count FROM user_indexes WHERE index_name = 'IDX_SES_INSTANCE_STAT_ID';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'CREATE INDEX IDX_SES_INSTANCE_STAT_ID ON SES_APPROVAL_INSTANCE (STATUS, INSTANCE_ID DESC)';
    END IF;

    SELECT COUNT(*) INTO v_count FROM user_indexes WHERE index_name = 'IDX_SES_INSTANCE_USER_STAT';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'CREATE INDEX IDX_SES_INSTANCE_USER_STAT ON SES_APPROVAL_INSTANCE (REQUESTOR_USER_ID, STATUS)';
    END IF;
END;
/

-- 5. Indeks SES_APPROVAL_TASK_SIGNOFF untuk SES_APPROVAL_PENDING_VIEW
DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count FROM user_indexes WHERE index_name = 'IDX_SES_SIGNOFF_TASK_EMPL';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'CREATE INDEX IDX_SES_SIGNOFF_TASK_EMPL ON SES_APPROVAL_TASK_SIGNOFF (TASK_ID, EMPL_ID)';
    END IF;
END;
/

-- 6. Indeks SES_REQUEST_WORKER untuk NIK Search
DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count FROM user_indexes WHERE index_name = 'IDX_SES_WORKER_NIK';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'CREATE INDEX IDX_SES_WORKER_NIK ON SES_REQUEST_WORKER (NIK)';
    END IF;
END;
/

-- 7. Indeks SES_REQUEST_PAYMENT untuk Antrean Verifikasi Kas
DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count FROM user_indexes WHERE index_name = 'IDX_SES_PAYMENT_STAT_CREATED';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'CREATE INDEX IDX_SES_PAYMENT_STAT_CREATED ON SES_REQUEST_PAYMENT (STATUS, CREATED_DATE DESC)';
    END IF;
END;
/

-- 8. Refresh Statistik Kamus Data Oracle (DBMS_STATS)
BEGIN
    DBMS_STATS.GATHER_TABLE_STATS(ownname => 'SES', tabname => 'APP_USER', cascade => TRUE);
    DBMS_STATS.GATHER_TABLE_STATS(ownname => 'SES', tabname => 'SES_REQUEST', cascade => TRUE);
    DBMS_STATS.GATHER_TABLE_STATS(ownname => 'SES', tabname => 'SES_STICKER_REQUEST', cascade => TRUE);
    DBMS_STATS.GATHER_TABLE_STATS(ownname => 'SES', tabname => 'SES_APPROVAL_INSTANCE', cascade => TRUE);
    DBMS_STATS.GATHER_TABLE_STATS(ownname => 'SES', tabname => 'SES_APPROVAL_TASK_SIGNOFF', cascade => TRUE);
    DBMS_STATS.GATHER_TABLE_STATS(ownname => 'SES', tabname => 'SES_REQUEST_WORKER', cascade => TRUE);
    DBMS_STATS.GATHER_TABLE_STATS(ownname => 'SES', tabname => 'SES_REQUEST_PAYMENT', cascade => TRUE);
END;
/
