-- ============================================================
-- SES 261019 — 90_security_rbac_align.sql
-- Makes the SECURITY role on this schema match ELLDEV exactly.
--
-- On ELLPRD, role 5 was hand-made ("Security Administration", later renamed
-- SECURITY) with full rights on 18 pages and 15 menus. 74 cannot correct
-- that: it only inserts rows that are missing. ELLDEV's SECURITY role, where
-- the officer checkpoint flow was tested, has exactly the 10 page grants
-- below and no menus; officers get their sidebar from REQUESTOR.
--
--   1. SECURITY page grants  -> exactly the 10 pages / action codes below.
--      Notably /history drops SUBMIT, the "see everyone's Riwayat" flag
--      (HistoryController), so officers see only requests they are
--      involved in.
--   2. SECURITY menu grants  -> none.
--   3. Every user holding SECURITY but not REQUESTOR is given REQUESTOR,
--      so removing SECURITY's menus does not leave them without a sidebar.
--
-- Run AFTER 72 and 74 (both insert SECURITY rows this script then trims).
-- Idempotent.
-- ============================================================

SET DEFINE OFF
SET SERVEROUTPUT ON

DECLARE
    v_security  app_role.role_id%TYPE;
    v_requestor app_role.role_id%TYPE;
    v_count     NUMBER;
BEGIN
    SELECT role_id INTO v_security  FROM app_role WHERE role_name = 'SECURITY';
    SELECT role_id INTO v_requestor FROM app_role WHERE role_name = 'REQUESTOR';

    -- 1a. Drop every SECURITY page grant outside the ELLDEV set.
    DELETE FROM app_role_page
     WHERE role_id = v_security
       AND page_id NOT IN (
           SELECT page_id FROM app_page WHERE page_url IN (
               '/approval', '/approval/form', '/history',
               '/payment/verify', '/payment/verify/form',
               '/request', '/request/form',
               '/scheduling/calendar', '/scheduling/queue', '/scheduling/queue/form'));
    DBMS_OUTPUT.PUT_LINE('SECURITY: ' || SQL%ROWCOUNT || ' page grant di luar set ELLDEV dihapus.');

    -- 1b. Set the remaining ten to ELLDEV's action codes.
    MERGE INTO app_role_page t
    USING (
        SELECT v_security AS role_id, p.page_id, d.action_code
          FROM app_page p
          JOIN (SELECT '/approval'               AS page_url, 'READ,CREATE,UPDATE,ADD,SUBMIT' AS action_code FROM dual UNION ALL
                SELECT '/approval/form',                      'READ,CREATE,UPDATE,ADD,SUBMIT' FROM dual UNION ALL
                SELECT '/history',                            'READ,PRINT'                    FROM dual UNION ALL
                SELECT '/payment/verify',                     'READ'                          FROM dual UNION ALL
                SELECT '/payment/verify/form',                'READ,SUBMIT'                   FROM dual UNION ALL
                SELECT '/request',                            'READ,CREATE,SUBMIT'            FROM dual UNION ALL
                SELECT '/request/form',                       'READ,CREATE,SUBMIT'            FROM dual UNION ALL
                SELECT '/scheduling/calendar',                'READ'                          FROM dual UNION ALL
                SELECT '/scheduling/queue',                   'READ'                          FROM dual UNION ALL
                SELECT '/scheduling/queue/form',              'READ,SUBMIT'                   FROM dual) d
            ON d.page_url = p.page_url
    ) s
    ON (t.role_id = s.role_id AND t.page_id = s.page_id)
    WHEN MATCHED THEN
        UPDATE SET t.action_code = s.action_code
    WHEN NOT MATCHED THEN
        INSERT (role_id, page_id, action_code, added_by, added_date)
        VALUES (s.role_id, s.page_id, s.action_code, 'SYSTEM', SYSDATE);
    DBMS_OUTPUT.PUT_LINE('SECURITY: ' || SQL%ROWCOUNT || ' page grant diselaraskan ke ELLDEV.');

    -- 2. No menus of its own, as on ELLDEV.
    DELETE FROM app_role_menu WHERE role_id = v_security;
    DBMS_OUTPUT.PUT_LINE('SECURITY: ' || SQL%ROWCOUNT || ' menu grant dihapus.');

    -- 3. Officers without REQUESTOR get it, so they keep a sidebar.
    INSERT INTO app_user_role (user_id, role_id, added_by, added_date)
    SELECT ur.user_id, v_requestor, 'SYSTEM', SYSDATE
      FROM app_user_role ur
     WHERE ur.role_id = v_security
       AND NOT EXISTS (SELECT 1 FROM app_user_role x
                        WHERE x.user_id = ur.user_id AND x.role_id = v_requestor);
    DBMS_OUTPUT.PUT_LINE('REQUESTOR ditambahkan ke ' || SQL%ROWCOUNT || ' petugas SECURITY.');

    SELECT COUNT(*) INTO v_count FROM app_role_page WHERE role_id = v_security;
    IF v_count <> 10 THEN
        RAISE_APPLICATION_ERROR(-20090, 'SECURITY memiliki ' || v_count || ' page grant, seharusnya 10.');
    END IF;
END;
/

COMMIT;
