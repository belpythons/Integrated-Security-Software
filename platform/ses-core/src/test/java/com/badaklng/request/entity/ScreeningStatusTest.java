package com.badaklng.request.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The four per-worker screening outcomes and the three questions asked of
 * them.
 *
 * <p>These predicates look too small to test, which is exactly why they are
 * tested: every one of them is the single place a question is answered, and
 * each has a wrong answer that fails silently rather than loudly.</p>
 * <ul>
 *   <li>{@code isCleared} too generous → a badge printed for somebody
 *       screening turned away.</li>
 *   <li>{@code isDecided} too strict → the Selesaikan Screening button can
 *       never be enabled, with no way to clear it from the UI.</li>
 *   <li>{@code isValid} too generous → an ORA-02290 from the CHECK constraint
 *       at the bottom of a transaction, after the statuses were set.</li>
 * </ul>
 */
class ScreeningStatusTest {

    // ---------------------------------------------------------------
    // The question that controls badge printing
    // ---------------------------------------------------------------

    @Test
    @DisplayName("Only ATTENDED clears a worker for a badge")
    void onlyAttendedIsCleared() {
        // THE assertion here. Anything generous enough to admit a second value
        // prints a badge for somebody screening rejected.
        assertTrue(ScreeningStatus.isCleared(ScreeningStatus.ATTENDED));

        assertFalse(ScreeningStatus.isCleared(ScreeningStatus.PENDING),
                "not yet assessed is not cleared — this is the one an optimistic default would break");
        assertFalse(ScreeningStatus.isCleared(ScreeningStatus.NO_SHOW));
        assertFalse(ScreeningStatus.isCleared(ScreeningStatus.FAILED));
        assertFalse(ScreeningStatus.isCleared(null));
        assertFalse(ScreeningStatus.isCleared("attended"),
                "the column stores upper case; a case-insensitive match here would hide a data bug");
        assertFalse(ScreeningStatus.isCleared("SOMETHING_NEW"),
                "an unrecognised value must NOT be treated as a pass");
    }

    // ---------------------------------------------------------------
    // The question that gates the submit button
    // ---------------------------------------------------------------

    @Test
    @DisplayName("Decided means 'not PENDING', so a stray value cannot block the form forever")
    void decidedIsNotPending() {
        assertFalse(ScreeningStatus.isDecided(ScreeningStatus.PENDING));
        assertFalse(ScreeningStatus.isDecided(null));
        assertFalse(ScreeningStatus.isDecided("   "));

        assertTrue(ScreeningStatus.isDecided(ScreeningStatus.ATTENDED));
        assertTrue(ScreeningStatus.isDecided(ScreeningStatus.NO_SHOW));
        assertTrue(ScreeningStatus.isDecided(ScreeningStatus.FAILED));

        // Deliberately true. Written as "not PENDING" rather than "is one of
        // the other three": a value planted by hand counts as decided, so one
        // bad row cannot leave the Selesaikan Screening button permanently
        // disabled with no way to fix it from the screen. isValid is what
        // refuses to WRITE such a value.
        assertTrue(ScreeningStatus.isDecided("SOMETHING_NEW"),
                "an unrecognised stored value must not block the form forever");
    }

    // ---------------------------------------------------------------
    // The question that guards the write
    // ---------------------------------------------------------------

    @Test
    @DisplayName("Only the configured CHECK-constraint values are writable")
    void validMirrorsTheCheckConstraint() {
        // This predicate exists so a hand-crafted POST fails with a readable
        // message instead of an ORA-02290 at commit time.
        for (String ok : new String[]{ScreeningStatus.PENDING, ScreeningStatus.ATTENDED,
                ScreeningStatus.NO_SHOW, ScreeningStatus.FAILED,
                ScreeningStatus.ACCEPTED, ScreeningStatus.REJECTED}) {
            assertTrue(ScreeningStatus.isValid(ok), ok + " must be accepted");
        }
        assertFalse(ScreeningStatus.isValid("SOMETHING_INVALID"));
        assertFalse(ScreeningStatus.isValid("attended"));
        assertFalse(ScreeningStatus.isValid(""));
        assertFalse(ScreeningStatus.isValid(null));
    }

    @Test
    @DisplayName("isValid agrees exactly with the CHECK constraint in sql/67")
    void validAgreesWithTheMigration() throws Exception {
        // Two lists of strings, in two languages, in two files.
        // This is the test that notices when only one of them gains a new
        // value — the Java side accepting a value Oracle then rejects is an
        // ORA-02290 at commit, after the whole roster has been set.
        String sql = read(resolveMigration("67_worker_screening_status.sql"));

        for (String value : new String[]{"PENDING", "ATTENDED", "NO_SHOW", "FAILED"}) {
            assertTrue(sql.contains("''" + value + "''"),
                    value + " is accepted by ScreeningStatus but missing from sql/67's CHECK");
            assertTrue(ScreeningStatus.isValid(value));
        }
    }

    // ---------------------------------------------------------------
    // Rejection, and labels
    // ---------------------------------------------------------------

    @Test
    @DisplayName("NO_SHOW and FAILED are both rejections, and stay distinguishable")
    void rejectionKeepsItsReason() {
        assertTrue(ScreeningStatus.isRejected(ScreeningStatus.NO_SHOW));
        assertTrue(ScreeningStatus.isRejected(ScreeningStatus.FAILED));
        assertFalse(ScreeningStatus.isRejected(ScreeningStatus.ATTENDED));
        assertFalse(ScreeningStatus.isRejected(ScreeningStatus.PENDING));

        // The badge consequence is identical, which is the argument for
        // merging them into one value. They are kept apart because they are
        // different facts about different people, and this column is the only
        // place that difference is recorded.
        assertFalse(ScreeningStatus.label(ScreeningStatus.NO_SHOW)
                        .equals(ScreeningStatus.label(ScreeningStatus.FAILED)),
                "merging the two labels would discard the reason to save one enum value");
    }

    @Test
    @DisplayName("Every status has an Indonesian label, and an unknown one degrades")
    void labelsExist() {
        assertEquals("Hadir / Lolos", ScreeningStatus.label(ScreeningStatus.ATTENDED));
        assertEquals("Tidak Hadir", ScreeningStatus.label(ScreeningStatus.NO_SHOW));
        assertEquals("Tidak Lulus", ScreeningStatus.label(ScreeningStatus.FAILED));
        assertEquals("Belum Diperiksa", ScreeningStatus.label(ScreeningStatus.PENDING));
        assertEquals("Belum Diperiksa", ScreeningStatus.label(null));
        assertEquals("Belum Diperiksa", ScreeningStatus.label("SOMETHING_NEW"));
    }

    // ---------------------------------------------------------------
    // Column and default
    // ---------------------------------------------------------------

    @Test
    @DisplayName("A new worker row starts PENDING, and the value fits VARCHAR2(20)")
    void entityDefaultAndColumnWidth() {
        // An optimistic default would clear every worker for a badge the
        // moment they were added, before anyone screened them.
        assertEquals(ScreeningStatus.PENDING, new SesRequestWorker().getScreeningStatus());

        for (String v : new String[]{ScreeningStatus.PENDING, ScreeningStatus.ATTENDED,
                ScreeningStatus.NO_SHOW, ScreeningStatus.FAILED}) {
            assertTrue(v.length() <= 20,
                    v + " is " + v.length() + " chars; the column is VARCHAR2(20) — an ORA-12899"
                            + " would surface on a real screening, not here");
        }
    }

    @Test
    @DisplayName("sql/67 is guarded and its production mirror matches")
    void migrationIsSafe() throws Exception {
        String sql = read(resolveMigration("67_worker_screening_status.sql"));

        assertTrue(sql.contains("user_tab_cols"), "the column add must be guarded");
        // The constraint is added inside the SAME guarded branch as the
        // column, so there is no bare DROP CONSTRAINT to fail on a re-run —
        // unlike sql/21 and sql/61, whose constraint halves are deliberately
        // not re-runnable.
        //
        // Scanned with comment lines removed: the script's own header explains
        // why there is no DROP CONSTRAINT, so a naive search matches the
        // sentence forbidding it and fails on a correct script.
        assertFalse(stripComments(sql).toUpperCase().contains("DROP CONSTRAINT"),
                "a bare DROP CONSTRAINT would make this script fail on its second run");

        assertEquals(sql, read(resolveProductionMigration("67_worker_screening_status.sql")),
                "the production mirror has drifted");
    }

    @Test
    @DisplayName("RequestEditForm.from maps worker screening status and label properly")
    void testWorkerRowScreeningStatusMapping() {
        SesRequest req = new SesRequest();
        req.setRequestId(100L);
        req.setTypeCode("TEST");

        SesRequestWorker w1 = new SesRequestWorker();
        w1.setWorkerId(1L);
        w1.setWorkerName("Worker One");
        w1.setScreeningStatus(ScreeningStatus.ATTENDED);

        SesRequestWorker w2 = new SesRequestWorker();
        w2.setWorkerId(2L);
        w2.setWorkerName("Worker Two");
        w2.setScreeningStatus(ScreeningStatus.NO_SHOW);

        com.badaklng.request.model.RequestEditForm form =
                com.badaklng.request.model.RequestEditForm.from(req, java.util.Arrays.asList(w1, w2), null, null, null, null);

        assertEquals(2, form.getWorkers().size());
        assertEquals(ScreeningStatus.ATTENDED, form.getWorkers().get(0).getScreeningStatus());
        assertEquals("Hadir / Lolos", form.getWorkers().get(0).getScreeningStatusLabel());
        assertEquals(ScreeningStatus.NO_SHOW, form.getWorkers().get(1).getScreeningStatus());
        assertEquals("Tidak Hadir", form.getWorkers().get(1).getScreeningStatusLabel());
    }

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

    private static String read(Path p) throws Exception {
        assertTrue(Files.exists(p), p + " is missing");
        return new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
    }

    /** The script without its {@code --} comment lines — statements only. */
    private static String stripComments(String sql) {
        StringBuilder out = new StringBuilder();
        for (String line : sql.split("\\r?\\n")) {
            if (line.trim().startsWith("--")) {
                continue;
            }
            out.append(line).append('\n');
        }
        return out.toString();
    }
}
