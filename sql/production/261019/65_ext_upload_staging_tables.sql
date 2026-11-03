-- ============================================================
-- SES — Tabel staging unggah dokumen eksternal — Oracle
-- Run as schema owner on ELLDEV / production, AFTER 03_request_ddl.sql
-- dan 61_request_email_and_upload_token_alter.sql.
--
-- ------------------------------------------------------------
-- MENGAPA SKRIP INI ADA
-- ------------------------------------------------------------
-- Fase 9 (docs/stages/stage9_upload_via_email_link.md) membuat kontraktor
-- mengunggah langsung ke SES_REQUEST_WORKER_DOC, yaitu tabel dokumen RESMI
-- pengajuan. Berkas yang baru setengah terkumpul jadi tidak bisa dibedakan
-- dari berkas yang sudah disahkan kontraktor lewat tombol Kirim Final.
--
-- Dua tabel di bawah memisahkan keduanya:
--
--   SES_EXT_UPLOAD_REQUEST  satu baris per pengajuan yang menunggu dokumen —
--                           jembatan berisi metadata yang ditampilkan halaman
--                           unggah publik, plus jejak token dan finalisasi.
--   SES_EXT_UPLOAD_DOC      satu baris per (pekerja, jenis dokumen) yang
--                           sedang ditunggu/sudah masuk ke staging.
--
-- Pada tombol Kirim Final, RequestService#finalizeUpload memindahkan baris
-- staging ke SES_REQUEST_WORKER_DOC, menandai IS_FINALIZED = 'Y', membakar
-- token, dan baru kemudian memicu approval chain.
--
-- ------------------------------------------------------------
-- ⚠️ DUA ATURAN YANG MENJAGA KEDUA TABEL INI TIDAK SALING BERBOHONG
-- ------------------------------------------------------------
-- 1. HANYA FILE_ID — JANGAN PERNAH MENAMBAH KOLOM BLOB DI SINI.
--    Byte berkas tetap satu-satunya salinan di APP_FILE.FILE_DATA.
--    AppFileService#store menulisnya sekali; AppFileService#reassign hanya
--    mengubah APP_FILE.REF_ID saat finalisasi. Tidak ada byte yang pernah
--    disalin, jadi tidak ada dua salinan yang bisa berbeda isi. Menggandakan
--    BLOB puluhan megabita demi menyatakan "dokumen yang sama" adalah
--    kebohongan yang lebih mahal (alasan yang sama dengan stage9 §5.3).
--
-- 2. SES_REQUEST TETAP SATU-SATUNYA SUMBER KEBENARAN VALIDASI TOKEN.
--    UploadTokenService#resolve membaca SES_REQUEST.UPLOAD_TOKEN dan TIDAK
--    diubah sama sekali. Kolom UPLOAD_TOKEN/TOKEN_EXPIRY di tabel jembatan
--    ini adalah PROYEKSI yang ditulis di transaksi yang sama — dipakai untuk
--    menampilkan dan mengaudit, JANGAN PERNAH dibaca untuk memutuskan apakah
--    sebuah token sah. Dua pemeriksa token yang bisa berbeda jawaban adalah
--    lubang autentikasi, bukan redundansi.
--
-- ------------------------------------------------------------
-- CATATAN DESAIN
-- ------------------------------------------------------------
-- * Tidak ada kolom audit (CREATED_BY/UPDATED_BY). Halaman unggah diakses
--   TANPA login, jadi AuditEntityListener hanya akan menuliskan null. Entity-
--   nya @Entity biasa, bukan FullAuditEntity — mengikuti SesRequestWorkerDoc
--   yang sudah begitu, dengan alasan yang sama.
-- * FILE_ID tidak di-FK ke APP_FILE, sama seperti seluruh schema ini
--   (lihat catatan "no-DB-FK convention" di 03_request_ddl.sql).
-- * UNIQUE index pada SES_EXT_UPLOAD_DOC berbasis fungsi (CASE) supaya hanya
--   baris AKTIF yang diikat. Baris yang dihapus kontraktor di-soft-delete
--   (IS_ACTIVE='N'), dan tanpa trik CASE ini unggah-ulang dokumen yang sama
--   akan kena ORA-00001 melawan baris yang sudah dibuang.
--
-- Idempotent & aman di-rerun berkali-kali: setiap objek berpagar terhadap
-- user_tables / user_sequences / user_indexes. TIDAK ada DROP TABLE di sini —
-- skrip ini tidak boleh membuang dokumen yang sedang dikumpulkan kontraktor.
-- ============================================================

SET DEFINE OFF;
SET SERVEROUTPUT ON;

-- ---- 1. Sequences (berpagar) ----
DECLARE
    v_count NUMBER;
BEGIN
    FOR s IN (
        SELECT 'SEQ_SES_EXT_UPLOAD_REQ' AS name FROM dual
        UNION ALL
        SELECT 'SEQ_SES_EXT_UPLOAD_DOC' AS name FROM dual
    ) LOOP
        SELECT COUNT(*) INTO v_count FROM user_sequences WHERE sequence_name = s.name;
        IF v_count = 0 THEN
            EXECUTE IMMEDIATE 'CREATE SEQUENCE ' || s.name
                    || ' START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE';
            DBMS_OUTPUT.PUT_LINE('Sequence ' || s.name || ' dibuat.');
        ELSE
            DBMS_OUTPUT.PUT_LINE('Sequence ' || s.name || ' sudah ada.');
        END IF;
    END LOOP;
END;
/

-- ---- 2. SES_EXT_UPLOAD_REQUEST (jembatan, satu baris per pengajuan) ----
DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count FROM user_tables WHERE table_name = 'SES_EXT_UPLOAD_REQUEST';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE '
            CREATE TABLE ses_ext_upload_request (
                ext_request_id   NUMBER(19)    NOT NULL,
                request_id       NUMBER(19)    NOT NULL,
                request_no       VARCHAR2(30),
                type_code        VARCHAR2(30),
                type_name        VARCHAR2(200),
                recipient_email  VARCHAR2(150),
                upload_token     VARCHAR2(100),
                token_expiry     DATE,
                is_finalized     CHAR(1)       DEFAULT ''N'' NOT NULL,
                created_date     DATE          DEFAULT SYSDATE NOT NULL,
                finalized_date   DATE,
                CONSTRAINT pk_ses_ext_upload_request PRIMARY KEY (ext_request_id),
                CONSTRAINT uq_ses_ext_upload_req_rid UNIQUE (request_id),
                CONSTRAINT ck_ses_ext_upload_req_fin CHECK (is_finalized IN (''Y'', ''N'')),
                CONSTRAINT fk_ses_ext_upload_req_req FOREIGN KEY (request_id)
                    REFERENCES ses_request (request_id)
            )';
        DBMS_OUTPUT.PUT_LINE('Tabel SES_EXT_UPLOAD_REQUEST dibuat.');
    ELSE
        DBMS_OUTPUT.PUT_LINE('Tabel SES_EXT_UPLOAD_REQUEST sudah ada.');
    END IF;
END;
/

-- Token unik, tapi hanya untuk baris yang punya token. Index berbasis kolom
-- biasa sudah cukup: Oracle tidak mengindeks baris yang seluruh key-nya NULL,
-- jadi banyak baris dengan upload_token NULL tidak saling bertabrakan.
DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count FROM user_indexes WHERE index_name = 'UX_SES_EXT_UPLOAD_REQ_TOKEN';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'CREATE UNIQUE INDEX ux_ses_ext_upload_req_token
                           ON ses_ext_upload_request (upload_token)';
        DBMS_OUTPUT.PUT_LINE('Index UX_SES_EXT_UPLOAD_REQ_TOKEN dibuat.');
    ELSE
        DBMS_OUTPUT.PUT_LINE('Index UX_SES_EXT_UPLOAD_REQ_TOKEN sudah ada.');
    END IF;
END;
/

COMMENT ON TABLE  ses_ext_upload_request                 IS 'Jembatan satu-baris-per-pengajuan untuk halaman unggah dokumen eksternal (tanpa login). Diisi saat submit, ditandai finalized saat kontraktor menekan Kirim Final.';
COMMENT ON COLUMN ses_ext_upload_request.request_id      IS 'FK ke SES_REQUEST. UNIQUE: satu sesi unggah aktif per pengajuan.';
COMMENT ON COLUMN ses_ext_upload_request.request_no      IS 'Snapshot SES_REQUEST.REQUEST_NO — ditampilkan di halaman publik tanpa join.';
COMMENT ON COLUMN ses_ext_upload_request.type_name       IS 'Snapshot nama jenis pengajuan saat tautan diterbitkan.';
COMMENT ON COLUMN ses_ext_upload_request.recipient_email IS 'Alamat kontraktor yang dikirimi tautan. BUKAN email pemohon.';
COMMENT ON COLUMN ses_ext_upload_request.upload_token    IS 'PROYEKSI dari SES_REQUEST.UPLOAD_TOKEN untuk audit. JANGAN dipakai memvalidasi token — UploadTokenService#resolve hanya membaca SES_REQUEST.';
COMMENT ON COLUMN ses_ext_upload_request.is_finalized    IS 'Y setelah dokumen staging dipindahkan ke SES_REQUEST_WORKER_DOC dan approval chain dipicu.';

-- ---- 3. SES_EXT_UPLOAD_DOC (staging dokumen) ----
DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count FROM user_tables WHERE table_name = 'SES_EXT_UPLOAD_DOC';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE '
            CREATE TABLE ses_ext_upload_doc (
                ext_doc_id        NUMBER(19)    NOT NULL,
                ext_request_id    NUMBER(19)    NOT NULL,
                request_id        NUMBER(19)    NOT NULL,
                worker_id         NUMBER(19)    NOT NULL,
                doc_code          VARCHAR2(30)  NOT NULL,
                doc_name          VARCHAR2(200),
                file_id           VARCHAR2(36)  NOT NULL,
                file_name         VARCHAR2(255),
                file_content_type VARCHAR2(100),
                file_size         NUMBER(19),
                uploaded_at       DATE          DEFAULT SYSDATE NOT NULL,
                is_active         CHAR(1)       DEFAULT ''Y'' NOT NULL,
                CONSTRAINT pk_ses_ext_upload_doc PRIMARY KEY (ext_doc_id),
                CONSTRAINT ck_ses_ext_upload_doc_act CHECK (is_active IN (''Y'', ''N'')),
                CONSTRAINT fk_ses_ext_upload_doc_ext FOREIGN KEY (ext_request_id)
                    REFERENCES ses_ext_upload_request (ext_request_id)
            )';
        DBMS_OUTPUT.PUT_LINE('Tabel SES_EXT_UPLOAD_DOC dibuat.');
    ELSE
        DBMS_OUTPUT.PUT_LINE('Tabel SES_EXT_UPLOAD_DOC sudah ada.');
    END IF;
END;
/

-- Satu dokumen AKTIF per (sesi, pekerja, jenis dokumen).
--
-- ⚠️ Berbasis fungsi dengan sengaja. Unggah-ulang dokumen yang sama
-- men-soft-delete baris lama (IS_ACTIVE='N') lalu menyisipkan yang baru; index
-- biasa pada (ext_request_id, worker_id, doc_code) akan menolak sisipan itu
-- dengan ORA-00001 melawan baris yang justru sudah dibuang. CASE membuat baris
-- non-aktif seluruh key-nya NULL, dan Oracle tidak mengindeks baris seperti itu.
DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count FROM user_indexes WHERE index_name = 'UX_SES_EXT_UPLOAD_DOC_ACTIVE';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'CREATE UNIQUE INDEX ux_ses_ext_upload_doc_active
                           ON ses_ext_upload_doc (
                               CASE WHEN is_active = ''Y'' THEN ext_request_id END,
                               CASE WHEN is_active = ''Y'' THEN worker_id      END,
                               CASE WHEN is_active = ''Y'' THEN doc_code       END)';
        DBMS_OUTPUT.PUT_LINE('Index UX_SES_EXT_UPLOAD_DOC_ACTIVE dibuat.');
    ELSE
        DBMS_OUTPUT.PUT_LINE('Index UX_SES_EXT_UPLOAD_DOC_ACTIVE sudah ada.');
    END IF;
END;
/

-- Jalur baca panas halaman unggah: semua dokumen aktif satu sesi.
DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count FROM user_indexes WHERE index_name = 'IX_SES_EXT_UPLOAD_DOC_REQ';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'CREATE INDEX ix_ses_ext_upload_doc_req
                           ON ses_ext_upload_doc (request_id, is_active)';
        DBMS_OUTPUT.PUT_LINE('Index IX_SES_EXT_UPLOAD_DOC_REQ dibuat.');
    ELSE
        DBMS_OUTPUT.PUT_LINE('Index IX_SES_EXT_UPLOAD_DOC_REQ sudah ada.');
    END IF;
END;
/

COMMENT ON TABLE  ses_ext_upload_doc                   IS 'Dokumen yang sudah diunggah kontraktor tapi BELUM difinalisasi. Dipindahkan ke SES_REQUEST_WORKER_DOC oleh RequestService#finalizeUpload.';
COMMENT ON COLUMN ses_ext_upload_doc.worker_id         IS 'FK logis ke SES_REQUEST_WORKER. Diperiksa ulang terhadap request_id sebelum ditulis (IDOR guard).';
COMMENT ON COLUMN ses_ext_upload_doc.doc_code          IS 'FK logis ke SES_DOC_REQUIREMENT.DOC_CODE.';
COMMENT ON COLUMN ses_ext_upload_doc.file_id           IS 'Pointer ke APP_FILE.FILE_ID. BUKAN FK, dan TIDAK PERNAH ada BLOB di tabel ini — byte-nya satu salinan saja di APP_FILE.';
COMMENT ON COLUMN ses_ext_upload_doc.file_name         IS 'Snapshot APP_FILE.FILE_NAME supaya halaman unggah bisa menampilkan daftar tanpa menyentuh tabel BLOB.';
COMMENT ON COLUMN ses_ext_upload_doc.file_content_type IS 'Snapshot tipe hasil SNIFF magic-number, bukan header Content-Type dari klien.';
COMMENT ON COLUMN ses_ext_upload_doc.is_active         IS 'N = dihapus kontraktor lewat tombol Hapus (soft delete). Baris APP_FILE-nya dihapus betulan agar tidak jadi BLOB yatim (lihat sql/51).';

COMMIT;

BEGIN
    DBMS_OUTPUT.PUT_LINE('=== Tabel staging unggah dokumen eksternal selesai ===');
END;
/

-- ---- Verifikasi (jalankan manual bila perlu) ----
-- SELECT table_name FROM user_tables WHERE table_name LIKE 'SES_EXT_UPLOAD%';
-- SELECT index_name, uniqueness FROM user_indexes WHERE table_name LIKE 'SES_EXT_UPLOAD%';
-- SELECT constraint_name, constraint_type, search_condition FROM user_constraints
--  WHERE table_name LIKE 'SES_EXT_UPLOAD%';
-- SELECT r.request_no, r.is_finalized, COUNT(d.ext_doc_id) AS dok_aktif
--   FROM ses_ext_upload_request r
--   LEFT JOIN ses_ext_upload_doc d
--          ON d.ext_request_id = r.ext_request_id AND d.is_active = 'Y'
--  GROUP BY r.request_no, r.is_finalized;
