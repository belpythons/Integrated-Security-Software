-- ============================================================
-- SES — Status screening per pekerja — Oracle
-- Run as schema owner on ELLDEV / production, AFTER 03_request_ddl.sql.
--
-- ------------------------------------------------------------
-- MENGAPA SKRIP INI ADA
-- ------------------------------------------------------------
-- Screening dicatat PER PENGAJUAN, bukan per pekerja. SES_REQUEST_SCHEDULE
-- berkunci pada (request_id, schedule_type) dengan UNIQUE, jadi satu pengajuan
-- berisi enam pekerja hanya punya satu tanggal dan satu hasil
-- ATTENDED / NO_SHOW untuk keenamnya.
--
-- Kenyataannya tidak begitu: dari enam pekerja yang dijadwalkan, biasanya ada
-- yang datang, ada yang tidak, dan ada yang datang tapi tidak lulus. Model
-- lama memaksa petugas memilih satu dari dua jawaban yang sama-sama salah —
-- dan karena NO_SHOW pada screening MENOLAK SELURUH PENGAJUAN
-- (SchedulingService.markNoShow), memilih yang jujur berarti membuang lima
-- pekerja yang hadir.
--
-- Kolom ini membuat hasilnya per orang.
--
-- ------------------------------------------------------------
-- NILAI
-- ------------------------------------------------------------
--   PENDING   belum diperiksa (default; semua baris lama jadi ini)
--   ATTENDED  hadir dan lolos screening  -> ikut ke pengambilan badge
--   NO_SHOW   tidak hadir                -> tidak dicetakkan badge
--   FAILED    hadir tapi tidak lulus     -> tidak dicetakkan badge
--
-- NO_SHOW dan FAILED dipisah walaupun konsekuensi badge-nya sama. Keduanya
-- fakta yang berbeda tentang orang yang berbeda, dan satu-satunya tempat
-- perbedaan itu tercatat adalah kolom ini — menggabungkannya jadi 'REJECTED'
-- akan membuang alasannya untuk menghemat satu nilai enum.
--
-- 'ATTENDED' dan 'NO_SHOW' sengaja memakai ejaan yang SAMA dengan
-- ScheduleStatus pada SES_REQUEST_SCHEDULE. Keduanya memang menjawab
-- pertanyaan yang sama pada tingkat yang berbeda (satu pengajuan vs satu
-- orang), dan ejaan berbeda untuk konsep yang sama adalah hal yang nanti
-- dibandingkan orang lalu salah.
--
-- ------------------------------------------------------------
-- CATATAN
-- ------------------------------------------------------------
-- ⚠️ VARCHAR2(20) sudah cukup: nilai terpanjang 'ATTENDED' (8 karakter).
--    Ruangnya dilebihkan dengan sengaja — menambah nilai nanti tidak boleh
--    butuh ALTER kolom, hanya ALTER constraint.
--
-- ⚠️ saveAndSubmit menghapus-dan-menyisipkan ulang baris SES_REQUEST_WORKER
--    pada setiap edit-resubmit, jadi kolom ini kembali ke PENDING saat itu.
--    Tidak berbahaya hari ini — pengajuan sudah tidak bisa diedit pemohon
--    begitu screening terjangkau — tapi ini persis jenis hal yang berhenti
--    tidak-berbahaya secara diam-diam, jadi dicatat di sini dan di
--    docs/stages/stage15_worker_screening_checklist.md.
--
-- Idempotent & aman di-rerun berkali-kali. Kolom DAN constraint-nya berpagar
-- di satu cabang yang sama, jadi tidak ada DROP CONSTRAINT yang gagal pada
-- rerun (berbeda dengan 21_... dan 61_..., yang bagian constraint-nya memang
-- tidak re-runnable).
-- ============================================================

SET DEFINE OFF;
SET SERVEROUTPUT ON;

DECLARE
    v_col_exists NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_col_exists
    FROM user_tab_cols
    WHERE table_name = 'SES_REQUEST_WORKER' AND column_name = 'SCREENING_STATUS';

    IF v_col_exists = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE ses_request_worker ADD '
                       || '(screening_status VARCHAR2(20) DEFAULT ''PENDING'' NOT NULL)';
        EXECUTE IMMEDIATE 'ALTER TABLE ses_request_worker ADD CONSTRAINT ck_ses_worker_scr_status '
                       || 'CHECK (screening_status IN (''PENDING'', ''ATTENDED'', ''NO_SHOW'', ''FAILED''))';
        DBMS_OUTPUT.PUT_LINE('Kolom SCREENING_STATUS ditambahkan ke SES_REQUEST_WORKER.');
    ELSE
        DBMS_OUTPUT.PUT_LINE('Kolom SCREENING_STATUS sudah ada.');
    END IF;
END;
/

COMMENT ON COLUMN ses_request_worker.screening_status IS 'Hasil screening per pekerja: PENDING (belum diperiksa) | ATTENDED (hadir & lolos, ikut ke pengambilan badge) | NO_SHOW (tidak hadir) | FAILED (hadir, tidak lulus). Hanya ATTENDED yang dicetakkan badge.';

-- Jalur baca panas: daftar pekerja satu pengajuan beserta statusnya.
DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count FROM user_indexes WHERE index_name = 'IX_SES_WORKER_SCR_STATUS';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'CREATE INDEX ix_ses_worker_scr_status '
                       || 'ON ses_request_worker (request_id, screening_status)';
        DBMS_OUTPUT.PUT_LINE('Index IX_SES_WORKER_SCR_STATUS dibuat.');
    ELSE
        DBMS_OUTPUT.PUT_LINE('Index IX_SES_WORKER_SCR_STATUS sudah ada.');
    END IF;
END;
/

COMMIT;

BEGIN
    DBMS_OUTPUT.PUT_LINE('=== Status screening per pekerja selesai ===');
END;
/

-- ---- Verifikasi (jalankan manual bila perlu) ----
-- SELECT column_name, data_type, data_length, nullable, data_default
--   FROM user_tab_columns
--  WHERE table_name = 'SES_REQUEST_WORKER' AND column_name = 'SCREENING_STATUS';
--
-- SELECT constraint_name, search_condition FROM user_constraints
--  WHERE constraint_name = 'CK_SES_WORKER_SCR_STATUS';
--
-- Sebaran status per pengajuan yang sedang / sudah screening:
-- SELECT r.request_no, w.screening_status, COUNT(*) AS jumlah
--   FROM ses_request_worker w JOIN ses_request r ON r.request_id = w.request_id
--  GROUP BY r.request_no, w.screening_status
--  ORDER BY r.request_no, w.screening_status;
