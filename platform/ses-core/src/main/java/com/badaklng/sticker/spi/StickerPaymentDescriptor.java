package com.badaklng.sticker.spi;

import com.badaklng.payment.model.PaymentRowData;
import com.badaklng.payment.spi.PaymentBusinessDescriptor;
import com.badaklng.ses.jdbc.InChunks;
import com.badaklng.sticker.entity.SesStickerRequest;
import com.badaklng.sticker.repository.SesStickerRequestRepository;
import com.badaklng.sticker.service.StickerService;
import com.badaklng.ses.text.SesText;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The sticker module's plug-in to the generalised payment engine (see
 * {@link PaymentBusinessDescriptor}) — the ONLY class in this module
 * allowed to import {@code com.badaklng.payment.spi.*}, mirroring {@code
 * com.badaklng.request.spi.RequestPaymentDescriptor}'s role.
 */
@Component
@RequiredArgsConstructor
public class StickerPaymentDescriptor implements PaymentBusinessDescriptor {

    private final SesStickerRequestRepository requestRepository;
    private final StickerService stickerService;
    private final SesText text;

    @Override
    public String businessType() {
        return StickerService.APPROVAL_FORM_CODE;
    }

    @Override
    public String displayName() {
        return text.get("SES_TXT_MODULE_STICKER_NAME", "Stiker Kendaraan");
    }

    @Override
    public String transactionNo(Long businessRefId) {
        return requestRepository.findById(businessRefId).map(SesStickerRequest::getRequestNo).orElse(null);
    }

    @Override
    public String requestorUsername(Long businessRefId) {
        return requestRepository.findById(businessRefId).map(SesStickerRequest::getRequestorUserId).orElse(null);
    }

    @Override
    public List<Long> myRefIds(String username) {
        return requestRepository.findByRequestorUserIdOrderByStickerRequestIdDesc(username).stream()
                .map(SesStickerRequest::getStickerRequestId)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public Long amountDue(Long businessRefId) {
        return stickerService.amountDue(businessRefId);
    }

    /**
     * Bulk row data: one query for the requests plus the two inside
     * {@link StickerService#amountDue(Collection)}, instead of the inherited
     * default's three {@code findById}s and a vehicle lookup per row.
     */
    @Override
    @Transactional(readOnly = true)
    public Map<Long, PaymentRowData> rowData(Collection<Long> businessRefIds) {
        Map<Long, PaymentRowData> out = new LinkedHashMap<>();
        if (businessRefIds == null || businessRefIds.isEmpty()) {
            return out;
        }
        List<SesStickerRequest> requests = InChunks.flatMap(businessRefIds, requestRepository::findAllById);
        Map<Long, Long> dueById = stickerService.amountDue(requests.stream()
                .map(SesStickerRequest::getStickerRequestId)
                .collect(Collectors.toList()));
        for (SesStickerRequest request : requests) {
            out.put(request.getStickerRequestId(), new PaymentRowData(
                    request.getRequestNo(),
                    request.getRequestorUserId(),
                    dueById.get(request.getStickerRequestId())));
        }
        return out;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> detailModel(Long businessRefId) {
        return stickerService.detailModel(businessRefId);
    }

    @Override
    public String detailFragment() {
        return "sticker/fragments/sticker-detail :: detail";
    }
}
