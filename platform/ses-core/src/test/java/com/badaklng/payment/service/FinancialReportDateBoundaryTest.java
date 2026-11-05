package com.badaklng.payment.service;

import com.badaklng.ses.text.SesText;
import com.badaklng.platform.system.service.AppParameterService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The date boundaries of the financial report.
 *
 * <p>This is the one genuinely error-prone thing in
 * {@link FinancialReportExcelService}, and it fails silently. The two sides of
 * the report filter different columns and only one of them carries a time:</p>
 * <ul>
 *   <li>{@code ses_request_payment.reviewed_date} is a <b>TIMESTAMP</b>.</li>
 *   <li>{@code ses_cash_expense.expense_date} is a <b>DATE</b>.</li>
 * </ul>
 *
 * <p>The repository finders use {@code BETWEEN}, which is inclusive. An end
 * bound of plain midnight therefore excludes everything verified <i>during</i>
 * the final day of the range — a whole day of money missing from a report
 * whose reader has no reason to suspect anything was left out, and no way to
 * tell from the file. That is why {@code endOfDay} exists as a named method
 * instead of being inlined, and why it is tested.</p>
 *
 * <p>Plain JUnit: both helpers are static and take a {@code Date}. Building a
 * real workbook would need the repositories and Oracle; the stage document
 * lists that as unverified.</p>
 */
class FinancialReportDateBoundaryTest {
    /**
     * A {@code SesText} with no parameter rows behind it, so every lookup
     * returns the compiled-in fallback. {@code AppParameterService} starts
     * with an empty cache and only reloads on a Spring context event, so this
     * touches no database — which is the same premise the null repositories
     * above rest on.
     */
    private static SesText literalText() {
        return new SesText(new AppParameterService(null));
    }

    private static final SimpleDateFormat STAMP = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");

    private static Date on(int year, int month, int day, int hour, int min) {
        Calendar c = Calendar.getInstance();
        c.set(year, month - 1, day, hour, min, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTime();
    }

    // ---------------------------------------------------------------
    // The boundary that would lose a day of money
    // ---------------------------------------------------------------

    @Test
    @DisplayName("The end bound reaches the last instant of its day")
    void endOfDayCoversTheWholeDay() {
        // THE assertion in this class. 2026-01-31 as an end bound must include
        // a payment verified at 16:45 that afternoon. A bare midnight bound
        // would silently drop it, along with everything else that day.
        Date end = FinancialReportExcelService.endOfDay(on(2026, 1, 31, 0, 0));

        assertEquals("2026-01-31 23:59:59.999", STAMP.format(end));

        Date verifiedThatAfternoon = on(2026, 1, 31, 16, 45);
        assertTrue(verifiedThatAfternoon.before(end),
                "a payment verified during the last day of the range must fall inside it");

        Date nextMorning = on(2026, 2, 1, 0, 1);
        assertFalse(nextMorning.before(end), "and the next day must not");
    }

    @Test
    @DisplayName("The start bound reaches back to midnight")
    void startOfDayCoversFromMidnight() {
        // The mirror of the above: a payment verified at 00:30 on the first day
        // of the range must be included. The user picked a DAY, not an instant.
        Date start = FinancialReportExcelService.startOfDay(on(2026, 1, 1, 14, 30));

        assertEquals("2026-01-01 00:00:00.000", STAMP.format(start));
        assertTrue(FinancialReportExcelService.startOfDay(on(2026, 1, 1, 14, 30))
                .before(on(2026, 1, 1, 0, 30)));
    }

    @Test
    @DisplayName("A single-day range includes that whole day and nothing else")
    void singleDayRangeIsWholeDay() {
        // from == to is the likeliest real query ("today's figures") and the
        // one an off-by-one boundary turns into an empty report.
        Date day = on(2026, 3, 15, 9, 0);
        Date from = FinancialReportExcelService.startOfDay(day);
        Date to = FinancialReportExcelService.endOfDay(day);

        assertTrue(from.before(to));
        for (int hour = 0; hour < 24; hour++) {
            Date moment = on(2026, 3, 15, hour, 30);
            assertFalse(moment.before(from), hour + ":30 must not fall before the range");
            assertTrue(moment.before(to), hour + ":30 must fall inside the range");
        }
        assertFalse(on(2026, 3, 14, 23, 30).after(from), "the previous evening is outside");
        assertFalse(on(2026, 3, 16, 0, 30).before(to), "the next morning is outside");
    }

    @Test
    @DisplayName("A null end bound still bounds the query, far in the future")
    void nullEndStillProducesAnUpperBound() {
        // The finders take BETWEEN and cannot accept a null bound, so "open at
        // that end" has to become a date rather than a null — otherwise the
        // open-ended download throws instead of returning everything.
        Date end = FinancialReportExcelService.endOfDay(null);
        assertTrue(end.after(new Date()), "a null end bound must still be in the future");

        Calendar tenYears = Calendar.getInstance();
        tenYears.add(Calendar.YEAR, 10);
        assertTrue(end.after(tenYears.getTime()),
                "and far enough ahead that nothing realistic is cut off");
    }

    // ---------------------------------------------------------------
    // File naming
    // ---------------------------------------------------------------

    @Test
    @DisplayName("The filename states the period it covers")
    void fileNameCarriesThePeriod() {
        // Three of these land in somebody's Downloads folder in the same week.
        // A constant name means three files called "report.xlsx" and no way to
        // tell which is which.
        FinancialReportExcelService svc = new FinancialReportExcelService(literalText(), null, null, null, null);

        assertEquals("Laporan-Keuangan-20260101-20260131.xlsx",
                svc.fileNameFor(on(2026, 1, 1, 0, 0), on(2026, 1, 31, 0, 0)));
        assertEquals("Laporan-Keuangan-semua.xlsx", svc.fileNameFor(null, null));
        assertTrue(svc.fileNameFor(null, on(2026, 1, 31, 0, 0)).contains("awal"));
        assertTrue(svc.fileNameFor(on(2026, 1, 1, 0, 0), null).contains("kini"));
    }

    @Test
    @DisplayName("The filename is always a safe .xlsx name")
    void fileNameIsSafe() {
        // It goes straight into a Content-Disposition header. A character that
        // needed quoting would truncate the name or break the header.
        FinancialReportExcelService svc = new FinancialReportExcelService(literalText(), null, null, null, null);
        for (String name : new String[]{
                svc.fileNameFor(null, null),
                svc.fileNameFor(on(2026, 1, 1, 0, 0), on(2026, 12, 31, 0, 0))}) {
            assertTrue(name.endsWith(".xlsx"));
            assertTrue(name.matches("[A-Za-z0-9._\\-]+"),
                    name + " must contain nothing that needs quoting in a header");
        }
    }
}
