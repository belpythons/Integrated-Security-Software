package com.badaklng.request.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.persistence.Column;
import javax.persistence.Lob;
import javax.persistence.Table;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The invariants that keep the two external-upload staging tables from
 * drifting away from {@code sql/65_ext_upload_staging_tables.sql} and from
 * each other.
 *
 * <p>No Spring context on purpose. {@code ddl-auto=none}, so Hibernate never
 * validates these mappings against Oracle at boot — a column that does not
 * exist, or a length the DDL does not allow, surfaces as an ORA-00904 or an
 * ORA-12899 on a contractor's upload rather than on anyone's build. These are
 * reflection checks plus a read of the migration file, which is the most that
 * can be proven without a database.</p>
 *
 * <p>The BLOB assertion is the one worth keeping forever. The whole design
 * rests on the staging table holding a {@code file_id} pointer and nothing
 * else: the bytes stay as a single copy in {@code app_file}, and finalisation
 * is a {@code ref_id} repoint rather than a copy. Someone "simplifying" this
 * later by storing the bytes in the staging row would create two stores that
 * can hold different bytes, which is the exact failure the pointer exists to
 * make impossible.</p>
 */
class ExtUploadStagingShapeTest {

    private static Path resolveMigration(String name) {
        Path p = Paths.get("sql", name);
        if (Files.exists(p)) return p;
        p = Paths.get("..", "sql", name);
        if (Files.exists(p)) return p;
        p = Paths.get("sql", "archive", "migrations", name);
        if (Files.exists(p)) return p;
        p = Paths.get("..", "sql", "archive", "migrations", name);
        if (Files.exists(p)) return p;
        return Paths.get("sql", name);
    }

    private static Path resolveProductionMigration(String name) {
        Path p = Paths.get("sql", "production", name);
        if (Files.exists(p)) return p;
        p = Paths.get("..", "sql", "production", name);
        if (Files.exists(p)) return p;
        p = Paths.get("sql", "archive", "migrations", name);
        if (Files.exists(p)) return p;
        p = Paths.get("..", "sql", "archive", "migrations", name);
        if (Files.exists(p)) return p;
        return Paths.get("sql", "production", name);
    }

    private static final Path MIGRATION = resolveMigration("65_ext_upload_staging_tables.sql");

    private static String migration() throws Exception {
        assertTrue(Files.exists(MIGRATION), MIGRATION + " is missing — the entities map nothing");
        return new String(Files.readAllBytes(MIGRATION), StandardCharsets.UTF_8);
    }

    /**
     * The migration with documentation stripped: both {@code --} comment lines
     * and {@code COMMENT ON} statements, whose payload is a quoted string of
     * prose rather than schema.
     *
     * <p>Needed by the two prohibition checks below. This script documents at
     * length <em>why</em> it declares no BLOB column and drops no table, so a
     * naive search for those words matches the sentences forbidding them and
     * fails on a perfectly correct script. What the checks mean to ask is
     * whether the <b>statements</b> do it.</p>
     */
    private static String migrationDdl() throws Exception {
        StringBuilder ddl = new StringBuilder();
        for (String line : migration().split("\\r?\\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("--") || trimmed.toUpperCase().startsWith("COMMENT ON")) {
                continue;
            }
            ddl.append(line).append('\n');
        }
        return ddl.toString();
    }

    // ---------------------------------------------------------------
    // The rule the whole design rests on
    // ---------------------------------------------------------------

    @Test
    @DisplayName("Neither staging entity carries a BLOB — file bytes live in app_file only")
    void noBlobInStaging() throws Exception {
        for (Class<?> type : new Class<?>[]{SesExtUploadDoc.class, SesExtUploadRequest.class}) {
            for (Field f : type.getDeclaredFields()) {
                assertFalse(f.isAnnotationPresent(Lob.class),
                        type.getSimpleName() + "." + f.getName() + " is a @Lob. The staging table holds a"
                                + " file_id pointer and nothing else; a second copy of the bytes is a second"
                                + " copy that can disagree.");
                assertFalse(f.getType() == byte[].class,
                        type.getSimpleName() + "." + f.getName() + " is a byte[] — same objection as @Lob.");
            }
        }
        assertFalse(migrationDdl().toUpperCase().contains(" BLOB"),
                "sql/65 must not declare a BLOB column either");
    }

    @Test
    @DisplayName("The staging doc points at app_file with a 36-char id")
    void fileIdIsAUuidPointer() throws Exception {
        Column c = SesExtUploadDoc.class.getDeclaredField("fileId").getAnnotation(Column.class);
        assertEquals("file_id", c.name());
        assertEquals(36, c.length(), "app_file.file_id is a 36-character UUID");
        assertFalse(c.nullable(), "a staging row with no file is not a staging row");
    }

    // ---------------------------------------------------------------
    // Token authority
    // ---------------------------------------------------------------

    @Test
    @DisplayName("Nothing can look a request up by the bridge's token copy")
    void bridgeOffersNoTokenFinder() throws Exception {
        // UploadTokenService.resolve reads SES_REQUEST and must stay the only
        // token authority. A findByUploadToken on the bridge repository is how
        // a future caller would authenticate against the projection instead —
        // two checkers that can disagree is an authentication hole, not
        // redundancy. This asserts the finder was never added.
        Class<?> repo = Class.forName("com.badaklng.request.repository.SesExtUploadRequestRepository");
        for (java.lang.reflect.Method m : repo.getDeclaredMethods()) {
            assertFalse(m.getName().toLowerCase().contains("uploadtoken"),
                    "SesExtUploadRequestRepository." + m.getName() + " resolves a token against the"
                            + " projection; token validation belongs to UploadTokenService against SES_REQUEST.");
        }
    }

    // ---------------------------------------------------------------
    // Flags and defaults
    // ---------------------------------------------------------------

    @Test
    @DisplayName("Both flags default to the safe value and read back as booleans")
    void flagsDefaultSafely() {
        SesExtUploadDoc doc = new SesExtUploadDoc();
        assertEquals("Y", doc.getIsActive(), "a freshly uploaded document is active");
        assertTrue(doc.isActiveFlag());
        doc.setIsActive("N");
        assertFalse(doc.isActiveFlag());

        SesExtUploadRequest bridge = new SesExtUploadRequest();
        assertEquals("N", bridge.getIsFinalized(),
                "a new upload session must NOT start finalised — the unsafe default here would skip the"
                        + " document transfer and trigger an approval over an empty document set");
        assertFalse(bridge.isFinalizedFlag());
        bridge.setIsFinalized("Y");
        assertTrue(bridge.isFinalizedFlag());
    }

    @Test
    @DisplayName("Timestamps are stamped in the constructor, since there is no audit listener")
    void timestampsAreSelfSet() {
        // These tables carry no audit columns: the upload page has no
        // principal, so AuditEntityListener would only write nulls. That makes
        // the entity itself responsible for the one date that is NOT NULL.
        assertNotNull(new SesExtUploadDoc().getUploadedAt());
        assertNotNull(new SesExtUploadRequest().getCreatedDate());

        Date before = new Date(System.currentTimeMillis() - 1000);
        assertTrue(new SesExtUploadDoc().getUploadedAt().after(before));
    }

    @Test
    @DisplayName("Neither entity extends FullAuditEntity")
    void noAuditSuperclass() {
        assertEquals(Object.class, SesExtUploadDoc.class.getSuperclass(),
                "there is no logged-in user on the upload page; audit columns would be all nulls");
        assertEquals(Object.class, SesExtUploadRequest.class.getSuperclass());
    }

    // ---------------------------------------------------------------
    // Entity <-> DDL agreement
    // ---------------------------------------------------------------

    @Test
    @DisplayName("Table and sequence names match the migration exactly")
    void namesMatchTheMigration() throws Exception {
        String sql = migration().toLowerCase();

        assertEquals("ses_ext_upload_request", SesExtUploadRequest.class.getAnnotation(Table.class).name());
        assertEquals("ses_ext_upload_doc", SesExtUploadDoc.class.getAnnotation(Table.class).name());

        assertTrue(sql.contains("create table ses_ext_upload_request"));
        assertTrue(sql.contains("create table ses_ext_upload_doc"));
        // Sequence names are the half that cannot be caught by a smoke test:
        // a wrong one throws only when a row is first inserted.
        assertTrue(sql.contains("seq_ses_ext_upload_req"), "SEQ_SES_EXT_UPLOAD_REQ must be created");
        assertTrue(sql.contains("seq_ses_ext_upload_doc"), "SEQ_SES_EXT_UPLOAD_DOC must be created");
    }

    @Test
    @DisplayName("Every mapped column exists in the migration")
    void everyColumnIsInTheMigration() throws Exception {
        String sql = migration().toLowerCase();
        for (Class<?> type : new Class<?>[]{SesExtUploadRequest.class, SesExtUploadDoc.class}) {
            for (Field f : type.getDeclaredFields()) {
                Column c = f.getAnnotation(Column.class);
                if (c == null) {
                    continue;
                }
                assertTrue(sql.contains(c.name()),
                        type.getSimpleName() + " maps column '" + c.name() + "' which sql/65 never creates"
                                + " — that is an ORA-00904 on a real upload, not here.");
            }
        }
    }

    @Test
    @DisplayName("The active-row unique index is function-based, not a plain column index")
    void activeUniqueIndexIsFunctionBased() throws Exception {
        // A plain UNIQUE(ext_request_id, worker_id, doc_code) would reject the
        // INSERT half of an upload-replace, colliding with the row the same
        // operation had just soft-deleted. The CASE form leaves inactive rows
        // with an all-NULL key, which Oracle does not index at all.
        String sql = migration().toLowerCase().replaceAll("\\s+", " ");
        assertTrue(sql.contains("create unique index ux_ses_ext_upload_doc_active"));
        assertTrue(sql.contains("case when is_active = ''y'' then ext_request_id end"),
                "the active unique index must be function-based or re-uploading a document throws ORA-00001");
    }

    @Test
    @DisplayName("The migration is guarded and never drops a staging table")
    void migrationIsSafeToRerun() throws Exception {
        String sql = migrationDdl().toLowerCase();

        assertTrue(sql.contains("user_tables"), "table creation must be guarded");
        assertTrue(sql.contains("user_sequences"), "sequence creation must be guarded");
        assertTrue(sql.contains("user_indexes"), "index creation must be guarded");
        assertFalse(sql.contains("drop table"),
                "sql/65 must never drop a staging table — that is a contractor's half-finished upload");
    }

    @Test
    @DisplayName("The production mirror is byte-identical to the dev script")
    void productionMirrorMatches() throws Exception {
        // Both READMEs claim they are identical. A mirror that silently drifts
        // is how production ends up a column short.
        Path prod = resolveProductionMigration("65_ext_upload_staging_tables.sql");
        assertTrue(Files.exists(prod), "sql/production/65_... is missing");
        assertEquals(migration(), new String(Files.readAllBytes(prod), StandardCharsets.UTF_8),
                "sql/production/65_... has drifted from sql/65_...");
    }
}
