-- ============================================================
-- SES — SECURITY role & Checkpoint Tasks RBAC grant — Oracle
-- Run AFTER 72_riwayat_user_access_grant.sql.
--
-- Registers role 'SECURITY' (role_id = 5) for Security Officers
-- who are responsible for Checkpoint Tasks (Payment verification &
-- Screening scheduling).
-- Grants SUBMIT on /payment/verify/form and /scheduling/queue/form,
-- as well as access to Riwayat (/history) with PRINT capability.
-- Links user '133458' (BRYAN ADAM WAHYU SUTIKNO) to role SECURITY.
--
-- Idempotent: safe to re-run.
-- ============================================================

SET DEFINE OFF;

-- 1. Register role SECURITY
INSERT INTO app_role (role_id, role_name, role_desc, active_flag, created_by, created_date)
SELECT 5, 'SECURITY', 'Security Officer for Checkpoints and Verification', 'Y', 'SYSTEM', SYSDATE
FROM dual
WHERE NOT EXISTS (SELECT 1 FROM app_role WHERE role_name = 'SECURITY');

-- 2. Grants for role SECURITY on Payment Verification & Scheduling
INSERT INTO app_role_page (role_id, page_id, action_code, added_by, added_date)
SELECT r.role_id, p.page_id,
       CASE p.page_url
           WHEN '/payment/verify' THEN 'READ'
           WHEN '/payment/verify/form' THEN 'READ,SUBMIT'
           WHEN '/scheduling/queue' THEN 'READ'
           WHEN '/scheduling/queue/form' THEN 'READ,SUBMIT'
           WHEN '/scheduling/calendar' THEN 'READ'
           WHEN '/history' THEN 'READ,PRINT'
           WHEN '/approval' THEN 'READ,CREATE,UPDATE,ADD,SUBMIT'
           WHEN '/approval/form' THEN 'READ,CREATE,UPDATE,ADD,SUBMIT'
           WHEN '/request' THEN 'READ,CREATE,SUBMIT'
           WHEN '/request/form' THEN 'READ,CREATE,SUBMIT'
           ELSE 'READ'
       END,
       'SYSTEM', SYSDATE
FROM app_role r
CROSS JOIN app_page p
WHERE r.role_name = 'SECURITY'
AND p.page_url IN (
    '/payment/verify', '/payment/verify/form',
    '/scheduling/queue', '/scheduling/queue/form', '/scheduling/calendar',
    '/history', '/approval', '/approval/form', '/request', '/request/form'
)
AND NOT EXISTS (
    SELECT 1 FROM app_role_page rp
    WHERE rp.role_id = r.role_id AND rp.page_id = p.page_id
);

-- Update action_code if row already existed with lower privileges
UPDATE app_role_page rp
SET rp.action_code = 'READ,SUBMIT'
WHERE rp.role_id = (SELECT role_id FROM app_role WHERE role_name = 'SECURITY')
AND rp.page_id IN (SELECT page_id FROM app_page WHERE page_url IN ('/payment/verify/form', '/scheduling/queue/form'))
AND INSTR(rp.action_code, 'SUBMIT') = 0;

-- 3. Assign role SECURITY to user 133458 (BRYAN ADAM WAHYU SUTIKNO)
INSERT INTO app_user_role (user_id, role_id, added_by, added_date)
SELECT u.user_id, r.role_id, 'SYSTEM', SYSDATE
FROM app_user u
CROSS JOIN app_role r
WHERE u.username = '133458'
AND r.role_name = 'SECURITY'
AND NOT EXISTS (
    SELECT 1 FROM app_user_role ur
    WHERE ur.user_id = u.user_id AND ur.role_id = r.role_id
);

-- 4. Advance SEQ_APP_ROLE
DECLARE v_max NUMBER;
BEGIN
    SELECT NVL(MAX(role_id), 0) + 1 INTO v_max FROM app_role;
    BEGIN
        EXECUTE IMMEDIATE 'DROP SEQUENCE SEQ_APP_ROLE';
    EXCEPTION
        WHEN OTHERS THEN NULL;
    END;
    EXECUTE IMMEDIATE 'CREATE SEQUENCE SEQ_APP_ROLE START WITH ' || v_max || ' INCREMENT BY 1 NOCACHE NOCYCLE';
END;
/

COMMIT;
