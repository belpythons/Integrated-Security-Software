-- ============================================================
-- SES — Pemetaan jenis pengajuan berbayar ke rantai ber-checkpoint bayar — Oracle
-- Run as schema owner on ELLDEV / production, AFTER 20_approval_chain_seed.sql
-- dan 63_badge_naming_alignment.sql.
--
-- ------------------------------------------------------------
-- MENGAPA SKRIP INI ADA
-- ------------------------------------------------------------
-- NEEDS_PAYMENT_FLAG pada SES_REQUEST_TYPE sama sekali TIDAK memengaruhi
-- pemilihan rantai persetujuan. Rantai dipilih murni oleh SES_FORM_APPROVAL_MAP
-- berdasarkan kode jenis pengajuan (match_key), dan sql/README.md mencatat
-- pemetaan itu "deliberately left unseeded for the admin to wire at
-- /approval/map".
--
-- Akibatnya: jenis pengajuan berbayar yang dipetakan ke BADGE_DEFAULT (rantai
-- tanpa checkpoint PAYMENT_VERIFICATION) langsung melaju ke tahap screening
-- TANPA ADA YANG MEMBACA BUKTI BAYAR. Tidak ada yang berteriak:
-- PaymentService.approve memanggil completeCheckpointByKey, yang tidak
-- menemukan checkpoint apa pun lalu WARN-no-op — jejak satu-satunya adalah
-- baris log yang tidak dibaca siapa pun.
--
-- Skrip ini memperbaiki DATA-nya. Penjaga KODE-nya terpisah dan sudah
-- dipasang: ApprovalService.trigger menolak memulai approval untuk jenis
-- berbayar yang rantainya tidak memuat checkpoint itu
-- (ApprovalConfigurationException, sebelum satu baris pun ditulis). Keduanya
-- diperlukan — skrip ini membereskan yang sudah salah, penjaganya mencegah
-- yang berikutnya.
--
-- ------------------------------------------------------------
-- APA YANG DILAKUKAN
-- ------------------------------------------------------------
-- Untuk setiap SES_REQUEST_TYPE dengan NEEDS_PAYMENT_FLAG = 'Y':
--   * kalau belum ada override di SES_FORM_APPROVAL_MAP  -> INSERT ke chain berbayar
--   * kalau sudah ada override tapi ke chain tanpa checkpoint -> UPDATE
--   * kalau sudah benar -> dibiarkan
--
-- "Chain berbayar" TIDAK di-hardcode sebagai chain_id 2 atau chain_key
-- 'BADGE_PAYMENT'. Ia dicari berdasarkan sifat yang sebenarnya dipedulikan:
-- rantai aktif yang punya step dengan checkpoint_key = 'PAYMENT_VERIFICATION'.
-- Pelajaran yang sama dengan sql/README.md baris 50 (id yang di-hardcode mati
-- kena ORA-00001) — dan di sini lebih kuat lagi: nama rantai sudah pernah
-- berganti sekali (DEFAULT/PAYMENT -> BADGE_DEFAULT/BADGE_PAYMENT di sql/63).
--
-- ⚠️ Skrip ini TIDAK menyentuh jenis pengajuan yang NEEDS_PAYMENT_FLAG = 'N'.
-- Kesalahan sebaliknya — jenis tak-berbayar dipetakan ke rantai berbayar —
-- membekukan checkpoint PAYMENT_VERIFICATION tanpa ada baris pembayaran, dan
-- hanya bisa dibereskan dengan Mark Done / Skip manual. Itu keputusan
-- administratif, bukan sesuatu yang boleh ditebak oleh skrip migrasi.
--
-- Idempotent & aman di-rerun berkali-kali.
-- ============================================================

SET DEFINE OFF;
SET SERVEROUTPUT ON;

DECLARE
    v_chain_id   NUMBER;
    v_inserted   NUMBER := 0;
    v_updated    NUMBER := 0;
    v_ok         NUMBER := 0;
BEGIN
    -- Rantai berbayar = rantai AKTIF yang membawa checkpoint PAYMENT_VERIFICATION.
    -- MIN() supaya hasilnya deterministik kalau seseorang membuat dua.
    BEGIN
        SELECT MIN(c.chain_id)
          INTO v_chain_id
          FROM ses_approval_chain c
          JOIN ses_approval_step s      ON s.chain_id = c.chain_id
          JOIN ses_approval_step_task t ON t.step_id  = s.step_id
         WHERE c.active_flag = 'Y'
           AND t.checkpoint_key = 'PAYMENT_VERIFICATION';
    EXCEPTION
        WHEN NO_DATA_FOUND THEN
            v_chain_id := NULL;
    END;

    IF v_chain_id IS NULL THEN
        -- Tidak ada rantai berbayar sama sekali. Membuatnya di sini akan
        -- menebak bentuk rantai persetujuan sebuah organisasi, jadi skrip ini
        -- berhenti dan mengatakannya dengan jelas.
        DBMS_OUTPUT.PUT_LINE('!! TIDAK ADA rantai aktif yang memuat checkpoint PAYMENT_VERIFICATION.');
        DBMS_OUTPUT.PUT_LINE('!! Jalankan 20_approval_chain_seed.sql, atau tambahkan checkpoint');
        DBMS_OUTPUT.PUT_LINE('!! PAYMENT_VERIFICATION pada sebuah rantai di /approval/chain,');
        DBMS_OUTPUT.PUT_LINE('!! lalu jalankan skrip ini lagi. Tidak ada perubahan yang dilakukan.');
        RETURN;
    END IF;

    DBMS_OUTPUT.PUT_LINE('Rantai berbayar terpilih: chain_id = ' || v_chain_id);

    FOR t IN (
        SELECT rt.type_code
          FROM ses_request_type rt
         WHERE rt.needs_payment_flag = 'Y'
         ORDER BY rt.type_code
    ) LOOP
        DECLARE
            v_map_id      NUMBER;
            v_mapped      NUMBER;
            v_has_chkp    NUMBER;
        BEGIN
            SELECT MAX(map_id) INTO v_map_id
              FROM ses_form_approval_map
             WHERE form_code = 'SES_REQUEST' AND match_key = t.type_code;

            IF v_map_id IS NULL THEN
                -- Belum ada override: jenis ini ikut rantai default form.
                INSERT INTO ses_form_approval_map
                       (map_id, form_code, match_key, chain_id, active_flag, created_by, created_date)
                VALUES (NVL((SELECT MAX(map_id) + 1 FROM ses_form_approval_map), 1),
                        'SES_REQUEST', t.type_code, v_chain_id, 'Y', 'SYSTEM', SYSDATE);
                v_inserted := v_inserted + 1;
                DBMS_OUTPUT.PUT_LINE('  + ' || t.type_code || ' -> chain ' || v_chain_id || ' (baru)');
            ELSE
                SELECT chain_id INTO v_mapped
                  FROM ses_form_approval_map WHERE map_id = v_map_id;

                SELECT COUNT(*) INTO v_has_chkp
                  FROM ses_approval_step s
                  JOIN ses_approval_step_task st ON st.step_id = s.step_id
                 WHERE s.chain_id = v_mapped
                   AND st.checkpoint_key = 'PAYMENT_VERIFICATION';

                IF v_has_chkp = 0 THEN
                    UPDATE ses_form_approval_map
                       SET chain_id = v_chain_id, active_flag = 'Y',
                           updated_by = 'SYSTEM', updated_date = SYSDATE
                     WHERE map_id = v_map_id;
                    v_updated := v_updated + 1;
                    DBMS_OUTPUT.PUT_LINE('  ~ ' || t.type_code || ' -> chain ' || v_chain_id
                                         || ' (dialihkan dari ' || v_mapped || ', tanpa checkpoint bayar)');
                ELSE
                    v_ok := v_ok + 1;
                    DBMS_OUTPUT.PUT_LINE('  = ' || t.type_code || ' sudah benar (chain ' || v_mapped || ')');
                END IF;
            END IF;
        END;
    END LOOP;

    DBMS_OUTPUT.PUT_LINE('Ringkasan: ' || v_inserted || ' baru, ' || v_updated
                         || ' dialihkan, ' || v_ok || ' sudah benar.');
END;
/

COMMIT;

BEGIN
    DBMS_OUTPUT.PUT_LINE('=== Pemetaan jenis pengajuan berbayar selesai ===');
END;
/

-- ---- Verifikasi (jalankan manual) ----
-- Setiap baris hasil query ini adalah pengajuan berbayar yang MASIH bisa
-- lolos tanpa verifikasi bayar. Harus kosong.
--
-- SELECT rt.type_code, rt.type_name, c.chain_key, c.chain_name
--   FROM ses_request_type rt
--   LEFT JOIN ses_form_approval_map m
--          ON m.form_code = 'SES_REQUEST' AND m.match_key = rt.type_code AND m.active_flag = 'Y'
--   LEFT JOIN ses_form_approval_map d
--          ON d.form_code = 'SES_REQUEST' AND d.match_key IS NULL AND d.active_flag = 'Y'
--   LEFT JOIN ses_approval_chain c
--          ON c.chain_id = NVL(m.chain_id, d.chain_id)
--  WHERE rt.needs_payment_flag = 'Y'
--    AND NOT EXISTS (SELECT 1 FROM ses_approval_step s
--                      JOIN ses_approval_step_task st ON st.step_id = s.step_id
--                     WHERE s.chain_id = c.chain_id
--                       AND st.checkpoint_key = 'PAYMENT_VERIFICATION');
