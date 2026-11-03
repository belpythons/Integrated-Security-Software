-- 36_app_approval_view_ses.sql
-- Replaces APP_APPROVAL_VIEW entirely with SES-only content.
--
-- ⚠️ Run AFTER 55_approval_pending_view_parallel.sql, NOT after any
-- 35-numbered script — this production script set never seeds the
-- single-task-per-instance SES_APPROVAL_PENDING_VIEW (ses/sql/35); it
-- goes straight to the parallel-aware version (55), and this view is
-- built directly on top of that.
--
-- APP_APPROVAL_VIEW may already exist in this schema as a company-wide
-- template DDL (7 UNION ALL branches to unrelated legacy HR/Talent-
-- Management tables this schema has no grants on) — check for that before
-- running, and if so this script's intent is to replace it entirely with
-- SES's own content, not add an 8th branch.
--
-- Column shape kept identical to the inherited template (TRANS_ID/TRANS_CAT/
-- etc.) in case anything outside this repo already expects this exact view
-- name/shape.
--
-- Built on top of SES_APPROVAL_PENDING_VIEW (55), just projected into
-- the target column names + a couple of joins for human-readable text.

CREATE OR REPLACE VIEW APP_APPROVAL_VIEW AS
SELECT
    p.task_id                                              AS trans_id,
    p.business_ref_type                                    AS trans_cat,
    p.step_name || ' - ' || COALESCE(r.request_no, s.request_no) AS trans_desc,
    p.approver_empl_id                                      AS approver_id,
    p.approver_name                                         AS approver_name,
    p.requestor_user_id                                     AS req_by,
    ru.full_name                                             AS req_by_name,
    p.req_date                                              AS req_date,
    '/approval/detail/' || p.instance_id                    AS url_detail,
    '/approval'                                              AS url_apprv,
    0                                                        AS is_simple_apprv
FROM SES_APPROVAL_PENDING_VIEW p
LEFT JOIN ses_request r         ON p.business_ref_type = 'SES_REQUEST'
                                AND r.request_id = p.business_ref_id
LEFT JOIN ses_sticker_request s ON p.business_ref_type = 'SES_STICKER_REQUEST'
                                AND s.sticker_request_id = p.business_ref_id
LEFT JOIN app_user ru           ON ru.username = p.requestor_user_id;
