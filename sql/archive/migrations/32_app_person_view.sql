-- ============================================================
-- SES — APP_PERSON: unfiltered employee/Mitra source.
-- Active on ELLDEV and consumed by SES_WORKER_LOOKUP_VIEW for current
-- Mitra workers (EMPL_ID beginning with 3 and IS_ACTIVE = 1).
--
-- ellora.tbl_person must already be granted SELECT to this schema (no new
-- grant needed on ELLDEV) and is the most complete employee source
-- available: more complete than SES_WORKER_LOOKUP_VIEW (badge-keyed dedup
-- across 5 sources, built for the Request form) or APP_EMP_LKP_VIEW
-- (active-only).
--
-- This standalone script mirrors the APP_PERSON prerequisite block in
-- 23_worker_lookup_view.sql and is safe to re-run independently.
-- ============================================================

CREATE OR REPLACE VIEW APP_PERSON AS
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
FROM   ellora.tbl_person;
