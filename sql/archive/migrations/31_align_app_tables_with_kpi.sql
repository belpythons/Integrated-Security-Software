-- ============================================================
-- SES — align APP_* column widths/types with KPI schema
-- Verified live against ELLDEV 2026-09-09/10 (docs/ldap-kpi-alignment-plan.md
-- Bagian 2a) and confirmed APPLIED on ELLDEV as of 2026-10-13.
--
-- Scope: widening ONLY -- VARCHAR2 length increases, DATE -> TIMESTAMP(6)
-- (strictly more precision, no data loss), and one new nullable column
-- (APP_FILE.FILE_SHA256). No data conversion, no narrowing, no dropped
-- columns.
--
-- Deliberately NOT included (see docs/ldap-kpi-alignment-plan.md Bagian 2a/2b):
--   - APP_FILE.FILE_SIZE (SES BINARY_DOUBLE vs KPI NUMBER) -- real type
--     conversion, not widening; no functional benefit found.
--   - APP_ROLE/APP_MENU/APP_USER.ACTIVE_FLAG (SES CHAR(1) vs KPI
--     VARCHAR2(1)) -- cosmetic only, SES's NOT NULL is stricter/safer.
--   - ROLE_ID/MENU_ID/PAGE_ID NUMBER->VARCHAR2 (Bagian 2b) -- NOT
--     recommended, would break platform's hardcoded Long fields.
-- ============================================================

ALTER TABLE APP_USER MODIFY (
    USERNAME    VARCHAR2(64),
    PASSWORD    VARCHAR2(200),
    AUTH_TYPE   VARCHAR2(32),
    CREATED_BY  VARCHAR2(32),
    UPDATED_BY  VARCHAR2(32)
);
ALTER TABLE APP_USER MODIFY (CREATED_DATE TIMESTAMP(6), UPDATED_DATE TIMESTAMP(6));

ALTER TABLE APP_MENU MODIFY (
    MENU_NAME  VARCHAR2(100 CHAR),
    MENU_ICON  VARCHAR2(100 CHAR),
    CREATED_BY VARCHAR2(32),
    UPDATED_BY VARCHAR2(32)
);
ALTER TABLE APP_MENU MODIFY (CREATED_DATE TIMESTAMP(6), UPDATED_DATE TIMESTAMP(6));

ALTER TABLE APP_ROLE MODIFY (ROLE_NAME VARCHAR2(100 CHAR));

ALTER TABLE APP_PARAMETER MODIFY (
    PARAM_ID    VARCHAR2(64),
    PARAM_VALUE VARCHAR2(1000 CHAR),
    PARAM_DESC  VARCHAR2(500 CHAR),
    UPDATED_BY  VARCHAR2(32)
);
ALTER TABLE APP_PARAMETER MODIFY (UPDATED_DATE TIMESTAMP(6));

ALTER TABLE APP_CAT MODIFY (
    CAT_TYPE   VARCHAR2(64),
    CAT_VALUE  VARCHAR2(64),
    CAT_LABEL  VARCHAR2(255 CHAR),
    CAT_OTHER1 VARCHAR2(255 CHAR),
    CAT_OTHER2 VARCHAR2(255 CHAR),
    CAT_OTHER3 VARCHAR2(255 CHAR),
    CAT_OTHER4 VARCHAR2(255 CHAR),
    CAT_OTHER5 VARCHAR2(255 CHAR)
);

ALTER TABLE APP_FILE ADD (FILE_SHA256 VARCHAR2(64));
ALTER TABLE APP_FILE MODIFY (
    CREATED_BY VARCHAR2(32),
    UPDATED_BY VARCHAR2(32),
    FILE_TYPE  VARCHAR2(128)
);
ALTER TABLE APP_FILE MODIFY (CREATED_DATE TIMESTAMP(6), UPDATED_DATE TIMESTAMP(6));

COMMIT;
