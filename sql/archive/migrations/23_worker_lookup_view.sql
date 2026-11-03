-- ============================================================
-- SES — SES_WORKER_LOOKUP_VIEW: unified, deduplicated worker autofill
-- source for the Request "Worker Data" table (see request/form.html's
-- worker-lookup-input combobox and RequestController#workerSearch).
--
-- Replaces the old approach of stitching four separate queries together
-- in RequestController (APP_USER + 3x APP_POSITION_VIEW + SES_REQUEST_WORKER),
-- which is what produced duplicate dropdown entries for the same person
-- (one row per source, e.g. one WITH a NIK from a past request row and one
-- WITHOUT because APP_POSITION_VIEW never carries NIK).
--
-- Sources merged, in priority order (best/most authoritative first):
--   1. EMP_NIK_VIEW      — real view, already granted (read-only) in every
--                          environment this runs against (see
--                          extracted-emp-nik-view.sql for its DDL/shape).
--                          The only source with KTP/NIK, dept/section
--                          desc and gender all in one row. This script
--                          never creates it and assumes it already exists
--                          — 22_emp_nik_view_dev_seed.sql (a local
--                          snapshot table of the same shape) is INACTIVE/
--                          commented out, not part of any run order.
--   2. APP_POSITION_VIEW — PJS-aware current holder of a position.
--                          No NIK.
--   3. APP_PERSON         — active Mitra workers only: six-digit badges
--                          beginning with 3 and IS_ACTIVE = 1. Gender is
--                          translated from L/P to M/F. No NIK.
--   4. APP_USER           — LDAP accounts only (username IS the badge
--                          for LDAP; LOCAL accounts never store a real
--                          badge in username, so they are excluded here).
--   5. SES_REQUEST_WORKER — past request rows; still the only place a
--                          NIK might be known for a badge EMP_NIK_VIEW
--                          doesn't (yet) have.
--
-- SES_MITRA_MASTER deliberately remains in the schema but is NOT a source
-- for this view. APP_PERSON is the live source for active Mitra workers.
--
-- A "badge" only counts as a real employee badge when it is EXACTLY 6
-- digits, 0-9 only (per the requirement: badges never contain letters).
-- Rows whose badge/username/EMPL_ID doesn't match that shape are treated
-- as badgeless and passed through unmerged (e.g. LOCAL app users, or old
-- SES_REQUEST_WORKER rows entered before badge capture existed / for
-- external non-employee workers) — there is nothing to dedupe them
-- against.
--
-- For every real badge, FIRST_VALUE(...) IGNORE NULLS partitioned by
-- badge and ordered by source priority picks, independently per column,
-- the best non-null value across all sources that mention that badge —
-- so a person known to EMP_NIK_VIEW (name/nik/dept/section/gender) AND
-- to a past SES_REQUEST_WORKER row (jabatan, say) surfaces as ONE row
-- with both, instead of two separate dropdown entries.
--
-- Run after 03_request_ddl.sql (needs SES_REQUEST_WORKER) and, locally,
-- after 05_dev_seed.sql (APP_POSITION_VIEW). EMP_NIK_VIEW is a real view
-- already granted in every environment. APP_PERSON is recreated below from
-- ELLORA.TBL_PERSON so this script is self-contained and safe to re-run.
-- ============================================================

SET SQLBLANKLINES ON

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

CREATE OR REPLACE VIEW ses_worker_lookup_view AS
WITH sources AS (
    -- 1. EMP_NIK_VIEW — best source: has NIK, dept/section, gender.
    -- TRIM() guards against stray whitespace/newlines seen in the
    -- production extract (e.g. a KTP value with an embedded line break).
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

    -- 2. APP_POSITION_VIEW — PJS-aware current holder, no NIK.
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

    -- 3. APP_PERSON — active Mitra workers (badge prefix 3), no NIK.
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

    -- 4. APP_USER — LDAP only; username IS the badge for LDAP accounts
    -- (never for LOCAL — see AppUser/RequestController conventions).
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

    -- 5. SES_REQUEST_WORKER — past request rows.
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
    -- FIRST_VALUE(...) IGNORE NULLS needs an explicit "whole partition" frame:
    -- the default frame (RANGE UNBOUNDED PRECEDING TO CURRENT ROW) would make
    -- row 1 of each partition — the very row kept below via rn=1 — only ever
    -- see ITSELF, defeating the merge entirely (it would never reach a
    -- lower-priority row's non-null value). ROWS BETWEEN UNBOUNDED PRECEDING
    -- AND UNBOUNDED FOLLOWING makes every row see the ENTIRE partition.
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
    -- One row per real badge, best value per column.
    SELECT badge, worker_name, nik, jabatan, department, section, gender
    FROM merged
    WHERE rn = 1

    UNION ALL

    -- Badgeless rows pass through unmerged — nothing to dedupe against.
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
