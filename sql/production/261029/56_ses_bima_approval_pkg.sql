-- 56_ses_bima_approval_pkg.sql
-- Integrasi Pending Approval SES dengan Aplikasi BIMA (Badak Integrated Mobile Application)
-- Tanggal: 2026-10-29
--
-- Menyediakan 2 objek database:
-- 1. SES_BIMA_APPROVAL_V : View antarmuka standar BIMA (9 kolom)
-- 2. PKG_SES_BIMA_APPROVAL : PL/SQL Package (Spec & Body) untuk memanggil data pending,
--                           counter badge, dan pagination.
--
-- 9 Kolom Standar BIMA:
--   1. TO_USER         VARCHAR2(6)   -- Badge approver tujuan
--   2. MOD_ID          CHAR(3)       -- 'SES'
--   3. MOD_SUB_ID      CHAR(2)       -- 'BD' (Badge/SES_REQUEST) atau 'ST' (Sticker/SES_STICKER_REQUEST)
--   4. REQ_ID          VARCHAR2(16)  -- Nomor Pengajuan (REQUEST_NO)
--   5. REQ_DESC        VARCHAR2(408) -- Deskripsi: Nama Tahap - No Pengajuan - Jenis Pengajuan (Nama Pemohon)
--   6. REQ_DATE        DATE          -- Tanggal submit pengajuan
--   7. REQ_BY          VARCHAR2(6)   -- Badge pemohon
--   8. APP_REMARK      VARCHAR2(4000)-- Remark aksi persetujuan (NULL saat status pending)
--   9. APP_REMARK_TYPE VARCHAR2(50)  -- Tipe remark (NULL saat status pending)

-- ============================================================================
-- 1. VIEW: SES_BIMA_APPROVAL_V
-- ============================================================================
CREATE OR REPLACE VIEW SES_BIMA_APPROVAL_V AS
SELECT DISTINCT
    CAST(SUBSTR(TRIM(p.approver_empl_id), 1, 6) AS VARCHAR2(6)) AS TO_USER,
    CAST('SES' AS CHAR(3))                                       AS MOD_ID,
    CAST(CASE 
        WHEN p.business_ref_type = 'SES_REQUEST' THEN 'BD' 
        WHEN p.business_ref_type = 'SES_STICKER_REQUEST' THEN 'ST' 
        ELSE 'OT' 
    END AS CHAR(2))                                              AS MOD_SUB_ID,
    CAST(COALESCE(r.request_no, s.request_no, TO_CHAR(p.business_ref_id)) AS VARCHAR2(16)) AS REQ_ID,
    CAST(SUBSTR(
        p.step_name || ' - ' || COALESCE(r.request_no, s.request_no, TO_CHAR(p.business_ref_id)) || 
        ' - ' || COALESCE(rt.type_name, st.sticker_type_name, 'Pengajuan SES') || 
        ' (' || COALESCE(ru.full_name, p.requestor_user_id) || ')',
        1, 408
    ) AS VARCHAR2(408))                                          AS REQ_DESC,
    p.req_date                                                   AS REQ_DATE,
    CAST(SUBSTR(TRIM(COALESCE(ru.username, p.requestor_user_id)), 1, 6) AS VARCHAR2(6)) AS REQ_BY,
    CAST(NULL AS VARCHAR2(4000))                                 AS APP_REMARK,
    CAST(NULL AS VARCHAR2(50))                                   AS APP_REMARK_TYPE
FROM ses_approval_pending_view p
LEFT JOIN ses_request r         ON p.business_ref_type = 'SES_REQUEST'
                                AND r.request_id = p.business_ref_id
LEFT JOIN ses_request_type rt    ON rt.type_code = r.type_code
LEFT JOIN ses_sticker_request s ON p.business_ref_type = 'SES_STICKER_REQUEST'
                                AND s.sticker_request_id = p.business_ref_id
LEFT JOIN ses_sticker_type st   ON st.sticker_type_code = s.sticker_type_code
LEFT JOIN app_user ru           ON ru.username = p.requestor_user_id
WHERE p.approver_empl_id IS NOT NULL;

COMMENT ON TABLE SES_BIMA_APPROVAL_V IS 'View antarmuka integrasi data pending approval SES untuk aplikasi BIMA.';
COMMENT ON COLUMN SES_BIMA_APPROVAL_V.TO_USER IS 'Nomor badge karyawan approver saat ini.';
COMMENT ON COLUMN SES_BIMA_APPROVAL_V.MOD_ID IS 'Identifier modul sistem SES (SES).';
COMMENT ON COLUMN SES_BIMA_APPROVAL_V.MOD_SUB_ID IS 'Identifier sub-modul (BD = Badge, ST = Sticker Kendaraan).';
COMMENT ON COLUMN SES_BIMA_APPROVAL_V.REQ_ID IS 'Nomor unik pengajuan (REQUEST_NO).';
COMMENT ON COLUMN SES_BIMA_APPROVAL_V.REQ_DESC IS 'Deskripsi ringkas pengajuan untuk daftar tugas mobile.';
COMMENT ON COLUMN SES_BIMA_APPROVAL_V.REQ_DATE IS 'Waktu pengajuan disubmit oleh pemohon.';
COMMENT ON COLUMN SES_BIMA_APPROVAL_V.REQ_BY IS 'Nomor badge karyawan pemohon.';
COMMENT ON COLUMN SES_BIMA_APPROVAL_V.APP_REMARK IS 'Catatan/remark persetujuan.';
COMMENT ON COLUMN SES_BIMA_APPROVAL_V.APP_REMARK_TYPE IS 'Tipe remark persetujuan.';

-- ============================================================================
-- 2. PACKAGE SPECIFICATION: PKG_SES_BIMA_APPROVAL
-- ============================================================================
CREATE OR REPLACE PACKAGE PKG_SES_BIMA_APPROVAL AUTHID DEFINER AS
    /**
     * Package Integrasi Data Approval SES untuk Aplikasi BIMA.
     * Mengikuti spesifikasi antarmuka tabel BIMA 9 kolom:
     * (TO_USER, MOD_ID, MOD_SUB_ID, REQ_ID, REQ_DESC, REQ_DATE, REQ_BY, APP_REMARK, APP_REMARK_TYPE)
     */

    -- Mengambil seluruh daftar pending approval atau difilter berdasarkan badge user
    FUNCTION GET_PENDING_APPROVALS(
        p_to_user IN VARCHAR2 DEFAULT NULL
    ) RETURN SYS_REFCURSOR;

    -- Mengambil jumlah (count) pending approval untuk notifikasi/badge count user
    FUNCTION GET_PENDING_COUNT(
        p_to_user IN VARCHAR2
    ) RETURN NUMBER;

    -- Mengambil detail satu approval spesifik berdasarkan nomor pengajuan (REQ_ID)
    FUNCTION GET_APPROVAL_BY_REQ_ID(
        p_req_id IN VARCHAR2
    ) RETURN SYS_REFCURSOR;

    -- Mengambil daftar pending approval dengan pagination (offset & limit)
    FUNCTION GET_PENDING_APPROVALS_PAGED(
        p_to_user IN VARCHAR2 DEFAULT NULL,
        p_offset  IN NUMBER DEFAULT 0,
        p_limit   IN NUMBER DEFAULT 20
    ) RETURN SYS_REFCURSOR;

END PKG_SES_BIMA_APPROVAL;
/

-- ============================================================================
-- 3. PACKAGE BODY: PKG_SES_BIMA_APPROVAL
-- ============================================================================
CREATE OR REPLACE PACKAGE BODY PKG_SES_BIMA_APPROVAL AS

    FUNCTION GET_PENDING_APPROVALS(
        p_to_user IN VARCHAR2 DEFAULT NULL
    ) RETURN SYS_REFCURSOR IS
        v_cur SYS_REFCURSOR;
    BEGIN
        IF p_to_user IS NULL OR TRIM(p_to_user) IS NULL THEN
            OPEN v_cur FOR
                SELECT *
                FROM SES_BIMA_APPROVAL_V
                ORDER BY REQ_DATE DESC;
        ELSE
            OPEN v_cur FOR
                SELECT *
                FROM SES_BIMA_APPROVAL_V
                WHERE TO_USER = TRIM(p_to_user)
                ORDER BY REQ_DATE DESC;
        END IF;
        RETURN v_cur;
    END GET_PENDING_APPROVALS;

    FUNCTION GET_PENDING_COUNT(
        p_to_user IN VARCHAR2
    ) RETURN NUMBER IS
        v_count NUMBER := 0;
    BEGIN
        IF p_to_user IS NOT NULL AND TRIM(p_to_user) IS NOT NULL THEN
            SELECT COUNT(*)
            INTO v_count
            FROM SES_BIMA_APPROVAL_V
            WHERE TO_USER = TRIM(p_to_user);
        ELSE
            SELECT COUNT(*)
            INTO v_count
            FROM SES_BIMA_APPROVAL_V;
        END IF;
        RETURN v_count;
    EXCEPTION
        WHEN OTHERS THEN
            RETURN 0;
    END GET_PENDING_COUNT;

    FUNCTION GET_APPROVAL_BY_REQ_ID(
        p_req_id IN VARCHAR2
    ) RETURN SYS_REFCURSOR IS
        v_cur SYS_REFCURSOR;
    BEGIN
        OPEN v_cur FOR
            SELECT *
            FROM SES_BIMA_APPROVAL_V
            WHERE REQ_ID = TRIM(p_req_id);
        RETURN v_cur;
    END GET_APPROVAL_BY_REQ_ID;

    FUNCTION GET_PENDING_APPROVALS_PAGED(
        p_to_user IN VARCHAR2 DEFAULT NULL,
        p_offset  IN NUMBER DEFAULT 0,
        p_limit   IN NUMBER DEFAULT 20
    ) RETURN SYS_REFCURSOR IS
        v_cur SYS_REFCURSOR;
        v_off NUMBER := NVL(p_offset, 0);
        v_lim NUMBER := NVL(p_limit, 20);
    BEGIN
        IF v_off < 0 THEN
            v_off := 0;
        END IF;
        IF v_lim <= 0 THEN
            v_lim := 20;
        END IF;

        IF p_to_user IS NULL OR TRIM(p_to_user) IS NULL THEN
            OPEN v_cur FOR
                SELECT *
                FROM SES_BIMA_APPROVAL_V
                ORDER BY REQ_DATE DESC
                OFFSET v_off ROWS FETCH NEXT v_lim ROWS ONLY;
        ELSE
            OPEN v_cur FOR
                SELECT *
                FROM SES_BIMA_APPROVAL_V
                WHERE TO_USER = TRIM(p_to_user)
                ORDER BY REQ_DATE DESC
                OFFSET v_off ROWS FETCH NEXT v_lim ROWS ONLY;
        END IF;
        RETURN v_cur;
    END GET_PENDING_APPROVALS_PAGED;

END PKG_SES_BIMA_APPROVAL;
/
