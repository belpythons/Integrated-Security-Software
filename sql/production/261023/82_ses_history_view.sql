-- =============================================================================
-- 82_ses_history_view.sql
--
-- SES_HISTORY_V — satu feed riwayat lintas modul, supaya /history bisa
-- dipaginasi dan dicari di DATABASE, bukan di memori aplikasi.
--
-- MASALAH YANG DISELESAIKAN
--   HistoryService menggabungkan setiap bean HistorySource di memori:
--   SES_REQUEST dan SES_STICKER_REQUEST dibaca utuh, di-merge, diurutkan,
--   lalu SELURUHNYA dirender. Terukur: 37 baris = 107 KB HTML (~2.8 KB per
--   baris), jadi pada 3.600 baris halaman ini menjadi ~10 MB yang harus
--   di-parse browser sebelum sepuluh baris pertama terlihat.
--
-- YANG TIDAK DIPINDAH KE SINI, DAN ALASANNYA
--   URL ("/request/{id}/document.pdf", "/approval/detail/{id}") TETAP dibangun
--   di Java oleh modul pemiliknya lewat HistorySource. View ini hanya
--   menyediakan kolom mentahnya (business_ref_id, approval_instance_id,
--   status). Memasukkan bentuk URL ke SQL akan memindahkan pengetahuan modul
--   ke dalam view bersama -- persis batas yang dijaga SPI itu.
--
-- KOLOM
--   history_key          'SES_REQUEST:12' -- kunci sintetis untuk @Id JPA;
--                        view tidak punya primary key dan Hibernate butuh satu
--   business_type        kode modul, dicocokkan dengan HistorySource.businessType()
--   business_ref_id      PK baris bisnisnya
--   module_name          nama tampil: nama jenis badge, atau literal untuk stiker
--   transaction_no       nomor pengajuan
--   requestor_user_id    USERNAME pemilik (lihat javadoc HistoryRow)
--   status               status modul sendiri sebagai string
--   created_date         untuk urutan terbaru-dulu
--   approval_instance_id null bila alur persetujuan belum dipicu
--
-- CATATAN
--   CREATE OR REPLACE: aman dijalankan ulang, tidak ada DROP. Menambah modul
--   riwayat baru berarti menambah satu cabang UNION ALL di sini DAN satu bean
--   HistorySource di aplikasi -- keduanya, atau modul itu tidak muncul.
-- =============================================================================

CREATE OR REPLACE VIEW ses_history_v AS
SELECT 'SES_REQUEST:' || r.request_id         AS history_key,
       'SES_REQUEST'                          AS business_type,
       r.request_id                           AS business_ref_id,
       NVL(t.type_name, r.type_code)          AS module_name,
       r.request_no                           AS transaction_no,
       r.requestor_user_id                    AS requestor_user_id,
       r.status                               AS status,
       r.created_date                         AS created_date,
       r.approval_instance_id                 AS approval_instance_id
  FROM ses_request r
  LEFT JOIN ses_request_type t ON t.type_code = r.type_code
UNION ALL
SELECT 'SES_STICKER_REQUEST:' || s.sticker_request_id,
       'SES_STICKER_REQUEST',
       s.sticker_request_id,
       'Stiker Kendaraan',
       s.request_no,
       s.requestor_user_id,
       s.status,
       s.created_date,
       s.approval_instance_id
  FROM ses_sticker_request s;

-- Verifikasi: jumlahnya harus sama dengan jumlah kedua tabel sumber
-- SELECT (SELECT COUNT(*) FROM ses_history_v)                                  AS lewat_view,
--        (SELECT COUNT(*) FROM ses_request)
--      + (SELECT COUNT(*) FROM ses_sticker_request)                            AS langsung
--   FROM dual;
--
-- Verifikasi: history_key unik (dipakai sebagai @Id oleh Hibernate)
-- SELECT COUNT(*) - COUNT(DISTINCT history_key) AS duplikat FROM ses_history_v;
