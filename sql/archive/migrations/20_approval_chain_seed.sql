-- ============================================================
-- SES — Approval chain seed: DEFAULT + PAYMENT — Oracle
-- Run AFTER 02_approval_ddl.sql (chain/step/assignee tables) and
-- AFTER platform/sql/02_seed_data.sql (needs the 'admin' user,
-- '00000000-0000-0000-0000-000000000001').
--
-- Chains, steps and assignees are normally fully GUI/DB-driven (see
-- ses/sql/README.md) -- this is a starting-point seed, not a
-- replacement for /approval/chain. Both chains use the same 3-step
-- shape:
--   Step 1 "Manager Approval" -- REQUESTOR_MANAGER (dynamic org-tree
--                                 resolution) + USER 'admin' as a
--                                 fallback assignee
--   Step 2 "Admin Approval"   -- USER 'admin'
--   Step 3 "Final Approval"   -- USER 'admin'
-- Two chains exist (DEFAULT, PAYMENT) so a future SES_FORM_APPROVAL_MAP
-- row can route specific request types to a different chain than the
-- catch-all default -- that routing (which request type/match_key goes
-- to which chain) is NOT seeded here, left for the admin to wire at
-- /approval/map (see also SES_REQUEST_TYPE.needs_payment_flag on
-- BADGE_NEW/BADGE_EXT).
--
-- Step 3 also carries the checkpoint gates for the three
-- CheckpointTaskType beans registered in code today: PAYMENT's
-- Final Approval gets all three (PAYMENT_VERIFICATION, plus
-- SCREENING_SCHEDULE and BADGE_RETRIEVAL_SCHEDULE); DEFAULT's Final
-- Approval gets only the two scheduling ones, excluding
-- PAYMENT_VERIFICATION. checkpoint_key must match a bean's getKey()
-- exactly for its GUI dropdown/auto-resolve to line up (see chain
-- editor's checkpoint-key select).
--
-- Idempotent: each chain guarded on chain_key; steps/assignees are
-- only created the same transaction the chain itself is created (a
-- chain found pre-existing is left completely alone, including any
-- steps an admin has since edited via the GUI).
-- ============================================================

DECLARE
    v_admin_user_id  app_user.user_id%TYPE := '00000000-0000-0000-0000-000000000001';
    v_chain_id       ses_approval_chain.chain_id%TYPE;
    v_step1_id       ses_approval_step.step_id%TYPE;
    v_step2_id       ses_approval_step.step_id%TYPE;
    v_step3_id       ses_approval_step.step_id%TYPE;

    PROCEDURE seed_chain(p_key IN VARCHAR2, p_name IN VARCHAR2, p_desc IN VARCHAR2, p_payment_checkpoint IN CHAR) IS
        v_exists NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_exists FROM ses_approval_chain WHERE chain_key = p_key;
        IF v_exists > 0 THEN
            RETURN;
        END IF;

        INSERT INTO ses_approval_chain (chain_id, chain_key, chain_name, description, active_flag, created_by, created_date)
        VALUES (SEQ_SES_APPROVAL_CHAIN.NEXTVAL, p_key, p_name, p_desc, 'Y', 'SYSTEM', SYSDATE)
        RETURNING chain_id INTO v_chain_id;

        INSERT INTO ses_approval_step (step_id, chain_id, step_order, step_name, created_by, created_date)
        VALUES (SEQ_SES_APPROVAL_STEP.NEXTVAL, v_chain_id, 1, 'Manager Approval', 'SYSTEM', SYSDATE)
        RETURNING step_id INTO v_step1_id;
        INSERT INTO ses_approval_step (step_id, chain_id, step_order, step_name, created_by, created_date)
        VALUES (SEQ_SES_APPROVAL_STEP.NEXTVAL, v_chain_id, 2, 'Admin Approval', 'SYSTEM', SYSDATE)
        RETURNING step_id INTO v_step2_id;
        INSERT INTO ses_approval_step (step_id, chain_id, step_order, step_name, created_by, created_date)
        VALUES (SEQ_SES_APPROVAL_STEP.NEXTVAL, v_chain_id, 3, 'Final Approval', 'SYSTEM', SYSDATE)
        RETURNING step_id INTO v_step3_id;

        -- Step 1: dynamic requestor's manager, plus 'admin' as a fixed fallback
        INSERT INTO ses_approval_assignee (assignee_id, step_id, assignee_type, pos_id, user_id, order_by, created_by, created_date)
        VALUES (SEQ_SES_APPROVAL_ASSIGNEE.NEXTVAL, v_step1_id, 'REQUESTOR_MANAGER', NULL, NULL, 10, 'SYSTEM', SYSDATE);
        INSERT INTO ses_approval_assignee (assignee_id, step_id, assignee_type, pos_id, user_id, order_by, created_by, created_date)
        VALUES (SEQ_SES_APPROVAL_ASSIGNEE.NEXTVAL, v_step1_id, 'USER', NULL, v_admin_user_id, 20, 'SYSTEM', SYSDATE);

        -- Step 2: 'admin'
        INSERT INTO ses_approval_assignee (assignee_id, step_id, assignee_type, pos_id, user_id, order_by, created_by, created_date)
        VALUES (SEQ_SES_APPROVAL_ASSIGNEE.NEXTVAL, v_step2_id, 'USER', NULL, v_admin_user_id, 10, 'SYSTEM', SYSDATE);

        -- Step 3: 'admin'
        INSERT INTO ses_approval_assignee (assignee_id, step_id, assignee_type, pos_id, user_id, order_by, created_by, created_date)
        VALUES (SEQ_SES_APPROVAL_ASSIGNEE.NEXTVAL, v_step3_id, 'USER', NULL, v_admin_user_id, 10, 'SYSTEM', SYSDATE);

        -- Step 3 checkpoint gates -- PAYMENT_VERIFICATION only on the
        -- payment chain; both scheduling ones on every chain.
        IF p_payment_checkpoint = 'Y' THEN
            INSERT INTO ses_approval_step_task (step_task_id, step_id, task_label, checkpoint_key, order_by, created_by, created_date)
            VALUES (SEQ_SES_APPROVAL_STEP_TASK.NEXTVAL, v_step3_id, 'Payment proof verified', 'PAYMENT_VERIFICATION', 10, 'SYSTEM', SYSDATE);
        END IF;
        INSERT INTO ses_approval_step_task (step_task_id, step_id, task_label, checkpoint_key, order_by, created_by, created_date)
        VALUES (SEQ_SES_APPROVAL_STEP_TASK.NEXTVAL, v_step3_id, 'Screening attended', 'SCREENING_SCHEDULE', 20, 'SYSTEM', SYSDATE);
        INSERT INTO ses_approval_step_task (step_task_id, step_id, task_label, checkpoint_key, order_by, created_by, created_date)
        VALUES (SEQ_SES_APPROVAL_STEP_TASK.NEXTVAL, v_step3_id, 'Badge retrieval attended', 'BADGE_RETRIEVAL_SCHEDULE', 30, 'SYSTEM', SYSDATE);
    END seed_chain;
BEGIN
    seed_chain('DEFAULT', 'Default Approval', 'Default 3-step approval chain for SES requests', 'N');
    seed_chain('PAYMENT', 'Payment Approval', 'Payment verification 3-step approval chain for SES requests', 'Y');
END;
/

COMMIT;
