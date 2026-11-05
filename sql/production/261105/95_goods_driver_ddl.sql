-- ============================================================
-- PT Badak NGL - Secure Entry System (SES)
-- Production Deployment 261105 — 95_goods_driver_ddl.sql
--
-- DDL Idempoten Tabel SES_GOODS_DRIVER dan SEQ_SES_GOODS_DRIVER
-- untuk mendukung modul Goods Entry Permit (Izin Masuk Barang).
-- ============================================================

SET SERVEROUTPUT ON SIZE UNLIMITED

-- 1. Sequence SEQ_SES_GOODS_DRIVER
DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count FROM user_sequences WHERE sequence_name = 'SEQ_SES_GOODS_DRIVER';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE '
            CREATE SEQUENCE SEQ_SES_GOODS_DRIVER
                MINVALUE 1
                MAXVALUE 9999999999999999999999999999
                INCREMENT BY 1
                START WITH 1
                NOCACHE
                NOORDER
                NOCYCLE';
        DBMS_OUTPUT.PUT_LINE('Sequence SEQ_SES_GOODS_DRIVER berhasil dibuat.');
    ELSE
        DBMS_OUTPUT.PUT_LINE('Sequence SEQ_SES_GOODS_DRIVER sudah ada, lewati pembuatan.');
    END IF;
END;
/

-- 2. Tabel SES_GOODS_DRIVER
DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count FROM user_tables WHERE table_name = 'SES_GOODS_DRIVER';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE '
            CREATE TABLE SES_GOODS_DRIVER (
                GOODS_DRIVER_ID   NUMBER NOT NULL,
                GOODS_REQUEST_ID  NUMBER NOT NULL,
                LINE_NO           NUMBER NOT NULL,
                DRIVER_NAME       VARCHAR2(200) NOT NULL,
                DRIVER_BADGE      VARCHAR2(50),
                SIM_NO            VARCHAR2(50),
                SIM_CLASS         VARCHAR2(10),
                SIM_VALID_TO      DATE,
                SIM_FILE_ID       VARCHAR2(36),
                CREATED_BY        VARCHAR2(50),
                CREATED_DATE      DATE,
                UPDATED_BY        VARCHAR2(50),
                UPDATED_DATE      DATE,
                CONSTRAINT PK_SES_GOODS_DRIVER PRIMARY KEY (GOODS_DRIVER_ID)
            )';
        DBMS_OUTPUT.PUT_LINE('Tabel SES_GOODS_DRIVER berhasil dibuat.');
    ELSE
        DBMS_OUTPUT.PUT_LINE('Tabel SES_GOODS_DRIVER sudah ada, lewati pembuatan.');
    END IF;
END;
/

-- 3. Indeks FK / Search pada GOODS_REQUEST_ID
DECLARE
    e_col_indexed EXCEPTION;
    PRAGMA EXCEPTION_INIT(e_col_indexed, -1408);
    e_idx_exists  EXCEPTION;
    PRAGMA EXCEPTION_INIT(e_idx_exists, -955);
BEGIN
    EXECUTE IMMEDIATE 'CREATE INDEX IDX_SES_GOODS_DRV_REQ ON SES_GOODS_DRIVER(GOODS_REQUEST_ID)';
    DBMS_OUTPUT.PUT_LINE('Indeks IDX_SES_GOODS_DRV_REQ berhasil dibuat.');
EXCEPTION
    WHEN e_col_indexed OR e_idx_exists THEN
        DBMS_OUTPUT.PUT_LINE('Indeks pada GOODS_REQUEST_ID sudah ada, lewati pembuatan.');
END;
/
