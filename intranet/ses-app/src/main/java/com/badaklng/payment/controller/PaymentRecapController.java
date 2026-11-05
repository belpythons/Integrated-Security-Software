package com.badaklng.payment.controller;

import com.badaklng.payment.entity.SesRequestPayment;
import com.badaklng.payment.model.PaymentRecapRow;
import com.badaklng.payment.model.PaymentRowData;
import com.badaklng.payment.service.FinancialReportExcelService;
import com.badaklng.payment.service.PaymentService;
import com.badaklng.platform.security.PageAccessGuard;
import com.badaklng.ses.user.UserDisplayNameService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import com.badaklng.ses.web.PageRequests;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Admin-facing cash-inflow recap: "/payment/recap" (read-only report) —
 * sums {@link SesRequestPayment#getPaidAmount()} across every VERIFIED
 * payment item, the officer-recorded amount actually received (see
 * PaymentVerificationController#approve). Read-only — no mutation, so no
 * "/form" virtual RBAC pair (contrast /payment/verify).
 */
@Controller
@RequestMapping("/payment/recap")
@RequiredArgsConstructor
public class PaymentRecapController {

    public static final String LIST_URL = "/payment/recap";

    /** Excel's own serialisation, and what {@code <input type="date">} posts. */
    private static final String ISO_DATE = "yyyy-MM-dd";

    private final PaymentService paymentService;
    private final PageAccessGuard pageAccessGuard;
    private final UserDisplayNameService userDisplayNameService;
    private final FinancialReportExcelService excelService;

    /**
     * @param from inclusive start of the review-date window, or null for all
     * @param to   inclusive end (whole day), or null for all
     */
    @GetMapping
    public String recap(@RequestParam(value = "from", required = false)
                        @DateTimeFormat(pattern = ISO_DATE) Date from,
                        @RequestParam(value = "to", required = false)
                        @DateTimeFormat(pattern = ISO_DATE) Date to,
                        @RequestParam(value = "q", required = false) String q,
                        @RequestParam(value = "page", required = false) Integer pageNo,
                        @RequestParam(value = "size", required = false) Integer size,
                        Model model) {
        model.addAttribute("pageTitle", pageAccessGuard.require(LIST_URL));

        // The date range filters the TABLE as well as the download. This page
        // used to load every VERIFIED payment ever, with no filter at all, so
        // a range that only narrowed the spreadsheet would leave the screen
        // and the file disagreeing about the same question.
        List<SesRequestPayment> verified = paymentService.findVerified(from, to);
        List<PaymentRecapRow> rows = toRows(verified);

        // The term narrows the TABLE only. The totals below stay over the full
        // date range on purpose: a recap whose footer added up just the rows on
        // screen would report a different figure per page, which is worse than
        // useless on a financial summary.
        String term = PageRequests.term(q);
        List<PaymentRecapRow> matching = rows.stream()
                .filter(r -> PageRequests.matches(term, r.getRequestNo(), r.getTypeName(),
                        r.getRequestorUserId(), r.getReviewedByName()))
                .collect(Collectors.toList());
        Page<PaymentRecapRow> paged = PageRequests.slice(matching,
                PageRequests.of(pageNo, size, "paymentId"));
        model.addAttribute("page", paged);

        // ONE definition of "money received", shared with the Pengeluaran Kas
        // screen and the dashboard widget — but only while the view is
        // unfiltered. A filtered view must total what it is showing, or the
        // footer contradicts the rows above it.
        boolean filtered = from != null || to != null;
        long total = filtered
                ? rows.stream().mapToLong(r -> r.getPaidAmount() == null ? 0L : r.getPaidAmount()).sum()
                : paymentService.totalVerifiedAmount();

        model.addAttribute("rows", paged.getContent());
        model.addAttribute("totalAmount", total);
        model.addAttribute("filtered", filtered);
        model.addAttribute("from", from);
        model.addAttribute("to", to);
        // Nama lengkap di samping username. Satu query batch untuk seluruh
        // halaman lewat UserDisplayNameService.displayNames — layar ini
        // menampilkan username mentah sejak dibuat, yaitu regresi yang
        // javadoc service itu sendiri tulis sebagai alasan keberadaannya.
        model.addAttribute("requestorNames", userDisplayNameService.displayNames(
                paged.getContent().stream().map(PaymentRecapRow::getRequestorUserId).collect(Collectors.toList())));
        return "payment/recap";
    }

    /**
     * The three-sheet financial report.
     *
     * <p>Same shape as {@code RequestController#documentPdf}: guard first,
     * {@code ResponseEntity<byte[]>}, a hand-built Content-Disposition, and
     * the bytes produced by a dedicated service rather than here.</p>
     *
     * <p>Guarded on {@code LIST_URL} — the read grant for this screen. The
     * report contains nothing the page does not already show, so demanding a
     * second permission for the same information would only invite somebody
     * to grant it inconsistently.</p>
     */
    @GetMapping(value = "/report.xlsx",
            produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    public ResponseEntity<byte[]> reportXlsx(@RequestParam(value = "from", required = false)
                                             @DateTimeFormat(pattern = ISO_DATE) Date from,
                                             @RequestParam(value = "to", required = false)
                                             @DateTimeFormat(pattern = ISO_DATE) Date to) {
        pageAccessGuard.require(LIST_URL);
        byte[] xlsx = excelService.render(from, to);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + excelService.fileNameFor(from, to) + "\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(xlsx);
    }

    private List<PaymentRecapRow> toRows(List<SesRequestPayment> payments) {
        // Full names, not usernames. recap.html shows the requestor column
        // through UserDisplayNameService and this one mapped to
        // AppUser::getUsername, so a single row read "Siti Aminah ... verified
        // by bdarmawan" — two naming conventions side by side in the same
        // table, which reads as two different kinds of person.
        Map<String, String> userNames = userDisplayNameService.displayNames(payments.stream()
                .map(SesRequestPayment::getReviewedBy)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet()));
        // Transaction number and owner come from one batched descriptor call
        // for the whole page (PaymentService.rowData), not from three
        // findById-per-row accessors. The recap has no date bound by default,
        // so this list is every verified payment ever -- the per-row shape
        // scaled its query count with the ledger.
        Map<Long, PaymentRowData> data = paymentService.rowData(payments);
        return payments.stream().map(p -> {
            PaymentRowData d = data.get(p.getPaymentId());
            String reviewedByName = p.getReviewedBy() != null ? userNames.get(p.getReviewedBy()) : null;
            return new PaymentRecapRow(p.getPaymentId(),
                    d == null ? null : d.getTransactionNo(),
                    paymentService.descriptorFor(p.getBusinessType()).displayName(),
                    d == null ? null : d.getRequestorUsername(),
                    p.getPaidAmount(), p.getReviewedDate(), reviewedByName);
        }).collect(Collectors.toList());
    }
}
