-- ============================================================
-- SES — Corrective reshape for the PAYMENT chain (chain_id = 2)
--
-- sql/56_hybrid_chain_seed.sql reshapes DEFAULT and PAYMENT into the six-step
-- hybrid flow, but it refuses to touch a chain where any step looks
-- admin-edited (created_by != 'SYSTEM' or updated_date IS NOT NULL) — by
-- design, so it never clobbers a human's work at /approval/chain.
--
-- PAYMENT (chain_id = 2) tripped that guard: its "Badge Retrieval" step has
-- created_by = 'admin', and its first step has an updated_date from
-- 2026-09-22 — both stale metadata from before this wave, unrelated to the
-- hybrid migration. The result, discovered 2026-10-06 while walking through
-- the QA scenarios: PAYMENT silently kept its OLD four-step shape (no
-- SECURITY_STAFF step, no parallel groups), so every request type that maps
-- to it — BADGE_NEW and BADGE_EXT, the two most common submissions in the
-- app — never engaged the hybrid engine at all. Confirmed against ELLDEV:
-- submitting a new-badge request created zero ses_approval_task_signoff
-- rows and routed through "Requestor's Manager Approval" as step 1 instead
-- of the Staf Security the requestor picked.
--
-- This script does exactly what shape_chain('PAYMENT', ...) in sql/56 would
-- have done, applied directly to chain_id = 2 since its identity is now
-- known and confirmed (not looked up by key), with the admin-edited guard
-- deliberately bypassed — reviewed and authorized for this one chain only.
-- Shape and constants (position ids, admin fallback user, payment
-- checkpoint) are copied verbatim from sql/56 so the two chains stay
-- identical in structure.
--
-- Idempotent: if chain_id = 2 already has a step with step_role set, this
-- is a no-op.
--
-- ⚠️ ALREADY EXECUTED against ELLDEV on 2026-10-06 (with explicit user
-- authorization) during an independent QA browser-walkthrough session run
-- concurrently with the one that produced sql/57_hybrid_chain_reshape_forced
-- .sql, sql/58, sql/59, sql/60 and sql/61_request_email_and_upload_token_
-- alter.sql in this same directory. Deliberately given a non-numeric name
-- (not sql/57, not sql/61) because this script and that session's scripts
-- were written independently, minutes apart, to fix the exact same root
-- cause — and that session kept incrementing its own sequence (57, then 61)
-- faster than coordination was possible. A plain number here would keep
-- colliding with whatever it picks next; this name never will.
--
-- Consequence worth knowing: this script put PAYMENT into the OLD six-step
-- parallel shape (matching sql/56's intent). That is now the shape
-- sql/60_badge_chain_reshape_v2.sql's own header describes as "digantikan"
-- (superseded) by the newer seven-step serial design (PROCUREMENT_SUPERVISOR
-- + PROCUREMENT_MANAGER as two serial rungs, no parallel groups on Badge).
-- Scenarios 3-8 verified against this script's shape (see this session's
-- section of docs/TESTING_AND_VERIFICATION_REPORT.md) will need re-running
-- once sql/58 then sql/60 are applied.
-- ============================================================

SET SERVEROUTPUT ON

DECLARE
    v_chain_id        CONSTANT NUMBER := 2;
    v_already_shaped  NUMBER;
    v_sec_mgr_pos_id  ses_approval_assignee.pos_id%TYPE := 'E01000';
    v_proc_mgr_pos_id ses_approval_assignee.pos_id%TYPE := 'EH1200';
    v_officer_pos_id  ses_approval_assignee.pos_id%TYPE := 'E01G10';
    v_admin_user_id   app_user.user_id%TYPE := '00000000-0000-0000-0000-000000000001';
    v_s1 NUMBER; v_s2 NUMBER; v_s3 NUMBER; v_s4 NUMBER; v_s5 NUMBER; v_s6 NUMBER;

    PROCEDURE add_assignee(p_step_id IN NUMBER, p_type IN VARCHAR2,
                           p_pos_id IN VARCHAR2, p_user_id IN VARCHAR2, p_order IN NUMBER) IS
    BEGIN
        INSERT INTO ses_approval_assignee
            (assignee_id, step_id, assignee_type, pos_id, user_id, order_by, created_by, created_date)
        VALUES (SEQ_SES_APPROVAL_ASSIGNEE.NEXTVAL, p_step_id, p_type, p_pos_id, p_user_id, p_order, 'SYSTEM', SYSDATE);
    END add_assignee;

    FUNCTION new_step(p_order IN NUMBER, p_name IN VARCHAR2,
                      p_role IN VARCHAR2, p_group IN NUMBER, p_rule IN VARCHAR2) RETURN NUMBER IS
        v_step_id NUMBER;
    BEGIN
        INSERT INTO ses_approval_step
            (step_id, chain_id, step_order, step_name, step_role, parallel_group, completion_rule,
             created_by, created_date)
        VALUES (SEQ_SES_APPROVAL_STEP.NEXTVAL, v_chain_id, p_order, p_name, p_role, p_group, p_rule,
                'SYSTEM', SYSDATE)
        RETURNING step_id INTO v_step_id;
        RETURN v_step_id;
    END new_step;
BEGIN
    SELECT COUNT(*) INTO v_already_shaped FROM ses_approval_step
    WHERE chain_id = v_chain_id AND step_role IS NOT NULL;
    IF v_already_shaped > 0 THEN
        DBMS_OUTPUT.PUT_LINE('chain_id=2: sudah berbentuk hybrid, dilewati.');
        RETURN;
    END IF;

    -- ON DELETE CASCADE on ses_approval_assignee and ses_approval_step_task
    -- clears their children, so the step rows are all that need deleting.
    DELETE FROM ses_approval_step WHERE chain_id = v_chain_id;

    v_s1 := new_step(1, 'Persetujuan Staf Security',       'SECURITY_STAFF',      1, 'ALL');
    v_s2 := new_step(2, 'Persetujuan Manager Pemohon',     'REQUESTOR_MANAGER',   2, 'ANY');
    v_s3 := new_step(3, 'Persetujuan Procurement',         'PROCUREMENT_MANAGER', 2, 'ANY');
    v_s4 := new_step(4, 'Persetujuan Manager Security',    'SECURITY_MANAGER',    3, 'ANY');
    v_s5 := new_step(5, 'Officer / Co-Officer Approval',   NULL,                  4, 'ANY');
    v_s6 := new_step(6, 'Badge Retrieval',                  NULL,                  5, 'ANY');

    -- Step 1: no assignee row — resolved per submission from ses_request_evaluator.

    add_assignee(v_s2, 'REQUESTOR_MANAGER', NULL,              NULL,            0);
    add_assignee(v_s2, 'USER',              NULL,              v_admin_user_id, 1);

    add_assignee(v_s3, 'POSITION',          v_proc_mgr_pos_id, NULL,            0);
    add_assignee(v_s3, 'USER',              NULL,              v_admin_user_id, 1);

    add_assignee(v_s4, 'POSITION',          v_sec_mgr_pos_id,  NULL,            0);
    add_assignee(v_s4, 'USER',              NULL,              v_admin_user_id, 1);

    add_assignee(v_s5, 'POSITION',          v_officer_pos_id,  NULL,            0);
    add_assignee(v_s5, 'USER',              NULL,              v_admin_user_id, 1);

    -- Step 6 (Badge Retrieval): no human assignee — pure checkpoint gate.

    -- PAYMENT chain: payment verification checkpoint on step 5, same as sql/56.
    INSERT INTO ses_approval_step_task
        (step_task_id, step_id, task_label, checkpoint_key, order_by, created_by, created_date)
    VALUES (SEQ_SES_APPROVAL_STEP_TASK.NEXTVAL, v_s5, 'Payment proof verified', 'PAYMENT_VERIFICATION', 0, 'SYSTEM', SYSDATE);
    INSERT INTO ses_approval_step_task
        (step_task_id, step_id, task_label, checkpoint_key, order_by, created_by, created_date)
    VALUES (SEQ_SES_APPROVAL_STEP_TASK.NEXTVAL, v_s5, 'Screening attended', 'SCREENING_SCHEDULE', 1, 'SYSTEM', SYSDATE);

    INSERT INTO ses_approval_step_task
        (step_task_id, step_id, task_label, checkpoint_key, order_by, created_by, created_date)
    VALUES (SEQ_SES_APPROVAL_STEP_TASK.NEXTVAL, v_s6, 'Badge Retrieval', 'BADGE_RETRIEVAL_SCHEDULE', 0, 'SYSTEM', SYSDATE);

    DBMS_OUTPUT.PUT_LINE('chain_id=2 (PAYMENT): ditata ulang menjadi 6 tahap hybrid.');
END;
/

COMMIT;
