-- ============================================================
-- SES — CONTRACT_V: a live view over CARSIS.CONTRACT_V
-- (see docs/ldap-kpi-alignment-plan.md Bagian 3).
--
-- This schema must already hold a direct SELECT grant on CARSIS.CONTRACT_V
-- before running this script.
--
-- NOT a plain synonym: CARSIS.CONTRACT_V's raw data can carry rows with a
-- NULL NO_KONTRAK and exact-duplicate-key rows. ContractV.noKontrak is this
-- table's JPA @Id; loading duplicate/null-keyed rows in one query lets
-- Hibernate's persistence-context identity map silently collapse/scramble
-- rows. This view applies a dedup (first row per NO_KONTRAK, nulls
-- excluded) on top of the live CARSIS source, so CONTRACT_V stays both
-- live AND Hibernate-safe.
--
-- ContractV.java / ContractVRepository need NO code change: both use the
-- unqualified name "CONTRACT_V", which resolves to whatever object by
-- that name exists in the connection's default schema.
-- spring.jpa.hibernate.ddl-auto=none means no schema validation runs at
-- boot to trip over the object-type change.
--
-- If this schema has no local CONTRACT_V table (a fresh deploy never
-- creates one — that was a dev-only snapshot, see sql/README.md), the
-- DROP below is a guarded no-op.
-- ============================================================

BEGIN
    FOR t IN (SELECT table_name FROM user_tables WHERE table_name = 'CONTRACT_V') LOOP
        EXECUTE IMMEDIATE 'DROP TABLE CONTRACT_V PURGE';
    END LOOP;
END;
/

CREATE OR REPLACE VIEW CONTRACT_V AS
SELECT NO_KONTRAK, COMPANY, JUDUL_PEKERJAAN, TANGGAL_MULAI, TANGGAL_SELESAI
FROM (
  SELECT c.*, ROW_NUMBER() OVER (PARTITION BY NO_KONTRAK ORDER BY NO_KONTRAK) rn
  FROM CARSIS.CONTRACT_V c
  WHERE NO_KONTRAK IS NOT NULL
)
WHERE rn = 1;
