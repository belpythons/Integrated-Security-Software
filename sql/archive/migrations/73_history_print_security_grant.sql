-- ============================================================
-- SES — Grant PRINT action on /history to role SECURITY
-- Lets SECURITY print the full (unscoped) Riwayat list as a PDF.
-- ============================================================

SET DEFINE OFF;

INSERT INTO app_role_page (role_id, page_id, action_code, added_by, added_date)
SELECT r.role_id, p.page_id, 'PRINT', 'SYSTEM', SYSDATE
FROM app_role r
CROSS JOIN (SELECT page_id FROM app_page WHERE page_url = '/history') p
WHERE r.role_name = 'SECURITY'
AND NOT EXISTS (
    SELECT 1 FROM app_role_page rp
    WHERE rp.role_id = r.role_id AND rp.page_id = p.page_id AND rp.action_code = 'PRINT'
);

COMMIT;
