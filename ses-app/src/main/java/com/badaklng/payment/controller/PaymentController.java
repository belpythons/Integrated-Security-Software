package com.badaklng.payment.controller;

import com.badaklng.payment.entity.SesRequestPayment;
import com.badaklng.payment.entity.SesRequestPaymentHistory;
import com.badaklng.payment.model.PaymentListRow;
import com.badaklng.payment.service.PaymentService;
import com.badaklng.payment.spi.PaymentBusinessDescriptor;
import com.badaklng.platform.security.PageAccessGuard;
import com.badaklng.platform.security.SecurityUtils;
import com.badaklng.platform.system.service.AppLogService;
import com.badaklng.ses.user.UserDisplayNameService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import com.badaklng.ses.web.PageRequests;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Requestor-facing payment screens: "/payment" (list, read) + "/payment/form"
 * (virtual CRUD/SUBMIT-grant URL — no controller mapping exists there, only
 * the app_page row, same convention as RequestController). A plain
 * requestor can only VIEW a payment item and upload proof for one that
 * already exists — items are system-created by each business module.
 * <p>
 * An OFFICER (anyone holding SUBMIT on {@code /payment/verify/form} — the
 * same grant that lets them approve/reject on the verification queue) also
 * sees every OTHER requestor's PENDING items here, and can pay on their
 * behalf via {@link #officerSubmit} — see {@link PaymentService#submitAndVerifyOnBehalf}.
 */
@Controller
@RequestMapping("/payment")
@RequiredArgsConstructor
public class PaymentController {

    public static final String LIST_URL = "/payment";
    public static final String FORM_URL = "/payment/form";
    private static final String VERIFY_FORM_URL = "/payment/verify/form";

    private final PaymentService paymentService;
    /** Read-only, for the approval-flow card — the mutating side goes through PaymentService. */
    private final com.badaklng.approval.service.ApprovalService approvalService;
    private final PageAccessGuard pageAccessGuard;
    private final AppLogService logService;
    private final UserDisplayNameService userDisplayNameService;

    @GetMapping
    public String myList(Model model,
                         @RequestParam(required = false) String q,
                         @RequestParam(required = false) String status,
                         @RequestParam(required = false) String module,
                         @RequestParam(required = false) Integer page,
                         @RequestParam(required = false) Integer size) {
        model.addAttribute("pageTitle", pageAccessGuard.require(LIST_URL));
        String username = SecurityUtils.getCurrentUsername();
        boolean officer = isOfficer();
        model.addAttribute("officerMode", officer);

        List<SesRequestPayment> payments;
        if (officer) {
            Map<Long, SesRequestPayment> byId = new LinkedHashMap<>();
            for (SesRequestPayment p : paymentService.findMine(username)) {
                byId.put(p.getPaymentId(), p);
            }
            for (SesRequestPayment p : paymentService.findForOfficer()) {
                byId.put(p.getPaymentId(), p);
            }
            payments = new ArrayList<>(byId.values());
            payments.sort(Comparator.comparing(SesRequestPayment::getPaymentId).reversed());
        } else {
            payments = paymentService.findMine(username);
        }
        // Rows are built first, then filtered, then sliced.
        String term = PageRequests.term(q);
        String statusTerm = PageRequests.term(status);
        String moduleTerm = PageRequests.term(module);

        List<PaymentListRow> allRows = paymentService.listRows(payments);
        Map<String, String> requestorDisplayNames = userDisplayNameService.displayNames(
                allRows.stream().map(PaymentListRow::getRequestorUserId).collect(Collectors.toList()));

        List<PaymentListRow> matching = allRows.stream()
                .filter(r -> {
                    String displayName = requestorDisplayNames.get(r.getRequestorUserId());
                    boolean matchQ = term == null || PageRequests.matches(term,
                            r.getTransactionNo(),
                            r.getModuleName(),
                            r.getBusinessType(),
                            r.getRequestorUserId(),
                            displayName,
                            r.getStatus() == null ? null : r.getStatus().name());
                    boolean matchStatus = statusTerm == null
                            || (r.getStatus() != null && (
                                    r.getStatus().name().equalsIgnoreCase(statusTerm)
                                    || ("WAITING_PAYMENT".equalsIgnoreCase(statusTerm) && r.getStatus() == com.badaklng.payment.entity.PaymentStatus.PENDING)
                                    || ("APPROVED".equalsIgnoreCase(statusTerm) && r.getStatus() == com.badaklng.payment.entity.PaymentStatus.VERIFIED)
                            ));
                    boolean matchModule = moduleTerm == null
                            || (r.getBusinessType() != null && r.getBusinessType().equalsIgnoreCase(moduleTerm));
                    return matchQ && matchStatus && matchModule;
                })
                .collect(Collectors.toList());

        Page<PaymentListRow> paged = PageRequests.slice(matching, PageRequests.of(page, size, "paymentId"));
        List<PaymentListRow> paymentRows = paged.getContent();
        model.addAttribute("payments", paymentRows);
        model.addAttribute("page", paged);
        model.addAttribute("requestorNames", requestorDisplayNames);
        return "payment/list";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        model.addAttribute("pageTitle", pageAccessGuard.require(FORM_URL));
        SesRequestPayment payment = paymentService.findById(id);
        String username = SecurityUtils.getCurrentUsername();
        requireOwner(payment, username);

        List<SesRequestPaymentHistory> history = paymentService.findHistory(id);
        model.addAttribute("payment", payment);
        model.addAttribute("paymentHistory", history);
        model.addAttribute("historyActorNames", actorNames(history));
        PaymentBusinessDescriptor descriptor = paymentService.descriptorFor(payment.getBusinessType());
        descriptor.detailModel(payment.getBusinessRefId()).forEach(model::addAttribute);
        addDetailFragment(model, descriptor.detailFragment());
        Long amountDue = descriptor.amountDue(payment.getBusinessRefId());
        model.addAttribute("paymentAmount", amountDue);
        String requestor = descriptor.requestorUsername(payment.getBusinessRefId());
        boolean isOwner = requestor != null && requestor.equalsIgnoreCase(username);
        model.addAttribute("isOwner", isOwner);
        model.addAttribute("officerMode", isOfficer());
        model.addAttribute("approvalSummary",
                approvalService.summaryModel(payment.getBusinessType(), payment.getBusinessRefId()));
        return "payment/detail";
    }

    /**
     * History actor ids -> the name a human recognises.
     *
     * <p>Resolved through {@code UserDisplayNameService}, which prefers
     * {@code full_name} and only falls back to the username. This used to map
     * straight to {@code AppUser::getUsername}, so the same person appeared as
     * "SITI AMINAH" in a notification email and as "saminah" on the page that
     * email linked to — the exact inconsistency that service was extracted to
     * end.</p>
     *
     * <p>One batched query for the whole table rather than a {@code findById}
     * per row.</p>
     */
    private Map<Long, String> actorNames(List<SesRequestPaymentHistory> history) {
        Map<String, String> byRef = userDisplayNameService.displayNames(history.stream()
                .map(SesRequestPaymentHistory::getActorUserId)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet()));
        Map<Long, String> names = new HashMap<>();
        for (SesRequestPaymentHistory h : history) {
            names.put(h.getHistoryId(),
                    byRef.getOrDefault(h.getActorUserId(), h.getActorUserId()));
        }
        return names;
    }

    @PostMapping("/{id}/submit")
    public String submit(@PathVariable Long id, @RequestParam("proofFile") MultipartFile proofFile,
                         RedirectAttributes ra, HttpServletRequest request) {
        pageAccessGuard.requireAction(FORM_URL, "SUBMIT");
        String username = SecurityUtils.getCurrentUsername();
        SesRequestPayment payment = paymentService.findById(id);
        requireOwner(payment, username);
        try {
            paymentService.submitProof(id, proofFile, username);
            logService.log("UPDATE", "SesRequestPayment", "Uploaded proof of payment " + id, id, "SES_REQUEST_PAYMENT", request);
            ra.addFlashAttribute("successMsg", "Proof of payment uploaded — awaiting verification.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            ra.addFlashAttribute("errorMsg", ex.getMessage());
        }
        return "redirect:" + LIST_URL + "/" + id;
    }

    /**
     * Officer pays on behalf of the requestor — uploads proof AND records
     * the actual amount received in one step, skipping the normal
     * verification queue entirely (the officer submitting IS the
     * verification). Re-checks the officer grant server-side (defense in
     * depth — the button is hidden client-side by the same check, but a
     * direct POST must not bypass it) rather than trusting only the hidden
     * "Bayarkan" button in list.html/detail.html.
     */
    @PostMapping("/{id}/officer-submit")
    public String officerSubmit(@PathVariable Long id, @RequestParam("proofFile") MultipartFile proofFile,
                                 @RequestParam("paidAmount") Long paidAmount,
                                 @RequestParam(value = "note", required = false) String note,
                                 RedirectAttributes ra, HttpServletRequest request) {
        pageAccessGuard.requireAction(VERIFY_FORM_URL, "SUBMIT");
        String username = SecurityUtils.getCurrentUsername();
        try {
            paymentService.submitAndVerifyOnBehalf(id, proofFile, paidAmount, note, username);
            logService.log("UPDATE", "SesRequestPayment",
                    "Officer submitted and verified proof of payment " + id + " on behalf of the requestor",
                    id, "SES_REQUEST_PAYMENT", request);
            ra.addFlashAttribute("successMsg", "Payment recorded on behalf of the requestor.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            ra.addFlashAttribute("errorMsg", ex.getMessage());
        }
        return "redirect:" + LIST_URL;
    }

    /**
     * Splits a "template :: selector" fragment spec into two model attributes
     * instead of one. Thymeleaf's {@code th:insert="${detailFragment}"} looks
     * broken but ISN'T a typo fix candidate — {@code th:insert}/{@code
     * th:replace} only recognise the {@code ::} fragment separator when it is
     * literally present in the TAG'S OWN MARKUP TEXT at parse time
     * (AbstractStandardFragmentInsertionTagProcessor#shouldBeWrappedAsFragmentExpression
     * scans the raw attribute text, not any runtime value); a single Java
     * String containing "::" evaluated via {@code ${...}} gets treated as one
     * whole (bogus) template name — including the "::" — and template
     * resolution throws. Splitting server-side and writing
     * {@code th:insert="${detailFragmentTemplate} :: ${detailFragmentSelector}"}
     * keeps a literal {@code ::} in the markup so Thymeleaf's parser splits
     * it correctly while both sides still resolve dynamically.
     */
    private void addDetailFragment(Model model, String fragment) {
        if (fragment == null) {
            return;
        }
        String[] parts = fragment.split("::", 2);
        model.addAttribute("detailFragmentTemplate", parts[0].trim());
        model.addAttribute("detailFragmentSelector", parts.length > 1 ? parts[1].trim() : null);
    }

    private boolean isOfficer() {
        return pageAccessGuard.hasAction(VERIFY_FORM_URL, "SUBMIT");
    }

    private void requireOwner(SesRequestPayment payment, String username) {
        String requestor = paymentService.descriptorFor(payment.getBusinessType())
                .requestorUsername(payment.getBusinessRefId());
        boolean isOwner = requestor != null && requestor.equalsIgnoreCase(username);
        // An officer may open (but not otherwise mutate through this
        // controller) another requestor's item — needed so the "Bayarkan"
        // modal on list.html can read amountDue before posting.
        if (!isOwner && !isOfficer()) {
            throw new AccessDeniedException("You do not have access to this payment item.");
        }
    }

}
