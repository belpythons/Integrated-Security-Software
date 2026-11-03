-- =============================================================================
-- SES (Secure Entry System) — Production Safe Schema & Data Synchronizer
-- Target Database: Oracle Database 10g / 11g / 12c / 19c
-- Target Schema: SES Application Schema (e.g., ses / elldev / ellprd)
--
-- CHARACTERISTICS (NON-DESTRUCTIVE & IDEMPOTENT):
--   - 100% AMAN UNTUK DATA PRODUKSI YANG SUDAH BERJALAN (LIVE).
--   - TIDAK ADA 'DROP TABLE', TIDAK ADA 'DROP SEQUENCE', TIDAK ADA 'DELETE FROM'
--     pada tabel transaksi maupun data operasional yang sudah ada.
--   - Skrip ini bekerja secara SINKRONISASI (upsert & add if missing):
--       1. Tabel yang sudah ada DIPERTAHANKAN beserta seluruh datanya. Kolom
--          baru yang belum ada akan ditambahkan (ALTER TABLE ADD), tipe data
--          dilebarkan bila perlu (misal: NIK 30 digit untuk passport).
--       2. Sequence yang sudah ada DIBIARKAN (tidak di-reset) agar tidak bentrok
--          dengan ID data lama. Sequence baru dibuat dimulai dari MAX(id) + 1.
--       3. Database View diperbarui (CREATE OR REPLACE VIEW) ke versi final.
--       4. Data master, roles, pages, menu, dan parameter disinkronisasi
--          menggunakan NOT EXISTS / MERGE tanpa menimpa konfigurasi yang sudah
--          diubah oleh administrator di lingkungan produksi.
--       5. Rantai approval diperiksa secara pintar: bila sudah 7 tahap serial,
--          dibiarkan utuh. Instance approval yang sedang berjalan (PENDING)
--          TIDAK terganggu.
-- =============================================================================

SET DEFINE OFF;
SET SERVEROUTPUT ON;
SET FEEDBACK ON;

PROMPT =========================================================================
PROMPT [SES SYNC] Step 1/8: Synchronizing Sequences (Non-destructive)
PROMPT =========================================================================

DECLARE
    PROCEDURE ensure_sequence(p_seq VARCHAR2, p_table VARCHAR2, p_col VARCHAR2) IS
        v_cnt      NUMBER;
        v_start    NUMBER := 1;
        v_max_id   NUMBER := 0;
        v_curr     NUMBER := 0;
        v_diff     NUMBER := 0;
    BEGIN
        SELECT COUNT(*) INTO v_cnt FROM user_sequences WHERE sequence_name = UPPER(p_seq);
        IF v_cnt = 0 THEN
            IF p_table IS NOT NULL AND p_col IS NOT NULL THEN
                BEGIN
                    EXECUTE IMMEDIATE 'SELECT NVL(MAX(' || p_col || '), 0) + 1 FROM ' || p_table INTO v_start;
                EXCEPTION
                    WHEN OTHERS THEN v_start := 1;
                END;
            END IF;
            v_start := GREATEST(NVL(TRUNC(v_start), 1), 1);
            EXECUTE IMMEDIATE 'CREATE SEQUENCE ' || p_seq || ' START WITH ' || v_start || ' INCREMENT BY 1 NOCACHE NOCYCLE';
            DBMS_OUTPUT.PUT_LINE('  [+] Created sequence: ' || p_seq || ' (START WITH ' || v_start || ')');
        ELSE
            -- Sequence exists: check if sequence is lagging behind max table ID and safely align if needed
            IF p_table IS NOT NULL AND p_col IS NOT NULL THEN
                BEGIN
                    EXECUTE IMMEDIATE 'SELECT NVL(MAX(' || p_col || '), 0) FROM ' || p_table INTO v_max_id;
                    EXECUTE IMMEDIATE 'SELECT ' || p_seq || '.NEXTVAL FROM dual' INTO v_curr;
                    IF v_curr <= v_max_id THEN
                        v_diff := (v_max_id - v_curr) + 1;
                        EXECUTE IMMEDIATE 'ALTER SEQUENCE ' || p_seq || ' INCREMENT BY ' || v_diff;
                        EXECUTE IMMEDIATE 'SELECT ' || p_seq || '.NEXTVAL FROM dual' INTO v_curr;
                        EXECUTE IMMEDIATE 'ALTER SEQUENCE ' || p_seq || ' INCREMENT BY 1';
                        DBMS_OUTPUT.PUT_LINE('  [^] Aligned sequence: ' || p_seq || ' ahead of data (' || v_curr || ')');
                    ELSE
                        DBMS_OUTPUT.PUT_LINE('  [=] Sequence exists:  ' || p_seq || ' (healthy, preserved)');
                    END IF;
                EXCEPTION
                    WHEN OTHERS THEN
                        DBMS_OUTPUT.PUT_LINE('  [=] Sequence exists:  ' || p_seq || ' (preserved)');
                END;
            ELSE
                DBMS_OUTPUT.PUT_LINE('  [=] Sequence exists:  ' || p_seq || ' (preserved)');
            END IF;
        END IF;
    END;
BEGIN
    -- Platform Sequences
    ensure_sequence('SEQ_APP_PAGE',                   'APP_PAGE',                   'PAGE_ID');
    ensure_sequence('SEQ_APP_MENU',                   'APP_MENU',                   'MENU_ID');
    ensure_sequence('SEQ_APP_ROLE',                   'APP_ROLE',                   'ROLE_ID');
    ensure_sequence('SEQ_APP_JOB',                    'APP_JOB',                    'JOB_ID');

    -- Approval Sequences
    ensure_sequence('SEQ_SES_APPROVAL_CHAIN',         'SES_APPROVAL_CHAIN',         'CHAIN_ID');
    ensure_sequence('SEQ_SES_APPROVAL_STEP',          'SES_APPROVAL_STEP',          'STEP_ID');
    ensure_sequence('SEQ_SES_APPROVAL_ASSIGNEE',      'SES_APPROVAL_ASSIGNEE',      'ASSIGNEE_ID');
    ensure_sequence('SEQ_SES_APPROVAL_INSTANCE',      'SES_APPROVAL_INSTANCE',      'INSTANCE_ID');
    ensure_sequence('SEQ_SES_APPROVAL_TASK',          'SES_APPROVAL_TASK',          'TASK_ID');
    ensure_sequence('SEQ_SES_APPROVAL_TASK_ASSIGNEE', 'SES_APPROVAL_TASK_ASSIGNEE', 'TASK_ASSIGNEE_ID');
    ensure_sequence('SEQ_SES_APPR_TASK_ASSIGNEE_DEF', 'SES_APPROVAL_TASK_ASSIGNEE_DEF', 'TASK_ASSIGNEE_DEF_ID');
    ensure_sequence('SEQ_SES_APPROVAL_STEP_TASK',     'SES_APPROVAL_STEP_TASK',     'STEP_TASK_ID');
    ensure_sequence('SEQ_SES_APPROVAL_TASK_CHKP',     'SES_APPROVAL_TASK_CHKP',     'CHECKPOINT_ID');
    ensure_sequence('SEQ_SES_APPR_TASK_SIGNOFF',      'SES_APPROVAL_TASK_SIGNOFF',  'SIGNOFF_ID');
    ensure_sequence('SEQ_SES_APPROVAL_HISTORY',       'SES_APPROVAL_HISTORY',       'HISTORY_ID');
    ensure_sequence('SEQ_SES_FORM',                   'SES_FORM',                   'FORM_ID');
    ensure_sequence('SEQ_SES_FORM_APPROVAL_MAP',      'SES_FORM_APPROVAL_MAP',      'MAP_ID');
    ensure_sequence('SEQ_SES_USER_EXT',               NULL,                         NULL);

    -- Request Module Sequences
    ensure_sequence('SEQ_SES_REQUEST',                'SES_REQUEST',                'REQUEST_ID');
    ensure_sequence('SEQ_SES_REQUEST_NO',             NULL,                         NULL);
    ensure_sequence('SEQ_SES_REQUEST_WORKER',         'SES_REQUEST_WORKER',         'WORKER_ID');
    ensure_sequence('SEQ_SES_REQUEST_WORKER_DOC',     'SES_REQUEST_WORKER_DOC',     'WORKER_DOC_ID');
    ensure_sequence('SEQ_SES_REQUEST_EVALUATOR',      'SES_REQUEST_EVALUATOR',      'EVALUATOR_ID');

    -- External Staging Upload Sequences
    ensure_sequence('SEQ_SES_EXT_UPLOAD_REQ',         'SES_EXT_UPLOAD_REQUEST',     'UPLOAD_REQUEST_ID');
    ensure_sequence('SEQ_SES_EXT_UPLOAD_DOC',         'SES_EXT_UPLOAD_DOC',         'UPLOAD_DOC_ID');

    -- Payment Module Sequences
    ensure_sequence('SEQ_SES_REQUEST_PAYMENT',        'SES_REQUEST_PAYMENT',        'PAYMENT_ID');
    ensure_sequence('SEQ_SES_REQUEST_PAY_HISTORY',    'SES_REQUEST_PAYMENT_HISTORY','HISTORY_ID');

    -- Scheduling Module Sequences
    ensure_sequence('SEQ_SES_REQUEST_SCHEDULE',       'SES_REQUEST_SCHEDULE',       'SCHEDULE_ID');
    ensure_sequence('SEQ_SES_REQUEST_SCHED_HIST',     'SES_REQUEST_SCHEDULE_HISTORY','HISTORY_ID');

    -- Vehicle Sticker Module Sequences
    ensure_sequence('SEQ_SES_STICKER_REQ',            'SES_STICKER_REQUEST',        'STICKER_REQUEST_ID');
    ensure_sequence('SEQ_SES_STICKER_REQ_NO',         NULL,                         NULL);
    ensure_sequence('SEQ_SES_STICKER_VEHICLE',        'SES_STICKER_VEHICLE',        'STICKER_VEHICLE_ID');

    -- Cash Expense Module Sequence
    ensure_sequence('SEQ_SES_CASH_EXPENSE',           'SES_CASH_EXPENSE',           'EXPENSE_ID');
END;
/

PROMPT =========================================================================
PROMPT [SES SYNC] Step 2/8: Extending Platform Audit Columns (APP_FILE)
PROMPT =========================================================================

DECLARE
    PROCEDURE add_col_if_missing(p_table VARCHAR2, p_col VARCHAR2, p_type VARCHAR2) IS
        v_count NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_count
        FROM user_tab_columns
        WHERE table_name = UPPER(p_table) AND column_name = UPPER(p_col);
        IF v_count = 0 THEN
            EXECUTE IMMEDIATE 'ALTER TABLE ' || p_table || ' ADD (' || p_col || ' ' || p_type || ')';
            DBMS_OUTPUT.PUT_LINE('  [+] Added ' || p_col || ' to ' || p_table);
        END IF;
    END;
BEGIN
    add_col_if_missing('APP_FILE', 'CREATED_BY',   'VARCHAR2(50)');
    add_col_if_missing('APP_FILE', 'CREATED_DATE', 'TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP');
    add_col_if_missing('APP_FILE', 'UPDATED_BY',   'VARCHAR2(50)');
    add_col_if_missing('APP_FILE', 'UPDATED_DATE', 'TIMESTAMP(6)');
    add_col_if_missing('APP_FILE', 'FILE_SHA256',  'VARCHAR2(64)');
END;
/

PROMPT =========================================================================
PROMPT [SES SYNC] Step 3/8: Synchronizing Tables, Columns & Constraints
PROMPT =========================================================================

-- Helper block to safely create tables if missing or add missing columns without dropping data
DECLARE
    PROCEDURE create_table_if_missing(p_table VARCHAR2, p_ddl VARCHAR2) IS
        v_cnt NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_cnt FROM user_tables WHERE table_name = UPPER(p_table);
        IF v_cnt = 0 THEN
            EXECUTE IMMEDIATE p_ddl;
            DBMS_OUTPUT.PUT_LINE('  [+] Created table: ' || p_table);
        ELSE
            DBMS_OUTPUT.PUT_LINE('  [=] Table exists:  ' || p_table || ' (data preserved)');
        END IF;
    END;

    PROCEDURE add_col_if_missing(p_table VARCHAR2, p_col VARCHAR2, p_type VARCHAR2) IS
        v_cnt NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_cnt FROM user_tab_columns
        WHERE table_name = UPPER(p_table) AND column_name = UPPER(p_col);
        IF v_cnt = 0 THEN
            EXECUTE IMMEDIATE 'ALTER TABLE ' || p_table || ' ADD (' || p_col || ' ' || p_type || ')';
            DBMS_OUTPUT.PUT_LINE('  [+] Added column: ' || p_table || '.' || p_col);
        END IF;
    END;

    PROCEDURE widen_col_if_needed(p_table VARCHAR2, p_col VARCHAR2, p_len NUMBER) IS
        v_len NUMBER;
    BEGIN
        SELECT data_length INTO v_len FROM user_tab_columns
        WHERE table_name = UPPER(p_table) AND column_name = UPPER(p_col);
        IF v_len < p_len THEN
            EXECUTE IMMEDIATE 'ALTER TABLE ' || p_table || ' MODIFY (' || p_col || ' VARCHAR2(' || p_len || '))';
            DBMS_OUTPUT.PUT_LINE('  [+] Widened column: ' || p_table || '.' || p_col || ' to VARCHAR2(' || p_len || ')');
        END IF;
    EXCEPTION
        WHEN NO_DATA_FOUND THEN NULL;
    END;

    PROCEDURE create_index_if_missing(p_idx VARCHAR2, p_sql VARCHAR2) IS
        v_cnt NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_cnt FROM user_indexes WHERE index_name = UPPER(p_idx);
        IF v_cnt = 0 THEN
            EXECUTE IMMEDIATE p_sql;
            DBMS_OUTPUT.PUT_LINE('  [+] Created index: ' || p_idx);
        END IF;
    END;
BEGIN
    -- 1. SES_USER_EXT
    create_table_if_missing('SES_USER_EXT',
        'CREATE TABLE ses_user_ext (' ||
        '  user_id VARCHAR2(36) NOT NULL, pos_id VARCHAR2(10), pos_title VARCHAR2(100), ' ||
        '  reports_to_id VARCHAR2(36), created_by VARCHAR2(50), created_date DATE, ' ||
        '  updated_by VARCHAR2(50), updated_date DATE, CONSTRAINT pk_ses_user_ext PRIMARY KEY (user_id))');

    -- 2. SES_APPROVAL_CHAIN
    create_table_if_missing('SES_APPROVAL_CHAIN',
        'CREATE TABLE ses_approval_chain (' ||
        '  chain_id NUMBER NOT NULL, chain_name VARCHAR2(200) NOT NULL, chain_key VARCHAR2(50), ' ||
        '  description VARCHAR2(500), active_flag CHAR(1) DEFAULT ''Y'' NOT NULL, ' ||
        '  created_by VARCHAR2(50), created_date DATE, updated_by VARCHAR2(50), updated_date DATE, ' ||
        '  CONSTRAINT pk_ses_approval_chain PRIMARY KEY (chain_id), ' ||
        '  CONSTRAINT uq_ses_approval_chain_key UNIQUE (chain_key), ' ||
        '  CONSTRAINT ck_ses_approval_chain_act CHECK (active_flag IN (''Y'', ''N'')))');

    -- 3. SES_APPROVAL_STEP
    create_table_if_missing('SES_APPROVAL_STEP',
        'CREATE TABLE ses_approval_step (' ||
        '  step_id NUMBER NOT NULL, chain_id NUMBER NOT NULL, step_order NUMBER NOT NULL, ' ||
        '  step_name VARCHAR2(200) NOT NULL, step_role VARCHAR2(30), parallel_group NUMBER DEFAULT 0 NOT NULL, ' ||
        '  completion_rule VARCHAR2(10) DEFAULT ''ANY'' NOT NULL, created_by VARCHAR2(50), created_date DATE, ' ||
        '  updated_by VARCHAR2(50), updated_date DATE, CONSTRAINT pk_ses_approval_step PRIMARY KEY (step_id), ' ||
        '  CONSTRAINT fk_ses_appr_step_chain FOREIGN KEY (chain_id) REFERENCES ses_approval_chain(chain_id) ON DELETE CASCADE, ' ||
        '  CONSTRAINT uq_ses_approval_step UNIQUE (chain_id, step_order) DEFERRABLE INITIALLY DEFERRED, ' ||
        '  CONSTRAINT ck_ses_step_rule CHECK (completion_rule IN (''ANY'', ''ALL'')))');

    -- Add missing step columns if table existed
    add_col_if_missing('SES_APPROVAL_STEP', 'STEP_ROLE',       'VARCHAR2(30)');
    add_col_if_missing('SES_APPROVAL_STEP', 'PARALLEL_GROUP',  'NUMBER DEFAULT 0 NOT NULL');
    add_col_if_missing('SES_APPROVAL_STEP', 'COMPLETION_RULE', 'VARCHAR2(10) DEFAULT ''ANY'' NOT NULL');

    -- 4. SES_APPROVAL_ASSIGNEE
    create_table_if_missing('SES_APPROVAL_ASSIGNEE',
        'CREATE TABLE ses_approval_assignee (' ||
        '  assignee_id NUMBER NOT NULL, step_id NUMBER NOT NULL, assignee_type VARCHAR2(30) NOT NULL, ' ||
        '  pos_id VARCHAR2(10), user_id VARCHAR2(36), order_by NUMBER DEFAULT 0 NOT NULL, ' ||
        '  created_by VARCHAR2(50), created_date DATE, updated_by VARCHAR2(50), updated_date DATE, ' ||
        '  CONSTRAINT pk_ses_approval_assignee PRIMARY KEY (assignee_id), ' ||
        '  CONSTRAINT fk_ses_appr_asgn_step FOREIGN KEY (step_id) REFERENCES ses_approval_step(step_id) ON DELETE CASCADE, ' ||
        '  CONSTRAINT ck_ses_assignee_type CHECK (assignee_type IN (''USER'', ''POSITION'', ''REQUESTOR_MANAGER'')))');

    -- 5. SES_APPROVAL_STEP_TASK
    create_table_if_missing('SES_APPROVAL_STEP_TASK',
        'CREATE TABLE ses_approval_step_task (' ||
        '  step_task_id NUMBER NOT NULL, step_id NUMBER NOT NULL, task_label VARCHAR2(200) NOT NULL, ' ||
        '  checkpoint_key VARCHAR2(50) NOT NULL, order_by NUMBER DEFAULT 0 NOT NULL, ' ||
        '  created_by VARCHAR2(50), created_date DATE, updated_by VARCHAR2(50), updated_date DATE, ' ||
        '  CONSTRAINT pk_ses_approval_step_task PRIMARY KEY (step_task_id), ' ||
        '  CONSTRAINT fk_ses_appr_stask_step FOREIGN KEY (step_id) REFERENCES ses_approval_step(step_id) ON DELETE CASCADE, ' ||
        '  CONSTRAINT uq_ses_step_task_key UNIQUE (step_id, checkpoint_key))');

    -- 6. SES_FORM
    create_table_if_missing('SES_FORM',
        'CREATE TABLE ses_form (' ||
        '  form_id NUMBER NOT NULL, form_code VARCHAR2(50) NOT NULL, form_name VARCHAR2(200) NOT NULL, ' ||
        '  description VARCHAR2(500), active_flag CHAR(1) DEFAULT ''Y'' NOT NULL, ' ||
        '  created_by VARCHAR2(50), created_date DATE, updated_by VARCHAR2(50), updated_date DATE, ' ||
        '  CONSTRAINT pk_ses_form PRIMARY KEY (form_id), CONSTRAINT uq_ses_form_code UNIQUE (form_code), ' ||
        '  CONSTRAINT ck_ses_form_act CHECK (active_flag IN (''Y'', ''N'')))');

    -- 7. SES_FORM_APPROVAL_MAP
    create_table_if_missing('SES_FORM_APPROVAL_MAP',
        'CREATE TABLE ses_form_approval_map (' ||
        '  map_id NUMBER NOT NULL, form_code VARCHAR2(50) NOT NULL, match_key VARCHAR2(100), ' ||
        '  chain_id NUMBER NOT NULL, active_flag CHAR(1) DEFAULT ''Y'' NOT NULL, ' ||
        '  created_by VARCHAR2(50), created_date DATE, updated_by VARCHAR2(50), updated_date DATE, ' ||
        '  CONSTRAINT pk_ses_form_approval_map PRIMARY KEY (map_id), ' ||
        '  CONSTRAINT fk_ses_form_map_chain FOREIGN KEY (chain_id) REFERENCES ses_approval_chain(chain_id), ' ||
        '  CONSTRAINT ck_ses_form_map_act CHECK (active_flag IN (''Y'', ''N'')))');

    -- 8. SES_APPROVAL_INSTANCE
    create_table_if_missing('SES_APPROVAL_INSTANCE',
        'CREATE TABLE ses_approval_instance (' ||
        '  instance_id NUMBER NOT NULL, business_ref_type VARCHAR2(50) NOT NULL, business_ref_id NUMBER NOT NULL, ' ||
        '  chain_id NUMBER NOT NULL, status VARCHAR2(20) DEFAULT ''PENDING'' NOT NULL, current_step_id NUMBER, ' ||
        '  current_task_id NUMBER, requestor_user_id VARCHAR2(36) NOT NULL, ' ||
        '  needs_mgr_approval_flag CHAR(1) DEFAULT ''Y'' NOT NULL, needs_proc_approval_flag CHAR(1) DEFAULT ''Y'' NOT NULL, ' ||
        '  auth_type VARCHAR2(20) DEFAULT ''ALL'' NOT NULL, completed_date DATE, ' ||
        '  created_by VARCHAR2(50), created_date DATE, updated_by VARCHAR2(50), updated_date DATE, ' ||
        '  CONSTRAINT pk_ses_approval_instance PRIMARY KEY (instance_id), ' ||
        '  CONSTRAINT fk_ses_appr_inst_chain FOREIGN KEY (chain_id) REFERENCES ses_approval_chain(chain_id), ' ||
        '  CONSTRAINT ck_ses_inst_status CHECK (status IN (''PENDING'', ''APPROVED'', ''REJECTED'', ''CANCELLED'')), ' ||
        '  CONSTRAINT ck_ses_inst_mgr CHECK (needs_mgr_approval_flag IN (''Y'', ''N'')), ' ||
        '  CONSTRAINT ck_ses_inst_proc CHECK (needs_proc_approval_flag IN (''Y'', ''N'')), ' ||
        '  CONSTRAINT ck_ses_inst_auth CHECK (auth_type IN (''ALL'', ''LOCAL_ONLY'', ''LDAP_ONLY'')) )');

    add_col_if_missing('SES_APPROVAL_INSTANCE', 'NEEDS_MGR_APPROVAL_FLAG',  'CHAR(1) DEFAULT ''Y'' NOT NULL');
    add_col_if_missing('SES_APPROVAL_INSTANCE', 'NEEDS_PROC_APPROVAL_FLAG', 'CHAR(1) DEFAULT ''Y'' NOT NULL');
    add_col_if_missing('SES_APPROVAL_INSTANCE', 'AUTH_TYPE',                'VARCHAR2(20) DEFAULT ''ALL'' NOT NULL');

    -- 9. SES_APPROVAL_TASK
    create_table_if_missing('SES_APPROVAL_TASK',
        'CREATE TABLE ses_approval_task (' ||
        '  task_id NUMBER NOT NULL, instance_id NUMBER NOT NULL, step_order NUMBER NOT NULL, ' ||
        '  step_name VARCHAR2(200) NOT NULL, step_role VARCHAR2(30), parallel_group NUMBER DEFAULT 0 NOT NULL, ' ||
        '  completion_rule VARCHAR2(10) DEFAULT ''ANY'' NOT NULL, status VARCHAR2(20) DEFAULT ''PENDING'' NOT NULL, ' ||
        '  act_user_id VARCHAR2(36), act_date DATE, note VARCHAR2(2000), ' ||
        '  created_by VARCHAR2(50), created_date DATE, updated_by VARCHAR2(50), updated_date DATE, ' ||
        '  CONSTRAINT pk_ses_approval_task PRIMARY KEY (task_id), ' ||
        '  CONSTRAINT fk_ses_appr_task_inst FOREIGN KEY (instance_id) REFERENCES ses_approval_instance(instance_id) ON DELETE CASCADE, ' ||
        '  CONSTRAINT ck_ses_task_status CHECK (status IN (''PENDING'', ''APPROVED'', ''REJECTED'', ''SKIPPED'')), ' ||
        '  CONSTRAINT ck_ses_task_rule CHECK (completion_rule IN (''ANY'', ''ALL'')) )');

    add_col_if_missing('SES_APPROVAL_TASK', 'STEP_ROLE',       'VARCHAR2(30)');
    add_col_if_missing('SES_APPROVAL_TASK', 'PARALLEL_GROUP',  'NUMBER DEFAULT 0 NOT NULL');
    add_col_if_missing('SES_APPROVAL_TASK', 'COMPLETION_RULE', 'VARCHAR2(10) DEFAULT ''ANY'' NOT NULL');

    -- 10. SES_APPROVAL_TASK_ASSIGNEE
    create_table_if_missing('SES_APPROVAL_TASK_ASSIGNEE',
        'CREATE TABLE ses_approval_task_assignee (' ||
        '  task_assignee_id NUMBER NOT NULL, task_id NUMBER NOT NULL, assignee_type VARCHAR2(30) NOT NULL, ' ||
        '  pos_id VARCHAR2(10), user_id VARCHAR2(36), empl_id VARCHAR2(20), display_name VARCHAR2(200), ' ||
        '  email VARCHAR2(200), resolution_source VARCHAR2(30) DEFAULT ''DYNAMIC'' NOT NULL, ' ||
        '  created_by VARCHAR2(50), created_date DATE, CONSTRAINT pk_ses_appr_task_asgn PRIMARY KEY (task_assignee_id), ' ||
        '  CONSTRAINT fk_ses_task_asgn_task FOREIGN KEY (task_id) REFERENCES ses_approval_task(task_id) ON DELETE CASCADE, ' ||
        '  CONSTRAINT ck_ses_task_asgn_type CHECK (assignee_type IN (''USER'', ''POSITION'', ''REQUESTOR_MANAGER'')), ' ||
        '  CONSTRAINT ck_ses_task_asgn_src  CHECK (resolution_source IN (''DYNAMIC'', ''POSITION_FALLBACK'', ''CHAIN_ADMIN_FALLBACK'', ''GLOBAL_ADMIN_FALLBACK'', ''MANAGER_SKIP'')) )');

    -- 11. SES_APPROVAL_TASK_ASSIGNEE_DEF
    create_table_if_missing('SES_APPROVAL_TASK_ASSIGNEE_DEF',
        'CREATE TABLE ses_approval_task_assignee_def (' ||
        '  task_assignee_def_id NUMBER NOT NULL, instance_id NUMBER NOT NULL, step_order NUMBER NOT NULL, ' ||
        '  assignee_type VARCHAR2(30) NOT NULL, pos_id VARCHAR2(10), user_id VARCHAR2(36), order_by NUMBER DEFAULT 0 NOT NULL, ' ||
        '  created_by VARCHAR2(50), created_date DATE, CONSTRAINT pk_ses_appr_task_asgn_def PRIMARY KEY (task_assignee_def_id), ' ||
        '  CONSTRAINT fk_ses_task_asgn_def_inst FOREIGN KEY (instance_id) REFERENCES ses_approval_instance(instance_id) ON DELETE CASCADE, ' ||
        '  CONSTRAINT ck_ses_task_asgn_def_type CHECK (assignee_type IN (''USER'', ''POSITION'', ''REQUESTOR_MANAGER'')) )');

    -- 12. SES_APPROVAL_TASK_CHKP
    create_table_if_missing('SES_APPROVAL_TASK_CHKP',
        'CREATE TABLE ses_approval_task_chkp (' ||
        '  checkpoint_id NUMBER NOT NULL, task_id NUMBER NOT NULL, task_label VARCHAR2(200) NOT NULL, ' ||
        '  checkpoint_key VARCHAR2(50) NOT NULL, status VARCHAR2(20) DEFAULT ''PENDING'' NOT NULL, ' ||
        '  completed_by VARCHAR2(36), completed_date DATE, created_by VARCHAR2(50), created_date DATE, ' ||
        '  CONSTRAINT pk_ses_appr_task_chkp PRIMARY KEY (checkpoint_id), ' ||
        '  CONSTRAINT fk_ses_task_chkp_task FOREIGN KEY (task_id) REFERENCES ses_approval_task(task_id) ON DELETE CASCADE, ' ||
        '  CONSTRAINT ck_ses_task_chkp_status CHECK (status IN (''PENDING'', ''COMPLETED'', ''SKIPPED'')), ' ||
        '  CONSTRAINT uq_ses_task_chkp_key UNIQUE (task_id, checkpoint_key))');

    -- 13. SES_APPROVAL_TASK_SIGNOFF
    create_table_if_missing('SES_APPROVAL_TASK_SIGNOFF',
        'CREATE TABLE ses_approval_task_signoff (' ||
        '  signoff_id NUMBER NOT NULL, task_id NUMBER NOT NULL, empl_id VARCHAR2(20) NOT NULL, ' ||
        '  user_id VARCHAR2(36), display_name VARCHAR2(200), pos_id VARCHAR2(10), pos_title VARCHAR2(100), ' ||
        '  status VARCHAR2(20) DEFAULT ''PENDING'' NOT NULL, note VARCHAR2(2000), completed_by VARCHAR2(36), ' ||
        '  completed_date DATE, order_by NUMBER DEFAULT 0 NOT NULL, ' ||
        '  CONSTRAINT pk_ses_appr_task_signoff PRIMARY KEY (signoff_id), ' ||
        '  CONSTRAINT uq_ses_appr_task_signoff UNIQUE (task_id, empl_id), ' ||
        '  CONSTRAINT ck_ses_signoff_status CHECK (status IN (''PENDING'', ''APPROVED'', ''REJECTED'')), ' ||
        '  CONSTRAINT fk_ses_signoff_task FOREIGN KEY (task_id) REFERENCES ses_approval_task(task_id) ON DELETE CASCADE)');

    -- 14. SES_APPROVAL_HISTORY
    create_table_if_missing('SES_APPROVAL_HISTORY',
        'CREATE TABLE ses_approval_history (' ||
        '  history_id NUMBER NOT NULL, instance_id NUMBER NOT NULL, task_id NUMBER, step_order NUMBER, ' ||
        '  step_name VARCHAR2(200), action VARCHAR2(30) NOT NULL, actor_user_id VARCHAR2(36) NOT NULL, ' ||
        '  actor_name VARCHAR2(200), actor_date DATE NOT NULL, note VARCHAR2(2000), created_by VARCHAR2(50), created_date DATE, ' ||
        '  CONSTRAINT pk_ses_approval_history PRIMARY KEY (history_id), ' ||
        '  CONSTRAINT fk_ses_appr_hist_inst FOREIGN KEY (instance_id) REFERENCES ses_approval_instance(instance_id) ON DELETE CASCADE)');

    -- 15. SES_ZONE
    create_table_if_missing('SES_ZONE',
        'CREATE TABLE ses_zone (' ||
        '  zone_code VARCHAR2(20) NOT NULL, zone_name VARCHAR2(100) NOT NULL, order_by NUMBER DEFAULT 0 NOT NULL, ' ||
        '  active_flag CHAR(1) DEFAULT ''Y'' NOT NULL, created_by VARCHAR2(50), created_date DATE, ' ||
        '  updated_by VARCHAR2(50), updated_date DATE, CONSTRAINT pk_ses_zone PRIMARY KEY (zone_code))');

    -- 16. SES_DOC_REQUIREMENT
    create_table_if_missing('SES_DOC_REQUIREMENT',
        'CREATE TABLE ses_doc_requirement (' ||
        '  doc_code VARCHAR2(30) NOT NULL, doc_name VARCHAR2(200) NOT NULL, doc_desc VARCHAR2(500), ' ||
        '  active_flag CHAR(1) DEFAULT ''Y'' NOT NULL, created_by VARCHAR2(50), created_date DATE, ' ||
        '  updated_by VARCHAR2(50), updated_date DATE, CONSTRAINT pk_ses_doc_requirement PRIMARY KEY (doc_code))');

    -- 17. SES_REQUEST_TYPE
    create_table_if_missing('SES_REQUEST_TYPE',
        'CREATE TABLE ses_request_type (' ||
        '  type_code VARCHAR2(30) NOT NULL, type_name VARCHAR2(200) NOT NULL, needs_payment_flag CHAR(1) DEFAULT ''N'' NOT NULL, ' ||
        '  payment_amount NUMBER(15), needs_mgr_approval_flag CHAR(1) DEFAULT ''Y'' NOT NULL, ' ||
        '  needs_proc_approval_flag CHAR(1) DEFAULT ''Y'' NOT NULL, auth_type VARCHAR2(20) DEFAULT ''ALL'' NOT NULL, ' ||
        '  need_ext_page_upload CHAR(1) DEFAULT ''N'' NOT NULL, order_by NUMBER DEFAULT 0 NOT NULL, ' ||
        '  active_flag CHAR(1) DEFAULT ''Y'' NOT NULL, created_by VARCHAR2(50), created_date DATE, ' ||
        '  updated_by VARCHAR2(50), updated_date DATE, CONSTRAINT pk_ses_request_type PRIMARY KEY (type_code), ' ||
        '  CONSTRAINT ck_ses_req_type_pay CHECK (needs_payment_flag IN (''Y'', ''N'')) )');

    add_col_if_missing('SES_REQUEST_TYPE', 'PAYMENT_AMOUNT',          'NUMBER(15)');
    add_col_if_missing('SES_REQUEST_TYPE', 'NEEDS_MGR_APPROVAL_FLAG', 'CHAR(1) DEFAULT ''Y'' NOT NULL');
    add_col_if_missing('SES_REQUEST_TYPE', 'NEEDS_PROC_APPROVAL_FLAG','CHAR(1) DEFAULT ''Y'' NOT NULL');
    add_col_if_missing('SES_REQUEST_TYPE', 'AUTH_TYPE',               'VARCHAR2(20) DEFAULT ''ALL'' NOT NULL');
    add_col_if_missing('SES_REQUEST_TYPE', 'NEED_EXT_PAGE_UPLOAD',   'CHAR(1) DEFAULT ''N'' NOT NULL');

    -- 18. SES_REQUEST_TYPE_DOC
    create_table_if_missing('SES_REQUEST_TYPE_DOC',
        'CREATE TABLE ses_request_type_doc (' ||
        '  type_code VARCHAR2(30) NOT NULL, doc_code VARCHAR2(30) NOT NULL, mandatory_flag CHAR(1) DEFAULT ''Y'' NOT NULL, ' ||
        '  order_by NUMBER DEFAULT 0 NOT NULL, created_by VARCHAR2(50), created_date DATE, ' ||
        '  updated_by VARCHAR2(50), updated_date DATE, CONSTRAINT pk_ses_request_type_doc PRIMARY KEY (type_code, doc_code), ' ||
        '  CONSTRAINT fk_ses_rtd_type FOREIGN KEY (type_code) REFERENCES ses_request_type(type_code) ON DELETE CASCADE, ' ||
        '  CONSTRAINT fk_ses_rtd_doc  FOREIGN KEY (doc_code)  REFERENCES ses_doc_requirement(doc_code), ' ||
        '  CONSTRAINT ck_ses_rtd_mandatory CHECK (mandatory_flag IN (''Y'', ''N'')))');

    -- 19. SES_REQUEST
    create_table_if_missing('SES_REQUEST',
        'CREATE TABLE ses_request (' ||
        '  request_id NUMBER NOT NULL, request_no VARCHAR2(30) NOT NULL, type_code VARCHAR2(30) NOT NULL, ' ||
        '  contract_no VARCHAR2(100), contract_type VARCHAR2(20), contract_title VARCHAR2(300), ' ||
        '  zone_code VARCHAR2(20), instance VARCHAR2(200), address VARCHAR2(500), start_period DATE, finish_period DATE, ' ||
        '  payment_file_id VARCHAR2(36), status VARCHAR2(20) DEFAULT ''PENDING'' NOT NULL, ' ||
        '  requestor_user_id VARCHAR2(36) NOT NULL, approval_instance_id NUMBER, email VARCHAR2(200), ' ||
        '  upload_token VARCHAR2(64), upload_token_expiry TIMESTAMP(6), upload_token_used CHAR(1) DEFAULT ''N'' NOT NULL, ' ||
        '  created_by VARCHAR2(50), created_date DATE, updated_by VARCHAR2(50), updated_date DATE, ' ||
        '  CONSTRAINT pk_ses_request PRIMARY KEY (request_id), CONSTRAINT uq_ses_request_no UNIQUE (request_no), ' ||
        '  CONSTRAINT fk_ses_request_type FOREIGN KEY (type_code) REFERENCES ses_request_type(type_code), ' ||
        '  CONSTRAINT fk_ses_request_zone FOREIGN KEY (zone_code) REFERENCES ses_zone(zone_code) )');

    add_col_if_missing('SES_REQUEST', 'EMAIL',               'VARCHAR2(200)');
    add_col_if_missing('SES_REQUEST', 'UPLOAD_TOKEN',        'VARCHAR2(64)');
    add_col_if_missing('SES_REQUEST', 'UPLOAD_TOKEN_EXPIRY', 'TIMESTAMP(6)');
    add_col_if_missing('SES_REQUEST', 'UPLOAD_TOKEN_USED',   'CHAR(1) DEFAULT ''N'' NOT NULL');

    -- 20. SES_REQUEST_WORKER
    create_table_if_missing('SES_REQUEST_WORKER',
        'CREATE TABLE ses_request_worker (' ||
        '  worker_id NUMBER NOT NULL, request_id NUMBER NOT NULL, line_no NUMBER NOT NULL, ' ||
        '  worker_name VARCHAR2(200) NOT NULL, id_type VARCHAR2(10) DEFAULT ''KTP'' NOT NULL, nik VARCHAR2(30) NOT NULL, ' ||
        '  jabatan VARCHAR2(200), gender CHAR(1), badge VARCHAR2(50), department VARCHAR2(200), section VARCHAR2(200), ' ||
        '  screening_status VARCHAR2(20) DEFAULT ''PENDING'' NOT NULL, screening_date DATE, screening_reason VARCHAR2(500), ' ||
        '  created_by VARCHAR2(50), created_date DATE, updated_by VARCHAR2(50), updated_date DATE, ' ||
        '  CONSTRAINT pk_ses_request_worker PRIMARY KEY (worker_id), ' ||
        '  CONSTRAINT uq_ses_request_worker_nik UNIQUE (request_id, nik), ' ||
        '  CONSTRAINT fk_ses_worker_request FOREIGN KEY (request_id) REFERENCES ses_request(request_id) ON DELETE CASCADE, ' ||
        '  CONSTRAINT ck_ses_worker_gender CHECK (gender IN (''M'', ''F'')) )');

    add_col_if_missing('SES_REQUEST_WORKER', 'ID_TYPE',          'VARCHAR2(10) DEFAULT ''KTP'' NOT NULL');
    add_col_if_missing('SES_REQUEST_WORKER', 'BADGE',            'VARCHAR2(50)');
    add_col_if_missing('SES_REQUEST_WORKER', 'DEPARTMENT',       'VARCHAR2(200)');
    add_col_if_missing('SES_REQUEST_WORKER', 'SECTION',          'VARCHAR2(200)');
    add_col_if_missing('SES_REQUEST_WORKER', 'SCREENING_STATUS', 'VARCHAR2(20) DEFAULT ''PENDING'' NOT NULL');
    add_col_if_missing('SES_REQUEST_WORKER', 'SCREENING_DATE',   'DATE');
    add_col_if_missing('SES_REQUEST_WORKER', 'SCREENING_REASON', 'VARCHAR2(500)');
    widen_col_if_needed('SES_REQUEST_WORKER', 'NIK', 30);

    -- 21. SES_REQUEST_WORKER_DOC
    create_table_if_missing('SES_REQUEST_WORKER_DOC',
        'CREATE TABLE ses_request_worker_doc (' ||
        '  worker_doc_id NUMBER NOT NULL, worker_id NUMBER NOT NULL, doc_code VARCHAR2(30) NOT NULL, ' ||
        '  file_id VARCHAR2(36) NOT NULL, CONSTRAINT pk_ses_request_worker_doc PRIMARY KEY (worker_doc_id), ' ||
        '  CONSTRAINT uq_ses_worker_doc UNIQUE (worker_id, doc_code), ' ||
        '  CONSTRAINT fk_ses_wdoc_worker FOREIGN KEY (worker_id) REFERENCES ses_request_worker(worker_id) ON DELETE CASCADE, ' ||
        '  CONSTRAINT fk_ses_wdoc_doc    FOREIGN KEY (doc_code)  REFERENCES ses_doc_requirement(doc_code))');

    -- 22. SES_REQUEST_EVALUATOR
    create_table_if_missing('SES_REQUEST_EVALUATOR',
        'CREATE TABLE ses_request_evaluator (' ||
        '  evaluator_id NUMBER NOT NULL, request_id NUMBER NOT NULL, line_no NUMBER DEFAULT 0 NOT NULL, ' ||
        '  pos_id VARCHAR2(10), pos_title VARCHAR2(100), empl_id VARCHAR2(20) NOT NULL, empl_name VARCHAR2(200), ' ||
        '  created_by VARCHAR2(50), created_date DATE, CONSTRAINT pk_ses_request_evaluator PRIMARY KEY (evaluator_id), ' ||
        '  CONSTRAINT uq_ses_request_evaluator UNIQUE (request_id, empl_id), ' ||
        '  CONSTRAINT fk_ses_evaluator_request FOREIGN KEY (request_id) REFERENCES ses_request(request_id) ON DELETE CASCADE)');

    -- 23. SES_EXT_UPLOAD_REQUEST & SES_EXT_UPLOAD_DOC
    create_table_if_missing('SES_EXT_UPLOAD_REQUEST',
        'CREATE TABLE ses_ext_upload_request (' ||
        '  upload_request_id NUMBER NOT NULL, request_id NUMBER NOT NULL, request_no VARCHAR2(30) NOT NULL, ' ||
        '  type_code VARCHAR2(30) NOT NULL, email VARCHAR2(200), upload_token VARCHAR2(64) NOT NULL, ' ||
        '  token_expiry TIMESTAMP(6) NOT NULL, is_finalized CHAR(1) DEFAULT ''N'' NOT NULL, finalized_date TIMESTAMP(6), ' ||
        '  created_date TIMESTAMP(6) DEFAULT SYSTIMESTAMP NOT NULL, ' ||
        '  CONSTRAINT pk_ses_ext_upload_request PRIMARY KEY (upload_request_id), ' ||
        '  CONSTRAINT fk_ses_ext_up_req FOREIGN KEY (request_id) REFERENCES ses_request(request_id) ON DELETE CASCADE, ' ||
        '  CONSTRAINT ck_ses_ext_up_finalized CHECK (is_finalized IN (''Y'', ''N'')))');

    create_table_if_missing('SES_EXT_UPLOAD_DOC',
        'CREATE TABLE ses_ext_upload_doc (' ||
        '  upload_doc_id NUMBER NOT NULL, upload_request_id NUMBER NOT NULL, worker_id NUMBER NOT NULL, ' ||
        '  doc_code VARCHAR2(30) NOT NULL, file_id VARCHAR2(36) NOT NULL, file_name VARCHAR2(255) NOT NULL, ' ||
        '  file_size NUMBER, is_active CHAR(1) DEFAULT ''Y'' NOT NULL, created_date TIMESTAMP(6) DEFAULT SYSTIMESTAMP NOT NULL, ' ||
        '  CONSTRAINT pk_ses_ext_upload_doc PRIMARY KEY (upload_doc_id), ' ||
        '  CONSTRAINT fk_ses_ext_doc_req FOREIGN KEY (upload_request_id) REFERENCES ses_ext_upload_request(upload_request_id) ON DELETE CASCADE, ' ||
        '  CONSTRAINT fk_ses_ext_doc_wkr FOREIGN KEY (worker_id) REFERENCES ses_request_worker(worker_id) ON DELETE CASCADE, ' ||
        '  CONSTRAINT ck_ses_ext_doc_active CHECK (is_active IN (''Y'', ''N'')))');

    -- 24. SES_REQUEST_PAYMENT
    create_table_if_missing('SES_REQUEST_PAYMENT',
        'CREATE TABLE ses_request_payment (' ||
        '  payment_id NUMBER NOT NULL, business_type VARCHAR2(30) DEFAULT ''SES_REQUEST'' NOT NULL, ' ||
        '  business_ref_id NUMBER NOT NULL, status VARCHAR2(20) DEFAULT ''PENDING'' NOT NULL, ' ||
        '  proof_file_id VARCHAR2(36), paid_amount NUMBER(15), rejection_reason VARCHAR2(500), ' ||
        '  reviewed_by VARCHAR2(36), reviewed_date DATE, created_by VARCHAR2(50), created_date DATE, ' ||
        '  updated_by VARCHAR2(50), updated_date DATE, CONSTRAINT pk_ses_request_payment PRIMARY KEY (payment_id) )');

    add_col_if_missing('SES_REQUEST_PAYMENT', 'BUSINESS_TYPE',    'VARCHAR2(30) DEFAULT ''SES_REQUEST'' NOT NULL');
    add_col_if_missing('SES_REQUEST_PAYMENT', 'BUSINESS_REF_ID',  'NUMBER');
    add_col_if_missing('SES_REQUEST_PAYMENT', 'PAID_AMOUNT',      'NUMBER(15)');
    add_col_if_missing('SES_REQUEST_PAYMENT', 'REJECTION_REASON', 'VARCHAR2(500)');

    -- 25. SES_REQUEST_PAYMENT_HISTORY
    create_table_if_missing('SES_REQUEST_PAYMENT_HISTORY',
        'CREATE TABLE ses_request_payment_history (' ||
        '  history_id NUMBER NOT NULL, payment_id NUMBER NOT NULL, action VARCHAR2(30) NOT NULL, ' ||
        '  proof_file_id VARCHAR2(36), reason VARCHAR2(500), amount NUMBER(15), actor_user_id VARCHAR2(36) NOT NULL, ' ||
        '  actor_date DATE NOT NULL, created_by VARCHAR2(50), created_date DATE, updated_by VARCHAR2(50), updated_date DATE, ' ||
        '  CONSTRAINT pk_ses_request_payment_history PRIMARY KEY (history_id), ' ||
        '  CONSTRAINT fk_ses_pay_hist_payment FOREIGN KEY (payment_id) REFERENCES ses_request_payment(payment_id) ON DELETE CASCADE)');

    add_col_if_missing('SES_REQUEST_PAYMENT_HISTORY', 'AMOUNT', 'NUMBER(15)');

    -- 26. SES_REQUEST_SCHEDULE & HISTORY
    create_table_if_missing('SES_REQUEST_SCHEDULE',
        'CREATE TABLE ses_request_schedule (' ||
        '  schedule_id NUMBER NOT NULL, request_id NUMBER NOT NULL, schedule_type VARCHAR2(20) NOT NULL, ' ||
        '  status VARCHAR2(20) DEFAULT ''SCHEDULED'' NOT NULL, scheduled_date DATE NOT NULL, note VARCHAR2(500), ' ||
        '  resolution_reason VARCHAR2(500), resolved_by VARCHAR2(36), resolved_date DATE, ' ||
        '  created_by VARCHAR2(50), created_date DATE, updated_by VARCHAR2(50), updated_date DATE, ' ||
        '  CONSTRAINT pk_ses_request_schedule PRIMARY KEY (schedule_id), ' ||
        '  CONSTRAINT fk_ses_schedule_request FOREIGN KEY (request_id) REFERENCES ses_request(request_id), ' ||
        '  CONSTRAINT uq_ses_schedule_req_type UNIQUE (request_id, schedule_type))');

    create_table_if_missing('SES_REQUEST_SCHEDULE_HISTORY',
        'CREATE TABLE ses_request_schedule_history (' ||
        '  history_id NUMBER NOT NULL, schedule_id NUMBER NOT NULL, action VARCHAR2(20) NOT NULL, ' ||
        '  scheduled_date DATE, reason VARCHAR2(500), actor_user_id VARCHAR2(36) NOT NULL, actor_date DATE NOT NULL, ' ||
        '  created_by VARCHAR2(50), created_date DATE, updated_by VARCHAR2(50), updated_date DATE, ' ||
        '  CONSTRAINT pk_ses_request_schedule_hist PRIMARY KEY (history_id), ' ||
        '  CONSTRAINT fk_ses_sched_hist_schedule FOREIGN KEY (schedule_id) REFERENCES ses_request_schedule(schedule_id) ON DELETE CASCADE)');

    -- 27. STICKER MODULE
    create_table_if_missing('SES_STICKER_TYPE',
        'CREATE TABLE ses_sticker_type (' ||
        '  sticker_type_code VARCHAR2(30) NOT NULL, sticker_type_name VARCHAR2(200) NOT NULL, price NUMBER(15) DEFAULT 0 NOT NULL, ' ||
        '  order_by NUMBER DEFAULT 0 NOT NULL, active_flag VARCHAR2(1) DEFAULT ''Y'' NOT NULL, created_by VARCHAR2(50), created_date DATE, ' ||
        '  updated_by VARCHAR2(50), updated_date DATE, CONSTRAINT pk_ses_sticker_type PRIMARY KEY (sticker_type_code))');

    create_table_if_missing('SES_STICKER_REQUEST',
        'CREATE TABLE ses_sticker_request (' ||
        '  sticker_request_id NUMBER NOT NULL, request_no VARCHAR2(30) NOT NULL, requestor_name VARCHAR2(200) NOT NULL, ' ||
        '  badge VARCHAR2(50), department VARCHAR2(200), section VARCHAR2(200), instance VARCHAR2(200), ' ||
        '  phone_no VARCHAR2(50), work_phone_no VARCHAR2(50), address VARCHAR2(500), status VARCHAR2(20) DEFAULT ''PENDING'' NOT NULL, ' ||
        '  requestor_user_id VARCHAR2(36) NOT NULL, approval_instance_id NUMBER, created_by VARCHAR2(50), created_date DATE, ' ||
        '  updated_by VARCHAR2(50), updated_date DATE, CONSTRAINT pk_ses_sticker_request PRIMARY KEY (sticker_request_id), ' ||
        '  CONSTRAINT uq_ses_sticker_request_no UNIQUE (request_no))');

    create_table_if_missing('SES_STICKER_VEHICLE',
        'CREATE TABLE ses_sticker_vehicle (' ||
        '  sticker_vehicle_id NUMBER NOT NULL, sticker_request_id NUMBER NOT NULL, line_no NUMBER NOT NULL, ' ||
        '  sticker_type_code VARCHAR2(30) NOT NULL, police_no VARCHAR2(30) NOT NULL, vehicle_type VARCHAR2(100), ' ||
        '  owner_name VARCHAR2(200), brand VARCHAR2(100), color VARCHAR2(50), year_made VARCHAR2(4), letter_file_id VARCHAR2(36), ' ||
        '  created_by VARCHAR2(50), created_date DATE, updated_by VARCHAR2(50), updated_date DATE, ' ||
        '  CONSTRAINT pk_ses_sticker_vehicle PRIMARY KEY (sticker_vehicle_id), ' ||
        '  CONSTRAINT fk_ses_sticker_veh_req FOREIGN KEY (sticker_request_id) REFERENCES ses_sticker_request(sticker_request_id))');

    -- 28. SES_CASH_EXPENSE
    create_table_if_missing('SES_CASH_EXPENSE',
        'CREATE TABLE ses_cash_expense (' ||
        '  expense_id NUMBER NOT NULL, paid_to VARCHAR2(200) NOT NULL, amount NUMBER(15) NOT NULL, ' ||
        '  notes VARCHAR2(2000), expense_date DATE NOT NULL, active_flag CHAR(1) DEFAULT ''Y'' NOT NULL, ' ||
        '  created_by VARCHAR2(50), created_date DATE, updated_by VARCHAR2(50), updated_date DATE, ' ||
        '  CONSTRAINT pk_ses_cash_expense PRIMARY KEY (expense_id), ' ||
        '  CONSTRAINT ck_ses_cash_expense_amt CHECK (amount > 0), ' ||
        '  CONSTRAINT ck_ses_cash_expense_act CHECK (active_flag IN (''Y'', ''N'')) )');

    -- Create indexes if missing
    create_index_if_missing('IDX_SES_APPR_STEP_CHAIN', 'CREATE INDEX idx_ses_appr_step_chain ON ses_approval_step(chain_id)');
    create_index_if_missing('IDX_SES_APPR_ASGN_STEP',  'CREATE INDEX idx_ses_appr_asgn_step  ON ses_approval_assignee(step_id)');
    create_index_if_missing('IDX_SES_APPR_STASK_STEP', 'CREATE INDEX idx_ses_appr_stask_step ON ses_approval_step_task(step_id)');
    create_index_if_missing('IDX_SES_APPR_INST_REF',   'CREATE INDEX idx_ses_appr_inst_ref   ON ses_approval_instance(business_ref_type, business_ref_id)');
    create_index_if_missing('IDX_SES_APPR_INST_STAT',  'CREATE INDEX idx_ses_appr_inst_stat  ON ses_approval_instance(status)');
    create_index_if_missing('IDX_SES_APPR_INST_REQ',   'CREATE INDEX idx_ses_appr_inst_req   ON ses_approval_instance(requestor_user_id)');
    create_index_if_missing('IDX_SES_APPR_TASK_INST',  'CREATE INDEX idx_ses_appr_task_inst  ON ses_approval_task(instance_id)');
    create_index_if_missing('IDX_SES_APPR_TASK_STAT',  'CREATE INDEX idx_ses_appr_task_stat  ON ses_approval_task(status)');
    create_index_if_missing('IDX_SES_APPR_TASGN_TASK', 'CREATE INDEX idx_ses_appr_tasgn_task ON ses_approval_task_assignee(task_id)');
    create_index_if_missing('IDX_SES_APPR_TASGN_POS',  'CREATE INDEX idx_ses_appr_tasgn_pos  ON ses_approval_task_assignee(pos_id)');
    create_index_if_missing('IDX_SES_APPR_TASGN_USER', 'CREATE INDEX idx_ses_appr_tasgn_user ON ses_approval_task_assignee(user_id)');
    create_index_if_missing('IDX_SES_APPR_TASGN_DEF',  'CREATE INDEX idx_ses_appr_tasgn_def  ON ses_approval_task_assignee_def(instance_id)');
    create_index_if_missing('IDX_SES_APPR_TCHKP_TASK', 'CREATE INDEX idx_ses_appr_tchkp_task ON ses_approval_task_chkp(task_id)');
    create_index_if_missing('IDX_SES_APPR_TCHKP_KEY',  'CREATE INDEX idx_ses_appr_tchkp_key  ON ses_approval_task_chkp(checkpoint_key, status)');
    create_index_if_missing('IDX_SES_SIGNOFF_TASK',    'CREATE INDEX idx_ses_signoff_task    ON ses_approval_task_signoff(task_id, status)');
    create_index_if_missing('IDX_SES_SIGNOFF_EMPL',    'CREATE INDEX idx_ses_signoff_empl    ON ses_approval_task_signoff(empl_id, status)');
    create_index_if_missing('IDX_SES_APPR_HIST_INST',  'CREATE INDEX idx_ses_appr_hist_inst  ON ses_approval_history(instance_id)');

    create_index_if_missing('IDX_SES_RTD_DOC',           'CREATE INDEX idx_ses_rtd_doc           ON ses_request_type_doc(doc_code)');
    create_index_if_missing('IDX_SES_REQUEST_TYPE',      'CREATE INDEX idx_ses_request_type      ON ses_request(type_code)');
    create_index_if_missing('IDX_SES_REQUEST_ZONE',      'CREATE INDEX idx_ses_request_zone      ON ses_request(zone_code)');
    create_index_if_missing('IDX_SES_REQUEST_STATUS',    'CREATE INDEX idx_ses_request_status    ON ses_request(status)');
    create_index_if_missing('IDX_SES_REQUEST_REQUESTOR', 'CREATE INDEX idx_ses_request_requestor ON ses_request(requestor_user_id)');
    create_index_if_missing('IDX_SES_REQUEST_INSTANCE',  'CREATE INDEX idx_ses_request_instance  ON ses_request(approval_instance_id)');
    create_index_if_missing('IDX_SES_REQUEST_TOKEN',     'CREATE INDEX idx_ses_request_token     ON ses_request(upload_token)');
    create_index_if_missing('IDX_SES_WORKER_REQUEST',    'CREATE INDEX idx_ses_worker_request    ON ses_request_worker(request_id)');
    create_index_if_missing('IDX_SES_WORKER_SCR_STATUS', 'CREATE INDEX idx_ses_worker_scr_status ON ses_request_worker(screening_status)');
    create_index_if_missing('IDX_SES_WDOC_WORKER',       'CREATE INDEX idx_ses_wdoc_worker       ON ses_request_worker_doc(worker_id)');
    create_index_if_missing('IDX_SES_WDOC_DOC',          'CREATE INDEX idx_ses_wdoc_doc          ON ses_request_worker_doc(doc_code)');
    create_index_if_missing('IDX_SES_EVALUATOR_REQUEST', 'CREATE INDEX idx_ses_evaluator_request ON ses_request_evaluator(request_id, line_no)');
    create_index_if_missing('IDX_SES_EXT_UP_TOKEN',      'CREATE INDEX idx_ses_ext_up_token      ON ses_ext_upload_request(upload_token)');
    create_index_if_missing('IDX_SES_EXT_DOC_WKR',       'CREATE INDEX idx_ses_ext_doc_wkr       ON ses_ext_upload_doc(worker_id)');

    create_index_if_missing('IDX_SES_PAYMENT_BUSINESS', 'CREATE INDEX idx_ses_payment_business ON ses_request_payment(business_type, business_ref_id)');
    create_index_if_missing('IDX_SES_PAYMENT_STATUS',   'CREATE INDEX idx_ses_payment_status   ON ses_request_payment(status)');
    create_index_if_missing('IDX_SES_PAY_HIST_PAYMENT', 'CREATE INDEX idx_ses_pay_hist_payment ON ses_request_payment_history(payment_id)');

    create_index_if_missing('IDX_SES_SCHEDULE_REQUEST', 'CREATE INDEX idx_ses_schedule_request ON ses_request_schedule(request_id)');
    create_index_if_missing('IDX_SES_SCHEDULE_DATE',    'CREATE INDEX idx_ses_schedule_date    ON ses_request_schedule(scheduled_date)');
    create_index_if_missing('IDX_SES_SCHED_HIST_SCHED', 'CREATE INDEX idx_ses_sched_hist_sched ON ses_request_schedule_history(schedule_id)');

    create_index_if_missing('IDX_SES_STICKER_REQ_USER', 'CREATE INDEX idx_ses_sticker_req_user  ON ses_sticker_request(requestor_user_id)');
    create_index_if_missing('IDX_SES_STICKER_VEH_REQ',  'CREATE INDEX idx_ses_sticker_veh_req   ON ses_sticker_vehicle(sticker_request_id)');
    create_index_if_missing('IDX_SES_STICKER_VEH_TYPE', 'CREATE INDEX idx_ses_sticker_veh_type  ON ses_sticker_vehicle(sticker_type_code)');
    create_index_if_missing('IDX_SES_CASH_EXPENSE_ACT', 'CREATE INDEX idx_ses_cash_expense_act ON ses_cash_expense(active_flag, expense_date)');
END;
/

-- Synchronize check constraints safely (recreate check constraint if needed)
DECLARE
    PROCEDURE sync_check(p_table VARCHAR2, p_con VARCHAR2, p_def VARCHAR2) IS
        v_cnt NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_cnt FROM user_constraints
        WHERE table_name = UPPER(p_table) AND constraint_name = UPPER(p_con);
        IF v_cnt > 0 THEN
            EXECUTE IMMEDIATE 'ALTER TABLE ' || p_table || ' DROP CONSTRAINT ' || p_con;
        END IF;
        EXECUTE IMMEDIATE 'ALTER TABLE ' || p_table || ' ADD CONSTRAINT ' || p_con || ' CHECK (' || p_def || ')';
    EXCEPTION
        WHEN OTHERS THEN
            DBMS_OUTPUT.PUT_LINE('  [!] Note on check ' || p_con || ': ' || SQLERRM);
    END;
BEGIN
    sync_check('SES_REQUEST',        'CK_SES_REQUEST_STATUS', 'status IN (''DRAFT'', ''WAITING_FOR_UPLOAD'', ''SUBMITTED'', ''IN_REVIEW'', ''APPROVED'', ''REJECTED'', ''CANCELLED'', ''EXPIRED'')');
    sync_check('SES_REQUEST_WORKER', 'CK_SES_WORKER_SCREENING', 'screening_status IN (''PENDING'', ''ATTENDED'', ''NO_SHOW'', ''FAILED'', ''ACCEPTED'', ''REJECTED'')');
    sync_check('SES_REQUEST_WORKER', 'CK_SES_WORKER_ID_TYPE', 'id_type IN (''KTP'', ''PASSPORT'')');
    sync_check('SES_APPROVAL_STEP',  'CK_SES_STEP_ROLE', 'step_role IS NULL OR step_role IN (''REQUESTOR_MANAGER'', ''PROCUREMENT_SUPERVISOR'', ''PROCUREMENT_MANAGER'', ''SECURITY_MANAGER'', ''SITE_MANAGER'')');
    sync_check('SES_APPROVAL_TASK',  'CK_SES_TASK_ROLE', 'step_role IS NULL OR step_role IN (''REQUESTOR_MANAGER'', ''PROCUREMENT_SUPERVISOR'', ''PROCUREMENT_MANAGER'', ''SECURITY_MANAGER'', ''SITE_MANAGER'')');
    sync_check('SES_REQUEST_PAYMENT','CK_SES_PAYMENT_STATUS', 'status IN (''PENDING'', ''VERIFYING'', ''VERIFIED'', ''REJECTED'', ''CANCELLED'')');
    sync_check('SES_REQUEST_PAYMENT_HISTORY', 'CK_SES_PAY_HIST_ACTION', 'action IN (''SUBMITTED'', ''SUBMITTED_ON_BEHALF'', ''APPROVED'', ''REJECTED'', ''CANCELLED'', ''REFUND_FLAGGED'')');
END;
/

PROMPT =========================================================================
PROMPT [SES SYNC] Step 4/8: Refreshing Enterprise Database Views
PROMPT =========================================================================

-- 1. APP_PERSON View (over ELLORA.TBL_PERSON)
CREATE OR REPLACE VIEW app_person AS
SELECT empl_id,
       empl_name,
       dept,
       section,
       TRIM(pos_id)          AS pos_id,
       TRIM(pos_title)       AS pos_title,
       is_active,
       section_id,
       dept_id,
       email,
       TRIM(superior_pos_id) AS superior_pos_id,
       start_date,
       end_date,
       is_extension,
       gender,
       company
FROM ellora.tbl_person;

-- 2. CONTRACT_V Live View (over CARSIS.CONTRACT_V, deduplicated)
CREATE OR REPLACE VIEW contract_v AS
SELECT no_kontrak, company, judul_pekerjaan, tanggal_mulai, tanggal_selesai
FROM (
    SELECT c.*, ROW_NUMBER() OVER (PARTITION BY no_kontrak ORDER BY no_kontrak) rn
    FROM CARSIS.CONTRACT_V c
    WHERE no_kontrak IS NOT NULL
)
WHERE rn = 1;

-- 3. SES_WORKER_LOOKUP_VIEW (Deduplicated Priority Composite Worker Autofill)
CREATE OR REPLACE VIEW ses_worker_lookup_view AS
WITH sources AS (
    SELECT
        TRIM(emplid)                                     AS badge_raw,
        TRIM(name_display)                                AS worker_name,
        TRIM(ktp)                                          AS nik,
        TRIM(pos_desc)                                     AS jabatan,
        TRIM(dept_desc)                                    AS department,
        TRIM(sect_desc)                                    AS section,
        CASE WHEN gender = 'MALE' THEN 'M' ELSE 'F' END    AS gender,
        1                                                   AS src_priority
    FROM emp_nik_view

    UNION ALL

    SELECT
        TRIM(holder_empl_id)                               AS badge_raw,
        TRIM(holder_empl_name)                              AS worker_name,
        NULL                                                 AS nik,
        TRIM(pos_title)                                     AS jabatan,
        TRIM(dept_desc)                                     AS department,
        TRIM(section_desc)                                  AS section,
        NULL                                                 AS gender,
        2                                                    AS src_priority
    FROM app_position_view
    WHERE holder_empl_id IS NOT NULL

    UNION ALL

    SELECT
        TRIM(empl_id)                                      AS badge_raw,
        TRIM(empl_name)                                    AS worker_name,
        NULL                                               AS nik,
        TRIM(pos_title)                                    AS jabatan,
        TRIM(dept)                                         AS department,
        TRIM(section)                                      AS section,
        CASE UPPER(TRIM(gender))
            WHEN 'L' THEN 'M'
            WHEN 'P' THEN 'F'
        END                                                AS gender,
        3                                                  AS src_priority
    FROM app_person
    WHERE TRIM(empl_id) LIKE '3%'
      AND is_active = 1

    UNION ALL

    SELECT
        TRIM(username)                                      AS badge_raw,
        TRIM(full_name)                                     AS worker_name,
        NULL                                                 AS nik,
        NULL                                                 AS jabatan,
        NULL                                                 AS department,
        NULL                                                 AS section,
        NULL                                                 AS gender,
        4                                                    AS src_priority
    FROM app_user
    WHERE active_flag = 'Y'
      AND auth_type = 'LDAP'
      AND REGEXP_LIKE(username, '^[0-9]{6}$')

    UNION ALL

    SELECT
        TRIM(badge)                                         AS badge_raw,
        TRIM(worker_name)                                   AS worker_name,
        TRIM(nik)                                            AS nik,
        TRIM(jabatan)                                        AS jabatan,
        TRIM(department)                                     AS department,
        TRIM(section)                                        AS section,
        gender,
        5                                                    AS src_priority
    FROM ses_request_worker
),
normalized AS (
    SELECT
        s.*,
        CASE WHEN REGEXP_LIKE(badge_raw, '^[0-9]{6}$') THEN badge_raw END AS badge_key
    FROM sources s
    WHERE worker_name IS NOT NULL
),
merged AS (
    SELECT
        badge_key AS badge,
        FIRST_VALUE(worker_name) IGNORE NULLS OVER (
            PARTITION BY badge_key ORDER BY src_priority
            ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING)        AS worker_name,
        FIRST_VALUE(nik) IGNORE NULLS OVER (
            PARTITION BY badge_key ORDER BY src_priority
            ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING)        AS nik,
        FIRST_VALUE(jabatan) IGNORE NULLS OVER (
            PARTITION BY badge_key ORDER BY src_priority
            ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING)        AS jabatan,
        FIRST_VALUE(department) IGNORE NULLS OVER (
            PARTITION BY badge_key ORDER BY src_priority
            ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING)        AS department,
        FIRST_VALUE(section) IGNORE NULLS OVER (
            PARTITION BY badge_key ORDER BY src_priority
            ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING)        AS section,
        FIRST_VALUE(gender) IGNORE NULLS OVER (
            PARTITION BY badge_key ORDER BY src_priority
            ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING)        AS gender,
        ROW_NUMBER() OVER (PARTITION BY badge_key ORDER BY src_priority)     AS rn
    FROM normalized
    WHERE badge_key IS NOT NULL
),
final_rows AS (
    SELECT badge, worker_name, nik, jabatan, department, section, gender
    FROM merged
    WHERE rn = 1

    UNION ALL

    SELECT badge_raw AS badge, worker_name, nik, jabatan, department, section, gender
    FROM normalized
    WHERE badge_key IS NULL
)
SELECT
    ROW_NUMBER() OVER (ORDER BY badge NULLS LAST, worker_name) AS lookup_id,
    badge,
    worker_name,
    nik,
    jabatan,
    department,
    section,
    gender
FROM final_rows;

-- 4. SES_APPROVAL_PENDING_VIEW (Parallel Hybrid Inbox Resolver)
CREATE OR REPLACE VIEW ses_approval_pending_view AS
WITH pending_assignees AS (
    SELECT
        sat.task_id, sat.instance_id, sai.business_ref_type, sai.business_ref_id,
        sat.step_name, sata.assignee_type, sata.pos_id,
        sata.empl_id AS frozen_empl_id, sata.display_name AS frozen_display_name,
        sata.email AS frozen_email,
        sai.requestor_user_id, sai.created_date AS req_date
    FROM ses_approval_task_assignee sata
    JOIN ses_approval_task sat ON sat.task_id = sata.task_id
    JOIN ses_approval_instance sai ON sai.instance_id = sat.instance_id
                                   AND sai.status = 'PENDING'
    WHERE sat.status = 'PENDING'
      AND sata.resolution_source != 'MANAGER_SKIP'
),
position_holders AS (
    SELECT DISTINCT
           TRIM(p.pos_id) AS pos_id,
           TRIM(p.holder_empl_id) AS holder_empl_id,
           p.holder_empl_name,
           p.holder_email
    FROM app_position_view p
    WHERE p.holder_empl_id IS NOT NULL
),
live_from_tmp AS (
    SELECT pa.task_id, pa.instance_id, pa.business_ref_type, pa.business_ref_id, pa.step_name,
           t.empid AS approver_empl_id, e.name_display AS approver_name, e.email AS approver_email,
           pa.requestor_user_id, pa.req_date
    FROM pending_assignees pa
    JOIN app_position_tmp_view t ON TRIM(t.posid) = TRIM(pa.pos_id)
                                  AND TRUNC(SYSDATE) BETWEEN t.start_date AND t.end_date
    LEFT JOIN app_emp_info_view e ON TRIM(e.emplid) = t.empid
    WHERE pa.pos_id IS NOT NULL
),
live_from_holder AS (
    SELECT pa.task_id, pa.instance_id, pa.business_ref_type, pa.business_ref_id, pa.step_name,
           ph.holder_empl_id AS approver_empl_id, ph.holder_empl_name AS approver_name,
           ph.holder_email AS approver_email,
           pa.requestor_user_id, pa.req_date
    FROM pending_assignees pa
    JOIN position_holders ph ON ph.pos_id = TRIM(pa.pos_id)
    WHERE pa.pos_id IS NOT NULL
      AND NOT EXISTS (
          SELECT 1 FROM app_position_tmp_view t
          WHERE TRIM(t.posid) = TRIM(pa.pos_id) AND TRUNC(SYSDATE) BETWEEN t.start_date AND t.end_date
      )
),
live_from_emp AS (
    SELECT pa.task_id, pa.instance_id, pa.business_ref_type, pa.business_ref_id, pa.step_name,
           TRIM(e.emplid) AS approver_empl_id, e.name_display AS approver_name, e.email AS approver_email,
           pa.requestor_user_id, pa.req_date
    FROM pending_assignees pa
    JOIN app_emp_info_view e ON e.position_nbr = pa.pos_id
    WHERE pa.pos_id IS NOT NULL
      AND NOT EXISTS (
          SELECT 1 FROM app_position_tmp_view t
          WHERE TRIM(t.posid) = TRIM(pa.pos_id) AND TRUNC(SYSDATE) BETWEEN t.start_date AND t.end_date
      )
      AND NOT EXISTS (
          SELECT 1 FROM position_holders ph
          WHERE ph.pos_id = TRIM(pa.pos_id)
      )
),
fallback_frozen AS (
    SELECT pa.task_id, pa.instance_id, pa.business_ref_type, pa.business_ref_id, pa.step_name,
           pa.frozen_empl_id AS approver_empl_id, pa.frozen_display_name AS approver_name,
           pa.frozen_email AS approver_email,
           pa.requestor_user_id, pa.req_date
    FROM pending_assignees pa
    WHERE pa.assignee_type = 'USER'
       OR pa.pos_id IS NULL
       OR (
            NOT EXISTS (
                SELECT 1 FROM app_position_tmp_view t
                WHERE TRIM(t.posid) = TRIM(pa.pos_id) AND TRUNC(SYSDATE) BETWEEN t.start_date AND t.end_date
            )
            AND NOT EXISTS (SELECT 1 FROM position_holders ph WHERE ph.pos_id = TRIM(pa.pos_id))
            AND NOT EXISTS (SELECT 1 FROM app_emp_info_view e WHERE e.position_nbr = pa.pos_id)
          )
),
resolved AS (
    SELECT * FROM live_from_tmp
    UNION ALL
    SELECT * FROM live_from_holder
    UNION ALL
    SELECT * FROM live_from_emp
    UNION ALL
    SELECT * FROM fallback_frozen
)
SELECT r.task_id, r.instance_id, r.business_ref_type, r.business_ref_id, r.step_name,
       r.approver_empl_id, r.approver_name, r.approver_email,
       r.requestor_user_id, r.req_date
FROM resolved r
WHERE NOT EXISTS (
    SELECT 1 FROM ses_approval_task_signoff so
    WHERE so.task_id = r.task_id
      AND TRIM(so.empl_id) = TRIM(r.approver_empl_id)
      AND so.status != 'PENDING'
);

-- 5. APP_APPROVAL_VIEW (Navbar Badge Counter)
CREATE OR REPLACE VIEW app_approval_view AS
SELECT
    p.task_id                                              AS trans_id,
    p.business_ref_type                                    AS trans_cat,
    p.step_name || ' - ' || COALESCE(r.request_no, s.request_no) AS trans_desc,
    p.approver_empl_id                                      AS approver_id,
    p.approver_name                                         AS approver_name,
    p.requestor_user_id                                     AS req_by,
    ru.full_name                                            AS req_by_name,
    p.req_date                                              AS req_date,
    '/approval/detail/' || p.instance_id                    AS url_detail,
    '/approval'                                             AS url_apprv,
    0                                                       AS is_simple_apprv
FROM ses_approval_pending_view p
LEFT JOIN ses_request r         ON p.business_ref_type = 'SES_REQUEST'
                                AND r.request_id = p.business_ref_id
LEFT JOIN ses_sticker_request s ON p.business_ref_type = 'SES_STICKER_REQUEST'
                                AND s.sticker_request_id = p.business_ref_id
LEFT JOIN app_user ru           ON ru.username = p.requestor_user_id;

PROMPT =========================================================================
PROMPT [SES SYNC] Step 5/8: Synchronizing Master Data (Zones, Docs, Request Types)
PROMPT =========================================================================

-- 1. SES_ZONE (Insert if missing)
INSERT INTO ses_zone (zone_code, zone_name, order_by, active_flag, created_by, created_date)
SELECT 'ZONE_01', 'Zone 01 (Offsite / Administration)', 10, 'Y', 'SYSTEM', SYSDATE FROM dual WHERE NOT EXISTS (SELECT 1 FROM ses_zone WHERE zone_code = 'ZONE_01');
INSERT INTO ses_zone (zone_code, zone_name, order_by, active_flag, created_by, created_date)
SELECT 'ZONE_02', 'Zone 02 (Utility / Warehouse)',      20, 'Y', 'SYSTEM', SYSDATE FROM dual WHERE NOT EXISTS (SELECT 1 FROM ses_zone WHERE zone_code = 'ZONE_02');
INSERT INTO ses_zone (zone_code, zone_name, order_by, active_flag, created_by, created_date)
SELECT 'ZONE_03', 'Zone 03 (Process Train / Marine)',   30, 'Y', 'SYSTEM', SYSDATE FROM dual WHERE NOT EXISTS (SELECT 1 FROM ses_zone WHERE zone_code = 'ZONE_03');

-- 2. SES_DOC_REQUIREMENT (Insert if missing)
INSERT INTO ses_doc_requirement (doc_code, doc_name, doc_desc, active_flag, created_by, created_date)
SELECT 'KTP', 'Kartu Tanda Penduduk (KTP) / Paspor', 'Indonesian National ID Card (KTP) or International Passport', 'Y', 'SYSTEM', SYSDATE
FROM dual WHERE NOT EXISTS (SELECT 1 FROM ses_doc_requirement WHERE doc_code = 'KTP');

INSERT INTO ses_doc_requirement (doc_code, doc_name, doc_desc, active_flag, created_by, created_date)
SELECT 'KK', 'Kartu Keluarga (KK)', 'Indonesian Family Card / Kartu Keluarga', 'Y', 'SYSTEM', SYSDATE
FROM dual WHERE NOT EXISTS (SELECT 1 FROM ses_doc_requirement WHERE doc_code = 'KK');

INSERT INTO ses_doc_requirement (doc_code, doc_name, doc_desc, active_flag, created_by, created_date)
SELECT 'SKCK', 'Surat Keterangan Catatan Kepolisian', 'Police Record Certificate (SKCK)', 'Y', 'SYSTEM', SYSDATE
FROM dual WHERE NOT EXISTS (SELECT 1 FROM ses_doc_requirement WHERE doc_code = 'SKCK');

INSERT INTO ses_doc_requirement (doc_code, doc_name, doc_desc, active_flag, created_by, created_date)
SELECT 'MCU', 'Surat Keterangan Sehat / MCU', 'Medical Checkup & Health Certificate', 'Y', 'SYSTEM', SYSDATE
FROM dual WHERE NOT EXISTS (SELECT 1 FROM ses_doc_requirement WHERE doc_code = 'MCU');

-- 3. SES_REQUEST_TYPE (Official Types: Insert if missing; preserve existing custom configurations)
INSERT INTO ses_request_type (type_code, type_name, needs_payment_flag, payment_amount, needs_mgr_approval_flag, needs_proc_approval_flag, auth_type, need_ext_page_upload, order_by, active_flag, created_by, created_date)
SELECT 'BADGE_NEW', 'Tanda Pengenal Pekerja (Baru)', 'Y', 50000, 'Y', 'Y', 'ALL', 'Y', 10, 'Y', 'SYSTEM', SYSDATE
FROM dual WHERE NOT EXISTS (SELECT 1 FROM ses_request_type WHERE type_code = 'BADGE_NEW');

INSERT INTO ses_request_type (type_code, type_name, needs_payment_flag, payment_amount, needs_mgr_approval_flag, needs_proc_approval_flag, auth_type, need_ext_page_upload, order_by, active_flag, created_by, created_date)
SELECT 'BADGE_EXT', 'Tanda Pengenal Pekerja (Perpanjangan)', 'Y', 25000, 'Y', 'Y', 'ALL', 'Y', 20, 'Y', 'SYSTEM', SYSDATE
FROM dual WHERE NOT EXISTS (SELECT 1 FROM ses_request_type WHERE type_code = 'BADGE_EXT');

INSERT INTO ses_request_type (type_code, type_name, needs_payment_flag, payment_amount, needs_mgr_approval_flag, needs_proc_approval_flag, auth_type, need_ext_page_upload, order_by, active_flag, created_by, created_date)
SELECT 'SCREENING', 'Screening Saja (Non-Badge)', 'N', 0, 'Y', 'N', 'ALL', 'N', 30, 'Y', 'SYSTEM', SYSDATE
FROM dual WHERE NOT EXISTS (SELECT 1 FROM ses_request_type WHERE type_code = 'SCREENING');

-- 4. SES_REQUEST_TYPE_DOC mapping
INSERT INTO ses_request_type_doc (type_code, doc_code, mandatory_flag, order_by, created_by, created_date)
SELECT 'BADGE_NEW', 'KTP', 'Y', 10, 'SYSTEM', SYSDATE FROM dual
WHERE NOT EXISTS (SELECT 1 FROM ses_request_type_doc WHERE type_code = 'BADGE_NEW' AND doc_code = 'KTP');

INSERT INTO ses_request_type_doc (type_code, doc_code, mandatory_flag, order_by, created_by, created_date)
SELECT 'BADGE_NEW', 'KK', 'Y', 20, 'SYSTEM', SYSDATE FROM dual
WHERE NOT EXISTS (SELECT 1 FROM ses_request_type_doc WHERE type_code = 'BADGE_NEW' AND doc_code = 'KK');

INSERT INTO ses_request_type_doc (type_code, doc_code, mandatory_flag, order_by, created_by, created_date)
SELECT 'BADGE_EXT', 'KTP', 'Y', 10, 'SYSTEM', SYSDATE FROM dual
WHERE NOT EXISTS (SELECT 1 FROM ses_request_type_doc WHERE type_code = 'BADGE_EXT' AND doc_code = 'KTP');

INSERT INTO ses_request_type_doc (type_code, doc_code, mandatory_flag, order_by, created_by, created_date)
SELECT 'SCREENING', 'KTP', 'Y', 10, 'SYSTEM', SYSDATE FROM dual
WHERE NOT EXISTS (SELECT 1 FROM ses_request_type_doc WHERE type_code = 'SCREENING' AND doc_code = 'KTP');

-- 5. SES_STICKER_TYPE
INSERT INTO ses_sticker_type (sticker_type_code, sticker_type_name, price, order_by, active_flag, created_by, created_date)
SELECT 'REGULAR', 'Stiker Reguler Pekerja', 20000, 10, 'Y', 'SYSTEM', SYSDATE
FROM dual WHERE NOT EXISTS (SELECT 1 FROM ses_sticker_type WHERE sticker_type_code = 'REGULAR');

INSERT INTO ses_sticker_type (sticker_type_code, sticker_type_name, price, order_by, active_flag, created_by, created_date)
SELECT 'VIP', 'Stiker VIP / Tamu Perusahaan', 50000, 20, 'Y', 'SYSTEM', SYSDATE
FROM dual WHERE NOT EXISTS (SELECT 1 FROM ses_sticker_type WHERE sticker_type_code = 'VIP');

INSERT INTO ses_sticker_type (sticker_type_code, sticker_type_name, price, order_by, active_flag, created_by, created_date)
SELECT 'CONTRACTOR', 'Stiker Kendaraan Proyek / Kontraktor', 35000, 30, 'Y', 'SYSTEM', SYSDATE
FROM dual WHERE NOT EXISTS (SELECT 1 FROM ses_sticker_type WHERE sticker_type_code = 'CONTRACTOR');

PROMPT =========================================================================
PROMPT [SES SYNC] Step 6/8: Synchronizing Pages, Menus & RBAC (Zero Deletion)
PROMPT =========================================================================

-- 1. Roles: REQUESTOR & SECURITY
INSERT INTO app_role (role_id, role_name, role_desc, active_flag, created_by, created_date)
SELECT 4, 'REQUESTOR', 'Employee / Requestor self-service role', 'Y', 'SYSTEM', SYSDATE
FROM dual WHERE NOT EXISTS (SELECT 1 FROM app_role WHERE role_name = 'REQUESTOR');

INSERT INTO app_role (role_id, role_name, role_desc, active_flag, created_by, created_date)
SELECT 5, 'SECURITY', 'Security Officer for Checkpoints and Verification', 'Y', 'SYSTEM', SYSDATE
FROM dual WHERE NOT EXISTS (SELECT 1 FROM app_role WHERE role_name = 'SECURITY');

-- 2. APP_PAGE Registrations (Insert if missing)
DECLARE
    PROCEDURE ensure_page(p_id NUMBER, p_name VARCHAR2, p_url VARCHAR2, p_desc VARCHAR2) IS
        v_cnt    NUMBER;
        v_id_cnt NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_cnt FROM app_page WHERE page_url = p_url;
        IF v_cnt = 0 THEN
            SELECT COUNT(*) INTO v_id_cnt FROM app_page WHERE page_id = p_id;
            IF v_id_cnt > 0 THEN
                INSERT INTO app_page (page_id, page_name, page_url, page_desc, active_flag, created_by, created_date)
                VALUES (SEQ_APP_PAGE.NEXTVAL, p_name, p_url, p_desc, 'Y', 'SYSTEM', SYSDATE);
            ELSE
                INSERT INTO app_page (page_id, page_name, page_url, page_desc, active_flag, created_by, created_date)
                VALUES (p_id, p_name, p_url, p_desc, 'Y', 'SYSTEM', SYSDATE);
            END IF;
        ELSE
            UPDATE app_page SET page_name = p_name, page_desc = p_desc
            WHERE page_url = p_url;
        END IF;
    END;
BEGIN
    ensure_page(19, 'Approvals', '/approval', 'Approval worklist, my requests, and detail (read)');
    ensure_page(20, 'Approval Action', '/approval/form', 'Approve/reject actions on current task');
    ensure_page(23, 'Approval Chains', '/approval/chain', 'Approval chain list (read)');
    ensure_page(24, 'Approval Chain Form', '/approval/chain/form', 'Create/edit/reorder/delete chains, steps, assignees');
    ensure_page(25, 'Approval Form Mapping', '/approval/map', 'Form-to-chain mapping list (read)');
    ensure_page(26, 'Approval Form Mapping Form', '/approval/map/form', 'Edit a form default chain and overrides');
    ensure_page(27, 'Badge Karyawan', '/request', 'My requests list (read)');
    ensure_page(28, 'Form Badge Karyawan', '/request/form', 'Create/edit/cancel a request');
    ensure_page(29, 'Contract Master', '/admin/contract', 'Contract list (read)');
    ensure_page(30, 'Contract Master Form', '/admin/contract/form', 'Create/edit a contract');
    ensure_page(31, 'Konfigurasi Jenis Pengajuan', '/admin/request-type', 'Request type list (read)');
    ensure_page(32, 'Form Jenis Pengajuan', '/admin/request-type/form', 'Create/edit a request type and document requirements');
    ensure_page(33, 'Switch User', '/switchuser', 'Impersonate another active user');
    ensure_page(34, 'My Payments', '/payment', 'Requestor payment list (read)');
    ensure_page(35, 'Payment Upload', '/payment/form', 'Upload payment receipt');
    ensure_page(36, 'Payment Verification', '/payment/verify', 'Finance officer payment worklist');
    ensure_page(37, 'Payment Verification Form', '/payment/verify/form', 'Verify / reject proof of payment');
    ensure_page(38, 'Cash Inflow Recap', '/payment/recap', 'Verified revenue summary and financial reports');
    ensure_page(39, 'Scheduling Queue', '/scheduling/queue', 'Screening scheduling queue (read)');
    ensure_page(40, 'Scheduling Form', '/scheduling/queue/form', 'Assign date/time/venue and record worker attendance');
    ensure_page(41, 'Scheduling Calendar', '/scheduling/calendar', 'FullCalendar view of scheduled screenings');
    ensure_page(42, 'Sticker Kendaraan', '/sticker', 'Vehicle sticker request list (read)');
    ensure_page(43, 'Sticker Kendaraan Form', '/sticker/form', 'Create/edit a vehicle sticker request');
    ensure_page(44, 'Konfigurasi Jenis Sticker', '/admin/sticker-type', 'Vehicle sticker type master (read)');
    ensure_page(45, 'Form Jenis Sticker', '/admin/sticker-type/form', 'Create/edit a vehicle sticker type');
    ensure_page(46, 'Riwayat', '/history', 'Unified history and tracking across all modules');
    ensure_page(47, 'Dashboard', '/dashboard', 'Summary cards, trends, and cash inflow recap');
END;
/

-- Cash Expense Pages (if missing)
DECLARE
    v_cnt NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_cnt FROM app_page WHERE page_url = '/admin/cash-expense';
    IF v_cnt = 0 THEN
        INSERT INTO app_page (page_id, page_name, page_url, page_desc, active_flag, created_by, created_date)
        VALUES (SEQ_APP_PAGE.NEXTVAL, 'Pengeluaran Kas', '/admin/cash-expense',
                'Pencatatan pengeluaran kas, histori, dan ringkasan Kas Masuk/Keluar/Balance',
                'Y', 'SYSTEM', SYSDATE);
    END IF;

    SELECT COUNT(*) INTO v_cnt FROM app_page WHERE page_url = '/admin/cash-expense/form';
    IF v_cnt = 0 THEN
        INSERT INTO app_page (page_id, page_name, page_url, page_desc, active_flag, created_by, created_date)
        VALUES (SEQ_APP_PAGE.NEXTVAL, 'Pengeluaran Kas (Form)', '/admin/cash-expense/form',
                'URL virtual — hak CREATE/UPDATE/DELETE/SUBMIT Pengeluaran Kas',
                'Y', 'SYSTEM', SYSDATE);
    END IF;
END;
/

-- 3. APP_MENU Structure (Non-destructive update & insert without deleting menu rows)
DECLARE
    PROCEDURE ensure_menu(p_id NUMBER, p_name VARCHAR2, p_url VARCHAR2, p_icon VARCHAR2,
                          p_order NUMBER, p_parent NUMBER) IS
        v_cnt NUMBER;
        v_page_id NUMBER := NULL;
    BEGIN
        IF p_url IS NOT NULL THEN
            BEGIN
                SELECT page_id INTO v_page_id FROM app_page WHERE page_url = p_url;
            EXCEPTION WHEN NO_DATA_FOUND THEN v_page_id := NULL;
            END;
        END IF;

        SELECT COUNT(*) INTO v_cnt FROM app_menu WHERE menu_id = p_id;
        IF v_cnt = 0 THEN
            INSERT INTO app_menu (menu_id, menu_name, page_id, menu_icon, menu_order, active_flag, parent_menu_id, created_by, created_date)
            VALUES (p_id, p_name, v_page_id, p_icon, p_order, 'Y', p_parent, 'SYSTEM', SYSDATE);
        ELSE
            UPDATE app_menu
            SET menu_name = p_name,
                page_id = NVL(v_page_id, page_id),
                menu_icon = p_icon,
                menu_order = p_order,
                parent_menu_id = p_parent,
                updated_by = 'SYSTEM',
                updated_date = SYSDATE
            WHERE menu_id = p_id;
        END IF;
    END;
BEGIN
    -- Top-level navigation
    ensure_menu(30, 'Dashboard',                '/dashboard',              'bi bi-speedometer2',   0, NULL);
    ensure_menu(20, 'SES',                      NULL,                      'bi bi-shield-lock',    1, NULL);
    ensure_menu(21, 'SES (Admin)',              NULL,                      'bi bi-shield-shaded',  2, NULL);
    ensure_menu(29, 'Riwayat',                  '/history',                'bi bi-clock-history',  3, NULL);
    ensure_menu(31, 'SES Form Configuration',   NULL,                      'bi bi-gear-fill',      4, NULL);
    ensure_menu(15, 'Approvals',                '/approval',               'bi bi-check2-circle',  5, NULL);

    -- Sub-menus under 20 (SES Group)
    ensure_menu(14, 'Badge Karyawan',           '/request',                'bi bi-file-earmark-plus', 1, 20);
    ensure_menu(27, 'Sticker Kendaraan',        '/sticker',                'bi bi-car-front',         2, 20);
    ensure_menu(22, 'Pembayaran',               '/payment',                'bi bi-credit-card',       3, 20);
    ensure_menu(26, 'Jadwal',                   '/scheduling/calendar',    'bi bi-calendar-check',    4, 20);

    -- Sub-menus under 21 (SES Admin Group)
    ensure_menu(23, 'Verifikasi Pembayaran',    '/payment/verify',         'bi bi-cash-coin',      1, 21);
    ensure_menu(25, 'Penjadwalan',              '/scheduling/queue',       'bi bi-calendar-event', 2, 21);
    ensure_menu(24, 'Pemasukan Kas',            '/payment/recap',          'bi bi-wallet2',        3, 21);

    -- Sub-menus under 31 (SES Form Configuration)
    ensure_menu(16, 'Approval Chains',          '/approval/chain',         'bi bi-diagram-3',       1, 31);
    ensure_menu(17, 'Approval Form Mapping',    '/approval/map',           'bi bi-link-45deg',      2, 31);
    ensure_menu(19, 'Konfigurasi Jenis Pengajuan', '/admin/request-type',  'bi bi-card-checklist',  3, 31);
    ensure_menu(28, 'Konfigurasi Jenis Sticker',   '/admin/sticker-type',  'bi bi-tag',             4, 31);
    ensure_menu(18, 'Contract Master',          '/admin/contract',         'bi bi-file-text',       6, 31);
END;
/

-- 4. RBAC Grants (Idempotent: Adds permissions if missing, never deletes existing grants)

-- ADMIN & SYSADM Grants
INSERT INTO app_role_page (role_id, page_id, action_code, added_by, added_date)
SELECT r.role_id, p.page_id,
       CASE
           WHEN p.page_url IN ('/history', '/dashboard') THEN 'READ,SUBMIT'
           ELSE 'READ,CREATE,UPDATE,DELETE,ADD,REMOVE,SUBMIT'
       END,
       'SYSTEM', SYSDATE
FROM app_role r
CROSS JOIN app_page p
WHERE r.role_name IN ('ADMIN', 'SYSADM')
  AND p.page_url IN (
      '/approval', '/approval/form', '/approval/chain', '/approval/chain/form',
      '/approval/map', '/approval/map/form', '/request', '/request/form',
      '/admin/contract', '/admin/contract/form', '/admin/request-type', '/admin/request-type/form',
      '/payment', '/payment/form', '/payment/verify', '/payment/verify/form', '/payment/recap',
      '/scheduling/queue', '/scheduling/queue/form', '/scheduling/calendar',
      '/sticker', '/sticker/form', '/admin/sticker-type', '/admin/sticker-type/form',
      '/history', '/dashboard', '/switchuser', '/admin/cash-expense', '/admin/cash-expense/form'
  )
  AND NOT EXISTS (
      SELECT 1 FROM app_role_page rp
      WHERE rp.role_id = r.role_id AND rp.page_id = p.page_id
  );

INSERT INTO app_role_menu (role_id, menu_id, added_by, added_date)
SELECT r.role_id, m.menu_id, 'SYSTEM', SYSDATE
FROM app_role r
CROSS JOIN app_menu m
WHERE r.role_name IN ('ADMIN', 'SYSADM')
  AND NOT EXISTS (
      SELECT 1 FROM app_role_menu rm
      WHERE rm.role_id = r.role_id AND rm.menu_id = m.menu_id
  );

-- REQUESTOR Grants
INSERT INTO app_role_page (role_id, page_id, action_code, added_by, added_date)
SELECT r.role_id, p.page_id,
       CASE p.page_url
           WHEN '/request' THEN 'READ,CREATE,SUBMIT'
           WHEN '/request/form' THEN 'READ,CREATE,SUBMIT'
           WHEN '/sticker' THEN 'READ,CREATE,SUBMIT'
           WHEN '/sticker/form' THEN 'READ,CREATE,SUBMIT'
           WHEN '/payment' THEN 'READ,SUBMIT'
           WHEN '/payment/form' THEN 'READ,SUBMIT'
           WHEN '/approval' THEN 'READ,SUBMIT'
           WHEN '/scheduling/calendar' THEN 'READ'
           WHEN '/dashboard' THEN 'READ'
           WHEN '/history' THEN 'READ'
           ELSE 'READ'
       END,
       'SYSTEM', SYSDATE
FROM app_role r
CROSS JOIN app_page p
WHERE r.role_name = 'REQUESTOR'
  AND p.page_url IN (
      '/request', '/request/form', '/sticker', '/sticker/form',
      '/payment', '/payment/form', '/approval', '/scheduling/calendar',
      '/dashboard', '/history'
  )
  AND NOT EXISTS (
      SELECT 1 FROM app_role_page rp
      WHERE rp.role_id = r.role_id AND rp.page_id = p.page_id
  );

INSERT INTO app_role_menu (role_id, menu_id, added_by, added_date)
SELECT r.role_id, m.menu_id, 'SYSTEM', SYSDATE
FROM app_role r
CROSS JOIN app_menu m
WHERE r.role_name = 'REQUESTOR'
  AND m.menu_id IN (30, 20, 14, 27, 22, 26, 15, 29)
  AND NOT EXISTS (
      SELECT 1 FROM app_role_menu rm
      WHERE rm.role_id = r.role_id AND rm.menu_id = m.menu_id
  );

-- SECURITY Grants
INSERT INTO app_role_page (role_id, page_id, action_code, added_by, added_date)
SELECT r.role_id, p.page_id,
       CASE p.page_url
           WHEN '/payment/verify' THEN 'READ'
           WHEN '/payment/verify/form' THEN 'READ,SUBMIT'
           WHEN '/scheduling/queue' THEN 'READ'
           WHEN '/scheduling/queue/form' THEN 'READ,SUBMIT'
           WHEN '/scheduling/calendar' THEN 'READ'
           WHEN '/history' THEN 'READ,PRINT'
           WHEN '/approval' THEN 'READ,CREATE,UPDATE,ADD,SUBMIT'
           WHEN '/approval/form' THEN 'READ,CREATE,UPDATE,ADD,SUBMIT'
           WHEN '/request' THEN 'READ,CREATE,SUBMIT'
           WHEN '/request/form' THEN 'READ,CREATE,SUBMIT'
           WHEN '/dashboard' THEN 'READ'
           ELSE 'READ'
       END,
       'SYSTEM', SYSDATE
FROM app_role r
CROSS JOIN app_page p
WHERE r.role_name = 'SECURITY'
  AND p.page_url IN (
      '/payment/verify', '/payment/verify/form',
      '/scheduling/queue', '/scheduling/queue/form', '/scheduling/calendar',
      '/history', '/approval', '/approval/form', '/request', '/request/form', '/dashboard'
  )
  AND NOT EXISTS (
      SELECT 1 FROM app_role_page rp
      WHERE rp.role_id = r.role_id AND rp.page_id = p.page_id
  );

INSERT INTO app_role_menu (role_id, menu_id, added_by, added_date)
SELECT r.role_id, m.menu_id, 'SYSTEM', SYSDATE
FROM app_role r
CROSS JOIN app_menu m
WHERE r.role_name = 'SECURITY'
  AND m.menu_id IN (30, 20, 14, 26, 21, 23, 25, 29, 15)
  AND NOT EXISTS (
      SELECT 1 FROM app_role_menu rm
      WHERE rm.role_id = r.role_id AND rm.menu_id = m.menu_id
  );

PROMPT =========================================================================
PROMPT [SES SYNC] Step 7/8: Synchronizing Approval Chains & Form Mapping (Safe)
PROMPT =========================================================================

-- Register SES_FORM descriptors if missing
DECLARE
    PROCEDURE ensure_form(p_code VARCHAR2, p_name VARCHAR2, p_desc VARCHAR2) IS
        v_cnt NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_cnt FROM ses_form WHERE form_code = p_code;
        IF v_cnt = 0 THEN
            INSERT INTO ses_form (form_id, form_code, form_name, description, active_flag, created_by, created_date)
            VALUES (SEQ_SES_FORM.NEXTVAL, p_code, p_name, p_desc, 'Y', 'SYSTEM', SYSDATE);
            DBMS_OUTPUT.PUT_LINE('  [+] Registered form: ' || p_code);
        ELSE
            DBMS_OUTPUT.PUT_LINE('  [=] Form exists:     ' || p_code || ' (preserved)');
        END IF;
    END;
BEGIN
    ensure_form('SES_REQUEST',         'Pengajuan Badge Karyawan',   'Formulir Pengajuan Tanda Pengenal Masuk Pekerja (SES)');
    ensure_form('SES_STICKER_REQUEST', 'Pengajuan Sticker Kendaraan', 'Formulir Permohonan Stiker Keamanan Kendaraan (SES)');
END;
/

-- Synchronize chain naming safely (align legacy 'DEFAULT'/'PAYMENT' only if target keys do not already exist)
DECLARE
    v_cnt NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_cnt FROM ses_approval_chain WHERE chain_key = 'BADGE_PAYMENT';
    IF v_cnt = 0 THEN
        UPDATE ses_approval_chain
        SET chain_key = 'BADGE_PAYMENT', chain_name = 'Badge Karyawan (Payment)'
        WHERE chain_key = 'PAYMENT';
    END IF;

    SELECT COUNT(*) INTO v_cnt FROM ses_approval_chain WHERE chain_key = 'BADGE_DEFAULT';
    IF v_cnt = 0 THEN
        UPDATE ses_approval_chain
        SET chain_key = 'BADGE_DEFAULT', chain_name = 'Badge Karyawan (Non-Payment)'
        WHERE chain_key = 'DEFAULT';
    END IF;
END;
/

-- Synchronize 7-step hybrid chain: only creates chain and baseline steps if missing; NEVER drops existing steps!
DECLARE
    v_admin_user_id    VARCHAR2(36) := '00000000-0000-0000-0000-000000000001';
    v_sec_mgr_pos_id   VARCHAR2(10) := 'E01000';
    v_proc_supv_pos_id VARCHAR2(10) := 'EH1200';
    v_proc_mgr_pos_id  VARCHAR2(10) := 'EH1000';
    v_officer_pos_id   VARCHAR2(10) := 'E01G10';

    PROCEDURE add_assignee(p_step_id IN NUMBER, p_type IN VARCHAR2,
                           p_pos_id IN VARCHAR2, p_user_id IN VARCHAR2, p_order IN NUMBER) IS
    BEGIN
        INSERT INTO ses_approval_assignee
            (assignee_id, step_id, assignee_type, pos_id, user_id, order_by, created_by, created_date)
        VALUES (SEQ_SES_APPROVAL_ASSIGNEE.NEXTVAL, p_step_id, p_type, p_pos_id, p_user_id, p_order,
                'SYSTEM', SYSDATE);
    END add_assignee;

    PROCEDURE add_checkpoint(p_step_id IN NUMBER, p_label IN VARCHAR2,
                             p_key IN VARCHAR2, p_order IN NUMBER) IS
    BEGIN
        INSERT INTO ses_approval_step_task
            (step_task_id, step_id, task_label, checkpoint_key, order_by, created_by, created_date)
        VALUES (SEQ_SES_APPROVAL_STEP_TASK.NEXTVAL, p_step_id, p_label, p_key, p_order,
                'SYSTEM', SYSDATE);
    END add_checkpoint;

    FUNCTION new_step(p_chain_id IN NUMBER, p_order IN NUMBER, p_name IN VARCHAR2,
                      p_role IN VARCHAR2, p_group IN NUMBER, p_rule IN VARCHAR2) RETURN NUMBER IS
        v_step_id NUMBER;
    BEGIN
        INSERT INTO ses_approval_step
            (step_id, chain_id, step_order, step_name, step_role, parallel_group, completion_rule,
             created_by, created_date)
        VALUES (SEQ_SES_APPROVAL_STEP.NEXTVAL, p_chain_id, p_order, p_name, p_role, p_group, p_rule,
                'SYSTEM', SYSDATE)
        RETURNING step_id INTO v_step_id;
        RETURN v_step_id;
    END new_step;

    PROCEDURE sync_chain(p_key IN VARCHAR2, p_name IN VARCHAR2, p_is_payment IN BOOLEAN) IS
        v_chain_id NUMBER;
        v_done     NUMBER;
        v_s1 NUMBER; v_s2 NUMBER; v_s3 NUMBER; v_s4 NUMBER;
        v_s5 NUMBER; v_s6 NUMBER; v_s7 NUMBER;
    BEGIN
        BEGIN
            SELECT chain_id INTO v_chain_id FROM ses_approval_chain WHERE chain_key = p_key;
        EXCEPTION
            WHEN NO_DATA_FOUND THEN
                -- Create chain if it does not exist at all
                INSERT INTO ses_approval_chain (chain_id, chain_name, chain_key, description, active_flag, created_by, created_date)
                VALUES (SEQ_SES_APPROVAL_CHAIN.NEXTVAL, p_name, p_key, '7-step serial hybrid chain', 'Y', 'SYSTEM', SYSDATE)
                RETURNING chain_id INTO v_chain_id;
                DBMS_OUTPUT.PUT_LINE('  [+] Created chain: ' || p_key);
        END;

        -- If chain already has steps in production, preserve all existing steps without deletion!
        SELECT COUNT(*) INTO v_done FROM ses_approval_step WHERE chain_id = v_chain_id;
        IF v_done > 0 THEN
            DBMS_OUTPUT.PUT_LINE('  [=] Chain ' || p_key || ' already has ' || v_done || ' step(s) in production (preserved)');
            RETURN;
        END IF;

        -- Create standard 7 baseline steps only when chain currently has 0 steps
        v_s1 := new_step(v_chain_id, 1, 'Additional Routing (Staf Security)', 'SECURITY_STAFF',         1, 'ALL');
        v_s2 := new_step(v_chain_id, 2, 'Persetujuan Supervisor Procurement', 'PROCUREMENT_SUPERVISOR', 2, 'ANY');
        v_s3 := new_step(v_chain_id, 3, 'Persetujuan Manager Procurement',    'PROCUREMENT_MANAGER',    3, 'ANY');
        v_s4 := new_step(v_chain_id, 4, 'Persetujuan Manager Pemohon',        'REQUESTOR_MANAGER',      4, 'ANY');
        v_s5 := new_step(v_chain_id, 5, 'Persetujuan Manager Security',       'SECURITY_MANAGER',       5, 'ANY');
        v_s6 := new_step(v_chain_id, 6, 'Officer / Co-Officer Approval',      NULL,                     6, 'ANY');
        v_s7 := new_step(v_chain_id, 7, 'Badge Retrieval',                    NULL,                     7, 'ANY');

        add_assignee(v_s2, 'POSITION',          v_proc_supv_pos_id, NULL,            10);
        add_assignee(v_s2, 'USER',              NULL,               v_admin_user_id, 20);

        add_assignee(v_s3, 'POSITION',          v_proc_mgr_pos_id,  NULL,            10);
        add_assignee(v_s3, 'USER',              NULL,               v_admin_user_id, 20);

        add_assignee(v_s4, 'REQUESTOR_MANAGER', NULL,               NULL,            10);
        add_assignee(v_s4, 'USER',              NULL,               v_admin_user_id, 20);

        add_assignee(v_s5, 'POSITION',          v_sec_mgr_pos_id,   NULL,            10);
        add_assignee(v_s5, 'USER',              NULL,               v_admin_user_id, 20);

        add_assignee(v_s6, 'POSITION',          v_officer_pos_id,   NULL,            10);
        add_assignee(v_s6, 'USER',              NULL,               v_admin_user_id, 20);

        IF p_is_payment THEN
            add_checkpoint(v_s6, 'Payment proof verified', 'PAYMENT_VERIFICATION', 10);
        END IF;
        add_checkpoint(v_s6, 'Screening attended',      'SCREENING_SCHEDULE',       20);
        add_checkpoint(v_s7, 'Badge Retrieval',         'BADGE_RETRIEVAL_SCHEDULE', 10);

        DBMS_OUTPUT.PUT_LINE('  [+] Created 7-step baseline chain: ' || p_key);
    END sync_chain;
BEGIN
    sync_chain('BADGE_DEFAULT', 'Badge Karyawan (Non-Payment)', FALSE);
    sync_chain('BADGE_PAYMENT', 'Badge Karyawan (Payment)',     TRUE);
END;
/

-- Synchronize Form Approval Map for Paid Types
DECLARE
    v_chain_payment NUMBER;
    v_chain_default NUMBER;
    v_map_id NUMBER;
BEGIN
    SELECT MIN(chain_id) INTO v_chain_payment FROM ses_approval_chain WHERE chain_key = 'BADGE_PAYMENT';
    SELECT MIN(chain_id) INTO v_chain_default FROM ses_approval_chain WHERE chain_key = 'BADGE_DEFAULT';

    -- Ensure default form mapping for SES_REQUEST
    SELECT MAX(map_id) INTO v_map_id FROM ses_form_approval_map WHERE form_code = 'SES_REQUEST' AND match_key IS NULL;
    IF v_map_id IS NULL AND v_chain_default IS NOT NULL THEN
        INSERT INTO ses_form_approval_map (map_id, form_code, match_key, chain_id, active_flag, created_by, created_date)
        VALUES (SEQ_SES_FORM_APPROVAL_MAP.NEXTVAL, 'SES_REQUEST', NULL, v_chain_default, 'Y', 'SYSTEM', SYSDATE);
    END IF;

    -- Ensure mapping for paid request types to a chain carrying PAYMENT_VERIFICATION
    IF v_chain_payment IS NOT NULL THEN
        FOR t IN (SELECT type_code FROM ses_request_type WHERE needs_payment_flag = 'Y') LOOP
            SELECT MAX(map_id) INTO v_map_id FROM ses_form_approval_map WHERE form_code = 'SES_REQUEST' AND match_key = t.type_code;
            IF v_map_id IS NULL THEN
                INSERT INTO ses_form_approval_map (map_id, form_code, match_key, chain_id, active_flag, created_by, created_date)
                VALUES (SEQ_SES_FORM_APPROVAL_MAP.NEXTVAL, 'SES_REQUEST', t.type_code, v_chain_payment, 'Y', 'SYSTEM', SYSDATE);
                DBMS_OUTPUT.PUT_LINE('  [+] Mapped paid type ' || t.type_code || ' -> chain ' || v_chain_payment);
            END IF;
        END LOOP;
    END IF;
END;
/

PROMPT =========================================================================
PROMPT [SES SYNC] Step 8/8: Synchronizing Application Parameters (APP_PARAMETER)
PROMPT =========================================================================

-- Non-destructive MERGE: Updates param_desc, but preserves existing param_value configured in production!
MERGE INTO app_parameter t
USING (
    SELECT 'TESTING_MODE'         AS param_id,
           'OFF'                  AS param_value,
           'Testing mode notification display on e-mail (ON / OFF)' AS param_desc
      FROM dual
    UNION ALL
    SELECT 'TESTING_BANNER',
           '**TESTING MODE, PLEASE IGNORE**',
           'Testing message banner on e-mail'
      FROM dual
    UNION ALL
    SELECT 'REDIRECT_MAIL',
           'NO',
           '(YES / NO) Redirect All Outbound Mail to Admin Email'
      FROM dual
    UNION ALL
    SELECT 'ADMIN_EMAIL',
           '',
           'Administrator Email recipient for testing mode redirects'
      FROM dual
    UNION ALL
    SELECT 'APP_LINK',
           'http://apps.badaklng.home:8005/ses',
           'Internal Intranet Base URL for Notification Links'
      FROM dual
    UNION ALL
    SELECT 'APP_LINK_EXT',
           'http://apps.badaklng.home:8005/ses',
           'External Gateway Public URL for Contractor Document Uploads and QR Verification'
      FROM dual
    UNION ALL
    SELECT 'APP_MAIL_SUBJ_PREFIX',
           '[SES]',
           'E-mail subject prefix for all outgoing notifications'
      FROM dual
) s
ON (t.param_id = s.param_id)
WHEN MATCHED THEN
    UPDATE SET t.param_desc = s.param_desc,
               t.param_value = NVL(t.param_value, s.param_value)
WHEN NOT MATCHED THEN
    INSERT (param_id, param_value, param_desc, updated_by, updated_date)
    VALUES (s.param_id, s.param_value, s.param_desc, 'SYSTEM', SYSTIMESTAMP);

COMMIT;

PROMPT =========================================================================
PROMPT [SES SYNC] Synchronization completed successfully!
PROMPT Existing production data and operational tables were safely preserved.
PROMPT =========================================================================
