package com.badaklng.request.spi;

import com.badaklng.history.model.HistoryRow;
import com.badaklng.history.spi.HistorySource;
import com.badaklng.request.entity.SesRequest;
import com.badaklng.request.entity.SesRequestType;
import com.badaklng.request.repository.SesRequestRepository;
import com.badaklng.request.repository.SesRequestTypeRepository;
import com.badaklng.ses.user.UserDisplayNameService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The request module's plug-in to the Riwayat / History screen (see {@link
 * HistorySource}) — the ONLY class in this module allowed to import
 * {@code com.badaklng.history.*}, mirroring {@link RequestApprovalBridge}/
 * {@link RequestPaymentDescriptor}'s role for their respective SPI packages.
 */
@Component
@RequiredArgsConstructor
public class RequestHistorySource implements HistorySource {

    private final SesRequestRepository requestRepository;
    private final SesRequestTypeRepository requestTypeRepository;
    private final UserDisplayNameService userDisplayNameService;

    @Override
    public String businessType() {
        return "SES_REQUEST";
    }

    /** Same rule {@link #allRows()} applies: the chain's page once it exists, this module's own before that. */
    @Override
    public String viewUrl(Long businessRefId, Long approvalInstanceId) {
        return approvalInstanceId != null
                ? "/approval/detail/" + approvalInstanceId
                : "/request/" + businessRefId + "/view";
    }

    /** The permit sheet exists only once every approval step is done. */
    @Override
    public String documentUrl(Long businessRefId, String status) {
        return "APPROVED".equalsIgnoreCase(status)
                ? "/request/" + businessRefId + "/document.pdf"
                : null;
    }

    @Override
    @Transactional(readOnly = true)
    public List<HistoryRow> allRows() {
        Map<String, String> typeNames = new HashMap<>();
        for (SesRequestType t : requestTypeRepository.findAll()) {
            typeNames.put(t.getTypeCode(), t.getTypeName());
        }
        List<SesRequest> all = requestRepository.findAll();
        // One query for every distinct requestor, not one per row.
        Set<String> refs = all.stream().map(SesRequest::getRequestorUserId).collect(Collectors.toSet());
        Map<String, String> names = userDisplayNameService.displayNames(refs);

        return all.stream()
                .map(r -> {
                    String docUrl = "APPROVED".equalsIgnoreCase(r.getStatus())
                            ? "/request/" + r.getRequestId() + "/document.pdf"
                            : null;
                    return new HistoryRow("SES_REQUEST",
                            typeNames.getOrDefault(r.getTypeCode(), r.getTypeCode()),
                            r.getRequestNo(), r.getRequestorUserId(),
                            names.getOrDefault(r.getRequestorUserId(), r.getRequestorUserId()),
                            r.getStatus(), r.getCreatedDate(),
                            r.getApprovalInstanceId() != null
                                    ? "/approval/detail/" + r.getApprovalInstanceId()
                                    : "/request/" + r.getRequestId() + "/view",
                            docUrl,
                            r.getApprovalInstanceId());
                })
                .collect(Collectors.toList());

    }
}
