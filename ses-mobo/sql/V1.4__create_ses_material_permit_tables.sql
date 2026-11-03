-- =================================================================================
-- Script    : V1.4__create_ses_material_permit_tables.sql
-- Modul     : Perizinan Material (Izin Masuk Barang, Izin Keluar, & Gate Pass)
-- Aplikasi  : Secure Entry System (SES) - PT Badak NGL
-- Sifat     : Idempotent PL/SQL Execution Block (Oracle Database Compatible)
-- =================================================================================

DECLARE
    v_count NUMBER := 0;
BEGIN
    -- 1. Tabel Master Akun Vendor Eksternal (Untuk Login Portal Internet via OTP + Password)
    SELECT COUNT(*) INTO v_count FROM USER_TABLES WHERE TABLE_NAME = 'SES_VENDOR_ACCOUNT';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE '
            CREATE TABLE SES_VENDOR_ACCOUNT (
                COMPANY_ID          VARCHAR2(30) PRIMARY KEY,
                COMPANY_NAME        VARCHAR2(150) NOT NULL,
                REGISTERED_EMAIL    VARCHAR2(100) NOT NULL UNIQUE,
                PHONE_NUMBER        VARCHAR2(30) NOT NULL,
                PASSWORD_HASH       VARCHAR2(255),
                IS_FIRST_LOGIN      CHAR(1) DEFAULT ''Y'' CHECK (IS_FIRST_LOGIN IN (''Y'', ''N'')),
                ACCOUNT_STATUS      VARCHAR2(15) DEFAULT ''ACTIVE'',
                CREATED_DATE        DATE DEFAULT SYSDATE
            )';
    END IF;

    -- 2. Tabel Header Pengajuan Perizinan Material (Izin Masuk, Izin Keluar, Gate Pass)
    SELECT COUNT(*) INTO v_count FROM USER_TABLES WHERE TABLE_NAME = 'SES_MAT_PERMIT_HDR';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE '
            CREATE TABLE SES_MAT_PERMIT_HDR (
                REQUEST_NO              VARCHAR2(30) PRIMARY KEY,
                SUB_MODULE_TYPE         VARCHAR2(20) NOT NULL CHECK (SUB_MODULE_TYPE IN (''ENTRY'', ''EXIT'', ''GATE_PASS'')),
                PERMIT_BASIS            VARCHAR2(20) NOT NULL CHECK (PERMIT_BASIS IN (''PO'', ''CA'', ''FACILITY_MEMO'', ''INTERNAL_ASSET'')),
                PARENT_ENTRY_REQ_NO     VARCHAR2(30),
                EXIT_REASON             VARCHAR2(100),
                REF_DOC_NO              VARCHAR2(50) NOT NULL,
                INPUT_CHANNEL           VARCHAR2(15) DEFAULT ''INTRANET'' CHECK (INPUT_CHANNEL IN (''INTRANET'', ''INTERNET'')),
                COMPANY_ID              VARCHAR2(30),
                VENDOR_NAME             VARCHAR2(150) NOT NULL,
                TARGET_DEPT_ID          VARCHAR2(20) NOT NULL,
                TARGET_SECTION_ID       VARCHAR2(20),
                DESTINATION_ZONE        VARCHAR2(20) NOT NULL CHECK (DESTINATION_ZONE IN (''ZONE_1'', ''ZONE_2'', ''GEDUNG_PUTIH'')),
                SPECIFIC_LOCATION       VARCHAR2(100) NOT NULL,
                EST_ARRIVAL_DATE        DATE NOT NULL,
                EST_ARRIVAL_TIME        VARCHAR2(5),
                MEMO_FILE_ID            NUMBER(10),
                CA_DOC_FILE_ID          NUMBER(10),
                HAS_EXPIRY_WARNING      CHAR(1) DEFAULT ''N'' CHECK (HAS_EXPIRY_WARNING IN (''Y'', ''N'')),
                VALID_FROM              DATE,
                VALID_TO                DATE,
                QR_CODE_TOKEN           VARCHAR2(100) UNIQUE,
                PERMIT_STATUS           VARCHAR2(30) DEFAULT ''DRAFT'' NOT NULL,
                CREATED_BY              VARCHAR2(50) NOT NULL,
                CREATED_DATE            DATE DEFAULT SYSDATE,
                UPDATED_BY              VARCHAR2(50),
                UPDATED_DATE            DATE,
                CONSTRAINT FK_MAT_PARENT_REQ FOREIGN KEY (PARENT_ENTRY_REQ_NO) REFERENCES SES_MAT_PERMIT_HDR(REQUEST_NO)
            )';
    END IF;

    -- 3. Tabel Detail PO & DO (Mendukung Multi-PO dalam 1 PT & Price Masking)
    SELECT COUNT(*) INTO v_count FROM USER_TABLES WHERE TABLE_NAME = 'SES_MAT_PERMIT_PO_DO';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE '
            CREATE TABLE SES_MAT_PERMIT_PO_DO (
                PERMIT_PO_ID            NUMBER(12) PRIMARY KEY,
                REQUEST_NO              VARCHAR2(30) NOT NULL,
                PO_NUMBER               VARCHAR2(50) NOT NULL,
                PO_ORDER_DATE           DATE,
                PROMISE_DELIVERY_DATE   DATE,
                PO_DESCRIPTION          VARCHAR2(500) NOT NULL,
                DO_NUMBER               VARCHAR2(50) NOT NULL,
                DO_DATE                 DATE NOT NULL,
                PO_ITEMS_SNAPSHOT       CLOB,
                UNIT_PRICE              NUMBER(18,2),
                PO_FILE_ID              NUMBER(10) NOT NULL,
                DO_FILE_ID              NUMBER(10) NOT NULL,
                CONSTRAINT FK_MAT_PODO_HDR FOREIGN KEY (REQUEST_NO) REFERENCES SES_MAT_PERMIT_HDR(REQUEST_NO)
            )';
    END IF;

    -- 4. Tabel Detail Kendaraan (Mendukung Multi-Kendaraan per Request & Flag KKB Bypass Admin)
    SELECT COUNT(*) INTO v_count FROM USER_TABLES WHERE TABLE_NAME = 'SES_MAT_PERMIT_VEHICLE';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE '
            CREATE TABLE SES_MAT_PERMIT_VEHICLE (
                VEHICLE_ITEM_ID         VARCHAR2(30) PRIMARY KEY,
                REQUEST_NO              VARCHAR2(30) NOT NULL,
                LICENSE_PLATE_NO        VARCHAR2(20) NOT NULL,
                VEHICLE_CATEGORY        VARCHAR2(30) NOT NULL,
                VEHICLE_BRAND           VARCHAR2(50) NOT NULL,
                STNK_5YR_EXPIRY         DATE NOT NULL,
                STNK_TAX_EXPIRY         DATE NOT NULL,
                STNK_FILE_ID            NUMBER(10) NOT NULL,
                IS_KIR_REQUIRED         CHAR(1) DEFAULT ''Y'' CHECK (IS_KIR_REQUIRED IN (''Y'', ''N'')),
                KIR_EXPIRY_DATE         DATE,
                KIR_FILE_ID             NUMBER(10),
                HAS_ACTIVE_KKB          CHAR(1) DEFAULT ''N'' CHECK (HAS_ACTIVE_KKB IN (''Y'', ''N'')),
                KKB_STICKER_NO          VARCHAR2(50),
                KKB_FILE_ID             NUMBER(10),
                ADMIN_SKIP_INSPECTION   CHAR(1) DEFAULT ''N'' CHECK (ADMIN_SKIP_INSPECTION IN (''Y'', ''N'')),
                CONSTRAINT FK_MAT_VEH_HDR FOREIGN KEY (REQUEST_NO) REFERENCES SES_MAT_PERMIT_HDR(REQUEST_NO)
            )';
    END IF;

    -- 5. Tabel Detail Pengemudi & Kenek (Mendukung Multi-Driver & Pembedaan SIMPER vs SIMPOL)
    SELECT COUNT(*) INTO v_count FROM USER_TABLES WHERE TABLE_NAME = 'SES_MAT_PERMIT_DRIVER';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE '
            CREATE TABLE SES_MAT_PERMIT_DRIVER (
                DRIVER_ITEM_ID          VARCHAR2(30) PRIMARY KEY,
                REQUEST_NO              VARCHAR2(30) NOT NULL,
                PERSONNEL_ROLE          VARCHAR2(20) NOT NULL CHECK (PERSONNEL_ROLE IN (''DRIVER'', ''HELPER'')),
                DRIVER_ORIGIN_TYPE      VARCHAR2(20) NOT NULL CHECK (DRIVER_ORIGIN_TYPE IN (''KKB_CONTRACTOR'', ''EXTERNAL_NON_KKB'')),
                BADGE_NO                VARCHAR2(20),
                NIK_KTP                 VARCHAR2(20),
                DRIVER_NAME             VARCHAR2(100) NOT NULL,
                ID_CARD_FILE_ID         NUMBER(10) NOT NULL,
                LICENSE_TYPE            VARCHAR2(20),
                LICENSE_NO              VARCHAR2(50),
                SIM_EXPIRY_DATE         DATE,
                SIM_FILE_ID             NUMBER(10),
                CONSTRAINT FK_MAT_DRV_HDR FOREIGN KEY (REQUEST_NO) REFERENCES SES_MAT_PERMIT_HDR(REQUEST_NO)
            )';
    END IF;

    -- 6. Tabel Log Screening & Siklus Check-In / Check-Out Posko (Wajib Re-Screening Pasca Check-Out)
    SELECT COUNT(*) INTO v_count FROM USER_TABLES WHERE TABLE_NAME = 'SES_MAT_PERMIT_SCREENING_LOG';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE '
            CREATE TABLE SES_MAT_PERMIT_SCREENING_LOG (
                SCREENING_LOG_ID        NUMBER(15) PRIMARY KEY,
                REQUEST_NO              VARCHAR2(30) NOT NULL,
                VEHICLE_ITEM_ID         VARCHAR2(30) NOT NULL,
                DRIVER_ITEM_ID          VARCHAR2(30) NOT NULL,
                ARRIVAL_TIMESTAMP       TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL,
                SCREENING_DATE          DATE DEFAULT SYSDATE NOT NULL,
                SCREENED_BY_USER_ID     VARCHAR2(30) NOT NULL,
                CHECKLIST_JSON_DATA     CLOB,
                SCREENING_RESULT        VARCHAR2(15) NOT NULL CHECK (SCREENING_RESULT IN (''APPROVED'', ''REJECTED'', ''BYPASSED'')),
                SCREENING_NOTES         VARCHAR2(500),
                VISITOR_BADGE_NO        VARCHAR2(30),
                IS_KTP_HELD             CHAR(1) DEFAULT ''N'' CHECK (IS_KTP_HELD IN (''Y'', ''N'')),
                CHECKIN_TIMESTAMP       TIMESTAMP,
                CHECKOUT_TIMESTAMP      TIMESTAMP,
                CHECKOUT_BY_USER_ID     VARCHAR2(30),
                VISIT_CYCLE_STATUS      VARCHAR2(20) NOT NULL CHECK (VISIT_CYCLE_STATUS IN (''SCREENED'', ''CHECKED_IN'', ''CHECKED_OUT'', ''REJECTED'')),
                CONSTRAINT FK_SCR_HDR FOREIGN KEY (REQUEST_NO) REFERENCES SES_MAT_PERMIT_HDR(REQUEST_NO),
                CONSTRAINT FK_SCR_VEH FOREIGN KEY (VEHICLE_ITEM_ID) REFERENCES SES_MAT_PERMIT_VEHICLE(VEHICLE_ITEM_ID),
                CONSTRAINT FK_SCR_DRV FOREIGN KEY (DRIVER_ITEM_ID) REFERENCES SES_MAT_PERMIT_DRIVER(DRIVER_ITEM_ID)
            )';
    END IF;

    -- Indeks Kinerja Tambahan
    SELECT COUNT(*) INTO v_count FROM USER_INDEXES WHERE INDEX_NAME = 'IDX_SES_MAT_HDR_STATUS';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'CREATE INDEX IDX_SES_MAT_HDR_STATUS ON SES_MAT_PERMIT_HDR(PERMIT_STATUS, CREATED_DATE DESC)';
    END IF;

    SELECT COUNT(*) INTO v_count FROM USER_INDEXES WHERE INDEX_NAME = 'IDX_SES_MAT_PO_NO';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'CREATE INDEX IDX_SES_MAT_PO_NO ON SES_MAT_PERMIT_PO_DO(PO_NUMBER)';
    END IF;

    SELECT COUNT(*) INTO v_count FROM USER_INDEXES WHERE INDEX_NAME = 'IDX_SES_MAT_VEH_PLATE';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'CREATE INDEX IDX_SES_MAT_VEH_PLATE ON SES_MAT_PERMIT_VEHICLE(LICENSE_PLATE_NO)';
    END IF;
END;
/

