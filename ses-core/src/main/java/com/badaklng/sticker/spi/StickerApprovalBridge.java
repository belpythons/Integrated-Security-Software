package com.badaklng.sticker.spi;

import com.badaklng.approval.entity.ApprovalStatus;
import com.badaklng.approval.spi.ApprovalFormDescriptor;
import com.badaklng.approval.spi.ApprovalOutcomeListener;
import com.badaklng.payment.service.PaymentService;
import com.badaklng.ses.jdbc.InChunks;
import com.badaklng.ses.text.SesText;
import com.badaklng.sticker.entity.SesStickerRequest;
import com.badaklng.sticker.model.StickerEditForm;
import com.badaklng.sticker.repository.SesStickerRequestRepository;
import com.badaklng.sticker.service.StickerService;
import java.util.Collection;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The ENTIRE coupling surface between com.badaklng.sticker and the approval
 * module — the only class in this package allowed to import
 * com.badaklng.approval.spi.* or com.badaklng.approval.entity.ApprovalStatus,
 * mirroring {@code com.badaklng.request.spi.RequestApprovalBridge}'s role.
 * <p>
 * Unlike the request module, this ALSO absorbs the payment-outcome cleanup
 * {@code com.badaklng.payment.spi.PaymentApprovalBridge} handles separately
 * for SES_REQUEST — a deliberate simplification (one listener bean instead
 * of two) since {@link PaymentService} is a plain {@code @Service} this
 * module can depend on directly without touching {@code payment.spi}.
 * {@code matchKeys()} is left at the interface default (empty) — this form
 * has no routing dropdown; every submission always resolves the ONE default
 * chain an admin maps via /approval/map ({@link StickerService#saveAndSubmit}
 * calls the null-matchKey {@code ApprovalService#trigger} overload).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StickerApprovalBridge implements ApprovalFormDescriptor, ApprovalOutcomeListener {

    private final SesStickerRequestRepository requestRepository;
    private final StickerService stickerService;
    private final PaymentService paymentService;
    private final SesText text;

    @Override
    public String formCode() {
        return StickerService.APPROVAL_FORM_CODE;
    }

    @Override
    public String formName() {
        return text.get("SES_TXT_MODULE_STICKER_NAME", "Stiker Kendaraan");
    }

    @Override
    public String businessRefType() {
        return StickerService.APPROVAL_FORM_CODE;
    }

    @Override
    public String transactionId(Long businessRefId) {
        return requestRepository.findById(businessRefId).map(SesStickerRequest::getRequestNo).orElse(null);
    }

    @Override
    public String description(Long businessRefId) {
        return requestRepository.findById(businessRefId).map(SesStickerRequest::getRequestorName).orElse(null);
    }

    /** One query for a whole worklist page instead of two per row. */
    @Override
    @Transactional(readOnly = true)
    public Map<Long, FormRowData> rowData(Collection<Long> businessRefIds) {
        Map<Long, FormRowData> out = new LinkedHashMap<>();
        if (businessRefIds == null || businessRefIds.isEmpty()) {
            return out;
        }
        for (SesStickerRequest r : InChunks.flatMap(businessRefIds, requestRepository::findAllById)) {
            out.put(r.getStickerRequestId(), new FormRowData(r.getRequestNo(), r.getRequestorName()));
        }
        return out;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> detailModel(Long businessRefId) {
        return stickerService.detailModel(businessRefId);
    }

    /**
     * The "Data Pemohon" rows from sticker/fragments/sticker-detail.html,
     * flattened for the notification email — same labels, same order, so the
     * mail and the page it links to read alike.
     *
     * <p>"Data Kendaraan" is collapsed to a count. That table has one row per
     * vehicle with nine columns each and no upper bound, while the mail body
     * column holds 4000 characters and truncates silently; the vehicles are a
     * click away via the button. Blank fields are dropped, not dashed — see
     * {@link com.badaklng.approval.spi.ApprovalFormDescriptor#emailDetail}.</p>
     */
    @Override
    @Transactional(readOnly = true)
    public Map<String, String> emailDetail(Long businessRefId) {
        Map<String, Object> model = stickerService.detailModel(businessRefId);
        StickerEditForm form = (StickerEditForm) model.get("stickerForm");
        if (form == null) {
            return Collections.emptyMap();
        }
        // "Nama Pemohon" is omitted: the mail card already opens with a
        // "Diajukan Oleh" row carrying the submitting account's real name.
        Map<String, String> rows = new LinkedHashMap<>();
        put(rows, "Badge", form.getBadge());
        put(rows, "Departemen", form.getDepartment());
        put(rows, "Seksi", form.getSection());
        put(rows, "Instansi", form.getInstance());
        put(rows, "No. Telepon", form.getPhoneNo());
        put(rows, "Alamat", form.getAddress());
        Long amount = (Long) model.get("stickerAmount");
        if (amount != null) {
            rows.put("Jumlah Dibayar", "Rp " + String.format("%,d", amount).replace(',', '.'));
        }
        if (!form.getVehicles().isEmpty()) {
            rows.put("Jumlah Kendaraan", String.valueOf(form.getVehicles().size()));
        }
        return rows;
    }

    private static void put(Map<String, String> rows, String label, String value) {
        if (value != null && !value.trim().isEmpty()) {
            rows.put(label, value.trim());
        }
    }

    /** {@code StickerController#view} — the requestor's own read-only page. */
    @Override
    public String requestorViewPath(Long businessRefId) {
        return "/sticker/" + businessRefId + "/view";
    }

    @Override
    public String detailFragment() {
        return "sticker/fragments/sticker-detail :: detail";
    }

    @Override
    @Transactional
    public void onCompleted(Long businessRefId, ApprovalStatus finalStatus) {
        Optional<SesStickerRequest> found = requestRepository.findById(businessRefId);
        if (!found.isPresent()) {
            log.warn("ApprovalOutcomeListener: no SES_STICKER_REQUEST found for sticker_request_id={} (final status {})",
                    businessRefId, finalStatus);
            return;
        }
        SesStickerRequest request = found.get();
        request.setStatus(finalStatus.name());
        requestRepository.save(request);

        if (finalStatus == ApprovalStatus.REJECTED) {
            paymentService.onRequestRejected(businessRefType(), businessRefId);
        }
    }
}
