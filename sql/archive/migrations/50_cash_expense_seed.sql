-- ============================================================
-- SES — Pengeluaran Kas: registrasi app_page / app_menu / RBAC — Oracle
-- Run AFTER 49_cash_expense_ddl.sql.
--
-- Isi:
--   1. APP_PAGE '/admin/cash-expense' + '/admin/cash-expense/form'.
--      Pasangan URL "nyata + virtual" adalah konvensi RBAC aplikasi ini:
--      URL daftar menjaga pembacaan, URL /form yang tidak punya mapping
--      controller sama sekali adalah tempat menggantungkan hak
--      CREATE/UPDATE/DELETE/SUBMIT. Bandingkan 28_sticker_seed.sql.
--   2. APP_MENU "Pengeluaran Kas" tepat di sebelah "Pemasukan Kas".
--   3. Grant penuh untuk ADMIN/SYSADM. Tidak ada grant untuk REQUESTOR —
--      ini layar kas internal, bukan worklist pemohon.
--
-- ID TIDAK HARDCODE — page_id/menu_id diambil dari SEQ_APP_PAGE/SEQ_APP_MENU
-- dan menu induk diresolusikan dari posisi "Pemasukan Kas", bukan angka
-- tetap — aman dijalankan pada skema baru di mana penomoran app_page/
-- app_menu bisa berbeda dari ELLDEV.
--
-- Idempotent: NOT EXISTS-guarded per (page_url) / (menu_name, parent) /
-- (role_id, page_id) / (role_id, menu_id). Aman dijalankan ulang.
-- ============================================================

DECLARE
    v_list_page_id   app_page.page_id%TYPE;
    v_form_page_id   app_page.page_id%TYPE;
    v_menu_id        app_menu.menu_id%TYPE;
    v_parent_menu_id app_menu.parent_menu_id%TYPE;
    v_menu_order     app_menu.menu_order%TYPE;
    v_count          NUMBER;
BEGIN
    -- ---- APP_PAGE: daftar ----
    SELECT COUNT(*) INTO v_count FROM app_page WHERE page_url = '/admin/cash-expense';
    IF v_count = 0 THEN
        INSERT INTO app_page (page_id, page_name, page_url, page_desc, active_flag, created_by, created_date)
        VALUES (SEQ_APP_PAGE.NEXTVAL, 'Pengeluaran Kas', '/admin/cash-expense',
                'Pencatatan pengeluaran kas, histori, dan ringkasan Kas Masuk/Keluar/Balance',
                'Y', 'SYSTEM', SYSDATE);
    END IF;
    SELECT page_id INTO v_list_page_id FROM app_page WHERE page_url = '/admin/cash-expense';

    -- ---- APP_PAGE: URL virtual pemegang hak tulis ----
    SELECT COUNT(*) INTO v_count FROM app_page WHERE page_url = '/admin/cash-expense/form';
    IF v_count = 0 THEN
        INSERT INTO app_page (page_id, page_name, page_url, page_desc, active_flag, created_by, created_date)
        VALUES (SEQ_APP_PAGE.NEXTVAL, 'Pengeluaran Kas (Form)', '/admin/cash-expense/form',
                'URL virtual — menggantungkan hak CREATE/UPDATE/DELETE/SUBMIT Pengeluaran Kas',
                'Y', 'SYSTEM', SYSDATE);
    END IF;
    SELECT page_id INTO v_form_page_id FROM app_page WHERE page_url = '/admin/cash-expense/form';

    -- ---- APP_MENU ----
    -- Induknya diambil dari menu "Pemasukan Kas" yang sudah ada.
    BEGIN
        SELECT parent_menu_id INTO v_parent_menu_id
        FROM app_menu WHERE menu_name = 'Pemasukan Kas' AND ROWNUM = 1;
    EXCEPTION
        WHEN NO_DATA_FOUND THEN
            -- Fallback ke grup "SES (Admin)" bila menu Pemasukan Kas sudah
            -- diganti nama. Kalau itu pun tidak ada, biarkan gagal keras:
            -- menaruh entri kas di akar sidebar lebih buruk daripada berhenti.
            SELECT menu_id INTO v_parent_menu_id
            FROM app_menu WHERE menu_name = 'SES (Admin)' AND ROWNUM = 1;
    END;

    SELECT COUNT(*) INTO v_count
    FROM app_menu WHERE menu_name = 'Pengeluaran Kas' AND parent_menu_id = v_parent_menu_id;
    IF v_count = 0 THEN
        SELECT NVL(MAX(menu_order), 0) + 1 INTO v_menu_order
        FROM app_menu WHERE parent_menu_id = v_parent_menu_id;

        INSERT INTO app_menu (menu_id, menu_name, page_id, menu_icon, menu_order,
                              active_flag, parent_menu_id, created_by, created_date)
        VALUES (SEQ_APP_MENU.NEXTVAL, 'Pengeluaran Kas', v_list_page_id, 'bi bi-cash-stack',
                v_menu_order, 'Y', v_parent_menu_id, 'SYSTEM', SYSDATE);
    END IF;
    SELECT menu_id INTO v_menu_id
    FROM app_menu WHERE menu_name = 'Pengeluaran Kas' AND parent_menu_id = v_parent_menu_id AND ROWNUM = 1;

    -- ---- APP_ROLE_PAGE ----
    -- action_code adalah daftar dipisah koma dalam SATU baris per (role, page)
    -- — bukan satu baris per aksi. Lihat catatan di 40_dashboard_admin_view_grant.sql.
    INSERT INTO app_role_page (role_id, page_id, action_code, added_by, added_date)
    SELECT r.role_id, p.page_id, 'READ,CREATE,UPDATE,DELETE,ADD,REMOVE,SUBMIT', 'SYSTEM', SYSDATE
    FROM app_role r, app_page p
    WHERE r.role_name IN ('ADMIN', 'SYSADM')
      AND p.page_id IN (v_list_page_id, v_form_page_id)
      AND NOT EXISTS (SELECT 1 FROM app_role_page rp
                      WHERE rp.role_id = r.role_id AND rp.page_id = p.page_id);

    -- ---- APP_ROLE_MENU ----
    INSERT INTO app_role_menu (role_id, menu_id, added_by, added_date)
    SELECT r.role_id, v_menu_id, 'SYSTEM', SYSDATE
    FROM app_role r
    WHERE r.role_name IN ('ADMIN', 'SYSADM')
      AND NOT EXISTS (SELECT 1 FROM app_role_menu rm
                      WHERE rm.role_id = r.role_id AND rm.menu_id = v_menu_id);

    DBMS_OUTPUT.PUT_LINE('Pengeluaran Kas: page ' || v_list_page_id || '/' || v_form_page_id
        || ', menu ' || v_menu_id || ' under parent ' || v_parent_menu_id);
END;
/

COMMIT;
