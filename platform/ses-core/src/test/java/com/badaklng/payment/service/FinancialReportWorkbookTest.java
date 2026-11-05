package com.badaklng.payment.service;

import com.badaklng.ses.text.SesText;
import com.badaklng.platform.system.service.AppParameterService;
import com.badaklng.cash.entity.SesCashExpense;
import com.badaklng.payment.entity.SesRequestPayment;
import com.badaklng.ses.user.UserDisplayNameService;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Builds a real {@code .xlsx} and reads it back.
 *
 * <p>No Spring and no database. {@code build(...)} is handed the two lists
 * directly, and the only collaborator it reaches on an empty list is
 * {@code UserDisplayNameService.displayNames}, which short-circuits on an
 * empty input without touching its repository — so a {@code null} repository
 * is safe here and the whole POI path still executes.</p>
 *
 * <p>This is the half of Apache POI's cost that has to be justified: three
 * sheets with per-sheet totals and a summary that agrees with them. If this
 * test could be satisfied by one flat sheet, the dependency would not belong
 * in the POM at all.</p>
 *
 * <p>What is NOT proven here: the mapping of real payment and expense rows
 * into cells, which needs the repositories and a descriptor. The stage
 * document lists it as unverified.</p>
 */
class FinancialReportWorkbookTest {
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

    /** Repositories and PaymentService are unreachable on an empty data set. */
    private final FinancialReportExcelService service =
            new FinancialReportExcelService(literalText(), null, null, null, new UserDisplayNameService(null));

    private static Date on(int year, int month, int day) {
        Calendar c = Calendar.getInstance();
        c.set(year, month - 1, day, 0, 0, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTime();
    }

    private Workbook build(Date from, Date to) throws IOException {
        byte[] bytes = service.build(Collections.<SesRequestPayment>emptyList(),
                Collections.<SesCashExpense>emptyList(), from, to);
        assertTrue(bytes.length > 0, "an empty report is still a workbook");
        return new XSSFWorkbook(new ByteArrayInputStream(bytes));
    }

    private static String str(Sheet s, int row, int col) {
        Row r = s.getRow(row);
        assertNotNull(r, "row " + row + " of " + s.getSheetName() + " is missing");
        Cell c = r.getCell(col);
        assertNotNull(c, "cell " + row + "," + col + " of " + s.getSheetName() + " is missing");
        return c.getCellType() == CellType.NUMERIC
                ? String.valueOf((long) c.getNumericCellValue())
                : c.getStringCellValue();
    }

    // ---------------------------------------------------------------
    // Structure
    // ---------------------------------------------------------------

    @Test
    @DisplayName("The workbook has exactly the three specified sheets, in order")
    void threeSheetsInOrder() throws Exception {
        try (Workbook wb = build(null, null)) {
            assertEquals(3, wb.getNumberOfSheets());
            assertEquals("Pemasukan", wb.getSheetAt(0).getSheetName());
            assertEquals("Pengeluaran", wb.getSheetAt(1).getSheetName());
            assertEquals("Ringkasan", wb.getSheetAt(2).getSheetName());
        }
    }

    @Test
    @DisplayName("Each data sheet carries its specified headers")
    void headersMatchTheSpecification() throws Exception {
        try (Workbook wb = build(null, null)) {
            Sheet in = wb.getSheet("Pemasukan");
            assertEquals("No. Transaksi", str(in, 0, 0));
            assertEquals("No. Pengajuan", str(in, 0, 1));
            assertEquals("Pemohon", str(in, 0, 3));
            assertEquals("Nominal (Rp)", str(in, 0, 4));
            assertEquals("Tanggal Verifikasi", str(in, 0, 5));
            assertEquals("Diverifikasi Oleh", str(in, 0, 6));

            Sheet out = wb.getSheet("Pengeluaran");
            assertEquals("Tanggal Kas", str(out, 0, 1));
            assertEquals("Dibayarkan Kepada", str(out, 0, 2));
            assertEquals("Nominal (Rp)", str(out, 0, 3));
            assertEquals("Dibuat Oleh", str(out, 0, 5));
        }
    }

    // ---------------------------------------------------------------
    // Amounts are numbers, not text
    // ---------------------------------------------------------------

    @Test
    @DisplayName("Totals are NUMERIC cells with a thousands format, not pre-formatted text")
    void amountsAreNumeric() throws Exception {
        // The first thing the report's reader does is select the Nominal
        // column and look at the sum. "Rp 1.500.000" written as a String
        // gives them zero, with no indication why.
        try (Workbook wb = build(null, null)) {
            Cell inTotal = wb.getSheet("Pemasukan").getRow(1).getCell(4);
            assertEquals(CellType.NUMERIC, inTotal.getCellType());
            assertTrue(inTotal.getCellStyle().getDataFormatString().contains("#,##0"),
                    "the money format must be applied to the cell, not baked into a string");

            Cell outTotal = wb.getSheet("Pengeluaran").getRow(1).getCell(3);
            assertEquals(CellType.NUMERIC, outTotal.getCellType());
        }
    }

    // ---------------------------------------------------------------
    // The summary must agree with the sheets
    // ---------------------------------------------------------------

    @Test
    @DisplayName("Ringkasan reports both totals and the net balance")
    void summaryCarriesBothTotalsAndBalance() throws Exception {
        try (Workbook wb = build(null, null)) {
            Sheet s = wb.getSheet("Ringkasan");

            assertEquals("LAPORAN KEUANGAN SES", str(s, 0, 0));
            assertEquals("Keterangan", str(s, 3, 0));

            assertTrue(str(s, 4, 0).startsWith("Pemasukan"));
            assertTrue(str(s, 5, 0).startsWith("Pengeluaran"));
            assertEquals("SALDO KAS BERSIH", str(s, 6, 0));

            // Empty report: both totals zero, so the balance is zero. The
            // point is that the balance cell is COMPUTED from the two, not
            // left blank — a blank balance on an empty period reads as
            // "unknown" rather than "nothing happened".
            assertEquals(0L, (long) s.getRow(4).getCell(2).getNumericCellValue());
            assertEquals(0L, (long) s.getRow(5).getCell(2).getNumericCellValue());
            assertEquals(0L, (long) s.getRow(6).getCell(2).getNumericCellValue());
            assertEquals(CellType.NUMERIC, s.getRow(6).getCell(2).getCellType());
        }
    }

    @Test
    @DisplayName("Ringkasan states which dates the two sides were filtered on")
    void summaryExplainsItsOwnDateBasis() throws Exception {
        // The two sides filter different columns — reviewed_date for income,
        // expense_date for outgoings. A reader reconciling this against
        // another system has no way to know that unless the file says so.
        try (Workbook wb = build(null, null)) {
            Sheet s = wb.getSheet("Ringkasan");
            String note = str(s, 8, 0);
            assertTrue(note.contains("VERIFIKASI"), "the income date basis must be stated");
            assertTrue(note.contains("TANGGAL KAS"), "the expense date basis must be stated");
        }
    }

    @Test
    @DisplayName("The period is written into the sheet, not only the filename")
    void periodIsInsideTheFile() throws Exception {
        // A file renamed on the way to somebody's inbox loses its period
        // otherwise, and a report whose coverage is unknowable is worse than
        // no report.
        try (Workbook wb = build(on(2026, 1, 1), on(2026, 1, 31))) {
            assertEquals("Periode", str(wb.getSheet("Ringkasan"), 1, 0));
            assertEquals("01-01-2026 s/d 31-01-2026", str(wb.getSheet("Ringkasan"), 1, 1));
        }
        try (Workbook wb = build(null, null)) {
            assertEquals("Seluruh data", str(wb.getSheet("Ringkasan"), 1, 1));
        }
    }

    // ---------------------------------------------------------------
    // Something to look at
    // ---------------------------------------------------------------

    @Test
    @DisplayName("Write a sample workbook so the report can actually be opened")
    void writeSampleFile() throws Exception {
        // Mirrors RequestEmailTemplateBuilderTest's preview writer: a report
        // nobody has opened is a report nobody has checked.
        Path dir = Paths.get("target", "report-preview");
        Files.createDirectories(dir);
        byte[] bytes = service.build(Collections.<SesRequestPayment>emptyList(),
                Collections.<SesCashExpense>emptyList(), on(2026, 1, 1), on(2026, 1, 31));
        Path f = dir.resolve("laporan-keuangan-sample.xlsx");
        Files.write(f, bytes);
        System.out.println("Financial report sample written to " + f.toAbsolutePath());

        assertTrue(Files.size(f) > 0);
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(Files.readAllBytes(f)))) {
            assertEquals(3, wb.getNumberOfSheets(), "the written file must re-open as a valid workbook");
        }
    }

    @Test
    @DisplayName("POI stays on the last Java 8 line")
    void poiVersionIsPinned() throws Exception {
        // 5.3.x requires Java 17; this module targets 1.8. A casual bump
        // breaks the build in a way whose cause is not obvious from the error.
        Path pomPath = Paths.get("pom.xml");
        if (Files.exists(Paths.get("..", "pom.xml"))) {
            Path parentPom = Paths.get("..", "pom.xml");
            List<String> parentLines = Files.readAllLines(parentPom);
            if (String.join("\n", parentLines).contains("poi-ooxml")) {
                pomPath = parentPom;
            }
        }
        List<String> pom = Files.readAllLines(pomPath);
        String joined = String.join("\n", pom);
        assertTrue(joined.contains("poi-ooxml"), "poi-ooxml must be declared");
        assertTrue(joined.contains("<version>5.2.5</version>"),
                "POI must stay pinned to 5.2.5 — the last line supporting Java 8");
    }
}
