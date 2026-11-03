-- ============================================================
-- SES — Menu Riwayat Top-Level (Menu Utama) & RBAC Access Grant
--
-- [261019] Production copy of sql/archive/migrations/72_riwayat_user_access_grant.sql
-- WITHOUT its step 4 (DELETE FROM app_mail / app_log / app_error). That
-- cleanup was for testing on ELLDEV; on ELLPRD it would erase the live mail
-- history. Steps 1-3 are unchanged.
-- ============================================================

SET DEFINE OFF;

-- 1. Move Menu 29 ("Riwayat") to top-level menu utama (parent_menu_id IS NULL)
--    Menjadikan Riwayat sebagai menu mandiri di sidebar (bukan sub-menu
--    Tanda Pengenal Pekerja) agar menghubungkan modul Badge, Sticker,
--    dan modul baru lainnya di masa depan.
UPDATE app_menu
SET parent_menu_id = NULL,
    menu_order = 3,
    menu_icon = 'bi bi-clock-history',
    updated_by = 'SYSTEM',
    updated_date = SYSDATE
WHERE menu_id = 29;

-- 2. Grant READ on /history to all roles that don't have it yet
INSERT INTO app_role_page (role_id, page_id, action_code, added_by, added_date)
SELECT r.role_id, p.page_id, 'READ', 'SYSTEM', SYSDATE
FROM app_role r
CROSS JOIN (SELECT page_id FROM app_page WHERE page_url = '/history') p
WHERE NOT EXISTS (
    SELECT 1 FROM app_role_page rp
    WHERE rp.role_id = r.role_id AND rp.page_id = p.page_id
);

-- 3. Grant Menu Riwayat (Menu 29) to all roles (including REQUESTOR)
INSERT INTO app_role_menu (role_id, menu_id, added_by, added_date)
SELECT r.role_id, m.menu_id, 'SYSTEM', SYSDATE
FROM app_role r
CROSS JOIN (SELECT menu_id FROM app_menu WHERE menu_id = 29) m
WHERE NOT EXISTS (
    SELECT 1 FROM app_role_menu rm
    WHERE rm.role_id = r.role_id AND rm.menu_id = m.menu_id
);

COMMIT;
