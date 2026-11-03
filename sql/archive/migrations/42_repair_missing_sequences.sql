-- SES / platform sequence repair for an existing ELLPRD schema.
--
-- This script is intentionally NON-DESTRUCTIVE:
--   * it creates only sequences that are absent;
--   * it never drops or truncates tables;
--   * each start value is computed above the current maximum ID.
--
-- Run as SES, after taking the normal DBA backup, with SQL*Plus/SQLcl:
--   @repair_missing_sequences.sql
--
-- Do NOT run the numbered *ddl.sql files just to restore sequences: those
-- scripts intentionally drop and recreate SES tables.

SET SERVEROUTPUT ON
SET VERIFY OFF

DECLARE
    v_start NUMBER;

    PROCEDURE ensure_sequence(p_name VARCHAR2, p_start NUMBER) IS
        v_exists NUMBER;
        v_start  NUMBER := GREATEST(NVL(TRUNC(p_start), 1), 1);
    BEGIN
        SELECT COUNT(*)
          INTO v_exists
          FROM user_sequences
         WHERE sequence_name = UPPER(p_name);

        IF v_exists = 0 THEN
            EXECUTE IMMEDIATE
                'CREATE SEQUENCE ' || p_name ||
                ' START WITH ' || TO_CHAR(v_start, 'FM9999999999999999990') ||
                ' INCREMENT BY 1 NOCACHE NOCYCLE';
            DBMS_OUTPUT.PUT_LINE('CREATED ' || p_name || ' START WITH ' || v_start);
        ELSE
            DBMS_OUTPUT.PUT_LINE('EXISTS  ' || p_name);
        END IF;
    END;

BEGIN
    -- Platform sequences used by the SES seed scripts.
    SELECT NVL(MAX(page_id), 0) + 1 INTO v_start FROM app_page;
    ensure_sequence('SEQ_APP_PAGE', v_start);

    SELECT NVL(MAX(menu_id), 0) + 1 INTO v_start FROM app_menu;
    ensure_sequence('SEQ_APP_MENU', v_start);

    SELECT NVL(MAX(role_id), 0) + 1 INTO v_start FROM app_role;
    ensure_sequence('SEQ_APP_ROLE', v_start);

    SELECT NVL(MAX(job_id), 0) + 1 INTO v_start FROM app_job;
    ensure_sequence('SEQ_APP_JOB', v_start);

    -- Approval configuration and runtime.
    SELECT NVL(MAX(chain_id), 0) + 1 INTO v_start FROM ses_approval_chain;
    ensure_sequence('SEQ_SES_APPROVAL_CHAIN', v_start);

    SELECT NVL(MAX(step_id), 0) + 1 INTO v_start FROM ses_approval_step;
    ensure_sequence('SEQ_SES_APPROVAL_STEP', v_start);

    SELECT NVL(MAX(assignee_id), 0) + 1 INTO v_start FROM ses_approval_assignee;
    ensure_sequence('SEQ_SES_APPROVAL_ASSIGNEE', v_start);

    SELECT NVL(MAX(instance_id), 0) + 1 INTO v_start FROM ses_approval_instance;
    ensure_sequence('SEQ_SES_APPROVAL_INSTANCE', v_start);

    SELECT NVL(MAX(task_id), 0) + 1 INTO v_start FROM ses_approval_task;
    ensure_sequence('SEQ_SES_APPROVAL_TASK', v_start);

    SELECT NVL(MAX(task_assignee_id), 0) + 1 INTO v_start FROM ses_approval_task_assignee;
    ensure_sequence('SEQ_SES_APPROVAL_TASK_ASSIGNEE', v_start);

    SELECT NVL(MAX(task_assignee_def_id), 0) + 1 INTO v_start FROM ses_approval_task_assignee_def;
    ensure_sequence('SEQ_SES_APPR_TASK_ASSIGNEE_DEF', v_start);

    SELECT NVL(MAX(history_id), 0) + 1 INTO v_start FROM ses_approval_history;
    ensure_sequence('SEQ_SES_APPROVAL_HISTORY', v_start);

    SELECT NVL(MAX(form_id), 0) + 1 INTO v_start FROM ses_form;
    ensure_sequence('SEQ_SES_FORM', v_start);

    SELECT NVL(MAX(map_id), 0) + 1 INTO v_start FROM ses_form_approval_map;
    ensure_sequence('SEQ_SES_FORM_APPROVAL_MAP', v_start);

    SELECT NVL(MAX(step_task_id), 0) + 1 INTO v_start FROM ses_approval_step_task;
    ensure_sequence('SEQ_SES_APPROVAL_STEP_TASK', v_start);

    SELECT NVL(MAX(checkpoint_id), 0) + 1 INTO v_start FROM ses_approval_task_chkp;
    ensure_sequence('SEQ_SES_APPROVAL_TASK_CHKP', v_start);

    -- This sequence is created by the baseline DDL for schema parity. The
    -- entity uses user_id as an assigned String ID, so it is not consumed.
    ensure_sequence('SEQ_SES_USER_EXT', 1);

    -- Request module.
    SELECT NVL(MAX(request_id), 0) + 1 INTO v_start FROM ses_request;
    ensure_sequence('SEQ_SES_REQUEST', v_start);

    SELECT NVL(MAX(worker_id), 0) + 1 INTO v_start FROM ses_request_worker;
    ensure_sequence('SEQ_SES_REQUEST_WORKER', v_start);

    SELECT NVL(MAX(worker_doc_id), 0) + 1 INTO v_start FROM ses_request_worker_doc;
    ensure_sequence('SEQ_SES_REQUEST_WORKER_DOC', v_start);

    SELECT NVL(MAX(TO_NUMBER(REGEXP_SUBSTR(request_no, '[0-9]+$'))), 0) + 1
      INTO v_start
      FROM ses_request
     WHERE REGEXP_LIKE(request_no, '[0-9]+$');
    ensure_sequence('SEQ_SES_REQUEST_NO', v_start);

    -- Payment module.
    SELECT NVL(MAX(payment_id), 0) + 1 INTO v_start FROM ses_request_payment;
    ensure_sequence('SEQ_SES_REQUEST_PAYMENT', v_start);

    SELECT NVL(MAX(history_id), 0) + 1 INTO v_start FROM ses_request_payment_history;
    ensure_sequence('SEQ_SES_REQUEST_PAY_HISTORY', v_start);

    -- Scheduling module.
    SELECT NVL(MAX(schedule_id), 0) + 1 INTO v_start FROM ses_request_schedule;
    ensure_sequence('SEQ_SES_REQUEST_SCHEDULE', v_start);

    SELECT NVL(MAX(history_id), 0) + 1 INTO v_start FROM ses_request_schedule_history;
    ensure_sequence('SEQ_SES_REQUEST_SCHED_HIST', v_start);

    -- Sticker module.
    SELECT NVL(MAX(sticker_request_id), 0) + 1 INTO v_start FROM ses_sticker_request;
    ensure_sequence('SEQ_SES_STICKER_REQ', v_start);

    SELECT NVL(MAX(sticker_vehicle_id), 0) + 1 INTO v_start FROM ses_sticker_vehicle;
    ensure_sequence('SEQ_SES_STICKER_VEHICLE', v_start);

    SELECT NVL(MAX(TO_NUMBER(REGEXP_SUBSTR(request_no, '[0-9]+$'))), 0) + 1
      INTO v_start
      FROM ses_sticker_request
     WHERE REGEXP_LIKE(request_no, '[0-9]+$');
    ensure_sequence('SEQ_SES_STICKER_REQ_NO', v_start);
END;
/

SELECT sequence_name, last_number
  FROM user_sequences
 WHERE sequence_name IN (
       'SEQ_APP_PAGE', 'SEQ_APP_MENU', 'SEQ_APP_ROLE', 'SEQ_APP_JOB',
       'SEQ_SES_APPROVAL_CHAIN', 'SEQ_SES_APPROVAL_STEP',
       'SEQ_SES_APPROVAL_ASSIGNEE', 'SEQ_SES_APPROVAL_INSTANCE',
       'SEQ_SES_APPROVAL_TASK', 'SEQ_SES_APPROVAL_TASK_ASSIGNEE',
       'SEQ_SES_APPR_TASK_ASSIGNEE_DEF', 'SEQ_SES_APPROVAL_HISTORY',
       'SEQ_SES_USER_EXT', 'SEQ_SES_FORM', 'SEQ_SES_FORM_APPROVAL_MAP',
       'SEQ_SES_APPROVAL_STEP_TASK', 'SEQ_SES_APPROVAL_TASK_CHKP',
       'SEQ_SES_REQUEST', 'SEQ_SES_REQUEST_NO',
       'SEQ_SES_REQUEST_WORKER', 'SEQ_SES_REQUEST_WORKER_DOC',
       'SEQ_SES_REQUEST_PAYMENT', 'SEQ_SES_REQUEST_PAY_HISTORY',
       'SEQ_SES_REQUEST_SCHEDULE', 'SEQ_SES_REQUEST_SCHED_HIST',
       'SEQ_SES_STICKER_REQ', 'SEQ_SES_STICKER_REQ_NO',
       'SEQ_SES_STICKER_VEHICLE'
   )
 ORDER BY sequence_name;

EXIT;
