-- 55_approval_pending_view_parallel.sql
-- SES_APPROVAL_PENDING_VIEW, for the hybrid routing engine.
-- Run AFTER 52_hybrid_routing_alter.sql and 53_task_signoff_ddl.sql.
--
-- This production script set never seeds the original single-task-per-
-- instance version of this view (dev's sql/35) — this IS the first and
-- only SES_APPROVAL_PENDING_VIEW a fresh deploy creates.
--
-- Idempotent (CREATE OR REPLACE). Column list is stable, so
-- 36_app_approval_view_ses.sql (APP_APPROVAL_VIEW, which selects from this
-- view) keeps working without being touched.
--
-- HOW THIS VIEW WORKS — live holder resolution on every SELECT, a four-rung
-- priority ladder per pending assignee, MANAGER_SKIP exclusion,
-- TRUNC(SYSDATE) windows, TRIM on both sides of position-id joins:
--
--   1) APP_POSITION_TMP_VIEW  -- PJS/temporary assignment active today
--   2) APP_POSITION_VIEW.HOLDER_* -- current PJS/position holder
--   3) APP_EMP_INFO_VIEW.POSITION_NBR = pos_id -- substantive incumbent(s),
--      can be more than one row
--   4) fallback to the frozen empl_id/display_name/email already stored on
--      ses_approval_task_assignee, so a task never silently disappears from
--      the inbox just because live position data has a gap
-- USER-type assignees (no pos_id) always use the frozen empl_id/display_name/
-- email directly, there's nothing to recompute live for a fixed user.
--
-- ------------------------------------------------------------------
-- The view does not assume ONE active task per instance: Manager Pemohon
-- and Manager Procurement are broadcast in PARALLEL and sit in two
-- different inboxes at the same moment, so the join on
-- sai.current_task_id = sat.task_id used by the original dev version is
-- intentionally absent. current_task_id still exists on the schema and
-- still points at the lowest-step_order pending task for "tahap saat ini"
-- display — it simply is not a filter here.
--
-- A consensus step (completion_rule = 'ALL', step 1 / Staf Security) shows
-- only the people still being waited on: the closing NOT EXISTS drops an
-- (task, person) pair once that person's ses_approval_task_signoff row is
-- no longer PENDING. Applied OUTSIDE the UNION ALL, once, rather than
-- inside each of the four rungs, since the rungs resolve WHO the task
-- points at today and whether that person has already answered is a
-- separate question asked once of the final answer. For an 'ANY' task
-- there are no sign-off rows at all, so the NOT EXISTS is always true.
--
-- This only works because activation writes BOTH ses_approval_task_assignee
-- rows (what this view resolves against) AND ses_approval_task_signoff rows
-- (what it subtracts) for a consensus step.

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
