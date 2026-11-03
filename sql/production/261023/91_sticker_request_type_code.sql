-- ============================================================
-- SES 91 — Sticker Request Enhancements & Vehicle Documents
-- Adds STICKER_TYPE_CODE, CONTRACT_NO, CONTRACT_EXPIRY_DATE,
-- REQUESTOR_TYPE to SES_STICKER_REQUEST, and creates
-- SES_STICKER_VEH_DOC table and sequence if missing.
-- ============================================================

-- 1. Alter SES_STICKER_REQUEST columns
DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count
      FROM user_tab_columns
     WHERE table_name = 'SES_STICKER_REQUEST'
       AND column_name = 'STICKER_TYPE_CODE';

    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE ses_sticker_request ADD (sticker_type_code VARCHAR2(30))';
        DBMS_OUTPUT.PUT_LINE('Column STICKER_TYPE_CODE added to SES_STICKER_REQUEST.');
    END IF;

    SELECT COUNT(*) INTO v_count
      FROM user_tab_columns
     WHERE table_name = 'SES_STICKER_REQUEST'
       AND column_name = 'CONTRACT_NO';

    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE ses_sticker_request ADD (contract_no VARCHAR2(100))';
        DBMS_OUTPUT.PUT_LINE('Column CONTRACT_NO added to SES_STICKER_REQUEST.');
    END IF;

    SELECT COUNT(*) INTO v_count
      FROM user_tab_columns
     WHERE table_name = 'SES_STICKER_REQUEST'
       AND column_name = 'CONTRACT_EXPIRY_DATE';

    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE ses_sticker_request ADD (contract_expiry_date DATE)';
        DBMS_OUTPUT.PUT_LINE('Column CONTRACT_EXPIRY_DATE added to SES_STICKER_REQUEST.');
    END IF;

    SELECT COUNT(*) INTO v_count
      FROM user_tab_columns
     WHERE table_name = 'SES_STICKER_REQUEST'
       AND column_name = 'REQUESTOR_TYPE';

    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE ses_sticker_request ADD (requestor_type VARCHAR2(30))';
        DBMS_OUTPUT.PUT_LINE('Column REQUESTOR_TYPE added to SES_STICKER_REQUEST.');
    END IF;
END;
/

-- Backfill sticker_type_code from existing vehicle row if any
UPDATE ses_sticker_request r
   SET r.sticker_type_code = (
       SELECT MIN(v.sticker_type_code)
         FROM ses_sticker_vehicle v
        WHERE v.sticker_request_id = r.sticker_request_id
   )
 WHERE r.sticker_type_code IS NULL
   AND EXISTS (
       SELECT 1 FROM ses_sticker_vehicle v WHERE v.sticker_request_id = r.sticker_request_id
   );

-- 2. Create SES_STICKER_VEH_DOC table and sequence
DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count FROM user_sequences WHERE sequence_name = 'SEQ_SES_STICKER_VEH_DOC';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'CREATE SEQUENCE SEQ_SES_STICKER_VEH_DOC START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE';
        DBMS_OUTPUT.PUT_LINE('Sequence SEQ_SES_STICKER_VEH_DOC created.');
    END IF;

    SELECT COUNT(*) INTO v_count FROM user_tables WHERE table_name = 'SES_STICKER_VEH_DOC';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'CREATE TABLE ses_sticker_veh_doc ('
            || 'sticker_veh_doc_id NUMBER NOT NULL, '
            || 'sticker_vehicle_id NUMBER NOT NULL, '
            || 'doc_code VARCHAR2(30) NOT NULL, '
            || 'file_id VARCHAR2(36), '
            || 'expiry_date DATE, '
            || 'created_by VARCHAR2(50), '
            || 'created_date DATE, '
            || 'updated_by VARCHAR2(50), '
            || 'updated_date DATE, '
            || 'CONSTRAINT pk_ses_sticker_veh_doc PRIMARY KEY (sticker_veh_doc_id), '
            || 'CONSTRAINT fk_ses_stk_veh_doc FOREIGN KEY (sticker_vehicle_id) REFERENCES ses_sticker_vehicle(sticker_vehicle_id)'
            || ')';
        EXECUTE IMMEDIATE 'CREATE INDEX idx_ses_stk_veh_doc_veh ON ses_sticker_veh_doc (sticker_vehicle_id)';
        DBMS_OUTPUT.PUT_LINE('Table SES_STICKER_VEH_DOC created.');
    END IF;
END;
/

COMMIT;
