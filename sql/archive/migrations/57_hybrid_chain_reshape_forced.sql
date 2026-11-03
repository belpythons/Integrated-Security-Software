-- ============================================================
-- SES — Tata ulang PAKSA chain DEFAULT + PAYMENT ke bentuk serial — Oracle
-- Run AFTER 52_hybrid_routing_alter.sql, 54_request_evaluator_ddl.sql dan
-- 58_procurement_supervisor_role_alter.sql (tanpa 58, CHECK constraint
-- ck_ses_step_role akan menolak nilai PROCUREMENT_SUPERVISOR).
--
-- ⚠️ SKRIP INI MENIMPA KONFIGURASI CHAIN YANG SUDAH DISUNTING ADMIN.
--    Jalankan hanya setelah membaca seluruh header ini. Bukan bagian dari
--    run order otomatis.
--
-- ------------------------------------------------------------
-- MENGAPA SKRIP INI ADA
-- ------------------------------------------------------------
-- 56_hybrid_chain_seed.sql sengaja MENOLAK menyentuh chain yang tampak
-- pernah disunting admin (`created_by <> 'SYSTEM'` atau `updated_date IS NOT
-- NULL` pada salah satu step). Pada ELLDEV penjaga itu memang menyala, dan
-- benar menyala:
--
--   DEFAULT  step 1-3  created_by=SYSTEM  updated_by=admin (2026-08-24)
--   DEFAULT  step 4    created_by=admin                     <- ditambah manual
--   PAYMENT  step 1-3  created_by=SYSTEM  updated_by=admin (s/d 2026-09-22)
--   PAYMENT  step 4    created_by=admin                     <- ditambah manual
--
-- Akibatnya 56 tidak melakukan apa pun, dan SELURUH mesin routing baru
-- menjadi INERT: tidak ada step yang punya step_role, jadi tidak ada yang bisa
-- dilewati toggle jenis pengajuan, tidak ada tahap Procurement, dan tahap
-- Additional Routing tidak pernah terbentuk. Aplikasinya berjalan — persis
-- seperti mesin empat tahap lama.
--
-- Skrip ini adalah jalan keluar yang EKSPLISIT: operator menyatakan "ya,
-- timpa konfigurasi chain yang ada".
--
-- ------------------------------------------------------------
-- APA YANG AMAN DAN APA YANG TIDAK
-- ------------------------------------------------------------
-- AMAN — pengajuan yang sedang berjalan tidak terpengaruh. ses_approval_task
-- dan tiga tabel pembekuannya (_task_assignee_def, _task_assignee, _task_chkp)
-- menyimpan salinan chain milik instance itu sendiri, diambil saat trigger().
-- Menata ulang DEFINISI tidak bisa memindahkan step di bawah kaki approval
-- yang sudah jalan. Pada ELLDEV saat penulisan: DEFAULT 3 instance PENDING,
-- PAYMENT 2 instance PENDING — semuanya tetap berjalan pada bentuk lamanya.
--
-- HILANG — penyesuaian manual apa pun yang admin buat pada DEFINISI chain:
-- nama step, urutan, assignee, dan checkpoint. Bentuk hasilnya ditulis ulang
-- persis seperti tabel di bawah. Kalau ada assignee khusus yang pernah
-- ditambahkan lewat /approval/chain, CATAT DULU sebelum menjalankan ini.
--
-- ALTERNATIF TANPA SKRIP — bentuk yang sama bisa disusun lewat GUI di
-- /approval/chain memakai chain builder modal: tambah/ubah step satu per satu,
-- isi Peran, Grup Paralel, dan Aturan Selesai. Lebih lambat, tetapi setiap
-- perubahan tervalidasi dan tidak ada yang terhapus tanpa Anda lihat.
--
-- ------------------------------------------------------------
-- BENTUK HASIL
-- ------------------------------------------------------------
--   grp step  name                               step_role               rule
--   --- ----  ---------------------------------  ----------------------  ----
--    1   1    Additional Routing (Staf Security) SECURITY_STAFF          ALL
--    2   2    Persetujuan Supervisor Procurement PROCUREMENT_SUPERVISOR  ANY
--    3   3    Persetujuan Manager Procurement    PROCUREMENT_MANAGER     ANY
--    4   4    Persetujuan Manager Pemohon        REQUESTOR_MANAGER       ANY
--    5   5    Persetujuan Manager Security       SECURITY_MANAGER        ANY
--    6   6    Officer / Co-Officer Approval      (none)                  ANY
--    7   7    Badge Retrieval                    (none)                  ANY
--
-- Serial sepenuhnya — satu approver pada satu waktu, sesuai
-- docs/reference/approval flow.jpeg. Tahap 2+3 dikendalikan SATU toggle
-- (needs_proc_approval_flag), tahap 4 oleh toggle-nya sendiri, dan tahap 1
-- dilewati bila pemohon tidak memilih siapa pun. Pengajuan dengan kedua
-- toggle 'N' dan tanpa additional routing langsung membuka tahap 5.
--
-- Hanya DEFAULT dan PAYMENT yang disentuh. GOODS_DEFAULT dan STICKER_DEFAULT
-- DIBIARKAN: modul goods sudah di-revert dari kode, dan stiker di luar scope
-- wave ini (Badge only).
--
-- Idempotent: chain yang sudah punya step_role dilewati. Chain yang terlanjur
-- ditata ke bentuk 6 tahap lama TIDAK akan disentuh oleh skrip ini — hapus
-- step_role-nya dulu, atau tata ulang lewat /approval/chain.
-- ============================================================

SET SERVEROUTPUT ON

DECLARE
    v_admin_user_id   app_user.user_id%TYPE := '00000000-0000-0000-0000-000000000001';
    v_sec_mgr_pos_id  ses_approval_assignee.pos_id%TYPE := 'E01000';
    -- The two procurement rungs are DIFFERENT chairs, in this order.
    -- EH1200 = SUPERVISOR, PROCUREMENT SUPPORT; EH1000 = MANAGER, PROCUREMENT.
    -- EH1000 is also the procurement SECTION id, which is why the reference
    -- diagram's typo (both boxes labelled EH1000) reads as plausible.
    -- Verified against APP_POSITION_VIEW on ELLDEV, 2026-10-06.
    v_proc_supv_pos_id ses_approval_assignee.pos_id%TYPE := 'EH1200';
    v_proc_mgr_pos_id  ses_approval_assignee.pos_id%TYPE := 'EH1000';
    v_officer_pos_id   ses_approval_assignee.pos_id%TYPE := 'E01G10';

    PROCEDURE add_assignee(p_step_id IN NUMBER, p_type IN VARCHAR2,
                           p_pos_id IN VARCHAR2, p_user_id IN VARCHAR2, p_order IN NUMBER) IS
    BEGIN
        INSERT INTO ses_approval_assignee
            (assignee_id, step_id, assignee_type, pos_id, user_id, order_by, created_by, created_date)
        VALUES (SEQ_SES_APPROVAL_ASSIGNEE.NEXTVAL, p_step_id, p_type, p_pos_id, p_user_id, p_order,
                'SYSTEM', SYSDATE);
    END add_assignee;

    FUNCTION new_step(p_chain_id IN NUMBER, p_order IN NUMBER, p_name IN VARCHAR2,
                      p_role IN VARCHAR2, p_group IN NUMBER, p_rule IN VARCHAR2) RETURN NUMBER IS
        v_step_id NUMBER;
    BEGIN
        INSERT INTO ses_approval_step
            (step_id, chain_id, step_order, step_name, step_role, parallel_group, completion_rule,
             created_by, created_date)
        VALUES (SEQ_SES_APPROVAL_STEP.NEXTVAL, p_chain_id, p_order, p_name, p_role, p_group, p_rule,
                'SYSTEM', SYSDATE)
        RETURNING step_id INTO v_step_id;
        RETURN v_step_id;
    END new_step;

    PROCEDURE reshape(p_key IN VARCHAR2, p_payment_checkpoint IN CHAR) IS
        v_chain_id NUMBER;
        v_shaped   NUMBER;
        v_pending  NUMBER;
        v_s1 NUMBER; v_s2 NUMBER; v_s3 NUMBER; v_s4 NUMBER; v_s5 NUMBER; v_s6 NUMBER; v_s7 NUMBER;
    BEGIN
        BEGIN
            SELECT chain_id INTO v_chain_id FROM ses_approval_chain WHERE chain_key = p_key;
        EXCEPTION
            WHEN NO_DATA_FOUND THEN
                DBMS_OUTPUT.PUT_LINE('Chain ' || p_key || ': tidak ada, dilewati.');
                RETURN;
        END;

        SELECT COUNT(*) INTO v_shaped FROM ses_approval_step
        WHERE chain_id = v_chain_id AND step_role IS NOT NULL;
        IF v_shaped > 0 THEN
            DBMS_OUTPUT.PUT_LINE('Chain ' || p_key || ': sudah punya step_role, dilewati.');
            RETURN;
        END IF;

        SELECT COUNT(*) INTO v_pending FROM ses_approval_instance
        WHERE chain_id = v_chain_id AND status = 'PENDING';
        DBMS_OUTPUT.PUT_LINE('Chain ' || p_key || ': menata ulang. ' || v_pending
            || ' instance PENDING tidak terpengaruh (memakai salinan beku masing-masing).');

        -- ON DELETE CASCADE pada ses_approval_assignee dan
        -- ses_approval_step_task membersihkan anak-anaknya.
        DELETE FROM ses_approval_step WHERE chain_id = v_chain_id;

        v_s1 := new_step(v_chain_id, 1, 'Additional Routing (Staf Security)', 'SECURITY_STAFF',         1, 'ALL');
        v_s2 := new_step(v_chain_id, 2, 'Persetujuan Supervisor Procurement', 'PROCUREMENT_SUPERVISOR', 2, 'ANY');
        v_s3 := new_step(v_chain_id, 3, 'Persetujuan Manager Procurement',    'PROCUREMENT_MANAGER',    3, 'ANY');
        v_s4 := new_step(v_chain_id, 4, 'Persetujuan Manager Pemohon',        'REQUESTOR_MANAGER',      4, 'ANY');
        v_s5 := new_step(v_chain_id, 5, 'Persetujuan Manager Security',       'SECURITY_MANAGER',       5, 'ANY');
        v_s6 := new_step(v_chain_id, 6, 'Officer / Co-Officer Approval',      NULL,                     6, 'ANY');
        v_s7 := new_step(v_chain_id, 7, 'Badge Retrieval',                    NULL,                     7, 'ANY');

        -- Step 1 sengaja tanpa assignee: approver-nya adalah Staf Security yang
        -- dipilih pemohon, disimpan di ses_request_evaluator dan diubah menjadi
        -- baris ses_approval_task_signoff oleh ApprovalService#trigger().
        -- Kalau pemohon tidak memilih siapa pun, tahap ini DILEWATI.

        add_assignee(v_s2, 'POSITION',          v_proc_supv_pos_id, NULL,            10);
        add_assignee(v_s2, 'USER',              NULL,               v_admin_user_id, 20);

        add_assignee(v_s3, 'POSITION',          v_proc_mgr_pos_id,  NULL,            10);
        add_assignee(v_s3, 'USER',              NULL,               v_admin_user_id, 20);

        add_assignee(v_s4, 'REQUESTOR_MANAGER', NULL,               NULL,            10);
        add_assignee(v_s4, 'USER',              NULL,               v_admin_user_id, 20);

        add_assignee(v_s5, 'POSITION',          v_sec_mgr_pos_id,   NULL,            10);
        add_assignee(v_s5, 'USER',              NULL,               v_admin_user_id, 20);

        add_assignee(v_s6, 'POSITION',          v_officer_pos_id,   NULL,            10);
        add_assignee(v_s6, 'USER',              NULL,               v_admin_user_id, 20);

        -- Step 7: tanpa assignee manusia — murni gerbang checkpoint.

        -- Checkpoint hanya pada step 6 dan 7, tidak pernah pada step yang bisa
        -- dilewati: checkpoint yang beku pada task terlewati akan PENDING
        -- selamanya, dan completeCheckpointByKey memindai SELURUH task instance
        -- sehingga bisa mencocoki yatim itu dan meninggalkan gerbang aslinya.
        IF p_payment_checkpoint = 'Y' THEN
            INSERT INTO ses_approval_step_task
                (step_task_id, step_id, task_label, checkpoint_key, order_by, created_by, created_date)
            VALUES (SEQ_SES_APPROVAL_STEP_TASK.NEXTVAL, v_s6, 'Payment proof verified',
                    'PAYMENT_VERIFICATION', 10, 'SYSTEM', SYSDATE);
        END IF;
        INSERT INTO ses_approval_step_task
            (step_task_id, step_id, task_label, checkpoint_key, order_by, created_by, created_date)
        VALUES (SEQ_SES_APPROVAL_STEP_TASK.NEXTVAL, v_s6, 'Screening attended',
                'SCREENING_SCHEDULE', 20, 'SYSTEM', SYSDATE);

        INSERT INTO ses_approval_step_task
            (step_task_id, step_id, task_label, checkpoint_key, order_by, created_by, created_date)
        VALUES (SEQ_SES_APPROVAL_STEP_TASK.NEXTVAL, v_s7, 'Badge retrieval attended',
                'BADGE_RETRIEVAL_SCHEDULE', 10, 'SYSTEM', SYSDATE);

        DBMS_OUTPUT.PUT_LINE('Chain ' || p_key || ': selesai, 7 tahap serial.');
    END reshape;
BEGIN
    reshape('DEFAULT', 'N');
    reshape('PAYMENT', 'Y');
END;
/

COMMIT;

-- Pemeriksaan sesudah: harus menampilkan 7 baris per chain, dengan peran dan
-- grup terisi, satu grup per tahap.
-- SELECT c.chain_key, s.step_order, s.step_name, s.step_role,
--        s.parallel_group, s.completion_rule
-- FROM ses_approval_chain c JOIN ses_approval_step s ON s.chain_id = c.chain_id
-- WHERE c.chain_key IN ('DEFAULT','PAYMENT')
-- ORDER BY c.chain_key, s.step_order;
