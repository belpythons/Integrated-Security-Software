package com.badaklng.sticker.spi;

import com.badaklng.history.model.HistoryRow;
import com.badaklng.history.spi.HistorySource;
import com.badaklng.ses.user.UserDisplayNameService;
import com.badaklng.sticker.entity.SesStickerRequest;
import com.badaklng.sticker.repository.SesStickerRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The sticker module's plug-in to the Riwayat / History screen (see {@code
 * com.badaklng.history.spi.HistorySource}) — the ONLY class in this module
 * allowed to import {@code com.badaklng.history.*}, mirroring {@link
 * StickerApprovalBridge}/{@link StickerPaymentDescriptor}'s role for their
 * respective SPI packages.
 */
@Component
@RequiredArgsConstructor
public class StickerHistorySource implements HistorySource {

    private final SesStickerRequestRepository requestRepository;
    private final UserDisplayNameService userDisplayNameService;

    @Override
    public String businessType() {
        return "SES_STICKER_REQUEST";
    }

    @Override
    public String viewUrl(Long businessRefId, Long approvalInstanceId) {
        return approvalInstanceId != null
                ? "/approval/detail/" + approvalInstanceId
                : "/sticker/" + businessRefId + "/view";
    }

    /** No printable document for a sticker request; null, same as {@link #allRows()} passes. */
    @Override
    public String documentUrl(Long businessRefId, String status) {
        return null;
    }

    @Override
    @Transactional(readOnly = true)
    public List<HistoryRow> allRows() {
        List<SesStickerRequest> all = requestRepository.findAll();
        // One query for every distinct requestor, not one per row.
        Set<String> refs = all.stream().map(SesStickerRequest::getRequestorUserId).collect(Collectors.toSet());
        Map<String, String> names = userDisplayNameService.displayNames(refs);

        return all.stream()
                .map(r -> new HistoryRow("SES_STICKER_REQUEST", "Stiker Kendaraan",
                        r.getRequestNo(), r.getRequestorUserId(),
                        names.getOrDefault(r.getRequestorUserId(), r.getRequestorUserId()),
                        r.getStatus(), r.getCreatedDate(),
                        r.getApprovalInstanceId() != null
                                ? "/approval/detail/" + r.getApprovalInstanceId()
                                : "/sticker/" + r.getStickerRequestId() + "/view",
                        null,
                        r.getApprovalInstanceId()))
                .collect(Collectors.toList());

    }
}
