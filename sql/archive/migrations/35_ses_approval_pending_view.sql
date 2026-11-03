-- sql/35_ses_approval_pending_view.sql
-- SES_APPROVAL_PENDING_VIEW: one row per (pending approval task, current
-- live holder) across every SES business module (request/sticker/payment/
-- scheduling all share one approval engine -- see sql/README.md "Approval
-- module conventions" -- so this single view already covers all of them,
-- no per-module union needed).
--
-- Holder resolution is LIVE (re-computed on every SELECT), by Belva's
-- explicit 2026-09-16 choice -- deliberately DIFFERENT from
-- ApprovalResolverService's frozen-at-trigger-time resolution stored on
-- ses_approval_task_assignee. Both mechanisms coexist: the frozen one still
-- decides WHO IS AUTHORIZED to act (ApprovalResolverService.canAct(), used
-- by ApprovalController's approve/reject endpoints, unchanged); this view
-- only feeds the pending-approval INBOX DISPLAY, and can show more than one
-- current holder per task (co-officers / multiple substantive incumbents),
-- which is expected, not a bug.
--
-- Priority per assignee row that carries a pos_id (POSITION /
-- REQUESTOR_MANAGER / REPORT_TO types):
--   1) APP_POSITION_TMP_VIEW  -- PJS/temporary assignment active today
--   2) APP_POSITION_VIEW.HOLDER_* -- current PJS/position holder
--   3) APP_EMP_INFO_VIEW.POSITION_NBR = pos_id -- substantive incumbent(s),
--      can be more than one row
--   4) fallback to the frozen empl_id/display_name/email already stored on
--      ses_approval_task_assignee, so a task never silently disappears from
--      the inbox just because live position data has a gap
-- USER-type assignees (no pos_id -- see sql/README.md poin 2 policy) always
-- use the frozen empl_id/display_name/email directly, there's nothing to
-- recompute live for a fixed user.
--
-- MANAGER_SKIP rows (ApprovalResolverService's "no one needs to act"
-- marker) are excluded, same as ApprovalResolverService.canAct() does.
--
-- Revised 2026-09-30, two fixes, both found while chasing the PJS->definitive
-- authority handover (see sql/47_app_position_view_pjs_date_filter.sql):
--
--   1. TRUNC(SYSDATE), not raw SYSDATE, in all four window tests. Every row
--      in ellora.tblposition_tmp carries a midnight timestamp (verified on
--      ELLDEV: 0 rows with a time component), so raw SYSDATE compared 10:00
--      today against 00:00 on the end date and dropped a PJS on its own
--      FINAL DAY. Off by one, silently, for every stand-in that ever served.
--   2. TRIM on both sides of t.posid = pa.pos_id. Every other branch of this
--      view already trims; this one did not, so it was the odd one out
--      against CHAR-padded position ids.
--
-- Note rung 2 below (live_from_holder) reads APP_POSITION_VIEW.HOLDER_*,
-- which until sql/47 returned an EXPIRED stand-in forever. That made rung 2
-- both wrong AND — through its own NOT EXISTS — a suppressor of rung 3, which
-- would have found the substantive holder. This view was not at fault there;
-- it faithfully reported what APP_POSITION_VIEW told it.
--
-- Idempotent (CREATE OR REPLACE), no dependent objects created here.

CREATE OR REPLACE VIEW SES_APPROVAL_PENDING_VIEW AS
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
                                   AND sai.current_task_id = sat.task_id
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
)
SELECT * FROM live_from_tmp
UNION ALL
SELECT * FROM live_from_holder
UNION ALL
SELECT * FROM live_from_emp
UNION ALL
SELECT * FROM fallback_frozen;
