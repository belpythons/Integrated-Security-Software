-- ============================================================
-- SES — New Request module DDL — Oracle 11g / 12c+
-- Run as schema owner (e.g. ses_user), AFTER 02_approval_ddl.sql.
--
-- This script is RE-RUNNABLE: it drops existing SES request
-- objects first, then recreates them.
--
-- Owned by com.badaklng.request. This module is the reference
-- consumer of the approval SPI (see RequestApprovalBridge) —
-- SES_REQUEST_TYPE doubles as the descriptor's matchKeys() source,
-- so adding a request type or rerouting one to a different
-- approval chain never requires a redeploy.
--
-- Uploads (worker documents, proof of payment) go through the
-- shared APP_FILE table (01_app_tables.sql) via AppFileService —
-- file_id columns below are plain VARCHAR2(36) pointers, not FKs
-- (APP_FILE is app-wide shared storage, same no-DB-FK convention
-- as APP_USER / APP_POSITION_VIEW elsewhere in this schema).
--
-- PRODUCTION VARIANT: SES_REQUEST_WORKER.badge/department/section
-- (dev's 11_request_worker_alter.sql ALTER TABLE ADD) are declared
-- directly on the table below instead — see ses/sql/production/README.md.
-- ============================================================

-- ---- Reset: drop existing SES request tables & sequences ----
BEGIN
    FOR t IN (
        SELECT table_name FROM user_tables WHERE table_name IN (
            'SES_REQUEST_WORKER_DOC', 'SES_REQUEST_WORKER', 'SES_REQUEST',
            'SES_REQUEST_TYPE_DOC', 'SES_DOC_REQUIREMENT', 'SES_REQUEST_TYPE',
            'SES_ZONE'
        )
    ) LOOP
        EXECUTE IMMEDIATE 'DROP TABLE ' || t.table_name || ' CASCADE CONSTRAINTS PURGE';
    END LOOP;
END;
/
BEGIN
    FOR s IN (
        SELECT sequence_name FROM user_sequences WHERE sequence_name IN (
            'SEQ_SES_REQUEST', 'SEQ_SES_REQUEST_NO', 'SEQ_SES_REQUEST_WORKER',
            'SEQ_SES_REQUEST_WORKER_DOC'
        )
    ) LOOP
        EXECUTE IMMEDIATE 'DROP SEQUENCE ' || s.sequence_name;
    END LOOP;
END;
/

-- ---- Sequences ----
CREATE SEQUENCE SEQ_SES_REQUEST            START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;
-- Separate from SEQ_SES_REQUEST so request_no numbering is never affected
-- by unrelated PK churn (e.g. a rolled-back insert never burns a visible
-- REQ-#### number).
CREATE SEQUENCE SEQ_SES_REQUEST_NO         START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;
CREATE SEQUENCE SEQ_SES_REQUEST_WORKER     START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;
CREATE SEQUENCE SEQ_SES_REQUEST_WORKER_DOC START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;

-- ---- SES_REQUEST_TYPE ----
-- "Types of Request" — GUI-managed at /admin/request-type. Also the
-- routing dimension for approval: RequestApprovalBridge.matchKeys() reads
-- this table live and exposes (type_code, type_name) to the approval
-- module's mapping screen. Adding a type here makes it selectable on the
-- request form AND as an approval override target — no redeploy.
CREATE TABLE ses_request_type (
    type_code          VARCHAR2(30)  NOT NULL,
    type_name          VARCHAR2(200) NOT NULL,
    needs_payment_flag CHAR(1) DEFAULT 'N' NOT NULL,
    payment_amount      NUMBER(15),          -- configured amount (IDR, whole Rupiah) for this type; null = not configured
    order_by           NUMBER DEFAULT 0 NOT NULL,
    active_flag        CHAR(1) DEFAULT 'Y' NOT NULL,
    created_by         VARCHAR2(50),
    created_date       DATE,
    updated_by         VARCHAR2(50),
    updated_date       DATE,
    CONSTRAINT pk_ses_request_type PRIMARY KEY (type_code),
    CONSTRAINT ck_ses_request_type_pay CHECK (needs_payment_flag IN ('Y', 'N'))
);

-- ---- SES_DOC_REQUIREMENT ----
-- Master list of document kinds a worker may need to upload (KTP, Kartu
-- Keluarga, ...). GUI-managed alongside request types.
CREATE TABLE ses_doc_requirement (
    doc_code     VARCHAR2(30)  NOT NULL,
    doc_name     VARCHAR2(200) NOT NULL,
    doc_desc     VARCHAR2(500),
    active_flag  CHAR(1) DEFAULT 'Y' NOT NULL,
    created_by   VARCHAR2(50),
    created_date DATE,
    updated_by   VARCHAR2(50),
    updated_date DATE,
    CONSTRAINT pk_ses_doc_requirement PRIMARY KEY (doc_code)
);

-- ---- SES_REQUEST_TYPE_DOC ----
-- Which documents a request type demands, PER WORKER. Drives both the
-- expanding requirement card on type selection and the dynamic per-worker
-- upload slots in the Worker Data table. Proof of Payment is NOT modeled
-- here — it is the sole REQUEST-level upload, gated by
-- SES_REQUEST_TYPE.needs_payment_flag instead.
CREATE TABLE ses_request_type_doc (
    type_code      VARCHAR2(30) NOT NULL,
    doc_code       VARCHAR2(30) NOT NULL,
    mandatory_flag CHAR(1) DEFAULT 'Y' NOT NULL,
    order_by       NUMBER DEFAULT 0 NOT NULL,
    created_by     VARCHAR2(50),
    created_date   DATE,
    updated_by     VARCHAR2(50),
    updated_date   DATE,
    CONSTRAINT pk_ses_request_type_doc PRIMARY KEY (type_code, doc_code),
    CONSTRAINT fk_ses_rtd_type FOREIGN KEY (type_code) REFERENCES ses_request_type(type_code) ON DELETE CASCADE,
    CONSTRAINT fk_ses_rtd_doc  FOREIGN KEY (doc_code)  REFERENCES ses_doc_requirement(doc_code),
    CONSTRAINT ck_ses_rtd_mandatory CHECK (mandatory_flag IN ('Y', 'N'))
);

-- ---- SES_ZONE ----
-- "Zone Access" options. Single zone per request (plain column on
-- SES_REQUEST) — a contractor needing multiple zones files multiple
-- requests.
CREATE TABLE ses_zone (
    zone_code    VARCHAR2(20)  NOT NULL,
    zone_name    VARCHAR2(100) NOT NULL,
    order_by     NUMBER DEFAULT 0 NOT NULL,
    active_flag  CHAR(1) DEFAULT 'Y' NOT NULL,
    created_by   VARCHAR2(50),
    created_date DATE,
    updated_by   VARCHAR2(50),
    updated_date DATE,
    CONSTRAINT pk_ses_zone PRIMARY KEY (zone_code)
);

-- ---- SES_REQUEST ----
-- One row per submitted request. Edit-while-PENDING SUPERSEDES the
-- running approval instance (cancel it, start a fresh one at step 1) —
-- see RequestService.saveAndSubmit — so approval_instance_id always
-- points at the CURRENT instance, never a stale one.
CREATE TABLE ses_request (
    request_id         NUMBER        NOT NULL,
    request_no         VARCHAR2(30)  NOT NULL,
    type_code          VARCHAR2(30)  NOT NULL,
    contract_no        VARCHAR2(100),          -- freetext allowed; NOT an FK (CONTRACT_V is read-only, granted from another schema)
    contract_type      VARCHAR2(20),
    contract_title     VARCHAR2(300),
    zone_code          VARCHAR2(20),
    instance           VARCHAR2(200),
    address            VARCHAR2(500),
    start_period       DATE,
    finish_period      DATE,
    payment_file_id    VARCHAR2(36),           -- -> app_file.file_id, no FK (shared table)
    status              VARCHAR2(20) DEFAULT 'PENDING' NOT NULL,
    requestor_user_id   VARCHAR2(36)  NOT NULL,
    approval_instance_id NUMBER,
    created_by          VARCHAR2(50),
    created_date        DATE,
    updated_by           VARCHAR2(50),
    updated_date         DATE,
    CONSTRAINT pk_ses_request PRIMARY KEY (request_id),
    CONSTRAINT uq_ses_request_no UNIQUE (request_no),
    CONSTRAINT fk_ses_request_type FOREIGN KEY (type_code) REFERENCES ses_request_type(type_code),
    CONSTRAINT fk_ses_request_zone FOREIGN KEY (zone_code) REFERENCES ses_zone(zone_code),
    CONSTRAINT ck_ses_request_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED')),
    CONSTRAINT ck_ses_request_period CHECK (finish_period IS NULL OR start_period IS NULL OR finish_period >= start_period)
);

-- ---- SES_REQUEST_WORKER ----
-- Worker Data table rows. UNIQUE(request_id, nik) blocks listing the same
-- person twice on one request. NIK format (16 digits, numeric) is
-- validated in RequestService, not by a DB CHECK — Oracle 11g regex CHECK
-- constraints are workable but the readable-error-message requirement
-- (reject before hitting the DB) makes app-layer validation the right
-- home for it.
CREATE TABLE ses_request_worker (
    worker_id    NUMBER        NOT NULL,
    request_id   NUMBER        NOT NULL,
    line_no      NUMBER        NOT NULL,
    worker_name  VARCHAR2(200) NOT NULL,
    nik          VARCHAR2(16)  NOT NULL,
    jabatan      VARCHAR2(200),
    gender       CHAR(1),
    badge        VARCHAR2(50),   -- employee badge/id no.; optionally autofilled from APP_POSITION_VIEW.HOLDER_EMPL_ID
    department   VARCHAR2(200),  -- optionally autofilled from APP_POSITION_VIEW.DEPT_DESC
    section      VARCHAR2(200),  -- optionally autofilled from APP_POSITION_VIEW.SECTION_DESC
    created_by   VARCHAR2(50),
    created_date DATE,
    updated_by   VARCHAR2(50),
    updated_date DATE,
    CONSTRAINT pk_ses_request_worker PRIMARY KEY (worker_id),
    CONSTRAINT uq_ses_request_worker_nik UNIQUE (request_id, nik),
    CONSTRAINT fk_ses_worker_request FOREIGN KEY (request_id) REFERENCES ses_request(request_id) ON DELETE CASCADE,
    CONSTRAINT ck_ses_worker_gender CHECK (gender IN ('M', 'F'))
);

-- ---- SES_REQUEST_WORKER_DOC ----
-- One uploaded file per (worker, document requirement). UNIQUE prevents
-- double-upload of the same document kind for the same worker; re-upload
-- replaces the row (old app_file row is deleted via AppFileService).
CREATE TABLE ses_request_worker_doc (
    worker_doc_id NUMBER       NOT NULL,
    worker_id     NUMBER       NOT NULL,
    doc_code      VARCHAR2(30) NOT NULL,
    file_id       VARCHAR2(36) NOT NULL,   -- -> app_file.file_id, no FK (shared table)
    CONSTRAINT pk_ses_request_worker_doc PRIMARY KEY (worker_doc_id),
    CONSTRAINT uq_ses_worker_doc UNIQUE (worker_id, doc_code),
    CONSTRAINT fk_ses_wdoc_worker FOREIGN KEY (worker_id) REFERENCES ses_request_worker(worker_id) ON DELETE CASCADE,
    CONSTRAINT fk_ses_wdoc_doc    FOREIGN KEY (doc_code)  REFERENCES ses_doc_requirement(doc_code)
);

-- ---- Indexes ----
CREATE INDEX idx_ses_rtd_doc          ON ses_request_type_doc(doc_code);
CREATE INDEX idx_ses_request_type     ON ses_request(type_code);
CREATE INDEX idx_ses_request_zone     ON ses_request(zone_code);
CREATE INDEX idx_ses_request_status   ON ses_request(status);
CREATE INDEX idx_ses_request_requestor ON ses_request(requestor_user_id);
CREATE INDEX idx_ses_request_instance ON ses_request(approval_instance_id);
CREATE INDEX idx_ses_worker_request   ON ses_request_worker(request_id);
CREATE INDEX idx_ses_wdoc_worker      ON ses_request_worker_doc(worker_id);
CREATE INDEX idx_ses_wdoc_doc         ON ses_request_worker_doc(doc_code);

COMMIT;
