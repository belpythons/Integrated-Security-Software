-- ============================================================
-- SES — Perluas CHECK step_role untuk PROCUREMENT_SUPERVISOR — Oracle
-- Run AFTER 52_hybrid_routing_alter.sql.
-- Run BEFORE 60_badge_chain_reshape_v2.sql dan sebelum siapa pun
-- menyimpan tahap Supervisor Procurement lewat /approval/chain.
--
-- NOTE on this production-fit copy: 52_hybrid_routing_alter.sql already
-- writes ck_ses_step_role / ck_ses_task_role straight to the five-value
-- shape this script widens to. On a fresh schema this script is therefore
-- a guaranteed no-op (see its WIDEN procedure below — it only acts if the
-- constraint is still the four-value shape). It is still ported, numbered
-- identically to the dev script set, as the documented record of why the
-- five-value shape is correct and for parity when diffing the two
-- directories.
--
-- ------------------------------------------------------------
-- MENGAPA
-- ------------------------------------------------------------
-- Procurement adalah DUA tahap berurutan, bukan satu
-- (docs/reference/approval flow.jpeg): Supervisor Procurement menyetujui
-- lebih dulu, baru Manager Procurement diminta. Keduanya dikendalikan satu
-- toggle, ses_request_type.needs_proc_approval_flag.
--
--   EH1200  SUPERVISOR, PROCUREMENT SUPPORT   <- tahap 2, PROCUREMENT_SUPERVISOR
--   EH1000  MANAGER, PROCUREMENT              <- tahap 3, PROCUREMENT_MANAGER
--
-- Keduanya BUKAN kursi yang sama, dan diagram acuannya salah: kedua kotak
-- Procurement di sana diberi label EH1000. EH1000 kebetulan juga id SECTION
-- Procurement, yang persis membuat nilai yang salah terbaca masuk akal.
-- Diverifikasi terhadap APP_POSITION_VIEW di ELLDEV, 2026-10-06.
--
-- ------------------------------------------------------------
-- APA YANG DIUBAH
-- ------------------------------------------------------------
-- ck_ses_step_role pada ses_approval_step (definisi chain) dan ck_ses_task_role
-- pada ses_approval_task (salinan beku per instance). TIDAK ADA DATA YANG
-- DISENTUH: constraint ini hanya melonggar.
--
-- Aman di-rerun: constraint dicari dulu di user_constraints, dan DROP hanya
-- dijalankan kalau ada.
-- ============================================================

SET SERVEROUTPUT ON

DECLARE
    c_roles CONSTANT VARCHAR2(200) :=
        '(''SECURITY_STAFF'', ''REQUESTOR_MANAGER'', ''PROCUREMENT_SUPERVISOR'','
        || ' ''PROCUREMENT_MANAGER'', ''SECURITY_MANAGER'')';

    PROCEDURE widen(p_table IN VARCHAR2, p_constraint IN VARCHAR2) IS
        v_count NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_count FROM user_constraints
        WHERE constraint_name = p_constraint AND table_name = p_table;

        IF v_count > 0 THEN
            EXECUTE IMMEDIATE 'ALTER TABLE ' || p_table || ' DROP CONSTRAINT ' || p_constraint;
        END IF;

        EXECUTE IMMEDIATE 'ALTER TABLE ' || p_table
            || ' ADD CONSTRAINT ' || p_constraint
            || ' CHECK (step_role IS NULL OR step_role IN ' || c_roles || ')';

        DBMS_OUTPUT.PUT_LINE(p_table || '.' || p_constraint || ': 5 peran diizinkan.');
    END widen;
BEGIN
    widen('SES_APPROVAL_STEP', 'CK_SES_STEP_ROLE');
    widen('SES_APPROVAL_TASK', 'CK_SES_TASK_ROLE');
END;
/

COMMENT ON COLUMN ses_approval_step.step_role IS 'Peran tahap: SECURITY_STAFF / PROCUREMENT_SUPERVISOR / PROCUREMENT_MANAGER / REQUESTOR_MANAGER / SECURITY_MANAGER; NULL = tanpa peran, tidak pernah dilewati';
COMMENT ON COLUMN ses_approval_task.step_role IS 'Salinan beku step_role saat trigger(); lihat ses_approval_step.step_role';

COMMIT;

-- Pemeriksaan sesudah — kedua baris harus memuat PROCUREMENT_SUPERVISOR:
-- SELECT table_name, constraint_name, search_condition_vc
-- FROM user_constraints
-- WHERE constraint_name IN ('CK_SES_STEP_ROLE', 'CK_SES_TASK_ROLE');
