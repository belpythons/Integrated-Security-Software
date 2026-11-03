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
import com.badaklng.ses.web.PageRequests;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Admin-facing payment verification screens: "/payment/verify" (queue,
 * read) + "/payment/verify/form" (virtual CRUD/SUBMIT-grant URL for the
 * detail+approve/reject page). Approving unlocks the PAYMENT_VERIFICATION
 * checkpoint on whichever approval-chain step a chain admin wired it to
 * (see PaymentService#approve); rejecting requires a reason and returns
 * the item to the requestor for re-upload.
 */
@Controller
@RequestMapping("/payment/verify")
@RequiredArgsConstructor
public class PaymentVerificationController {

    public static final String LIST_URL = "/payment/verify";
    public static final String FORM_URL = "/payment/verify/form";

    private final PaymentService paymentService;
    /** Read-only, for the approval-flow card. */
    private final com.badaklng.approval.service.ApprovalService approvalService;
    private final PageAccessGuard pageAccessGuard;
    private final AppLogService logService;
    private final UserDisplayNameService userDisplayNameService;

    @GetMapping
    public String queue(Model model,
                        @RequestParam(required = false) String q,
                        @RequestParam(required = false) String module,
                        @RequestParam(required = false) Integer page,
                        @RequestParam(required = false) Integer size) {
        model.addAttribute("pageTitle", pageAccessGuard.require(LIST_URL));
        String term = PageRequests.term(q);
        String moduleTerm = PageRequests.term(module);
        List<PaymentListRow> matching = paymentService.listRows(paymentService.findQueue()).stream()
                .filter(r -> {
                    boolean matchQ = term == null || PageRequests.matches(term, r.getTransactionNo(), r.getModuleName(),
                            r.getRequestorUserId());
                    boolean matchModule = moduleTerm == null || (r.getBusinessType() != null && r.getBusinessType().equalsIgnoreCase(moduleTerm));
                    return matchQ && matchModule;
                })
                .collect(Collectors.toList());
        Page<PaymentListRow> paged = PageRequests.slice(matching,
                PageRequests.of(page, size, "paymentId"));
        List<PaymentListRow> paymentRows = paged.getContent();
        model.addAttribute("payments", paymentRows);
        model.addAttribute("page", paged);
        // Nama lengkap di samping username. Satu query batch untuk seluruh
        // halaman lewat UserDisplayNameService.displayNames — layar ini
        // menampilkan username mentah sejak dibuat, yaitu regresi yang
        // javadoc service itu sendiri tulis sebagai alasan keberadaannya.
        model.addAttribute("requestorNames", userDisplayNameService.displayNames(
                paymentRows.stream().map(PaymentListRow::getRequestorUserId).collect(Collectors.toList())));
        return "payment/verify-list";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        model.addAttribute("pageTitle", pageAccessGuard.require(FORM_URL));
        SesRequestPayment payment = paymentService.findById(id);
        List<SesRequestPaymentHistory> history = paymentService.findHistory(id);
        model.addAttribute("payment", payment);
        model.addAttribute("paymentHistory", history);
        model.addAttribute("historyActorNames", actorNames(history));
        model.addAttribute("checkpointReachability",
                paymentService.checkpointReachability(payment.getBusinessType(), payment.getBusinessRefId()));
        PaymentBusinessDescriptor descriptor = paymentService.descriptorFor(payment.getBusinessType());
        descriptor.detailModel(payment.getBusinessRefId()).forEach(model::addAttribute);
        addDetailFragment(model, descriptor.detailFragment());
        model.addAttribute("paymentAmount", descriptor.amountDue(payment.getBusinessRefId()));
        // The whole point of this screen is deciding whether money that came in
        // belongs to this request — which needs the approval flow next to it,
        // not just the payment's own history.
        model.addAttribute("approvalSummary",
                approvalService.summaryModel(payment.getBusinessType(), payment.getBusinessRefId()));
        return "payment/verify-detail";
    }

    /**
     * Splits a "template :: selector" fragment spec into two model attributes
     * instead of one — see the same-named helper's javadoc on {@code
     * PaymentController} for why a single combined string breaks Thymeleaf's
     * {@code th:insert}/{@code th:replace} dynamic fragment resolution.
     */
    private void addDetailFragment(Model model, String fragment) {
        if (fragment == null) {
            return;
        }
        String[] parts = fragment.split("::", 2);
        model.addAttribute("detailFragmentTemplate", parts[0].trim());
        model.addAttribute("detailFragmentSelector", parts.length > 1 ? parts[1].trim() : null);
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

    @PostMapping("/{id}/approve")
    public String approve(@PathVariable Long id, @RequestParam(value = "note", required = false) String note,
                          @RequestParam("paidAmount") Long paidAmount,
                          @RequestParam(value = "returnTo", required = false) String returnTo,
                          RedirectAttributes ra, HttpServletRequest request) {
        String username = SecurityUtils.getCurrentUsername();
        SesRequestPayment payment = paymentService.findById(id);
        checkCanVerify(payment, username);
        try {
            paymentService.approve(id, note, paidAmount, username);
            logService.log("APPROVE", "SesRequestPayment", "Approved proof of payment " + id, id, "SES_REQUEST_PAYMENT", request);
            ra.addFlashAttribute("successMsg", "Payment verified.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            ra.addFlashAttribute("errorMsg", ex.getMessage());
        }
        return redirectTo(returnTo, LIST_URL + "/" + id);
    }

    @PostMapping("/{id}/reject")
    public String reject(@PathVariable Long id, @RequestParam("reason") String reason,
                         @RequestParam(value = "returnTo", required = false) String returnTo,
                         RedirectAttributes ra, HttpServletRequest request) {
        String username = SecurityUtils.getCurrentUsername();
        SesRequestPayment payment = paymentService.findById(id);
        checkCanVerify(payment, username);
        try {
            paymentService.reject(id, reason, username);
            logService.log("REJECT", "SesRequestPayment", "Rejected proof of payment " + id, id, "SES_REQUEST_PAYMENT", request);
            ra.addFlashAttribute("successMsg", "Payment rejected.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            ra.addFlashAttribute("errorMsg", ex.getMessage());
        }
        return redirectTo(returnTo, LIST_URL + "/" + id);
    }

    private void checkCanVerify(SesRequestPayment payment, String username) {
        if (pageAccessGuard.hasAction(FORM_URL, "SUBMIT")) {
            return;
        }
        if (payment != null && approvalService.canActOnBusiness(payment.getBusinessType(), payment.getBusinessRefId(), username)) {
            return;
        }
        pageAccessGuard.requireAction(FORM_URL, "SUBMIT");
    }


    /**
     * Where to land after an action: back to the approval detail page the
     * inline form was rendered on, when it asked for that, else this module's
     * own page as before.
     *
     * <p>Whitelisted to the one prefix rather than "any relative path": a
     * redirect target taken from a request parameter is an open redirect
     * waiting to happen, and this only ever needs the one destination.</p>
     */
    private static String redirectTo(String returnTo, String fallback) {
        if (returnTo != null && returnTo.startsWith("/approval/detail/")
                && returnTo.indexOf("//") < 0 && returnTo.indexOf(':') < 0) {
            return "redirect:" + returnTo;
        }
        return "redirect:" + fallback;
    }

}
