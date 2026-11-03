-- ============================================================
-- SES — Stiker Kendaraan (Vehicle Sticker Request) module DDL — Oracle 11g / 12c+
-- Run as schema owner, AFTER 26_payment_generalize_alter.sql.
--
-- This script is RE-RUNNABLE: it drops existing sticker objects first,
-- then recreates them.
--
-- Owned by com.badaklng.sticker. A specialised sibling of the request
-- module (com.badaklng.request/ses_request): one header row per
-- submission (ses_sticker_request) plus N vehicle rows (ses_sticker_vehicle),
-- each optionally carrying its own "lampiran surat" upload. Always routes
-- through ONE fixed default approval chain (formCode "SES_STICKER_REQUEST",
-- admin-mapped via the existing /approval/map screen — no per-type routing,
-- so no matchKey dropdown is offered on the form). Payment is mandatory and
-- flows through the SAME generalised payment engine ses_request_payment now
-- uses (business_type = 'SES_STICKER_REQUEST') — see
-- com.badaklng.sticker.spi.StickerPaymentDescriptor.
-- ============================================================

-- ---- Reset: drop existing sticker tables & sequences ----
BEGIN
    FOR t IN (
        SELECT table_name FROM user_tables WHERE table_name IN (
            'SES_STICKER_VEHICLE', 'SES_STICKER_REQUEST', 'SES_STICKER_TYPE'
        )
    ) LOOP
        EXECUTE IMMEDIATE 'DROP TABLE ' || t.table_name || ' CASCADE CONSTRAINTS PURGE';
    END LOOP;
END;
/
BEGIN
    FOR s IN (
        SELECT sequence_name FROM user_sequences WHERE sequence_name IN (
            'SEQ_SES_STICKER_REQ', 'SEQ_SES_STICKER_REQ_NO', 'SEQ_SES_STICKER_VEHICLE'
        )
    ) LOOP
        EXECUTE IMMEDIATE 'DROP SEQUENCE ' || s.sequence_name;
    END LOOP;
END;
/

-- ---- Sequences ----
CREATE SEQUENCE SEQ_SES_STICKER_REQ     START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;
CREATE SEQUENCE SEQ_SES_STICKER_REQ_NO  START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;
CREATE SEQUENCE SEQ_SES_STICKER_VEHICLE START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;

-- ---- SES_STICKER_TYPE ("Jenis Sticker" master) ----
-- Natural key, same convention as ses_request_type. Carries its own price —
-- the total amount due for a sticker request is a per-app_parameter base
-- amount (SES_STICKER_BASE_AMOUNT) plus the sum of each vehicle row's own
-- jenis-stiker price (see StickerService#amountDue).
CREATE TABLE ses_sticker_type (
    sticker_type_code VARCHAR2(30)  NOT NULL,
    sticker_type_name VARCHAR2(200) NOT NULL,
    price              NUMBER(15)   DEFAULT 0 NOT NULL,
    order_by           NUMBER       DEFAULT 0 NOT NULL,
    active_flag        VARCHAR2(1)  DEFAULT 'Y' NOT NULL,
    created_by         VARCHAR2(50),
    created_date       DATE,
    updated_by         VARCHAR2(50),
    updated_date       DATE,
    CONSTRAINT pk_ses_sticker_type PRIMARY KEY (sticker_type_code)
);

-- ---- SES_STICKER_REQUEST ----
-- One header row per submission — mirrors ses_request's shape/conventions
-- (status is this module's own plain-String concept, requestor_user_id
-- stores the app_user USERNAME not user_id, approval_instance_id is a soft
-- pointer into the approval module).
CREATE TABLE ses_sticker_request (
    sticker_request_id NUMBER        NOT NULL,
    request_no          VARCHAR2(30) NOT NULL,
    requestor_name       VARCHAR2(200) NOT NULL,
    badge                VARCHAR2(50),
    department           VARCHAR2(200),
    section              VARCHAR2(200),
    instance             VARCHAR2(200),
    phone_no             VARCHAR2(50),
    work_phone_no        VARCHAR2(50),
    address              VARCHAR2(500),
    status               VARCHAR2(20) DEFAULT 'PENDING' NOT NULL,
    requestor_user_id    VARCHAR2(36) NOT NULL,
    approval_instance_id NUMBER,
    created_by           VARCHAR2(50),
    created_date         DATE,
    updated_by           VARCHAR2(50),
    updated_date         DATE,
    CONSTRAINT pk_ses_sticker_request PRIMARY KEY (sticker_request_id),
    CONSTRAINT uq_ses_sticker_request_no UNIQUE (request_no),
    CONSTRAINT ck_ses_sticker_req_status CHECK (status IN ('DRAFT', 'PENDING', 'APPROVED', 'REJECTED', 'CANCELLED'))
);

-- ---- SES_STICKER_VEHICLE ("Data Kendaraan" repeat rows) ----
-- One row per vehicle on a request — mirrors ses_request_worker's shape
-- (a real FK to its header, full audit columns for consistency with that
-- table, unlike the no-audit ses_request_worker_doc). letter_file_id is a
-- plain pointer into app_file, same convention as every other upload in
-- this schema (no FK — shared table).
CREATE TABLE ses_sticker_vehicle (
    sticker_vehicle_id  NUMBER        NOT NULL,
    sticker_request_id  NUMBER        NOT NULL,
    line_no             NUMBER        NOT NULL,
    sticker_type_code   VARCHAR2(30)  NOT NULL,
    police_no           VARCHAR2(30)  NOT NULL,
    vehicle_type        VARCHAR2(100),
    owner_name          VARCHAR2(200),
    brand               VARCHAR2(100),
    color               VARCHAR2(50),
    year_made           VARCHAR2(4),
    letter_file_id      VARCHAR2(36),
    created_by          VARCHAR2(50),
    created_date        DATE,
    updated_by          VARCHAR2(50),
    updated_date        DATE,
    CONSTRAINT pk_ses_sticker_vehicle PRIMARY KEY (sticker_vehicle_id),
    CONSTRAINT fk_ses_sticker_veh_req FOREIGN KEY (sticker_request_id) REFERENCES ses_sticker_request(sticker_request_id)
);

-- ---- Indexes ----
CREATE INDEX idx_ses_sticker_req_user   ON ses_sticker_request(requestor_user_id);
CREATE INDEX idx_ses_sticker_veh_req    ON ses_sticker_vehicle(sticker_request_id);
CREATE INDEX idx_ses_sticker_veh_type   ON ses_sticker_vehicle(sticker_type_code);

COMMIT;
