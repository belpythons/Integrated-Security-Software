package com.badaklng.payment.service;

import com.badaklng.cash.entity.SesCashExpense;
import com.badaklng.cash.repository.SesCashExpenseRepository;
import com.badaklng.payment.entity.PaymentStatus;
import com.badaklng.payment.entity.SesRequestPayment;
import com.badaklng.payment.model.PaymentRowData;
import com.badaklng.payment.repository.SesRequestPaymentRepository;
import com.badaklng.payment.spi.PaymentBusinessDescriptor;
import com.badaklng.ses.text.SesText;
import com.badaklng.ses.user.UserDisplayNameService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The financial report, as a three-sheet {@code .xlsx}.
 *
 * <p>Sheet 1 Pemasukan (verified badge payments), sheet 2 Pengeluaran (the
 * cash-expense ledger), sheet 3 Ringkasan (totals and the net balance).
 * Multi-sheet is the whole reason Apache POI is in the POM at all — a
 * single-sheet export would need no dependency, and the per-sheet totals in
 * Ringkasan are what a CSV cannot express.</p>
 *
 * <h2>Date boundaries</h2>
 *
 * <p>The two sides filter on different columns and one of them has a time
 * component, which is the only genuinely error-prone thing in this class:</p>
 * <ul>
 *   <li><b>Pemasukan</b> filters {@code reviewedDate}, a TIMESTAMP — the
 *       moment an officer verified the money, which is when it arrived. A
 *       payment submitted in March and verified in April belongs to April.
 *       The end bound is pushed to 23:59:59.999 by {@link #endOfDay}; passing
 *       a bare date would silently drop everything verified after midnight on
 *       the last day.</li>
 *   <li><b>Pengeluaran</b> filters {@code expenseDate}, a DATE — the day the
 *       money left the till, not the day somebody typed it in.</li>
 * </ul>
 *
 * <p>An open range (either bound null) means "everything", so the download
 * works before anyone has chosen a period.</p>
 *
 * <h2>Amounts</h2>
 *
 * <p>Both columns are whole Rupiah in a {@code NUMBER} column, written as
 * numeric cells with a thousands format — not as pre-formatted strings.
 * "Rp 1.500.000" as text is a report nobody can sum in Excel, which is the
 * first thing its reader will try to do.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FinancialReportExcelService {

    private static final String ACTIVE = "Y";

    /** Oracle's own idiom for IDR: thousands separated by dots, no decimals. */
    private static final String MONEY_FORMAT = "#,##0";
    private static final String DATE_FORMAT = "dd-mm-yyyy";
    private static final String DATETIME_FORMAT = "dd-mm-yyyy hh:mm";

    /** SimpleDateFormat is not thread-safe, so this is a pattern rather than a shared instance -- two concurrent requests sharing one formatter mutate its internal Calendar mid-format, and the result is a silently wrong date, not an exception. */
    private static final String FILE_DATE_PATTERN = "yyyyMMdd";

    private final SesText text;
    private final SesRequestPaymentRepository paymentRepository;
    private final SesCashExpenseRepository expenseRepository;
    private final PaymentService paymentService;
    private final UserDisplayNameService userDisplayNameService;

    /**
     * Builds the workbook.
     *
     * @param from inclusive start, or null for "from the beginning"
     * @param to   inclusive end (whole day), or null for "up to now"
     */
    @Transactional(readOnly = true)
    public byte[] render(Date from, Date to) {
        return build(findPayments(from, to), findExpenses(from, to), from, to);
    }

    /**
     * The workbook itself, over data already fetched.
     *
     * <p>Split from {@link #render} so the POI usage can be exercised without
     * a database: the sheet layout, the cell types and the summary arithmetic
     * are the parts most likely to be wrong, and they do not need Oracle to be
     * checked. Package-private rather than public — this is a seam for the
     * test, not a second entry point for callers.</p>
     */
    byte[] build(List<SesRequestPayment> payments, List<SesCashExpense> expenses, Date from, Date to) {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Styles styles = new Styles(wb);

            long totalIn = writeIncome(wb, styles, payments);
            long totalOut = writeExpenses(wb, styles, expenses);
            writeSummary(wb, styles, from, to, payments.size(), expenses.size(), totalIn, totalOut);

            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            // A workbook written to a byte array cannot really fail on IO;
            // wrapping keeps the signature free of a checked exception no
            // caller could do anything about.
            throw new IllegalStateException("Gagal menyusun laporan keuangan.", e);
        }
    }

    /** {@code Laporan-Keuangan-20260101-20260131.xlsx}, or {-semua} for an open range. */
    public String fileNameFor(Date from, Date to) {
        String period = (from == null && to == null)
                ? "semua"
                : (from == null ? "awal" : new SimpleDateFormat(FILE_DATE_PATTERN).format(from))
                        + "-" + (to == null ? "kini" : new SimpleDateFormat(FILE_DATE_PATTERN).format(to));
        // The filename prefix is a parameter too: it is what lands in the
        // finance team's Downloads folder, and renaming the report should not
        // need a release. The date part stays code-generated.
        return text.get("SES_TXT_XLS_FILENAME_PREFIX", "Laporan-Keuangan") + "-" + period + ".xlsx";
    }

    // ---------------------------------------------------------------
    // Data
    // ---------------------------------------------------------------

    private List<SesRequestPayment> findPayments(Date from, Date to) {
        if (from == null && to == null) {
            return paymentRepository.findByStatusOrderByReviewedDateDesc(PaymentStatus.VERIFIED);
        }
        return paymentRepository.findByStatusAndReviewedDateBetweenOrderByReviewedDateDesc(
                PaymentStatus.VERIFIED, from == null ? new Date(0L) : startOfDay(from), endOfDay(to));
    }

    private List<SesCashExpense> findExpenses(Date from, Date to) {
        if (from == null && to == null) {
            return expenseRepository.findByActiveFlagOrderByExpenseDateDescExpenseIdDesc(ACTIVE);
        }
        return expenseRepository.findByActiveFlagAndExpenseDateBetweenOrderByExpenseDateDescExpenseIdDesc(
                ACTIVE, from == null ? new Date(0L) : startOfDay(from), endOfDay(to));
    }

    // ---------------------------------------------------------------
    // Sheets
    // ---------------------------------------------------------------

    private long writeIncome(Workbook wb, Styles s, List<SesRequestPayment> payments) {
        Sheet sheet = wb.createSheet(text.get("SES_TXT_XLS_SHEET_INCOME", "Pemasukan"));
        header(sheet, s,
                text.get("SES_TXT_XLS_IN_COL_TRX_NO", "No. Transaksi"),
                text.get("SES_TXT_XLS_IN_COL_REQUEST_NO", "No. Pengajuan"),
                text.get("SES_TXT_XLS_IN_COL_TYPE", "Jenis"),
                text.get("SES_TXT_XLS_IN_COL_REQUESTOR", "Pemohon"),
                text.get("SES_TXT_XLS_COL_AMOUNT", "Nominal (Rp)"),
                text.get("SES_TXT_XLS_IN_COL_VERIFIED_DATE", "Tanggal Verifikasi"),
                text.get("SES_TXT_XLS_IN_COL_VERIFIED_BY", "Diverifikasi Oleh"));

        // Transaction number and requestor for every row in one batched pass
        // per business type (PaymentService.rowData). Before this, each row
        // called descriptorFor(...).requestorUsername() and .transactionNo(),
        // and the requestor was fetched TWICE -- once to collect display-name
        // references and once to write the cell. Each of those resolves to a
        // findById on the same business row, so the report cost ~3 selects per
        // payment. The date range is optional, so with no range this list is
        // every verified payment ever recorded.
        //
        // Guarded on emptiness, not just for the saved round trip:
        // FinancialReportWorkbookTest builds this service with null
        // repositories precisely to assert that an empty report needs none of
        // them. An unconditional call here broke that contract.
        Map<Long, PaymentRowData> rowData = payments.isEmpty()
                ? Collections.<Long, PaymentRowData>emptyMap()
                : paymentService.rowData(payments);

        // One batched lookup for both name columns. The requestor and the
        // verifier are different references — a username and a UUID — and
        // UserDisplayNameService accepts both, which is why one map serves.
        Set<String> refs = new HashSet<>();
        for (SesRequestPayment p : payments) {
            if (p.getReviewedBy() != null) {
                refs.add(p.getReviewedBy());
            }
            PaymentRowData d = rowData.get(p.getPaymentId());
            if (d != null && d.getRequestorUsername() != null) {
                refs.add(d.getRequestorUsername());
            }
        }
        Map<String, String> names = userDisplayNameService.displayNames(refs);

        long total = 0L;
        int r = 1;
        for (SesRequestPayment p : payments) {
            PaymentRowData d = rowData.get(p.getPaymentId());
            String requestor = d == null ? null : d.getRequestorUsername();
            long amount = p.getPaidAmount() == null ? 0L : p.getPaidAmount();
            total += amount;

            Row row = sheet.createRow(r++);
            text(row, 0, s, String.valueOf(p.getPaymentId()));
            text(row, 1, s, d == null ? null : d.getTransactionNo());
            text(row, 2, s, paymentService.descriptorFor(p.getBusinessType()).displayName());
            text(row, 3, s, requestor == null ? "-" : names.getOrDefault(requestor, requestor));
            money(row, 4, s, amount);
            dateTime(row, 5, s, p.getReviewedDate());
            text(row, 6, s, p.getReviewedBy() == null
                    ? "-" : names.getOrDefault(p.getReviewedBy(), p.getReviewedBy()));
        }

        totalRow(sheet, s, r, text.get("SES_TXT_XLS_TOTAL_INCOME", "TOTAL PEMASUKAN"), 4, total, 7);
        autoSize(sheet, 7);
        return total;
    }

    private long writeExpenses(Workbook wb, Styles s, List<SesCashExpense> expenses) {
        Sheet sheet = wb.createSheet(text.get("SES_TXT_XLS_SHEET_EXPENSE", "Pengeluaran"));
        header(sheet, s,
                text.get("SES_TXT_XLS_OUT_COL_ID", "ID"),
                text.get("SES_TXT_XLS_OUT_COL_DATE", "Tanggal Kas"),
                text.get("SES_TXT_XLS_OUT_COL_PAID_TO", "Dibayarkan Kepada"),
                text.get("SES_TXT_XLS_COL_AMOUNT", "Nominal (Rp)"),
                text.get("SES_TXT_XLS_OUT_COL_NOTES", "Keperluan / Catatan"),
                text.get("SES_TXT_XLS_OUT_COL_CREATED_BY", "Dibuat Oleh"));

        Set<String> refs = new HashSet<>();
        for (SesCashExpense e : expenses) {
            if (e.getCreatedBy() != null) {
                refs.add(e.getCreatedBy());
            }
        }
        Map<String, String> names = userDisplayNameService.displayNames(refs);

        long total = 0L;
        int r = 1;
        for (SesCashExpense e : expenses) {
            long amount = e.getAmount() == null ? 0L : e.getAmount();
            total += amount;

            Row row = sheet.createRow(r++);
            text(row, 0, s, String.valueOf(e.getExpenseId()));
            dateOnly(row, 1, s, e.getExpenseDate());
            text(row, 2, s, e.getPaidTo());
            money(row, 3, s, amount);
            text(row, 4, s, e.getNotes() == null ? "-" : e.getNotes());
            text(row, 5, s, e.getCreatedBy() == null
                    ? "-" : names.getOrDefault(e.getCreatedBy(), e.getCreatedBy()));
        }

        totalRow(sheet, s, r, text.get("SES_TXT_XLS_TOTAL_EXPENSE", "TOTAL PENGELUARAN"), 3, total, 6);
        autoSize(sheet, 6);
        return total;
    }

    private void writeSummary(Workbook wb, Styles s, Date from, Date to,
                              int inCount, int outCount, long totalIn, long totalOut) {
        Sheet sheet = wb.createSheet(text.get("SES_TXT_XLS_SHEET_SUMMARY", "Ringkasan"));

        Row title = sheet.createRow(0);
        text(title, 0, s, text.get("SES_TXT_XLS_TITLE", "LAPORAN KEUANGAN SES"));
        title.getCell(0).setCellStyle(s.header);

        Row period = sheet.createRow(1);
        text(period, 0, s, text.get("SES_TXT_XLS_LABEL_PERIOD", "Periode"));
        text(period, 1, s, describePeriod(from, to));

        sheet.createRow(2);

        int r = 3;
        Row h = sheet.createRow(r++);
        text(h, 0, s, text.get("SES_TXT_XLS_SUM_COL_DESC", "Keterangan"));
        text(h, 1, s, text.get("SES_TXT_XLS_SUM_COL_COUNT", "Jumlah Transaksi"));
        text(h, 2, s, text.get("SES_TXT_XLS_COL_AMOUNT", "Nominal (Rp)"));
        for (int c = 0; c <= 2; c++) {
            h.getCell(c).setCellStyle(s.header);
        }

        Row in = sheet.createRow(r++);
        text(in, 0, s, text.get("SES_TXT_XLS_SUM_ROW_INCOME",
                "Pemasukan (pembayaran badge terverifikasi)"));
        number(in, 1, s, inCount);
        money(in, 2, s, totalIn);

        Row out = sheet.createRow(r++);
        text(out, 0, s, text.get("SES_TXT_XLS_SUM_ROW_EXPENSE", "Pengeluaran (kas keluar)"));
        number(out, 1, s, outCount);
        money(out, 2, s, totalOut);

        Row balance = sheet.createRow(r++);
        text(balance, 0, s, text.get("SES_TXT_XLS_SUM_ROW_BALANCE", "SALDO KAS BERSIH"));
        balance.getCell(0).setCellStyle(s.totalLabel);
        number(balance, 1, s, inCount + outCount);
        balance.getCell(1).setCellStyle(s.totalMoney);
        // Signed on purpose: a period that spent more than it took in reads as
        // a negative balance rather than as an absolute figure the reader has
        // to work out the direction of.
        money(balance, 2, s, totalIn - totalOut);
        balance.getCell(2).setCellStyle(s.totalMoney);

        sheet.createRow(r++);
        Row note = sheet.createRow(r);
        text(note, 0, s, text.get("SES_TXT_XLS_FOOTNOTE",
                "Pemasukan dihitung dari tanggal VERIFIKASI pembayaran; "
                        + "pengeluaran dari TANGGAL KAS, bukan tanggal entri."));

        autoSize(sheet, 3);
    }

    private String describePeriod(Date from, Date to) {
        if (from == null && to == null) {
            return text.get("SES_TXT_XLS_PERIOD_ALL", "Seluruh data");
        }
        SimpleDateFormat f = new SimpleDateFormat("dd-MM-yyyy");
        // One format key rather than three concatenated ones: SesText.get
        // trims, so a separator stored as " s/d " would arrive as "s/d" and
        // the two dates would run together. Inside a {0}/{1} template the
        // spaces are interior and survive.
        return text.format("SES_TXT_XLS_PERIOD_RANGE", "{0} s/d {1}",
                from == null ? text.get("SES_TXT_XLS_PERIOD_OPEN_START", "awal") : f.format(from),
                to == null ? text.get("SES_TXT_XLS_PERIOD_OPEN_END", "kini") : f.format(to));
    }

    // ---------------------------------------------------------------
    // Cells
    // ---------------------------------------------------------------

    private void header(Sheet sheet, Styles s, String... titles) {
        Row row = sheet.createRow(0);
        for (int i = 0; i < titles.length; i++) {
            Cell c = row.createCell(i);
            c.setCellValue(titles[i]);
            c.setCellStyle(s.header);
        }
    }

    private void totalRow(Sheet sheet, Styles s, int rowIdx, String label,
                          int amountCol, long total, int cols) {
        Row row = sheet.createRow(rowIdx);
        for (int c = 0; c < cols; c++) {
            row.createCell(c).setCellStyle(s.totalLabel);
        }
        row.getCell(0).setCellValue(label);
        Cell amount = row.getCell(amountCol);
        amount.setCellValue(total);
        amount.setCellStyle(s.totalMoney);
    }

    private void text(Row row, int col, Styles s, String value) {
        Cell c = row.createCell(col);
        c.setCellValue(value == null ? "" : value);
        c.setCellStyle(s.body);
    }

    private void number(Row row, int col, Styles s, long value) {
        Cell c = row.createCell(col);
        c.setCellValue(value);
        c.setCellStyle(s.body);
    }

    /** Numeric, never a pre-formatted string — the reader's first act is to sum this column. */
    private void money(Row row, int col, Styles s, long value) {
        Cell c = row.createCell(col);
        c.setCellValue(value);
        c.setCellStyle(s.money);
    }

    private void dateOnly(Row row, int col, Styles s, Date value) {
        Cell c = row.createCell(col);
        if (value != null) {
            c.setCellValue(value);
            c.setCellStyle(s.date);
        } else {
            c.setCellValue("-");
            c.setCellStyle(s.body);
        }
    }

    private void dateTime(Row row, int col, Styles s, Date value) {
        Cell c = row.createCell(col);
        if (value != null) {
            c.setCellValue(value);
            c.setCellStyle(s.dateTime);
        } else {
            c.setCellValue("-");
            c.setCellStyle(s.body);
        }
    }

    /**
     * Column widths from the longest rendered string, clamped.
     *
     * <p>Was {@code sheet.autoSizeColumn(c)}, whose own javadoc here already
     * called it the slowest thing in this class. The cap it applied bounded
     * the resulting <em>width</em>, not the work: POI measures every cell in
     * the column through AWT font metrics first, then the cap throws the
     * answer away. On three sheets over an unbounded date range that is an
     * AWT text layout per cell for the whole ledger.
     *
     * <p>Character count is a coarser proxy for rendered width than font
     * metrics, but the clamp below is 2600..14000 twips -- roughly 2 to 11
     * characters of slack -- so the difference lands inside the clamp for
     * every column this report actually produces. Visual result is the same
     * spreadsheet; the measurement is a string length instead of a glyph
     * layout.</p>
     */
    private void autoSize(Sheet sheet, int cols) {
        final int perChar = 280;   // twips per character at the body font
        final int padding = 900;   // room for the filter arrow and cell inset
        for (int c = 0; c < cols; c++) {
            int longest = 0;
            for (Row row : sheet) {
                Cell cell = row.getCell(c);
                if (cell == null) {
                    continue;
                }
                String rendered;
                switch (cell.getCellType()) {
                    case STRING:
                        rendered = cell.getStringCellValue();
                        break;
                    case NUMERIC:
                        // Dates and money are both numeric; their formatted
                        // widths are bounded and close enough to the raw digits.
                        rendered = DateUtil.isCellDateFormatted(cell)
                                ? DATETIME_FORMAT
                                : String.valueOf((long) cell.getNumericCellValue());
                        break;
                    default:
                        rendered = "";
                }
                longest = Math.max(longest, rendered == null ? 0 : rendered.length());
            }
            int width = longest * perChar + padding;
            sheet.setColumnWidth(c, Math.max(2600, Math.min(14000, width)));
        }
    }

    // ---------------------------------------------------------------
    // Dates
    // ---------------------------------------------------------------

    /** Midnight at the start of {@code d}. */
    static Date startOfDay(Date d) {
        Calendar c = Calendar.getInstance();
        c.setTime(d);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTime();
    }

    /**
     * The last instant of {@code d} — or "now, roughly" when {@code d} is null.
     *
     * <p>{@code reviewedDate} is a TIMESTAMP and {@code BETWEEN} is inclusive,
     * so an end bound of plain midnight excludes everything verified during
     * the final day of the range. That is a whole day of money missing from a
     * report nobody would have reason to distrust, which is why this exists
     * rather than being inlined.</p>
     */
    static Date endOfDay(Date d) {
        Calendar c = Calendar.getInstance();
        if (d != null) {
            c.setTime(d);
        } else {
            // No end bound: reach far enough forward that nothing is cut off.
            c.add(Calendar.YEAR, 100);
        }
        c.set(Calendar.HOUR_OF_DAY, 23);
        c.set(Calendar.MINUTE, 59);
        c.set(Calendar.SECOND, 59);
        c.set(Calendar.MILLISECOND, 999);
        return c.getTime();
    }

    /** The handful of cell styles, created once per workbook (POI caps them at 64k). */
    private static final class Styles {
        final CellStyle header;
        final CellStyle body;
        final CellStyle money;
        final CellStyle date;
        final CellStyle dateTime;
        final CellStyle totalLabel;
        final CellStyle totalMoney;

        Styles(Workbook wb) {
            Font bold = wb.createFont();
            bold.setBold(true);

            header = wb.createCellStyle();
            header.setFont(bold);
            header.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            header.setAlignment(HorizontalAlignment.CENTER);
            border(header);

            body = wb.createCellStyle();
            border(body);

            money = wb.createCellStyle();
            border(money);
            money.setDataFormat(wb.createDataFormat().getFormat(MONEY_FORMAT));

            date = wb.createCellStyle();
            border(date);
            date.setDataFormat(wb.createDataFormat().getFormat(DATE_FORMAT));

            dateTime = wb.createCellStyle();
            border(dateTime);
            dateTime.setDataFormat(wb.createDataFormat().getFormat(DATETIME_FORMAT));

            totalLabel = wb.createCellStyle();
            totalLabel.setFont(bold);
            totalLabel.setFillForegroundColor(IndexedColors.LEMON_CHIFFON.getIndex());
            totalLabel.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            border(totalLabel);

            totalMoney = wb.createCellStyle();
            totalMoney.setFont(bold);
            totalMoney.setFillForegroundColor(IndexedColors.LEMON_CHIFFON.getIndex());
            totalMoney.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            border(totalMoney);
            totalMoney.setDataFormat(wb.createDataFormat().getFormat(MONEY_FORMAT));
        }

        private static void border(CellStyle s) {
            s.setBorderTop(BorderStyle.THIN);
            s.setBorderBottom(BorderStyle.THIN);
            s.setBorderLeft(BorderStyle.THIN);
            s.setBorderRight(BorderStyle.THIN);
        }
    }
}
