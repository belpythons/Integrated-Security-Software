package com.badaklng.request.controller;

import com.badaklng.approval.service.ApprovalService;
import com.badaklng.request.entity.SesRequest;
import com.badaklng.request.repository.SesRequestRepository;
import com.badaklng.request.repository.SesRequestTypeRepository;
import com.badaklng.request.service.RequestService;
import com.badaklng.ses.user.UserDisplayNameService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * The page a QR scan lands on: {@code /verify/{requestNo}}.
 *
 * <p>Standalone and deliberately plain — it is read on a phone at a gate, not
 * at a desk. It uses no layout decorator, so it renders without the sidebar,
 * navbar or any of the application chrome that would be noise on a 5-inch
 * screen.</p>
 *
 * <p><b>Publicly accessible, with sensitive personal data hidden.</b>
 * Anyone scanning the barcode on the printed sheet can confirm its validity and
 * view the request details without exposing workers' NIK / personal data.</p>
 */
@Controller
@RequestMapping("/verify")
@RequiredArgsConstructor
public class RequestVerifyController {

    /**
     * SimpleDateFormat is not thread-safe, so this is a pattern rather than a shared instance -- two concurrent requests sharing one formatter mutate its internal Calendar mid-format, and the result is a silently wrong date, not an exception.
     *
     * <p>This is the public QR landing page -- the one place in SES most
     * likely to be hit by several people at once, from several phones at a
     * gate.</p>
     */
    private static final String SHORT_DATE_PATTERN = "dd-MM-yyyy";

    private final SesRequestRepository requestRepository;
    private final SesRequestTypeRepository requestTypeRepository;
    private final RequestService requestService;
    private final ApprovalService approvalService;
    private final UserDisplayNameService userDisplayNameService;

    @GetMapping("/{requestNo}")
    public String verify(@PathVariable String requestNo, Model model) {
        model.addAttribute("requestNo", requestNo);

        Optional<SesRequest> found = requestRepository.findByRequestNo(requestNo);
        if (!found.isPresent()) {
            model.addAttribute("found", false);
            return "request/verify";
        }

        SesRequest request = found.get();
        model.addAttribute("found", true);
        model.addAttribute("request", request);
        model.addAttribute("documentName", requestTypeRepository.findById(request.getTypeCode())
                .map(t -> t.getTypeName()).orElse(request.getTypeCode()));
        model.addAttribute("siteManagerName",
                userDisplayNameService.displayName(request.getRequestorUserId()));
        model.addAttribute("startPeriod", request.getStartPeriod() == null
                ? "-" : new SimpleDateFormat(SHORT_DATE_PATTERN).format(request.getStartPeriod()));
        model.addAttribute("finishPeriod", request.getFinishPeriod() == null
                ? "-" : new SimpleDateFormat(SHORT_DATE_PATTERN).format(request.getFinishPeriod()));
        model.addAttribute("workers", requestService.findWorkers(request.getRequestId()));

        List<ApprovalService.SignatureSlot> slots = request.getApprovalInstanceId() == null
                ? Collections.emptyList()
                : approvalService.signatureSlots(request.getApprovalInstanceId());

        String procurementSigner = null;
        for (ApprovalService.SignatureSlot slot : slots) {
            if ("Procurement".equalsIgnoreCase(slot.getRoleLabel())
                    || (slot.getRoleLabel() != null && slot.getRoleLabel().toLowerCase().contains("procurement"))) {
                procurementSigner = slot.isSkipped() ? null : slot.getSignerName();
                break;
            }
        }
        model.addAttribute("procurementSigner", procurementSigner);
        model.addAttribute("approved", "APPROVED".equals(request.getStatus()));
        return "request/verify";
    }
}
