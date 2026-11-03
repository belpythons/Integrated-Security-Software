-- ============================================================
-- SES — Reset data transaksional untuk QA bersih — Oracle
-- Run AFTER seluruh skrip skema (terakhir: 61).
--
-- ⚠️⚠️ SKRIP INI MENGHAPUS DATA. BUKAN BAGIAN DARI RUN ORDER.
--      Jangan pernah dijalankan di produksi.
--
-- ------------------------------------------------------------
-- MENGAPA
-- ------------------------------------------------------------
-- ELLDEV memuat pengajuan percobaan yang menumpuk sejak Agustus 2026,
-- sebagian besar dibuat terhadap bentuk approval chain yang SUDAH TIDAK ADA
-- LAGI (4 tahap, lalu 6 tahap paralel — lihat sql/56, 57, 60). Pengajuan itu
-- membawa salinan beku chain lamanya di ses_approval_task dan empat tabel
-- pembekuannya, jadi ia tetap berjalan pada bentuk lama selamanya.
--
-- Itu benar secara desain, tetapi membuat QA mustahil dibaca: daftar approval
-- bercampur antara tahap "Requestor's Manager Approval" (bentuk lama) dan
-- "Additional Routing (Staf Security)" (bentuk baru), dan tidak ada cara
-- melihat sekilas mana yang menguji apa.
--
-- Skrip ini mengosongkan seluruh data TRANSAKSIONAL supaya pengujian
-- berikutnya dimulai dari nol terhadap bentuk 7 tahap serial.
--
-- ------------------------------------------------------------
-- APA YANG DIHAPUS (transaksional)
-- ------------------------------------------------------------
--   Request      : ses_request, _worker, _worker_doc, _evaluator,
--                  _payment, _payment_history, _schedule, _schedule_history
--   Approval     : ses_approval_instance, _task, _task_assignee,
--                  _task_assignee_def, _task_chkp, _task_signoff, _history
--   Sticker      : ses_sticker_request, ses_sticker_vehicle, _veh_doc
--   Kendaraan    : ses_vehicle, ses_vehicle_doc, ses_goods_driver
--   Pelanggaran  : ses_speedcam_violation
--   Kas          : ses_cash_expense
--   Berkas       : app_file — HANYA ref_id milik modul di atas
--   Antrean mail : app_mail
--   Log/err      : app_log, app_error
--
-- ------------------------------------------------------------
-- APA YANG DIPERTAHANKAN (master + konfigurasi)
-- ------------------------------------------------------------
--   Pengguna & RBAC : app_user (574), app_user_role, app_role, app_page,
--                     app_menu, app_role_page, app_role_menu
--   Parameter       : app_parameter, app_cat (termasuk jadwal REQ_EXPIRY)
--   Konfigurasi SES : ses_request_type, ses_request_type_doc,
--                     ses_doc_requirement, ses_zone, ses_form,
--                     ses_form_approval_map, ses_sticker_type,
--                     ses_sticker_type_doc, ses_user_ext,
--                     ses_speedcam_location, ses_contract
--   Master besar    : ses_mitra_master (3.945 baris — master mitra, bukan uji)
--   CHAIN           : ses_approval_chain, ses_approval_step,
--                     ses_approval_assignee, ses_approval_step_task
--                     ⚠️ JANGAN dihapus — ini bentuk 7 tahap hasil sql/60.
--                     Menghapusnya berarti menjalankan 60 lagi.
--
-- ------------------------------------------------------------
-- URUTAN
-- ------------------------------------------------------------
-- Anak sebelum induk. Beberapa FK sudah ON DELETE CASCADE, tetapi urutan
-- eksplisit dipakai supaya skrip ini tidak bergantung pada CASCADE mana yang
-- kebetulan ada — dan supaya jumlah baris yang dihapus bisa dilaporkan per
-- tabel, bukan muncul diam-diam lewat cascade.
--
-- Sequence TIDAK di-reset. Nomor request akan melanjutkan dari angka terakhir,
-- bukan mulai dari 1. Disengaja: me-reset SEQ_SES_REQUEST_NO berarti nomor
-- pengajuan baru bisa bertabrakan dengan nomor yang sudah tercetak pada
-- dokumen lama di luar sistem.
--
-- Aman di-rerun (DELETE dari tabel kosong = 0 baris).
-- ============================================================

SET SERVEROUTPUT ON

DECLARE
    TYPE t_names IS TABLE OF VARCHAR2(40);

    -- Urutan penting: anak lebih dulu.
    v_order t_names := t_names(
        -- approval (anak -> induk)
        'SES_APPROVAL_TASK_SIGNOFF',
        'SES_APPROVAL_TASK_CHKP',
        'SES_APPROVAL_TASK_ASSIGNEE',
        'SES_APPROVAL_TASK_ASSIGNEE_DEF',
        'SES_APPROVAL_TASK',
        'SES_APPROVAL_HISTORY',
        'SES_APPROVAL_INSTANCE',
        -- request (anak -> induk)
        'SES_REQUEST_WORKER_DOC',
        'SES_REQUEST_WORKER',
        'SES_REQUEST_EVALUATOR',
        'SES_REQUEST_PAYMENT_HISTORY',
        'SES_REQUEST_PAYMENT',
        'SES_REQUEST_SCHEDULE_HISTORY',
        'SES_REQUEST_SCHEDULE',
        'SES_REQUEST',
        -- sticker
        'SES_STICKER_VEH_DOC',
        'SES_STICKER_VEHICLE',
        'SES_STICKER_REQUEST',
        -- kendaraan & lain-lain
        'SES_VEHICLE_DOC',
        'SES_VEHICLE',
        'SES_GOODS_DRIVER',
        'SES_SPEEDCAM_VIOLATION',
        'SES_CASH_EXPENSE',
        -- platform
        'APP_MAIL',
        'APP_LOG',
        'APP_ERROR'
    );

    v_count NUMBER;
    v_total NUMBER := 0;
BEGIN
    -- ---- 1. Berkas lebih dulu, selagi ref_id-nya masih bisa dikenali ----
    -- Dibatasi awalan ref_id milik modul yang dibersihkan. Awalan lain
    -- (kalau ada) sengaja dibiarkan: menghapus seluruh app_file akan ikut
    -- membuang berkas milik modul yang tidak disentuh skrip ini.
    DELETE FROM app_file
    WHERE ref_id LIKE 'SES_REQUEST_WORKER_DOC:%'
       OR ref_id LIKE 'SES_PAYMENT:%'
       OR ref_id LIKE 'SES_REQUEST_PAYMENT%'
       OR ref_id LIKE 'SES_STICKER%'
       OR ref_id LIKE 'SES_GOODS%'
       OR ref_id LIKE 'SES_VEHICLE_DOC:%'
       OR ref_id LIKE 'SES_APPROVAL_TASK:%'      -- sisa fitur TTD yang dicabut
       OR ref_id LIKE 'SES_USER_SIGNATURE:%';
    v_count := SQL%ROWCOUNT;
    v_total := v_total + v_count;
    DBMS_OUTPUT.PUT_LINE(RPAD('APP_FILE (ref SES_*)', 36) || v_count);

    -- ---- 2. Tabel transaksional, anak sebelum induk ----
    FOR i IN 1 .. v_order.COUNT LOOP
        BEGIN
            EXECUTE IMMEDIATE 'DELETE FROM ' || v_order(i);
            v_count := SQL%ROWCOUNT;
            v_total := v_total + v_count;
            DBMS_OUTPUT.PUT_LINE(RPAD(v_order(i), 36) || v_count);
        EXCEPTION
            WHEN OTHERS THEN
                -- ORA-00942 = tabel tidak ada di lingkungan ini (mis. modul
                -- goods yang di-revert). Bukan alasan menghentikan sapuan.
                IF SQLCODE = -942 THEN
                    DBMS_OUTPUT.PUT_LINE(RPAD(v_order(i), 36) || '(tabel tidak ada)');
                ELSE
                    RAISE;
                END IF;
        END;
    END LOOP;

    DBMS_OUTPUT.PUT_LINE(RPAD('TOTAL', 36) || v_total);
END;
/

COMMIT;

-- ============================================================
-- PEMERIKSAAN SESUDAH
-- ============================================================
-- Semua harus 0:
-- SELECT 'ses_request' t, COUNT(*) n FROM ses_request
-- UNION ALL SELECT 'ses_approval_instance', COUNT(*) FROM ses_approval_instance
-- UNION ALL SELECT 'ses_approval_task',     COUNT(*) FROM ses_approval_task
-- UNION ALL SELECT 'ses_sticker_request',   COUNT(*) FROM ses_sticker_request
-- UNION ALL SELECT 'app_mail',              COUNT(*) FROM app_mail;
--
-- Konfigurasi harus UTUH — chain 7 tahap, 5 jenis pengajuan, 574 pengguna:
-- SELECT c.chain_key, COUNT(*) AS tahap
-- FROM ses_approval_chain c JOIN ses_approval_step s ON s.chain_id = c.chain_id
-- GROUP BY c.chain_key ORDER BY 1;
-- SELECT COUNT(*) FROM ses_request_type;   -- 5
-- SELECT COUNT(*) FROM app_user;           -- 574
-- SELECT COUNT(*) FROM ses_mitra_master;   -- 3945
