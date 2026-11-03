-- REFERENCE SNAPSHOT ONLY
-- Captured from ELLPRD on 2026-09-18.
-- Do not execute as part of the normal deployment sequence.
--
-- This snapshot records the active five-source lookup definition, including
-- SES_MITRA_MASTER, as captured on that date. It is historical documentation
-- and does not represent the current definition in 23_worker_lookup_view.sql.

CREATE OR REPLACE FORCE EDITIONABLE VIEW "SES"."SES_WORKER_LOOKUP_VIEW"
    ("LOOKUP_ID", "BADGE", "WORKER_NAME", "NIK", "JABATAN",
     "DEPARTMENT", "SECTION", "GENDER") AS
WITH sources AS (
    -- 1. EMP_NIK_VIEW - organic, authoritative (NIK, dept/section, gender).
    SELECT
        TRIM(emplid) AS badge_raw,
        TRIM(name_display) AS worker_name,
        TRIM(ktp) AS nik,
        TRIM(pos_desc) AS jabatan,
        TRIM(dept_desc) AS department,
        TRIM(sect_desc) AS section,
        CASE WHEN gender = 'MALE' THEN 'M' ELSE 'F' END AS gender,
        1 AS src_priority
    FROM emp_nik_view

    UNION ALL

    -- 2. APP_POSITION_VIEW - PJS-aware current holder, no NIK.
    SELECT
        TRIM(holder_empl_id) AS badge_raw,
        TRIM(holder_empl_name) AS worker_name,
        NULL AS nik,
        TRIM(pos_title) AS jabatan,
        TRIM(dept_desc) AS department,
        TRIM(section_desc) AS section,
        NULL AS gender,
        2 AS src_priority
    FROM app_position_view
    WHERE holder_empl_id IS NOT NULL

    UNION ALL

    -- 3. SES_MITRA_MASTER - Mitra Kerja (badge awalan 2 & 3)
    --    from ELLORA.TBL_PERSON (10.10.1.190).
    SELECT
        TRIM(badge) AS badge_raw,
        TRIM(worker_name) AS worker_name,
        TRIM(nik) AS nik,
        TRIM(jabatan) AS jabatan,
        TRIM(department) AS department,
        TRIM(section) AS section,
        gender,
        3 AS src_priority
    FROM ses_mitra_master

    UNION ALL

    -- 4. APP_USER - LDAP only; username IS the badge for LDAP accounts.
    SELECT
        TRIM(username) AS badge_raw,
        TRIM(full_name) AS worker_name,
        NULL AS nik,
        NULL AS jabatan,
        NULL AS department,
        NULL AS section,
        NULL AS gender,
        4 AS src_priority
    FROM app_user
    WHERE active_flag = 'Y'
      AND auth_type = 'LDAP'
      AND REGEXP_LIKE(username, '^[0-9]{6}$')

    UNION ALL

    -- 5. SES_REQUEST_WORKER - past request rows (may carry manually-verified NIK).
    SELECT
        TRIM(badge) AS badge_raw,
        TRIM(worker_name) AS worker_name,
        TRIM(nik) AS nik,
        TRIM(jabatan) AS jabatan,
        TRIM(department) AS department,
        TRIM(section) AS section,
        gender,
        5 AS src_priority
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
            ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING) AS worker_name,
        FIRST_VALUE(nik) IGNORE NULLS OVER (
            PARTITION BY badge_key ORDER BY src_priority
            ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING) AS nik,
        FIRST_VALUE(jabatan) IGNORE NULLS OVER (
            PARTITION BY badge_key ORDER BY src_priority
            ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING) AS jabatan,
        FIRST_VALUE(department) IGNORE NULLS OVER (
            PARTITION BY badge_key ORDER BY src_priority
            ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING) AS department,
        FIRST_VALUE(section) IGNORE NULLS OVER (
            PARTITION BY badge_key ORDER BY src_priority
            ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING) AS section,
        FIRST_VALUE(gender) IGNORE NULLS OVER (
            PARTITION BY badge_key ORDER BY src_priority
            ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING) AS gender,
        ROW_NUMBER() OVER (PARTITION BY badge_key ORDER BY src_priority) AS rn
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
