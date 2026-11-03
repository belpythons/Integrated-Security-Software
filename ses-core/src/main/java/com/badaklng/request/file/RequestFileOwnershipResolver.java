package com.badaklng.request.file;

import com.badaklng.approval.entity.SesApprovalInstance;
import com.badaklng.approval.service.ApprovalService;
import com.badaklng.request.entity.SesRequest;
import com.badaklng.request.entity.SesRequestWorker;
import com.badaklng.request.repository.SesRequestRepository;
import com.badaklng.request.repository.SesRequestWorkerDocRepository;
import com.badaklng.request.repository.SesRequestWorkerRepository;
import com.badaklng.ses.file.FileOwnershipResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Resolves access to uploaded files owned by this module: per-worker
 * documents ({@code SES_REQUEST_WORKER_DOC:<workerId>} — see
 * RequestService.saveAndSubmit, which stores worker document files under
 * the WORKER's id, not the worker_doc row's own id, to sidestep the
 * chicken-and-egg of needing a worker_doc_id before the file exists) and
 * the request-level Proof of Payment upload ({@code SES_REQUEST:<requestId>}).
 * <p>
 * canAccess() allows the requestor, or a user currently assigned to act on
 * the request's pending approval task (same assignee match ApprovalController
 * uses to compute "canAct" on approval/detail.html — see
 * ApprovalController.detail()) — mirrors PaymentFileOwnershipResolver's
 * approach of extending requestor-only access to the reviewer.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RequestFileOwnershipResolver implements FileOwnershipResolver {

    private static final String WORKER_DOC_PREFIX = "SES_REQUEST_WORKER_DOC:";
    private static final String REQUEST_PREFIX = "SES_REQUEST:";

    private final SesRequestWorkerDocRepository workerDocRepository;
    private final SesRequestWorkerRepository workerRepository;
    private final SesRequestRepository requestRepository;
    private final ApprovalService approvalService;

    @Override
    public boolean supports(String refId) {
        return refId != null && (refId.startsWith(WORKER_DOC_PREFIX) || refId.startsWith(REQUEST_PREFIX));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean canAccess(String refId, String username, String userId) {
        Optional<SesRequest> owningRequest = resolveOwningRequest(refId);
        if (!owningRequest.isPresent()) {
            return false;
        }
        SesRequest request = owningRequest.get();
        String requestor = request.getRequestorUserId();
        if (requestor != null && requestor.equalsIgnoreCase(username)) {
            return true;
        }
        return isCurrentApprover(request, username, userId);
    }

    private boolean isCurrentApprover(SesRequest request, String username, String userId) {
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

    private Optional<SesRequest> resolveOwningRequest(String refId) {
        if (refId.startsWith(REQUEST_PREFIX)) {
            Long requestId = parseLong(refId.substring(REQUEST_PREFIX.length()));
            return requestId == null ? Optional.empty() : requestRepository.findById(requestId);
        }
        if (refId.startsWith(WORKER_DOC_PREFIX)) {
            Long workerId = parseLong(refId.substring(WORKER_DOC_PREFIX.length()));
            if (workerId == null) {
                return Optional.empty();
            }
            Optional<SesRequestWorker> worker = workerRepository.findById(workerId);
            if (!worker.isPresent()) {
                return Optional.empty();
            }
            return requestRepository.findById(worker.get().getRequestId());
        }
        return Optional.empty();
    }

    private Long parseLong(String s) {
        try {
            return Long.valueOf(s);
        } catch (NumberFormatException e) {
            log.warn("RequestFileOwnershipResolver: non-numeric id in refId suffix: {}", s);
            return null;
        }
    }
}
