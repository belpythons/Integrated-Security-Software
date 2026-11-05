package com.badaklng.sticker.file;

import com.badaklng.approval.entity.SesApprovalInstance;
import com.badaklng.approval.service.ApprovalService;
import com.badaklng.sticker.entity.SesStickerRequest;
import com.badaklng.sticker.entity.SesStickerVehicle;
import com.badaklng.sticker.repository.SesStickerRequestRepository;
import com.badaklng.sticker.repository.SesStickerVehicleRepository;
import com.badaklng.ses.file.FileOwnershipResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Resolves access to files owned by this module: per-vehicle "lampiran
 * surat" uploads ({@code SES_STICKER_VEHICLE_DOC:<vehicleId>} — see
 * StickerService, which stores them under the VEHICLE's id). Mirrors
 * {@code com.badaklng.request.file.RequestFileOwnershipResolver} — the
 * requestor, or a user currently assigned to act on the request's pending
 * approval task, may access it.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StickerFileOwnershipResolver implements FileOwnershipResolver {

    private static final String VEHICLE_DOC_PREFIX = "SES_STICKER_VEHICLE_DOC:";

    private final SesStickerVehicleRepository vehicleRepository;
    private final SesStickerRequestRepository requestRepository;
    private final ApprovalService approvalService;

    @Override
    public boolean supports(String refId) {
        return refId != null && refId.startsWith(VEHICLE_DOC_PREFIX);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean canAccess(String refId, String username, String userId) {
        Optional<SesStickerRequest> owningRequest = resolveOwningRequest(refId);
        if (!owningRequest.isPresent()) {
            return false;
        }
        SesStickerRequest request = owningRequest.get();
        String requestor = request.getRequestorUserId();
        if (requestor != null && requestor.equalsIgnoreCase(username)) {
            return true;
        }
        return isCurrentApprover(request, username, userId);
    }

    private boolean isCurrentApprover(SesStickerRequest request, String username, String userId) {
        if (request.getApprovalInstanceId() == null) {
            return false;
        }
        SesApprovalInstance instance;
        try {
            instance = approvalService.getInstance(request.getApprovalInstanceId());
        } catch (IllegalArgumentException e) {
            return false;
        }
        if (instance.getCurrentTaskId() == null) {
            return false;
        }
        return approvalService.getTaskAssignees(instance.getCurrentTaskId()).stream()
                .anyMatch(a -> (username != null && username.equalsIgnoreCase(a.getEmplId()))
                        || (userId != null && userId.equals(a.getUserId())));
    }

    private Optional<SesStickerRequest> resolveOwningRequest(String refId) {
        Long vehicleId = parseLong(refId.substring(VEHICLE_DOC_PREFIX.length()));
        if (vehicleId == null) {
            return Optional.empty();
        }
        Optional<SesStickerVehicle> vehicle = vehicleRepository.findById(vehicleId);
        if (!vehicle.isPresent()) {
            return Optional.empty();
        }
        return requestRepository.findById(vehicle.get().getStickerRequestId());
    }

    private Long parseLong(String s) {
        try {
            return Long.valueOf(s);
        } catch (NumberFormatException e) {
            log.warn("StickerFileOwnershipResolver: non-numeric id in refId suffix: {}", s);
            return null;
        }
    }
}
