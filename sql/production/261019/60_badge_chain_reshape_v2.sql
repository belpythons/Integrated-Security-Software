-- ============================================================
-- SES — Tata ulang chain DEFAULT + PAYMENT ke bentuk 7 tahap serial — Oracle
-- Run AFTER 20_approval_chain_seed.sql dan 58_procurement_supervisor_role_alter.sql.
--
-- [261019] Production copy of sql/archive/migrations/60_badge_chain_reshape_v2.sql.
-- One change: the admin fallback assignee is looked up by username 'admin'
-- instead of the hardcoded seed id 00000000-0000-0000-0000-000000000001,
-- which does not exist on ELLPRD (its admin row has a random UUID). With
-- the hardcoded id, every fallback assignee would point at no one.
--    ⚠️ Tanpa 58, skrip ini GAGAL dengan ORA-02290 pada tahap Supervisor
--    Procurement, dan karena satu blok PL/SQL bersifat atomik, chain yang
--    sedang ditata akan ter-rollback utuh. Jalankan 58 dulu.
--
-- NOTE pada salinan production-fit ini: di dev, skrip ini (sql/60) ada
-- karena dua percobaan reshape sebelumnya (sql/56, sql/57) macet melawan
-- penjaga "sudah disunting admin" pada chain ELLDEV yang sudah lama
-- berjalan. Skrip production ini TIDAK memuat sql/56 maupun sql/57 —
-- chain yang baru saja diseed oleh 20_approval_chain_seed.sql pada skema
-- baru belum pernah disunting siapa pun, jadi tidak ada sejarah untuk
-- dilewati. Skrip ini tetap diberi nama dan nomor yang sama dengan dev
-- untuk kemudahan diff, dan logikanya tidak berubah: ia membaca BENTUK
-- TUJUAN (ada tahap PROCUREMENT_SUPERVISOR) sebagai penjaga idempotensi,
-- bukan "step_role sudah terisi" — jadi ia tetap aman dijalankan pada
-- chain yang baru diseed sekalipun.
--
-- ------------------------------------------------------------
-- BENTUK TUJUAN (docs/reference/approval flow.jpeg)
-- ------------------------------------------------------------
--   grp step  nama                                step_role               rule
--   --- ----  ----------------------------------  ----------------------  ----
--    1   1    Additional Routing (Staf Security)  SECURITY_STAFF          ALL
--    2   2    Persetujuan Supervisor Procurement  PROCUREMENT_SUPERVISOR  ANY
--    3   3    Persetujuan Manager Procurement     PROCUREMENT_MANAGER     ANY
--    4   4    Persetujuan Manager Pemohon         REQUESTOR_MANAGER       ANY
--    5   5    Persetujuan Manager Security        SECURITY_MANAGER        ANY
--    6   6    Officer / Co-Officer Approval       (none)                  ANY
--    7   7    Badge Retrieval                     (none)                  ANY
--
-- Serial penuh, satu approver pada satu waktu. Tahap 2+3 dimatikan bersama
-- oleh needs_proc_approval_flag, tahap 4 oleh needs_mgr_approval_flag, dan
-- tahap 1 dilewati bila pemohon tidak memilih additional routing.
--
-- ------------------------------------------------------------
-- APA YANG AMAN
-- ------------------------------------------------------------
-- AMAN — PENGAJUAN YANG SEDANG BERJALAN TIDAK TERPENGARUH. ses_approval_task
-- dan tabel-tabel pembekuannya (_task_assignee_def, _task_assignee,
-- _task_chkp, _task_signoff) menyimpan salinan chain milik tiap instance,
-- diambil saat trigger(). Menata ulang DEFINISI tidak bisa memindahkan tahap
-- di bawah kaki approval yang sudah jalan.
--
-- ⚠️ SATU PERUBAHAN PERILAKU YANG NYATA, KHUSUS CHAIN DEFAULT:
--    Tahap 1 DEFAULT sekarang memakai assignee_type = REQUESTOR_MANAGER,
--    yang menelusuri tangga jabatan APP_POSITION_VIEW, BUKAN report-to yang
--    disetel manual di SES_USER_EXT.
--
-- GOODS_DEFAULT dan STICKER_DEFAULT TIDAK DISENTUH — di luar scope (Badge only).
-- Chain-chain lain yang mungkin dibuat lewat GUI pada environment ini juga
-- tidak disentuh — skrip ini hanya mencari chain_key = 'DEFAULT'/'PAYMENT'
-- (atau 'BADGE_DEFAULT'/'BADGE_PAYMENT' bila 63 sudah lebih dulu berjalan).
-- ============================================================

SET SERVEROUTPUT ON

DECLARE
    v_admin_user_id    app_user.user_id%TYPE;
    v_sec_mgr_pos_id   ses_approval_assignee.pos_id%TYPE := 'E01000';
    -- Dua kursi berbeda, dalam urutan ini. EH1200 = SUPERVISOR, PROCUREMENT
    -- SUPPORT; EH1000 = MANAGER, PROCUREMENT. EH1000 juga id SECTION
    -- Procurement, itulah sebabnya salah tulis di diagram acuan terbaca masuk
    -- akal. Diverifikasi terhadap APP_POSITION_VIEW di ELLDEV 2026-10-06.
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

    PROCEDURE add_checkpoint(p_step_id IN NUMBER, p_label IN VARCHAR2,
                             p_key IN VARCHAR2, p_order IN NUMBER) IS
    BEGIN
        INSERT INTO ses_approval_step_task
            (step_task_id, step_id, task_label, checkpoint_key, order_by, created_by, created_date)
        VALUES (SEQ_SES_APPROVAL_STEP_TASK.NEXTVAL, p_step_id, p_label, p_key, p_order,
                'SYSTEM', SYSDATE);
    END add_checkpoint;

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
        v_done     NUMBER;
        v_old      NUMBER;
        v_pending  NUMBER;
        v_s1 NUMBER; v_s2 NUMBER; v_s3 NUMBER; v_s4 NUMBER;
        v_s5 NUMBER; v_s6 NUMBER; v_s7 NUMBER;
    BEGIN
        BEGIN
            SELECT chain_id INTO v_chain_id FROM ses_approval_chain
            WHERE chain_key = p_key OR chain_key = 'BADGE_' || p_key;
        EXCEPTION
            WHEN NO_DATA_FOUND THEN
                DBMS_OUTPUT.PUT_LINE('Chain ' || p_key || ': tidak ada, dilewati.');
                RETURN;
        END;

        -- Penjaganya adalah BENTUK TUJUAN, bukan "ada step_role" — supaya
        -- skrip ini tetap mau memperbaiki chain yang terlanjur ditata ke
        -- bentuk lama/antara.
        SELECT COUNT(*) INTO v_done FROM ses_approval_step
        WHERE chain_id = v_chain_id AND step_role = 'PROCUREMENT_SUPERVISOR';
        IF v_done > 0 THEN
            DBMS_OUTPUT.PUT_LINE('Chain ' || p_key || ': sudah bentuk 7 tahap serial, dilewati.');
            RETURN;
        END IF;

        SELECT COUNT(*) INTO v_old FROM ses_approval_step WHERE chain_id = v_chain_id;
        SELECT COUNT(*) INTO v_pending FROM ses_approval_instance
        WHERE chain_id = v_chain_id AND status = 'PENDING';
        DBMS_OUTPUT.PUT_LINE('Chain ' || p_key || ': menata ulang dari ' || v_old
            || ' tahap. ' || v_pending || ' instance PENDING TIDAK terpengaruh'
            || ' (memakai salinan beku masing-masing).');

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

        -- Tahap 1 sengaja tanpa assignee: approver-nya adalah Staf Security
        -- yang dipilih pemohon, disimpan di ses_request_evaluator dan diubah
        -- menjadi baris ses_approval_task_signoff oleh ApprovalService#trigger().
        -- Kalau pemohon tidak memilih siapa pun, tahap ini DILEWATI.

        -- Tiap tahap 2-6 menyimpan 'admin' sebagai cadangan tetap, supaya
        -- lubang di bagan organisasi tidak pernah membuat sebuah tahap tidak
        -- bisa ditindak siapa pun.
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

        -- Tahap 7: tanpa assignee manusia — murni gerbang checkpoint.

        -- Checkpoint hanya pada tahap 6 dan 7, tidak pernah pada tahap yang
        -- bisa dilewati: checkpoint yang beku pada task terlewati akan PENDING
        -- selamanya, dan completeCheckpointByKey memindai SELURUH task
        -- instance sehingga bisa mencocoki yatim itu dan meninggalkan gerbang
        -- aslinya tetap menunggu.
        IF p_payment_checkpoint = 'Y' THEN
            add_checkpoint(v_s6, 'Payment proof verified', 'PAYMENT_VERIFICATION', 10);
        END IF;
        add_checkpoint(v_s6, 'Screening attended',      'SCREENING_SCHEDULE',       20);
        add_checkpoint(v_s7, 'Badge Retrieval',         'BADGE_RETRIEVAL_SCHEDULE', 10);

        DBMS_OUTPUT.PUT_LINE('Chain ' || p_key || ': selesai, 7 tahap serial.');
    END reshape;
BEGIN
    -- NO_DATA_FOUND here aborts the package before any chain is touched.
    SELECT user_id INTO v_admin_user_id FROM app_user WHERE username = 'admin';
    reshape('DEFAULT', 'N');
    reshape('PAYMENT', 'Y');
END;
/

COMMIT;

-- Pemeriksaan sesudah — harus 7 baris per chain, satu grup per tahap,
-- dan PROCUREMENT_SUPERVISOR ada di tahap 2:
-- SELECT c.chain_key, s.step_order, s.step_name, s.step_role,
--        s.parallel_group, s.completion_rule
-- FROM ses_approval_chain c JOIN ses_approval_step s ON s.chain_id = c.chain_id
-- WHERE c.chain_key IN ('DEFAULT','PAYMENT','BADGE_DEFAULT','BADGE_PAYMENT')
-- ORDER BY c.chain_key, s.step_order;
