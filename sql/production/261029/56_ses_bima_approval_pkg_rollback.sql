-- 56_ses_bima_approval_pkg_rollback.sql
-- Rollback script for 56_ses_bima_approval_pkg.sql
-- Tanggal: 2026-10-29

BEGIN
    EXECUTE IMMEDIATE 'DROP PACKAGE PKG_SES_BIMA_APPROVAL';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE != -4043 THEN
            RAISE;
        END IF;
END;
/

BEGIN
    EXECUTE IMMEDIATE 'DROP VIEW SES_BIMA_APPROVAL_V';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE != -942 THEN
            RAISE;
        END IF;
END;
/
