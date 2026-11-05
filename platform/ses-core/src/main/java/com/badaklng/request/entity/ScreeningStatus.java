package com.badaklng.request.entity;

/**
 * One worker's screening outcome.
 *
 * <p><b>Constants, not an enum</b> — the same call this module already made for
 * {@link RequestStatus}, and for the same reasons. The column is a plain
 * {@code VARCHAR2(20)} with a CHECK constraint, Thymeleaf compares Strings
 * anyway, and a mapped enum would mean a value written outside the application
 * (a data fix, an older release) throws on READ rather than merely failing a
 * comparison.</p>
 */
public final class ScreeningStatus {

    /** Not yet assessed. Every pre-existing worker row defaults to this. */
    public static final String PENDING = "PENDING";

    /** Turned up and passed. The only status that goes on to badge retrieval. */
    public static final String ATTENDED = "ATTENDED";

    /** Did not turn up. */
    public static final String NO_SHOW = "NO_SHOW";

    /** Turned up and did not pass. */
    public static final String FAILED = "FAILED";

    /** Submitted and accepted (screening passed). */
    public static final String ACCEPTED = "ACCEPTED";

    /** Submitted and rejected (screening failed). */
    public static final String REJECTED = "REJECTED";

    /**
     * Did this worker clear screening?
     *
     * <p>The single place that answers it. Badge printing, the retrieval
     * worker list and the "is this whole request dead" test all read this
     * rather than comparing strings, so adding a fifth status later cannot
     * leave one of them silently treating it as a pass.</p>
     */
    public static boolean isCleared(String status) {
        return ATTENDED.equals(status) || ACCEPTED.equals(status);
    }

    /**
     * Has somebody recorded an outcome for this worker yet?
     *
     * <p>Expressed as "not PENDING" rather than "is one of the other three"
     * deliberately: an unrecognised value stored by hand counts as decided,
     * not as still-waiting. The alternative would let one bad row block the
     * Selesaikan Screening button forever with no way to clear it from the
     * UI.</p>
     */
    public static boolean isDecided(String status) {
        return status != null && !status.trim().isEmpty() && !PENDING.equals(status);
    }

    /** {@code NO_SHOW}, {@code FAILED}, and {@code REJECTED} — decided, and not getting a badge. */
    public static boolean isRejected(String status) {
        return NO_SHOW.equals(status) || FAILED.equals(status) || REJECTED.equals(status);
    }

    /** Guards the controller against a hand-crafted POST carrying a value the CHECK would reject. */
    public static boolean isValid(String status) {
        return PENDING.equals(status) || ATTENDED.equals(status)
                || NO_SHOW.equals(status) || FAILED.equals(status)
                || ACCEPTED.equals(status) || REJECTED.equals(status);
    }

    /** Indonesian label for the one place a status is shown without its own badge markup. */
    public static String label(String status) {
        if (ACCEPTED.equals(status)) {
            return "Lulus";
        }
        if (ATTENDED.equals(status)) {
            return "Hadir / Lolos";
        }
        if (NO_SHOW.equals(status)) {
            return "Tidak Hadir";
        }
        if (FAILED.equals(status) || REJECTED.equals(status)) {
            return "Tidak Lulus";
        }
        return "Belum Diperiksa";
    }

    private ScreeningStatus() {
    }
}
