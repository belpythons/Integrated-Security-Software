package com.badaklng.payment.spi;

import com.badaklng.approval.entity.ApprovalStatus;
import com.badaklng.approval.spi.ApprovalOutcomeListener;
import com.badaklng.approval.spi.CheckpointActionProvider;
import com.badaklng.approval.spi.CheckpointTaskType;
import com.badaklng.approval.spi.SimpleCheckpointTaskType;
import com.badaklng.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The ENTIRE coupling surface between com.badaklng.payment and the
 * approval module's spi package — the only file in this module allowed
 * to import com.badaklng.approval.spi.* (mirrors
 * com.badaklng.request.spi.RequestApprovalBridge's role for the request
 * module). Everything else in this module calls {@link
 * com.badaklng.approval.service.ApprovalService} directly (a plain
 * {@code @Service} bean) but never touches approval's spi types.
 * <p>
 * Publishes the "PAYMENT_VERIFICATION" checkpoint key so a chain admin can
 * pick it from the checkpoint-task dropdown at /approval/chain/form —
 * approving a payment item (see PaymentService#approve) calls
 * ApprovalService#completeCheckpointByKey with this same key to unlock the
 * chain step it's wired to.
 * <p>
 * Also the {@link ApprovalOutcomeListener} for SES_REQUEST — reacts only to
 * a REJECTED outcome (the whole flow ending with no way forward) by
 * delegating to {@link PaymentService#onRequestRejected}; an APPROVED
 * outcome needs no payment-side action.
 */
@Configuration
@RequiredArgsConstructor
public class PaymentApprovalBridge implements ApprovalOutcomeListener, CheckpointActionProvider {

    private final PaymentService paymentService;

    @Bean
    public CheckpointTaskType paymentVerificationCheckpoint() {
        return SimpleCheckpointTaskType.of("PAYMENT_VERIFICATION", "Payment proof verified");
    }

    @Override
    public String businessRefType() {
        return "SES_REQUEST";
    }

    /**
     * The verification form, rendered straight onto the approval detail page.
     * Empty when no payment item exists (a type that needs no payment) — the
     * approver then just sees the manual Mark Done / Skip buttons.
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<CheckpointAction> actionFor(String checkpointKey, String businessRefType,
                                                Long businessRefId, String returnTo) {
        if (!"PAYMENT_VERIFICATION".equals(checkpointKey)) {
            return Optional.empty();
        }
        return paymentService.findByBusiness(businessRefType, businessRefId).map(payment -> {
            Map<String, Object> model = new LinkedHashMap<>();
            model.put("payment", payment);
            model.put("amountDue", paymentService.descriptorFor(payment.getBusinessType())
                    .amountDue(payment.getBusinessRefId()));
            model.put("returnTo", returnTo);
            return new CheckpointAction("payment/fragments/verify-inline", "form", model);
        });
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, String> summaryFor(String checkpointKey, String businessRefType, Long businessRefId) {
        if (!"PAYMENT_VERIFICATION".equals(checkpointKey)) {
            return Collections.emptyMap();
        }
        return paymentService.findByBusiness(businessRefType, businessRefId)
                .map(payment -> {
                    Map<String, String> rows = new LinkedHashMap<String, String>();
                    rows.put("Pembayaran", payment.getPaidAmount() != null
                            ? "Lunas - Rp " + String.format("%,d", payment.getPaidAmount()).replace(',', '.')
                            : payment.getStatus().name());
                    return rows;
                })
                .orElse(Collections.<String, String>emptyMap());
    }

    @Override
    @Transactional
    public void onCompleted(Long businessRefId, ApprovalStatus finalStatus) {
        if (finalStatus == ApprovalStatus.REJECTED) {
            paymentService.onRequestRejected(businessRefType(), businessRefId);
        }
    }
}
