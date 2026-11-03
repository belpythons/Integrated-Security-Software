-- ============================================================
-- SES — Serial Badge chain shape for DEFAULT + PAYMENT — Oracle
-- Run AFTER 20_approval_chain_seed.sql, 52_hybrid_routing_alter.sql and
-- 54_request_evaluator_ddl.sql.
--
-- Re-shapes the two seeded chains into the Badge flow of
-- docs/reference/approval flow.jpeg:
--
--   grp step  name                               role                    rule  assignees
--   --- ----  ---------------------------------  ----------------------  ----  ---------------------------
--    1   1    Additional Routing (Staf Security) SECURITY_STAFF          ALL   (per submission, see below)
--    2   2    Persetujuan Supervisor Procurement PROCUREMENT_SUPERVISOR  ANY   POSITION EH1200 + admin
--    3   3    Persetujuan Manager Procurement    PROCUREMENT_MANAGER     ANY   POSITION EH1000 + admin
--    4   4    Persetujuan Manager Pemohon        REQUESTOR_MANAGER       ANY   REQUESTOR_MANAGER + admin
--    5   5    Persetujuan Manager Security       SECURITY_MANAGER        ANY   POSITION E01000 + admin
--    6   6    Officer / Co-Officer Approval      (none)                  ANY   POSITION E01G10 + admin
--    7   7    Badge Retrieval                    (none)                  ANY   none - pure checkpoint gate
--
-- ONE STEP PER GROUP -- strictly serial, one approver at a time. An earlier
-- revision ran the two managers as one parallel group; that is gone.
--
-- Three of the first four steps can fall away. Steps 2 and 3 share ONE
-- toggle (needs_proc_approval_flag): procurement is the supervisor first and
-- the manager second, and half a procurement leg is not a configuration
-- anyone wants. Step 4 has its own (needs_mgr_approval_flag). Step 1 is
-- skipped when the requestor names nobody. A submission with both toggles
-- 'N' and no additional routing therefore opens directly on step 5, Manager
-- Security -- the diagram's direct bypass, safe precisely because
-- SECURITY_MANAGER is the one role nothing can switch off.
--
-- STEP 1 DELIBERATELY HAS NO ASSIGNEE ROW. Its approvers are not configuration
-- - they are the Staf Security the requestor picked on the form, stored in
-- ses_request_evaluator and turned into ses_approval_task_signoff rows by
-- ApprovalService#trigger(). A step whose step_role is SECURITY_STAFF is
-- therefore exempt from the "every step needs an assignee or a checkpoint"
-- rule in ApprovalChainService, and from the unresolvable-step guard in
-- trigger(). Nothing is validated at submit time either: additional routing
-- is OPTIONAL, and a submission naming nobody simply has this step SKIPPED.
-- Adding a fifth AssigneeType was considered and rejected: it would mean
-- widening ck_ses_assignee_type for a value that can never be resolved from
-- configuration alone.
--
-- Steps 6 and 7 are the pre-existing "Officer / Co-Officer Approval" and
-- "Badge Retrieval" steps, carried over unchanged with their checkpoint gates
-- (SCREENING_SCHEDULE, plus PAYMENT_VERIFICATION on the PAYMENT chain;
-- BADGE_RETRIEVAL_SCHEDULE on step 7). Only their step_order and
-- parallel_group move.
--
-- CHAIN INVARIANTS, all satisfied here and all enforced by
-- ApprovalChainService: each step_role appears at most once; steps 5, 6 and 7
-- can never be switched off, so the chain can never auto-approve with no human
-- review; no skippable step carries a checkpoint; and parallel_group 1 is the
-- only group containing an ALL step.
--
-- SAFETY. This script RESTRUCTURES chains that 20_approval_chain_seed.sql
-- created. It touches a chain only when every one of its steps still looks
-- untouched - created_by = 'SYSTEM' and updated_date IS NULL. A chain an admin
-- has edited at /approval/chain is left completely alone and reported, because
-- silently overwriting someone's configuration is worse than doing nothing.
-- A chain that does not exist at all is created from scratch in the new shape.
--
-- Running approvals are unaffected by design: ses_approval_task and its three
-- freeze tables hold their own copy of the chain, so re-shaping the definition
-- can never move a step under an instance that is already in flight.
--
-- Idempotent: re-running after the new shape is in place finds step_role
-- already set and returns without writing.
--
-- ⚠️ THE GUARD FIRED ON ELLDEV. Both chains there carry updated_by=admin, so
-- this script correctly did nothing and the chains stayed in their old shape.
-- 57_hybrid_chain_reshape_forced.sql is the explicit opt-out; read its header
-- before running it.
-- ============================================================

SET SERVEROUTPUT ON

DECLARE
    v_admin_user_id   app_user.user_id%TYPE := '00000000-0000-0000-0000-000000000001';
    v_sec_mgr_pos_id  ses_approval_assignee.pos_id%TYPE := 'E01000';
    -- The two procurement rungs are DIFFERENT chairs, in this order.
    -- EH1200 = SUPERVISOR, PROCUREMENT SUPPORT; EH1000 = MANAGER, PROCUREMENT.
    -- EH1000 is also the procurement SECTION id, which is why the reference
    -- diagram's typo (both boxes labelled EH1000) reads as plausible.
    -- Verified against APP_POSITION_VIEW on ELLDEV, 2026-10-06.
    v_proc_supv_pos_id ses_approval_assignee.pos_id%TYPE := 'EH1200';
    v_proc_mgr_pos_id  ses_approval_assignee.pos_id%TYPE := 'EH1000';
    v_officer_pos_id   ses_approval_assignee.pos_id%TYPE := 'E01G10';

    PROCEDURE add_assignee(p_step_id IN NUMBER, p_type IN VARCHAR2,
                           p_pos_id IN VARCHAR2, p_user_id IN VARCHAR2, p_order IN NUMBER) IS
    BEGIN
        INSERT INTO ses_approval_assignee
            (assignee_id, step_id, assignee_type, pos_id, user_id, order_by, created_by, created_date)
        VALUES (SEQ_SES_APPROVAL_ASSIGNEE.NEXTVAL, p_step_id, p_type, p_pos_id, p_user_id, p_order, 'SYSTEM', SYSDATE);
    END add_assignee;

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

    PROCEDURE shape_chain(p_key IN VARCHAR2, p_name IN VARCHAR2, p_desc IN VARCHAR2,
                          p_payment_checkpoint IN CHAR) IS
        v_chain_id  NUMBER;
        v_edited    NUMBER;
        v_shaped    NUMBER;
        v_s1 NUMBER; v_s2 NUMBER; v_s3 NUMBER; v_s4 NUMBER; v_s5 NUMBER; v_s6 NUMBER; v_s7 NUMBER;
    BEGIN
        BEGIN
            SELECT chain_id INTO v_chain_id FROM ses_approval_chain WHERE chain_key = p_key;
        EXCEPTION
            WHEN NO_DATA_FOUND THEN
                v_chain_id := NULL;
        END;

        IF v_chain_id IS NOT NULL THEN
            -- Already in the new shape? Then there is nothing to do.
            SELECT COUNT(*) INTO v_shaped FROM ses_approval_step
            WHERE chain_id = v_chain_id AND step_role IS NOT NULL;
            IF v_shaped > 0 THEN
                DBMS_OUTPUT.PUT_LINE('Chain ' || p_key || ': sudah berbentuk hybrid, dilewati.');
                RETURN;
            END IF;

            -- Edited by a human at /approval/chain? Then it is not ours to rewrite.
            SELECT COUNT(*) INTO v_edited FROM ses_approval_step
            WHERE chain_id = v_chain_id
              AND (created_by IS NULL OR created_by != 'SYSTEM' OR updated_date IS NOT NULL);
            IF v_edited > 0 THEN
                DBMS_OUTPUT.PUT_LINE('Chain ' || p_key || ': sudah disunting admin ('
                    || v_edited || ' tahap), DIBIARKAN. Tata ulang manual di /approval/chain.');
                RETURN;
            END IF;

            -- Untouched seed: drop the old steps. ON DELETE CASCADE on
            -- ses_approval_assignee and ses_approval_step_task clears their
            -- children, so the step rows are all that need deleting here.
            DELETE FROM ses_approval_step WHERE chain_id = v_chain_id;
        ELSE
            INSERT INTO ses_approval_chain
                (chain_id, chain_key, chain_name, description, active_flag, created_by, created_date)
            VALUES (SEQ_SES_APPROVAL_CHAIN.NEXTVAL, p_key, p_name, p_desc, 'Y', 'SYSTEM', SYSDATE)
            RETURNING chain_id INTO v_chain_id;
        END IF;

        v_s1 := new_step(v_chain_id, 1, 'Additional Routing (Staf Security)', 'SECURITY_STAFF',         1, 'ALL');
        v_s2 := new_step(v_chain_id, 2, 'Persetujuan Supervisor Procurement', 'PROCUREMENT_SUPERVISOR', 2, 'ANY');
        v_s3 := new_step(v_chain_id, 3, 'Persetujuan Manager Procurement',    'PROCUREMENT_MANAGER',    3, 'ANY');
        v_s4 := new_step(v_chain_id, 4, 'Persetujuan Manager Pemohon',        'REQUESTOR_MANAGER',      4, 'ANY');
        v_s5 := new_step(v_chain_id, 5, 'Persetujuan Manager Security',       'SECURITY_MANAGER',       5, 'ANY');
        v_s6 := new_step(v_chain_id, 6, 'Officer / Co-Officer Approval',      NULL,                     6, 'ANY');
        v_s7 := new_step(v_chain_id, 7, 'Badge Retrieval',                    NULL,                     7, 'ANY');

        -- Step 1: intentionally no assignee - resolved per submission from
        -- ses_request_evaluator. See the header.

        -- Steps 2-6 each keep 'admin' as a fixed fallback, so a gap in the org
        -- chart can never leave a step with nobody able to act on it.
        add_assignee(v_s2, 'POSITION',          v_proc_supv_pos_id, NULL,            0);
        add_assignee(v_s2, 'USER',              NULL,               v_admin_user_id, 1);

        add_assignee(v_s3, 'POSITION',          v_proc_mgr_pos_id,  NULL,            0);
        add_assignee(v_s3, 'USER',              NULL,               v_admin_user_id, 1);

        add_assignee(v_s4, 'REQUESTOR_MANAGER', NULL,               NULL,            0);
        add_assignee(v_s4, 'USER',              NULL,               v_admin_user_id, 1);

        add_assignee(v_s5, 'POSITION',          v_sec_mgr_pos_id,   NULL,            0);
        add_assignee(v_s5, 'USER',              NULL,               v_admin_user_id, 1);

        add_assignee(v_s6, 'POSITION',          v_officer_pos_id,   NULL,            0);
        add_assignee(v_s6, 'USER',              NULL,               v_admin_user_id, 1);

        -- Step 7 (Badge Retrieval): no human assignee - pure checkpoint gate.

        -- Checkpoint gates, carried over from 20_approval_chain_seed.sql
        -- unchanged. They sit on steps 6 and 7, never on a skippable step: a
        -- checkpoint frozen onto a skipped task stays PENDING forever, and
        -- completeCheckpointByKey scans EVERY task of the instance, so it can
        -- match that orphan and never clear the real one.
        IF p_payment_checkpoint = 'Y' THEN
            INSERT INTO ses_approval_step_task
                (step_task_id, step_id, task_label, checkpoint_key, order_by, created_by, created_date)
            VALUES (SEQ_SES_APPROVAL_STEP_TASK.NEXTVAL, v_s6, 'Payment proof verified', 'PAYMENT_VERIFICATION', 0, 'SYSTEM', SYSDATE);
            INSERT INTO ses_approval_step_task
                (step_task_id, step_id, task_label, checkpoint_key, order_by, created_by, created_date)
            VALUES (SEQ_SES_APPROVAL_STEP_TASK.NEXTVAL, v_s6, 'Screening attended', 'SCREENING_SCHEDULE', 1, 'SYSTEM', SYSDATE);
        ELSE
            INSERT INTO ses_approval_step_task
                (step_task_id, step_id, task_label, checkpoint_key, order_by, created_by, created_date)
            VALUES (SEQ_SES_APPROVAL_STEP_TASK.NEXTVAL, v_s6, 'Screening attended', 'SCREENING_SCHEDULE', 0, 'SYSTEM', SYSDATE);
        END IF;

        INSERT INTO ses_approval_step_task
            (step_task_id, step_id, task_label, checkpoint_key, order_by, created_by, created_date)
        VALUES (SEQ_SES_APPROVAL_STEP_TASK.NEXTVAL, v_s7, 'Badge Retrieval', 'BADGE_RETRIEVAL_SCHEDULE', 0, 'SYSTEM', SYSDATE);

        DBMS_OUTPUT.PUT_LINE('Chain ' || p_key || ': ditata ulang menjadi 7 tahap serial.');
    END shape_chain;
BEGIN
    shape_chain('DEFAULT', 'Default Approval',
                'Alur serial SES: Additional Routing (opsional, semua setuju) -> Supervisor Procurement -> Manager Procurement -> Manager Pemohon -> Manager Security -> Officer -> Badge Retrieval', 'N');
    shape_chain('PAYMENT', 'Payment Approval',
                'Alur serial SES dengan verifikasi pembayaran pada tahap Officer', 'Y');
END;
/

COMMIT;
