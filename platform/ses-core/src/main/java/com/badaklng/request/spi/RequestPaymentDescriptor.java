package com.badaklng.request.spi;

import com.badaklng.payment.model.PaymentRowData;
import com.badaklng.payment.spi.PaymentBusinessDescriptor;
import com.badaklng.request.entity.SesRequest;
import com.badaklng.request.entity.SesRequestType;
import com.badaklng.request.repository.SesRequestRepository;
import com.badaklng.request.repository.SesRequestTypeRepository;
import com.badaklng.request.repository.SesRequestWorkerRepository;
import com.badaklng.request.service.RequestService;
import com.badaklng.ses.jdbc.InChunks;
import com.badaklng.ses.text.SesText;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * The request module's plug-in to the generalised payment engine (see
 * {@link PaymentBusinessDescriptor}) — the ONLY class in this module allowed
 * to import {@code com.badaklng.payment.spi.*}, mirroring
 * {@link RequestApprovalBridge}'s role for {@code approval.spi}.
 */
@Component
@RequiredArgsConstructor
public class RequestPaymentDescriptor implements PaymentBusinessDescriptor {

    private final SesRequestRepository requestRepository;
    private final SesRequestTypeRepository requestTypeRepository;
    private final SesRequestWorkerRepository requestWorkerRepository;
    private final RequestService requestService;
    private final SesText text;

    @Override
    public String businessType() {
        return "SES_REQUEST";
    }

    @Override
    public String displayName() {
        return text.get("SES_TXT_MODULE_REQUEST_PAYMENT_NAME", "Badge Karyawan");
    }

    @Override
    public String transactionNo(Long businessRefId) {
        return requestRepository.findById(businessRefId).map(SesRequest::getRequestNo).orElse(null);
    }

    @Override
    public String requestorUsername(Long businessRefId) {
        return requestRepository.findById(businessRefId).map(SesRequest::getRequestorUserId).orElse(null);
    }

    @Override
    public List<Long> myRefIds(String username) {
        return requestRepository.findByRequestorUserIdOrderByRequestIdDesc(username).stream()
                .map(SesRequest::getRequestId)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public Long amountDue(Long businessRefId) {
        Optional<SesRequest> found = requestRepository.findById(businessRefId);
        if (!found.isPresent()) {
            return null;
        }
        SesRequest request = found.get();
        SesRequestType type = requestTypeRepository.findById(request.getTypeCode()).orElse(null);
        if (type == null || type.getPaymentAmount() == null) {
            return null;
        }
        return type.getPaymentAmount() * requestService.workerCount(businessRefId);
    }

    /**
     * Three queries for a whole list page, regardless of row count: the
     * requests, their types, and one grouped worker count. The inherited
     * default would instead run {@code findById} three times per row plus a
     * {@code COUNT(*)} — which is what made /payment issue 86 queries.
     */
    @Override
    @Transactional(readOnly = true)
    public Map<Long, PaymentRowData> rowData(Collection<Long> businessRefIds) {
        Map<Long, PaymentRowData> out = new LinkedHashMap<>();
        if (businessRefIds == null || businessRefIds.isEmpty()) {
            return out;
        }

        List<SesRequest> requests = InChunks.flatMap(businessRefIds, requestRepository::findAllById);

        Map<String, Long> amountByType = new HashMap<>();
        List<String> typeCodes = requests.stream()
                .map(SesRequest::getTypeCode)
                .filter(code -> code != null)
                .distinct()
                .collect(Collectors.toList());
        for (SesRequestType type : InChunks.flatMap(typeCodes, requestTypeRepository::findAllById)) {
            amountByType.put(type.getTypeCode(), type.getPaymentAmount());
        }

        // GROUP BY cannot emit a zero row, so a request with no workers is
        // absent here and falls back to 0 below — same arithmetic the
        // per-row path produced via countByRequestId.
        Map<Long, Long> workerCounts = new HashMap<>();
        List<Long> requestIds = requests.stream().map(SesRequest::getRequestId).collect(Collectors.toList());
        for (Object[] row : InChunks.flatMap(requestIds, requestWorkerRepository::countGroupedByRequestId)) {
            workerCounts.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
        }

        for (SesRequest request : requests) {
            Long configured = amountByType.get(request.getTypeCode());
            Long due = configured == null
                    ? null
                    : configured * workerCounts.getOrDefault(request.getRequestId(), 0L);
            out.put(request.getRequestId(), new PaymentRowData(
                    request.getRequestNo(), request.getRequestorUserId(), due));
        }
        return out;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> detailModel(Long businessRefId) {
        return requestService.detailModel(businessRefId);
    }

    @Override
    public String detailFragment() {
        return "request/fragments/request-detail :: detail";
    }
}
