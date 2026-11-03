-- ============================================================
-- SES — APP_POSITION_VIEW: honour the PJS date window — Oracle 11g / 12c+
-- Run as schema owner.
--
-- ⚠️⚠️ BEFORE RUNNING THIS ON ANY NEW ENVIRONMENT, RE-CHECK OWNERSHIP AND
-- GRANTS FIRST. sql/02_approval_ddl.sql and this schema's own conventions
-- state APP_POSITION_VIEW is read-only and owned by another schema/app in
-- general. That was verified NOT true on ELLDEV (owned by SES itself, zero
-- external grants, only SES views depend on it), which is what made
-- replacing it safe there. On a DIFFERENT environment it may genuinely be
-- owned by another application:
--
--     SELECT owner, object_type, status FROM all_objects
--      WHERE object_name = 'APP_POSITION_VIEW';
--     SELECT grantee, privilege FROM all_tab_privs
--      WHERE table_name = 'APP_POSITION_VIEW';
--     SELECT owner, name, type FROM all_dependencies
--      WHERE referenced_name = 'APP_POSITION_VIEW';
--
-- If it is owned by another schema there, or other schemas hold grants on
-- it, DO NOT run this — raise it with that schema's owner instead.
-- Replacing a view another application depends on is not ours to do
-- unilaterally.
--
-- ---- THE BUG ----
-- The inherited definition joins the PJS/temporary-assignment table with NO
-- date predicate:
--
--     LEFT JOIN ellora.tblposition_tmp tmppos ON mainpos.posid = tmppos.posid
--     ...
--     NVL(dt.pjs_empl_id, empl_id) AS holder_empl_id
--
-- so HOLDER_EMPL_ID keeps returning the stand-in FOREVER — years after their
-- acting period ended. Every consumer that trusts HOLDER_* inherits it:
-- SES_APPROVAL_PENDING_VIEW routes the inbox to the former stand-in,
-- ApprovalResolverService freezes new task assignees from HOLDER_*, and
-- SES_WORKER_LOOKUP_VIEW returns the former stand-in for badge/name
-- autofill.
--
-- ---- THE FIX ----
-- One predicate. The column list is UNCHANGED (same 18 columns, same order,
-- same types) so AppPositionView's @IdClass and both dependent views stay
-- valid:
--
--     AND TRUNC(SYSDATE) BETWEEN tmppos.start_date AND tmppos.end_date
--
-- TRUNC(SYSDATE), not raw SYSDATE: a raw SYSDATE comparison would compare
-- 10:00 today against 00:00 on the end date and drop the PJS on its own
-- final day. TRUNC makes the window inclusive at both ends, which is what
-- "berlaku sampai tanggal X" means to the people entering it.
--
-- Idempotent by construction (CREATE OR REPLACE). Creates no other object.
-- Revert DDL is at the bottom of this file, verbatim as captured from
-- ELLDEV before this change.
-- ============================================================

CREATE OR REPLACE VIEW APP_POSITION_VIEW AS
SELECT dt."POS_ID",
       trim(dt."POS_TITLE") as pos_title,
       dt."SUPERIOR_ID",
       dt."EMPL_ID",
       dt."EMPL_NAME",
       dt."DEPT_ID",
       dt."SECTION_ID",
       dt."DEPT_DESC",
       dt."SECTION_DESC",
       dt."EMPL_EMAIL",
       dt."PJS_EMPL_ID",
       dt."PJS_EMPL_NAME",
       dt."PJS_EMAIL",
       dt."START_DATE",
       dt."END_DATE",
       NVL (dt.pjs_Empl_id, empl_id)         AS holder_empl_id,
       NVL (dt.pjs_Empl_name, empl_name)     AS holder_Empl_name,
       NVL (dt.pjs_Email, empl_email)        AS holder_Email
  FROM (SELECT mainpos.posid                AS pos_id,
               pos_title,
               superior_id,
               mainpos.employee_id          AS empl_id,
               pos1.name_display            AS empl_name,
               pos1.deptid                  AS dept_id,
               TRIM (pos1.pb_section)       AS section_id,
               pos1.dept_desc,
               TRIM (pos1.section_desc)     AS section_desc,
               pos1.email                   AS empl_email,
               tmppos.empid                 AS pjs_empl_id,
               pos2.name_display            AS pjs_empl_name,
               pos2.email                   AS pjs_email,
               tmppos.start_date,
               tmppos.end_date
          FROM ellora.tblposition_list  mainpos
               INNER JOIN ellora.ps_bx_emp_info_detail pos1
                   ON mainpos.emplid = pos1.emplid
               -- THE FIX: a PJS row only counts while its window covers today.
               -- Outside the window the LEFT JOIN yields NULL, so the NVL()s
               -- above fall through to the substantive holder.
               LEFT JOIN ellora.tblposition_tmp tmppos
                   ON mainpos.posid = tmppos.posid
                  AND TRUNC (SYSDATE) BETWEEN tmppos.start_date AND tmppos.end_date
               LEFT JOIN ellora.ps_bx_emp_info_detail pos2
                   ON tmppos.empid = pos2.emplid) dt;


-- ---- Verification (run after) ----
-- 1) No expired PJS may still be the holder — expect 0:
--      SELECT COUNT(*) FROM app_position_view
--       WHERE pjs_empl_id IS NOT NULL AND end_date < TRUNC(SYSDATE)
--         AND TRIM(holder_empl_id) = TRIM(pjs_empl_id);
-- 2) Dependents must recompile clean:
--      SELECT object_name, status FROM user_objects
--       WHERE object_name IN ('APP_POSITION_VIEW',
--                             'SES_APPROVAL_PENDING_VIEW',
--                             'SES_WORKER_LOOKUP_VIEW');


-- ============================================================
-- ROLLBACK — the definition exactly as it stood on ELLDEV before this
-- script, captured from USER_VIEWS.TEXT on 2026-09-30. Run this block to
-- restore the previous (date-blind) behaviour.
--
-- Note this restores the BUG as well: every position with an expired
-- stand-in goes back to being held by them indefinitely. Only use it if
-- replacing the view caused a problem worse than that.
-- ============================================================
-- CREATE OR REPLACE VIEW APP_POSITION_VIEW AS
-- SELECT dt."POS_ID",
--            trim(dt."POS_TITLE") as pos_title,
--            dt."SUPERIOR_ID",
--            dt."EMPL_ID",
--            dt."EMPL_NAME",
--            dt."DEPT_ID",
--            dt."SECTION_ID",
--            dt."DEPT_DESC",
--            dt."SECTION_DESC",
--            dt."EMPL_EMAIL",
--            dt."PJS_EMPL_ID",
--            dt."PJS_EMPL_NAME",
--            dt."PJS_EMAIL",
--            dt."START_DATE",
--            dt."END_DATE",
--            NVL (dt.pjs_Empl_id, empl_id)         AS holder_empl_id,
--            NVL (dt.pjs_Empl_name, empl_name)     AS holder_Empl_name,
--            NVL (dt.pjs_Email, empl_email)        AS holder_Email
--       FROM (SELECT mainpos.posid                AS pos_id,
--                    pos_title,
--                    superior_id,
--                    mainpos.employee_id          AS empl_id,
--                    pos1.name_display            AS empl_name,
--                    pos1.deptid                  AS dept_id,
--                    TRIM (pos1.pb_section)       AS section_id,
--                    pos1.dept_desc,
--                    TRIM (pos1.section_desc)     AS section_desc,
--                    pos1.email                   AS empl_email,
--                    tmppos.empid                 AS pjs_empl_id,
--                    pos2.name_display            AS pjs_empl_name,
--                    pos2.email                   AS pjs_email,
--                    tmppos.start_date,
--                    tmppos.end_date
--               FROM ellora.tblposition_list  mainpos
--                    INNER JOIN ellora.ps_bx_emp_info_detail pos1
--                        ON mainpos.emplid = pos1.emplid
--                    LEFT JOIN ellora.tblposition_tmp tmppos
--                        ON mainpos.posid = tmppos.posid
--                    LEFT JOIN ellora.ps_bx_emp_info_detail pos2
--                        ON tmppos.empid = pos2.emplid) dt;
