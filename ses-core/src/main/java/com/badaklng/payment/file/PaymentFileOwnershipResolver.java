package com.badaklng.payment.file;

import com.badaklng.payment.entity.SesRequestPayment;
import com.badaklng.payment.repository.SesRequestPaymentRepository;
import com.badaklng.payment.service.PaymentService;
import com.badaklng.platform.security.PageAccessGuard;
import com.badaklng.ses.file.FileOwnershipResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Resolves access to proof-of-payment uploads ({@code SES_PAYMENT:<paymentId>}).
 * Unlike {@code RequestFileOwnershipResolver}'s documented v1 requestor-only
 * limitation, this resolver grants access to BOTH the owning requestor AND
 * any user holding SUBMIT on the admin verification detail page — reviewing
 * the proof is the entire point of the admin screen, so that case can't be
 * deferred to a later follow-up the way the request module's was. Ownership
 * is resolved generically via {@link PaymentService#descriptorFor} — this
 * resolver has no compile-time dependency on any one business module.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentFileOwnershipResolver implements FileOwnershipResolver {

    private static final String PAYMENT_PREFIX = "SES_PAYMENT:";
    private static final String VERIFY_FORM_URL = "/payment/verify/form";

    private final SesRequestPaymentRepository paymentRepository;
    private final PaymentService paymentService;
    private final PageAccessGuard pageAccessGuard;

    @Override
    public boolean supports(String refId) {
        return refId != null && refId.startsWith(PAYMENT_PREFIX);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean canAccess(String refId, String username, String userId) {
        Optional<SesRequestPayment> payment = resolvePayment(refId);
        if (!payment.isPresent()) {
            return false;
        }
        String requestor = paymentService.descriptorFor(payment.get().getBusinessType())
                .requestorUsername(payment.get().getBusinessRefId());
        if (requestor != null && requestor.equalsIgnoreCase(username)) {
            return true;
        }
        return pageAccessGuard.hasAction(VERIFY_FORM_URL, "SUBMIT");
    }

    private Optional<SesRequestPayment> resolvePayment(String refId) {
        Long paymentId = parseLong(refId.substring(PAYMENT_PREFIX.length()));
        return paymentId == null ? Optional.empty() : paymentRepository.findById(paymentId);
    }

    private Long parseLong(String s) {
        try {
            return Long.valueOf(s);
        } catch (NumberFormatException e) {
            log.warn("PaymentFileOwnershipResolver: non-numeric id in refId suffix: {}", s);
            return null;
        }
    }
}
