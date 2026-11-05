package com.badaklng.request.service;

import com.badaklng.approval.service.ApprovalService;
import com.badaklng.approval.service.SecurityEvaluatorService;
import com.badaklng.payment.entity.SesRequestPayment;
import com.badaklng.payment.service.PaymentService;
import com.badaklng.request.entity.SesExtUploadDoc;
import com.badaklng.request.entity.SesDocRequirement;
import com.badaklng.request.entity.RequestStatus;
import com.badaklng.request.entity.SesRequest;
import com.badaklng.request.entity.SesRequestType;
import com.badaklng.request.entity.SesRequestTypeDoc;
import com.badaklng.request.entity.SesRequestWorker;
import com.badaklng.request.entity.SesRequestWorkerDoc;
import com.badaklng.request.entity.SesZone;
import com.badaklng.request.model.RequestEditForm;
import com.badaklng.request.repository.SesDocRequirementRepository;
import com.badaklng.request.repository.SesRequestRepository;
import com.badaklng.request.repository.SesRequestTypeDocRepository;
import com.badaklng.request.entity.SesRequestEvaluator;
import com.badaklng.request.repository.SesRequestEvaluatorRepository;
import com.badaklng.request.repository.SesRequestTypeRepository;
import com.badaklng.request.repository.SesRequestWorkerDocRepository;
import com.badaklng.request.repository.SesRequestWorkerRepository;
import com.badaklng.request.repository.SesZoneRepository;
import com.badaklng.ses.file.AppFileService;
import com.badaklng.ses.jdbc.InChunks;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Core service for the request module. This class calls {@link ApprovalService}
 * directly (a plain {@code @Service} bean — that's the intended integration
 * point) but never imports approval's spi/entity types; see
 * {@code com.badaklng.request.spi.RequestApprovalBridge} for the one file
 * that is allowed to.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RequestService {

    private static final String PAYMENT_BUSINESS_TYPE = "SES_REQUEST";

    private final SesRequestRepository requestRepository;
    private final SesRequestWorkerRepository workerRepository;
    private final SesRequestWorkerDocRepository workerDocRepository;
    private final SesRequestTypeRepository requestTypeRepository;
    private final SesRequestTypeDocRepository requestTypeDocRepository;
    private final SesDocRequirementRepository docRequirementRepository;
    private final SesZoneRepository zoneRepository;
    private final AppFileService appFileService;
    private final ApprovalService approvalService;
    private final PaymentService paymentService;
    private final RequestTypeService requestTypeService;
    private final SesRequestEvaluatorRepository evaluatorRepository;
    private final SecurityEvaluatorService securityEvaluatorService;
    private final UploadTokenService uploadTokenService;
    private final RequestMailService requestMailService;
    private final ExtUploadStagingService extUploadStagingService;

    /**
     * Guard against a resubmitted edit re-triggering the approval chain
     * within a few seconds of the last trigger — the button-disable in
     * form.html covers the normal case, this is the server-side backstop
     * for a resend that gets through anyway (slow network retry, replayed
     * request, etc).
     */
    private static final long RESUBMIT_COOLDOWN_MILLIS = 10_000L;

    @PersistenceContext
    private EntityManager entityManager;

    // ---------------------------------------------------------------
    // Finders
    // ---------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<SesRequest> findMine(String username) {
        return requestRepository.findByRequestorUserIdOrderByRequestIdDesc(username);
    }

    /** One page of {@link #findMine}, optionally filtered by a free-text term. */
    @Transactional(readOnly = true)
    public Page<SesRequest> findMine(String username, String term, Pageable pageable) {
        return findMine(username, term, null, pageable);
    }

    /** One page of {@link #findMine}, optionally filtered by term and status. */
    @Transactional(readOnly = true)
    public Page<SesRequest> findMine(String username, String term, String status, Pageable pageable) {
        return requestRepository.searchMine(username, term, status, null, pageable);
    }

    /** One page of {@link #findMine}, optionally filtered by term, status and typeCode. */
    @Transactional(readOnly = true)
    public Page<SesRequest> findMine(String username, String term, String status, String typeCode, Pageable pageable) {
        return requestRepository.searchMine(username, term, status, typeCode, pageable);
    }

    @Transactional(readOnly = true)
    public SesRequest findById(Long requestId) {
        return requestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Request not found: " + requestId));
    }

    @Transactional(readOnly = true)
    public List<SesRequestWorker> findWorkers(Long requestId) {
        return workerRepository.findByRequestIdOrderByLineNoAsc(requestId);
    }

    /** Worker count for a request — the multiplier for that type's flat per-worker paymentAmount. */
    @Transactional(readOnly = true)
    public long workerCount(Long requestId) {
        return workerRepository.countByRequestId(requestId);
    }

    @Transactional(readOnly = true)
    public List<SesRequestWorkerDoc> findWorkerDocs(Long workerId) {
        return workerDocRepository.findByWorkerId(workerId);
    }

    /**
     * {@link #findWorkerDocs} for a whole worker list in one query, grouped
     * by worker id. Workers with no documents are present with an empty list,
     * so callers can index straight into the map.
     *
     * <p>Every screen that shows a worker roster needs the documents of all
     * of them, so the per-worker form was a query per row on
     * {@code /request/{id}/view}, {@code /approval/detail},
     * {@code /scheduling/manage} and the ext upload page.
     */
    @Transactional(readOnly = true)
    public Map<Long, List<SesRequestWorkerDoc>> findWorkerDocsByWorkerIds(Collection<Long> workerIds) {
        Map<Long, List<SesRequestWorkerDoc>> byWorker = new LinkedHashMap<>();
        if (workerIds == null || workerIds.isEmpty()) {
            return byWorker;
        }
        for (Long id : workerIds) {
            if (id != null) {
                byWorker.put(id, new ArrayList<>());
            }
        }
        for (SesRequestWorkerDoc doc : InChunks.flatMap(byWorker.keySet(), workerDocRepository::findByWorkerIdIn)) {
            byWorker.computeIfAbsent(doc.getWorkerId(), k -> new ArrayList<>()).add(doc);
        }
        return byWorker;
    }

    /** Document requirements for a type, ordered — join SesRequestTypeDoc(typeCode) -> SesDocRequirement. */
    @Transactional(readOnly = true)
    public List<SesDocRequirement> requirementsFor(String typeCode) {
        List<SesRequestTypeDoc> links = requestTypeDocRepository.findByTypeCodeOrderByOrderByAsc(typeCode);
        Map<String, SesDocRequirement> byCode = docRequirementsByCode(links);
        List<SesDocRequirement> result = new ArrayList<>();
        for (SesRequestTypeDoc link : links) {
            SesDocRequirement doc = byCode.get(link.getDocCode());
            if (doc != null) {
                result.add(doc);
            }
        }
        return result;
    }

    /**
     * Same join as {@link #requirementsFor}, but also carrying each
     * requirement's mandatory flag — what the /requirements/{typeCode} JSON
     * endpoint needs (RequestController.RequirementDto).
     */
    @Transactional(readOnly = true)
    public List<RequirementRow> requirementRowsFor(String typeCode) {
        List<SesRequestTypeDoc> links = requestTypeDocRepository.findByTypeCodeOrderByOrderByAsc(typeCode);
        Map<String, SesDocRequirement> byCode = docRequirementsByCode(links);
        List<RequirementRow> result = new ArrayList<>();
        for (SesRequestTypeDoc link : links) {
            SesDocRequirement doc = byCode.get(link.getDocCode());
            if (doc != null) {
                result.add(new RequirementRow(
                        doc.getDocCode(), doc.getDocName(), "Y".equals(link.getMandatoryFlag())));
            }
        }
        return result;
    }

    /**
     * Resolves every {@code doc_code} the links point at in one query.
     *
     * <p>Both requirement builders used to call {@code findById} per link.
     * That is a query per document requirement, and because
     * {@code requirementRowsFor} sits inside {@link #detailModel}, the cost
     * landed on the request detail page, the approval detail page, the
     * payment detail pages, the scheduling screens AND the mail builder.
     * Links whose code no longer resolves are skipped by the callers, same
     * as the {@code ifPresent} they replaced.
     */
    private Map<String, SesDocRequirement> docRequirementsByCode(List<SesRequestTypeDoc> links) {
        List<String> codes = links.stream()
                .map(SesRequestTypeDoc::getDocCode)
                .filter(code -> code != null)
                .distinct()
                .collect(Collectors.toList());
        Map<String, SesDocRequirement> byCode = new HashMap<>();
        for (SesDocRequirement doc : InChunks.flatMap(codes, docRequirementRepository::findAllById)) {
            byCode.put(doc.getDocCode(), doc);
        }
        return byCode;
    }

    @lombok.Getter
    @lombok.AllArgsConstructor
    public static class RequirementRow {
        private final String docCode;
        private final String docName;
        private final boolean mandatory;
    }

    /**
     * Read-only detail data for one request — contract/worker/attachment
     * fields plus the currently on-record proof-of-payment file (sourced
     * from PaymentService, NOT SesRequest.paymentFileId, which is dead —
     * see saveAndSubmit step 4). Shared by the requestor's own view
     * (RequestController#view), the approver's view (RequestApprovalBridge,
     * which delegates here), and both payment module detail pages (via
     * "which request is this for").
     */
    @Transactional(readOnly = true)
    public Map<String, Object> detailModel(Long requestId) {
        Optional<SesRequest> found = requestRepository.findById(requestId);
        if (!found.isPresent()) {
            return Collections.emptyMap();
        }
        SesRequest request = found.get();

        List<SesRequestWorker> workers = findWorkers(requestId);
        Map<Long, List<SesRequestWorkerDoc>> docsByWorkerId = findWorkerDocsByWorkerIds(
                workers.stream().map(SesRequestWorker::getWorkerId).collect(Collectors.toList()));
        List<RequirementRow> requirements = requirementRowsFor(request.getTypeCode());
        String currentProofFileId = paymentService.currentProofFileId(PAYMENT_BUSINESS_TYPE, requestId).orElse(null);

        Map<String, Object> model = new HashMap<>();
        List<SesRequestEvaluator> evaluators =
                evaluatorRepository.findByRequestIdOrderByLineNoAscEvaluatorIdAsc(requestId);
        model.put("requestForm",
                RequestEditForm.from(request, workers, docsByWorkerId, requirements, currentProofFileId, evaluators));
        model.put("requestEvaluators", evaluators);
        SesRequestType type = requestTypeRepository.findById(request.getTypeCode()).orElse(null);
        model.put("typeName", type != null ? type.getTypeName() : request.getTypeCode());
        // Upload-link state, for the "Email Penerima Dokumen" row on the detail
        // page. The expiry is only meaningful while the link can still be used,
        // so it is withheld once the token is spent — a date next to a dead
        // link reads as "still valid until then".
        model.put("uploadDone", "Y".equalsIgnoreCase(request.getUploadTokenUsed()));
        model.put("uploadTokenExpiry",
                RequestStatus.WAITING_FOR_UPLOAD.equals(request.getStatus())
                        ? request.getUploadTokenExpiry() : null);
        model.put("paymentAmount", type != null && type.getPaymentAmount() != null
                ? type.getPaymentAmount() * workers.size() : null);
        model.put("zoneName", request.getZoneCode() != null
                ? zoneRepository.findById(request.getZoneCode()).map(SesZone::getZoneName).orElse(request.getZoneCode())
                : null);
        paymentService.findByBusiness(PAYMENT_BUSINESS_TYPE, requestId).ifPresent(payment -> {
            model.put("paymentStatus", payment.getStatus() != null ? payment.getStatus().name() : null);
            model.put("paymentPaidAmount", payment.getPaidAmount());
            model.put("paymentRejectionReason", payment.getRejectionReason());
            model.put("paymentReviewedBy", payment.getReviewedBy());
            model.put("paymentReviewedDate", payment.getReviewedDate());
        });
        return model;
    }

    // ---------------------------------------------------------------
    // Save + submit
    // ---------------------------------------------------------------

    /**
     * Persists a submission and puts it into {@link RequestStatus#WAITING_FOR_UPLOAD},
     * then emails the document-upload link.
     *
     * <p><b>The approval chain is deliberately NOT triggered here.</b> The
     * worker documents belong to the contractor, who is not an SES user and
     * has not sent them yet; an approver asked to decide on a submission with
     * no documents attached can only say "wait". {@link #finalizeUpload} is
     * what starts the chain, once the documents are actually in.</p>
     *
     * <p>Everything up to and including the worker rows is unchanged from
     * before this split — the same validation, the same evaluator freeze, the
     * same payment handling. Only the tail differs.</p>
     */
    @Transactional
    public Long saveAndSubmit(RequestSaveInput input, String username) {
        SesRequestType type = validate(input, username);

        // ---- 2. Upsert header ----
        SesRequest request;
        boolean isCreate = input.getRequestId() == null;
        if (isCreate) {
            request = new SesRequest();
            request.setStatus(RequestStatus.WAITING_FOR_UPLOAD);
            request.setRequestorUserId(username);

            // request_no = "REQ-" + year + "-" + LPAD(<seq>,4,'0'), drawn from its
            // own dedicated SEQ_SES_REQUEST_NO — NOT SesRequest's own generated
            // request_id. request_no/type_code/etc. must all be set BEFORE the
            // first save(): request_no and type_code are both NOT NULL columns,
            // and Hibernate is free to flush a pending insert (e.g. the
            // deleteByRequestId query below triggers an auto-flush) at any point
            // after persist() — an initial save() with either column still null
            // risks a DataIntegrityViolationException on that flush.
            long seq = ((Number) entityManager.createNativeQuery("SELECT SEQ_SES_REQUEST_NO.NEXTVAL FROM DUAL")
                    .getSingleResult()).longValue();
            int year = java.time.Year.now().getValue();
            request.setRequestNo("REQ-" + year + "-" + String.format("%04d", seq));
        } else {
            request = findById(input.getRequestId());
            // IDOR fix: a crafted POST naming someone else's requestId would
            // otherwise overwrite their request wholesale.
            assertOwned(request, username);
            if (!RequestStatus.isRequestorEditable(request.getStatus())) {
                throw new IllegalStateException("Only a pending or draft request can be edited.");
            }
            if (request.getApprovalInstanceId() != null
                    && !approvalService.isEditable(request.getApprovalInstanceId())) {
                throw new IllegalStateException(
                        "This request already has an approval decision recorded and can no longer be edited.");
            }
        }
        request.setTypeCode(input.getTypeCode());
        request.setContractNo(input.getContractNo());
        request.setContractType(input.getContractType());
        request.setContractTitle(input.getContractTitle());
        request.setZoneCode(input.getZoneCode());
        request.setInstance(input.getInstance());
        request.setAddress(input.getAddress());
        request.setStartPeriod(input.getStartPeriod());
        request.setFinishPeriod(input.getFinishPeriod());
        request.setEmail(trimToNull(input.getEmail()));
        request = requestRepository.save(request);
        final Long requestId = request.getRequestId();

        // ---- 2b. Replace the Staf Security evaluator list ----
        // Before the approval is triggered, necessarily: trigger() reads these
        // rows to build the step-1 sign-offs.
        saveEvaluators(requestId, input.getEvaluatorEmplIds(), username);

        // ---- 3. Replace workers + docs by diff (delete-all-then-reinsert) ----
        workerRepository.deleteByRequestId(requestId);
        workerRepository.flush();

        int lineNo = 1;
        for (RequestSaveInput.WorkerInput wi : input.getWorkers()) {
            SesRequestWorker worker = new SesRequestWorker();
            worker.setRequestId(requestId);
            worker.setLineNo(lineNo++);
            worker.setWorkerName(wi.getWorkerName());
            worker.setNik(wi.getNik());
            worker.setIdType(normalizeIdType(wi.getIdType()));
            worker.setBadge(wi.getBadge());
            worker.setJabatan(wi.getJabatan());
            worker.setDepartment(wi.getDepartment());
            worker.setSection(wi.getSection());
            worker.setGender(wi.getGender().trim());
            worker = workerRepository.save(worker);
            final Long workerId = worker.getWorkerId();

            if (wi.getDocs() != null) {
                for (RequestSaveInput.WorkerDocInput di : wi.getDocs()) {
                    String fileId = resolveWorkerDocFileId(di, workerId, wi.getWorkerName());
                    if (fileId == null) {
                        continue; // optional document, nothing uploaded/carried forward
                    }
                    SesRequestWorkerDoc doc = new SesRequestWorkerDoc();
                    doc.setWorkerId(workerId);
                    doc.setDocCode(di.getDocCode());
                    doc.setFileId(fileId);
                    workerDocRepository.save(doc);
                }
            }
        }

        // ---- 4. Proof of Payment ----
        // ses_request.payment_file_id is no longer written here — payment
        // tracking moved to com.badaklng.payment (PaymentService /
        // ses_request_payment) once a type needs payment. The column stays
        // in the schema, unused, for backward compatibility with data
        // written before this module existed. validate() has already proven
        // a proof exists for a paying type by this point (either attached here
        // or carried forward), so the attachment below goes straight into the
        // payment item's first submission via the same path the dedicated
        // /payment page's upload action uses.
        if ("Y".equals(type.getNeedsPaymentFlag())) {
            paymentService.ensurePayment(PAYMENT_BUSINESS_TYPE, requestId, username);
            if (isNonEmpty(input.getPaymentFile())) {
                paymentService.submitProofFromRequestForm(PAYMENT_BUSINESS_TYPE, requestId, input.getPaymentFile(), username);
            }
        }

        // ---- 5. Supersede any running approval instance (safe no-op if terminal) ----
        if (request.getApprovalInstanceId() != null) {
            Date lastTriggered = approvalService.getInstanceCreatedDate(request.getApprovalInstanceId());
            if (lastTriggered != null
                    && System.currentTimeMillis() - lastTriggered.getTime() < RESUBMIT_COOLDOWN_MILLIS) {
                throw new IllegalStateException(
                        "This request was just submitted — please wait a few seconds before submitting again.");
            }
            approvalService.cancel(request.getApprovalInstanceId(), username, "Superseded by request edit");
        }

        // ---- 6. Finalize submit or hold for external upload ----
        if (type.isNeedExtPageUpload()) {
            request.setStatus(RequestStatus.WAITING_FOR_UPLOAD);

            // Re-issue and re-mail ONLY when there is no working link already.
            //
            // This branch is re-entered on every submit of an editable
            // request, and WAITING_FOR_UPLOAD is editable
            // (RequestStatus.isRequestorEditable) — so an edit-resubmit, or
            // simply a double-clicked Submit, used to run it a second time.
            // Issuing unconditionally there did two wrong things at once: it
            // queued a duplicate email, and — the expensive half — it
            // overwrote the token column, so the link the contractor was
            // already working from went dead with no explanation. The
            // RESUBMIT_COOLDOWN_MILLIS guard above cannot catch either, since
            // it is keyed on approvalInstanceId and this request has no
            // approval instance yet.
            //
            // Changing the recipient address is deliberately NOT a reason to
            // re-mail here: that is what "Kirim Ulang Tautan"
            // (resendUploadLink) is for, and it reissues on purpose.
            boolean linkAlreadyLive = uploadTokenService.hasLiveToken(request);
            String token = linkAlreadyLive
                    ? request.getUploadToken().trim()
                    : uploadTokenService.issue(request);
            request = requestRepository.save(request);

            // Open (or refresh) the staging session. Idempotent, and it must
            // run on the reuse path too: a re-submit can change the type name
            // or the recipient address the public page shows, and the bridge
            // row is where that page reads them from.
            extUploadStagingService.openSession(request, type.getTypeName());

            if (!linkAlreadyLive) {
                requestMailService.sendUploadDocumentsEmail(request, token);
            }
        } else {
            assertAllMandatoryDocsPresent(request, type);
            request.setStatus(RequestStatus.PENDING);
            request.setApprovalInstanceId(triggerApproval(request, type, username));
            request = requestRepository.save(request);
            if ("Y".equals(type.getNeedsPaymentFlag())) {
                paymentService.carryForwardVerifiedCheckpoint(PAYMENT_BUSINESS_TYPE, requestId, username);
            }
        }

        return requestId;
    }

    /**
     * Completes a submission once its documents have arrived, and starts the
     * approval chain.
     *
     * <p>Called from the token-protected upload page, so <b>there is no
     * authenticated user</b>: {@code actor} is a synthetic audit label, not a
     * username, and nothing in here may consult {@code SecurityUtils}.
     * Authorisation happened when {@code UploadTokenService.resolve} accepted
     * the token; this method re-reads the request by id and re-checks the
     * status rather than trusting the caller's object.</p>
     *
     * <p>The mandatory-document rule that {@link #validate} skips at submit
     * time is enforced here instead — this is the moment the documents are
     * supposed to exist, and refusing earlier would have made the whole
     * feature impossible.</p>
     */
    @Transactional
    public Long finalizeUpload(Long requestId, String actor) {
        SesRequest request = findById(requestId);
        if (!RequestStatus.WAITING_FOR_UPLOAD.equals(request.getStatus())) {
            // The token was valid when the page rendered but the request moved
            // on while the visitor was filling the form — cancelled by the
            // requestor, or swept away by RequestExpiryJob.
            throw new IllegalStateException(
                    "Pengajuan ini sudah tidak menunggu dokumen.");
        }
        SesRequestType type = requestTypeRepository.findById(request.getTypeCode())
                .orElseThrow(() -> new IllegalStateException(
                        "Jenis pengajuan tidak ditemukan: " + request.getTypeCode()));

        // Move the staged documents into the official table FIRST, and judge
        // completeness after.
        //
        // The order is the point. assertAllMandatoryDocsPresent already reads
        // ses_request_worker_doc; a second completeness rule reading the
        // staging table would be two rules that can disagree about whether a
        // submission is finished. Transferring first keeps exactly one.
        //
        // A failure below rolls this whole transfer back — including the
        // app_file.ref_id repoints and the is_finalized flag — while the
        // staging rows themselves survive, because they were committed by the
        // earlier per-file requests. The contractor sees their files still
        // listed, supplies what is missing, and submits again.
        transferStagedDocs(requestId);

        assertAllMandatoryDocsPresent(request, type);

        uploadTokenService.markUsed(request);
        request.setStatus(RequestStatus.PENDING);
        request.setApprovalInstanceId(triggerApproval(request, type, actor));
        requestRepository.save(request);

        if ("Y".equals(type.getNeedsPaymentFlag())) {
            paymentService.carryForwardVerifiedCheckpoint(PAYMENT_BUSINESS_TYPE, requestId, actor);
        }
        return requestId;
    }

    /**
     * Starts the approval chain for a saved request and returns the new
     * instance id.
     *
     * <p>Lifted out of {@code saveAndSubmit} unchanged when the upload step
     * was introduced. Only {@link #finalizeUpload} reaches it today, so this
     * could be inlined — it is kept separate because the submit path and the
     * trigger path are now genuinely different events that happen days apart,
     * and collapsing them would re-create the coupling this change removed.</p>
     *
     * <p>Chained (never assigned to a locally-typed variable) so this class
     * never needs to import {@code com.badaklng.approval.entity.SesApprovalInstance} —
     * only {@code RequestApprovalBridge} is allowed to import approval entities.
     * The per-type toggles and the chosen evaluators are passed in rather than
     * discovered through the approval SPI: {@code type} is already in hand and
     * the evaluator rows were written earlier in the same flow, so asking an
     * SPI would mean re-reading rows this module just wrote. It also keeps
     * request-type vocabulary out of a descriptor contract that carries only
     * routing and display concerns.</p>
     */
    private Long triggerApproval(SesRequest request, SesRequestType type, String actor) {
        Long requestId = request.getRequestId();

        // Supersede any running instance (safe no-op if terminal).
        if (request.getApprovalInstanceId() != null) {
            Date lastTriggered = approvalService.getInstanceCreatedDate(request.getApprovalInstanceId());
            if (lastTriggered != null
                    && System.currentTimeMillis() - lastTriggered.getTime() < RESUBMIT_COOLDOWN_MILLIS) {
                throw new IllegalStateException(
                        "This request was just submitted — please wait a few seconds before submitting again.");
            }
            approvalService.cancel(request.getApprovalInstanceId(), actor, "Superseded by request edit");
        }

        // Both flags are read as !"N" rather than == "Y", so a row predating
        // sql/48 (null flag) defaults to REQUIRING the step — the safe
        // direction.
        boolean requireManagerStep = !"N".equalsIgnoreCase(type.getNeedsMgrApprovalFlag());
        boolean requireProcStep = !"N".equalsIgnoreCase(type.getNeedsProcApprovalFlag());
        // Until now needs_payment_flag had NO influence on approval at all:
        // the chain is picked by SES_FORM_APPROVAL_MAP on the type code alone,
        // so a paying type mapped to a chain without a PAYMENT_VERIFICATION
        // checkpoint sailed past screening with the proof of payment unread.
        // Passing it lets trigger() refuse that configuration outright.
        boolean requirePaymentCheckpoint = "Y".equals(type.getNeedsPaymentFlag());

        // Read back from ses_request_evaluator rather than from any submitted
        // input: those rows are what saveEvaluators actually persisted, after
        // resolving and rejecting anything that is not a real candidate. Using
        // raw input would let a rejected id reach the approval engine.
        List<ApprovalService.EvaluatorInput> evaluators = new ArrayList<>();
        for (SesRequestEvaluator e : evaluatorRepository.findByRequestIdOrderByLineNoAscEvaluatorIdAsc(requestId)) {
            evaluators.add(new ApprovalService.EvaluatorInput(
                    e.getEmplId(), e.getEmplName(), e.getPosId(), e.getPosTitle(), null));
        }

        // The requestor, not the actor: the chain resolves REQUESTOR_MANAGER
        // from whoever submitted, and on the upload path the actor is a
        // synthetic label with no position in the org chart.
        return approvalService
                .trigger("SES_REQUEST", request.getTypeCode(), "SES_REQUEST", requestId,
                        request.getRequestorUserId(),
                        new ApprovalService.TriggerOptions(requireManagerStep, requireProcStep, evaluators,
                                requirePaymentCheckpoint))
                .getInstanceId();
    }

    /**
     * Every worker must carry every document the request type marks mandatory.
     *
     * <p>Used to run inside {@link #validate} against the form's in-flight
     * upload inputs. It now runs against what is actually <b>persisted</b>, at
     * the moment the upload page submits — the only point where the answer is
     * both knowable and final.</p>
     */
    private void assertAllMandatoryDocsPresent(SesRequest request, SesRequestType type) {
        List<String> mandatoryDocCodes = requestTypeDocRepository
                .findByTypeCodeOrderByOrderByAsc(type.getTypeCode()).stream()
                .filter(d -> "Y".equals(d.getMandatoryFlag()))
                .map(SesRequestTypeDoc::getDocCode)
                .collect(Collectors.toList());
        if (mandatoryDocCodes.isEmpty()) {
            return;
        }
        for (SesRequestWorker worker : findWorkers(request.getRequestId())) {
            Set<String> present = new HashSet<>();
            for (SesRequestWorkerDoc doc : findWorkerDocs(worker.getWorkerId())) {
                if (doc.getFileId() != null && !doc.getFileId().trim().isEmpty()) {
                    present.add(doc.getDocCode());
                }
            }
            for (String code : mandatoryDocCodes) {
                if (!present.contains(code)) {
                    throw new IllegalArgumentException("Dokumen wajib \"" + docDisplayName(code)
                            + "\" untuk pekerja \"" + worker.getWorkerName() + "\" belum diunggah.");
                }
            }
        }
    }

    /**
     * Issues a new upload link for a request still waiting for documents, and
     * emails it again.
     *
     * <p>The reason this exists: a seven-day link that lapses would otherwise
     * strand the submission with no way forward short of a database edit.
     * Re-issuing overwrites {@code upload_token}, so the previous link —
     * including any forwarded copy of the previous email — stops working the
     * moment this runs.</p>
     */
    @Transactional
    public void resendUploadLink(Long requestId, String username) {
        SesRequest request = findById(requestId);
        assertOwned(request, username);
        if (!RequestStatus.WAITING_FOR_UPLOAD.equals(request.getStatus())) {
            throw new IllegalStateException(
                    "Tautan unggah hanya bisa dikirim ulang untuk pengajuan yang masih menunggu dokumen.");
        }
        String token = uploadTokenService.issue(request);
        request = requestRepository.save(request);
        requestMailService.sendUploadDocumentsEmail(request, token);
    }

    /**
     * Persists a request as {@code DRAFT} — header + whichever worker rows
     * are already complete enough to satisfy {@code ses_request_worker}'s
     * NOT NULL columns ({@code worker_name}, {@code nik}) — without running
     * {@link #validate}'s mandatory-document/NIK-format checks and without
     * touching the approval module at all (no instance triggered, no
     * payment item created). A worker row missing name or NIK is silently
     * skipped rather than persisted incomplete; a duplicate NIK among
     * otherwise-complete rows is silently deduped (first one wins) instead
     * of throwing, since {@code uq_ses_request_worker_nik} would otherwise
     * turn a routine draft save into an unhandled DataIntegrityViolation.
     * Only {@code typeCode} is required — every other field, and the doc
     * mandatory-upload rule {@link #validate} enforces at submit time, is
     * relaxed here. Re-saving an existing draft requires it still be
     * {@code DRAFT} (a request already submitted is edited via
     * {@link #saveAndSubmit}, not this method).
     */
    @Transactional
    public Long saveDraft(RequestSaveInput input, String username) {
        if (input.getTypeCode() == null || input.getTypeCode().isEmpty()) {
            throw new IllegalArgumentException("Type of Request is required to save a draft.");
        }
        requestTypeRepository.findById(input.getTypeCode())
                .orElseThrow(() -> new IllegalArgumentException("Unknown request type: " + input.getTypeCode()));
        // Same auth-type gate as the submit path. Without it the restriction is
        // merely deferred: a forbidden type would be parked as a DRAFT now and
        // only rejected later, after the requestor has filled the whole form.
        requestTypeService.assertSelectable(input.getTypeCode(), username);

        SesRequest request;
        boolean isCreate = input.getRequestId() == null;
        if (isCreate) {
            request = new SesRequest();
            request.setStatus(RequestStatus.DRAFT);
            request.setRequestorUserId(username);

            long seq = ((Number) entityManager.createNativeQuery("SELECT SEQ_SES_REQUEST_NO.NEXTVAL FROM DUAL")
                    .getSingleResult()).longValue();
            int year = java.time.Year.now().getValue();
            request.setRequestNo("REQ-" + year + "-" + String.format("%04d", seq));
        } else {
            request = findById(input.getRequestId());
            // IDOR fix, same reasoning as the submit path above.
            assertOwned(request, username);
            if (!RequestStatus.DRAFT.equals(request.getStatus())) {
                throw new IllegalStateException("Only a draft request can be saved as draft.");
            }
        }
        request.setTypeCode(input.getTypeCode());
        request.setContractNo(input.getContractNo());
        request.setContractType(input.getContractType());
        request.setContractTitle(input.getContractTitle());
        request.setZoneCode(input.getZoneCode());
        request.setInstance(input.getInstance());
        request.setAddress(input.getAddress());
        request.setStartPeriod(input.getStartPeriod());
        request.setFinishPeriod(input.getFinishPeriod());
        request = requestRepository.save(request);
        final Long requestId = request.getRequestId();

        // Evaluators are saved on a draft too, so a half-filled form survives
        // a reload. The ids are still re-resolved and rejected if bogus — the
        // relaxation on this path is about completeness, never about trust.
        saveEvaluators(requestId, input.getEvaluatorEmplIds(), username);

        // Worker rows are deliberately NOT touched here.
        //
        // This method used to delete every worker of the request and re-insert
        // whatever the form posted. That was right when the form carried them
        // all; with the incremental saver it is catastrophic — the draft form
        // posts no worker fields at all, so "Save as Draft" would silently
        // wipe every worker the user had just saved one by one.
        //
        // Workers now have exactly one owner: saveWorker / deleteWorker.

        return requestId;
    }

    /**
     * Resolves the file_id to persist for one worker document cell: a newly
     * uploaded file wins (stored under the worker's just-generated id, and
     * — since workers/docs are fully replaced on every save — an existing
     * file carried across an edit is REASSIGNED to the new worker id so
     * RequestFileOwnershipResolver, which resolves ownership purely from the
     * refId's embedded worker id, keeps working after the old worker row is
     * gone). Returns null when neither a new file nor an existing id was
     * given (an optional document simply left blank).
     */
    private String resolveWorkerDocFileId(RequestSaveInput.WorkerDocInput di, Long workerId, String workerName) {
        if (isNonEmpty(di.getFile())) {
            return appFileService.store(di.getFile(), "SES_REQUEST_WORKER_DOC:" + workerId,
                    docDisplayName(di.getDocCode()) + " - " + (workerName == null ? "" : workerName));
        }
        if (di.getExistingFileId() != null && !di.getExistingFileId().isEmpty()) {
            appFileService.reassign(di.getExistingFileId(), "SES_REQUEST_WORKER_DOC:" + workerId);
            return di.getExistingFileId();
        }
        return null;
    }

    /**
     * Human name of a document requirement ("Family Card (Kartu Keluarga)"),
     * used to file the upload under something readable instead of the
     * browser's own filename. Falls back to the raw code when the requirement
     * row is gone — a stored file should never be left nameless over that.
     */
    private String docDisplayName(String docCode) {
        if (docCode == null) {
            return "Dokumen";
        }
        return docRequirementRepository.findById(docCode)
                .map(SesDocRequirement::getDocName)
                .filter(n -> n != null && !n.trim().isEmpty())
                .orElse(docCode);
    }

    // ---------------------------------------------------------------
    // Incremental item saving — one worker at a time, over AJAX
    // ---------------------------------------------------------------
    //
    // The form used to hold every worker and every attachment in unsaved DOM
    // until one enormous multipart POST. With 25 workers and three documents
    // each that is 75 files in a single request: slow, impossible to give
    // progress on, and all-or-nothing — a timeout lost the lot.
    //
    // Now each worker is saved on its own with its own files, and the final
    // Submit carries nothing but the header and the proof of payment.
    //
    // Every one of these methods asserts ownership. That is not optional
    // decoration: they take a requestId straight off the URL, so without it
    // anyone could append rows to, or read, a stranger's draft.

    /**
     * Ownership gate for anything that takes a {@code requestId} from the
     * client.
     *
     * <p>Mirrors the check {@code RequestController.editForm} has always made
     * on the GET side. The POST side never had one — see
     * {@code docs/stages/stage6_cash_dashboard_hardening.md} for the pre-existing
     * paths this was retrofitted onto.</p>
     */
    /** Null, empty or whitespace-only becomes null; anything else is trimmed. */
    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    /**
     * A deliberately loose shape check: one {@code @}, something on each side,
     * a dot in the domain, no whitespace.
     *
     * <p>Not RFC 5322. A regex that implements that specification is famously
     * unreadable and still rejects addresses that work, and the only test that
     * actually proves an address is deliverable is sending to it — which this
     * flow does moments later. The job here is to catch a typo like a missing
     * {@code @}, not to adjudicate the standard.</p>
     */
    static boolean isPlausibleEmail(String email) {
        if (email == null) {
            return false;
        }
        String t = email.trim();
        if (t.isEmpty() || t.length() > 150) {
            return false;
        }
        for (int i = 0; i < t.length(); i++) {
            if (Character.isWhitespace(t.charAt(i))) {
                return false;
            }
        }
        int at = t.indexOf('@');
        if (at <= 0 || at != t.lastIndexOf('@') || at == t.length() - 1) {
            return false;
        }
        String domain = t.substring(at + 1);
        int dot = domain.indexOf('.');
        return dot > 0 && dot < domain.length() - 1;
    }

    private void assertOwned(SesRequest request, String username) {
        if (request.getRequestorUserId() == null || username == null
                || !request.getRequestorUserId().equalsIgnoreCase(username)) {
            throw new AccessDeniedException("You do not have access to this request.");
        }
    }

    /** Owned, and still in a state where its contents may be changed. */
    private SesRequest editableDraft(Long requestId, String username) {
        SesRequest request = findById(requestId);
        assertOwned(request, username);
        if (!RequestStatus.isRequestorEditable(request.getStatus())) {
            throw new IllegalStateException("Only a pending or draft request can be edited.");
        }
        if (request.getApprovalInstanceId() != null
                && !approvalService.isEditable(request.getApprovalInstanceId())) {
            throw new IllegalStateException(
                    "This request already has an approval decision recorded and can no longer be edited.");
        }
        return request;
    }

    /**
     * Returns the draft this form is editing, creating one if it does not
     * exist yet.
     *
     * <p>Called the first time the user opens the worker modal on a brand new
     * request: a worker row needs a {@code request_id} to hang off, so the
     * header has to be persisted before any item can be. Everything but the
     * type code is allowed to be blank at this point — this is the "Save as
     * Draft" contract, reused rather than reinvented.</p>
     */
    @Transactional
    public Long ensureDraft(RequestSaveInput input, String username) {
        if (input.getRequestId() != null) {
            return editableDraft(input.getRequestId(), username).getRequestId();
        }
        return saveDraft(input, username);
    }

    /** Defaults a blank/unknown idType to NIK — the format every existing caller assumes. */
    private String normalizeIdType(String idType) {
        return "PASSPORT".equalsIgnoreCase(idType == null ? "" : idType.trim()) ? "PASSPORT" : "NIK";
    }

    /**
     * Shared NIK/Passport format check, used by both {@code saveWorker} and
     * {@code validate} so the two doors can't drift into different rules.
     */
    private void validateIdentity(String idType, String identityNum, String workerName) {
        if ("PASSPORT".equals(normalizeIdType(idType))) {
            if (identityNum.length() < 6 || identityNum.length() > 20 || !identityNum.matches("^[A-Za-z0-9]+$")) {
                throw new IllegalArgumentException(
                        "Nomor Passport \"" + workerName + "\" harus alfanumerik 6-20 karakter.");
            }
        } else {
            if (identityNum.length() != 16 || !identityNum.chars().allMatch(Character::isDigit)) {
                throw new IllegalArgumentException("NIK \"" + workerName + "\" harus tepat 16 digit angka.");
            }
        }
    }

    /**
     * Creates or updates ONE worker row and its documents.
     *
     * <p>A genuine upsert, not the delete-and-reinsert the whole-form path
     * uses. That matters for more than tidiness: the old path cascaded away
     * {@code ses_request_worker_doc} rows and left their BLOBs behind in
     * {@code app_file} forever, because nothing ever called
     * {@code AppFileService.delete} (see {@code sql/51}). Here a replaced file
     * is deleted explicitly, so the leak has no source.</p>
     *
     * <p>Validation is deliberately lighter than {@code validate()}: name and
     * NIK are required because the columns are NOT NULL and a 16-digit NIK is
     * cheap to check, but mandatory-document rules are NOT enforced. A worker
     * saved now may legitimately be missing a document the user is about to
     * upload; that rule belongs at Submit, where the whole picture exists.</p>
     *
     * @return the saved worker
     */
    @Transactional
    public SesRequestWorker saveWorker(Long requestId, RequestSaveInput.WorkerInput wi, String username) {
        SesRequest request = editableDraft(requestId, username);

        String name = wi.getWorkerName() == null ? "" : wi.getWorkerName().trim();
        String nik = wi.getNik() == null ? "" : wi.getNik().trim();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("Nama pekerja wajib diisi.");
        }
        String idType = normalizeIdType(wi.getIdType());
        validateIdentity(idType, nik, name);
        String gender = wi.getGender() == null ? "" : wi.getGender().trim();
        if (!"M".equals(gender) && !"F".equals(gender)) {
            throw new IllegalArgumentException("Jenis kelamin \"" + name + "\" harus dipilih.");
        }

        List<SesRequestWorker> existing = findWorkers(requestId);
        for (SesRequestWorker other : existing) {
            // uq_ses_request_worker_nik would raise ORA-00001 here; catching it
            // early turns an unreadable constraint violation into a sentence.
            if (!other.getWorkerId().equals(wi.getWorkerId()) && nik.equals(other.getNik())) {
                throw new IllegalArgumentException("NIK " + nik + " sudah terdaftar pada pekerja lain di pengajuan ini.");
            }
        }

        SesRequestWorker worker;
        if (wi.getWorkerId() == null) {
            worker = new SesRequestWorker();
            worker.setRequestId(requestId);
            worker.setLineNo(existing.size() + 1);
        } else {
            worker = workerRepository.findById(wi.getWorkerId())
                    .orElseThrow(() -> new IllegalArgumentException("Pekerja tidak ditemukan: " + wi.getWorkerId()));
            if (!requestId.equals(worker.getRequestId())) {
                // The row exists but belongs to someone else's request. Saying
                // so plainly beats letting a crafted id move a worker between
                // requests.
                throw new AccessDeniedException("Worker " + wi.getWorkerId() + " does not belong to request " + requestId);
            }
        }
        worker.setWorkerName(name);
        worker.setNik(nik);
        worker.setIdType(idType);
        worker.setBadge(wi.getBadge());
        worker.setJabatan(wi.getJabatan());
        worker.setDepartment(wi.getDepartment());
        worker.setSection(wi.getSection());
        worker.setGender(gender);
        worker = workerRepository.save(worker);
        final Long workerId = worker.getWorkerId();

        if (wi.getDocs() != null) {
            Map<String, SesRequestWorkerDoc> byCode = new HashMap<>();
            for (SesRequestWorkerDoc d : findWorkerDocs(workerId)) {
                byCode.put(d.getDocCode(), d);
            }
            for (RequestSaveInput.WorkerDocInput di : wi.getDocs()) {
                if (di == null || di.getDocCode() == null) {
                    continue;
                }
                SesRequestWorkerDoc row = byCode.get(di.getDocCode());
                if (!isNonEmpty(di.getFile())) {
                    // Nothing uploaded for this slot this time. Leave whatever
                    // is already there — an edit that only changes a name must
                    // not silently drop the documents.
                    continue;
                }
                String newFileId = appFileService.store(di.getFile(), "SES_REQUEST_WORKER_DOC:" + workerId,
                        docDisplayName(di.getDocCode()) + " - " + name);
                String replaced = null;
                if (row == null) {
                    row = new SesRequestWorkerDoc();
                    row.setWorkerId(workerId);
                    row.setDocCode(di.getDocCode());
                } else if (row.getFileId() != null && !row.getFileId().equals(newFileId)) {
                    replaced = row.getFileId();
                }
                row.setFileId(newFileId);
                workerDocRepository.save(row);
                // THE leak, closed at its source: the replaced BLOB is deleted
                // instead of being orphaned in app_file — but only once this
                // row has been repointed, and only if nothing ELSE still
                // references it (a renewal shares file_id with its source).
                workerDocRepository.flush();
                deleteFileIfUnreferenced(replaced);
            }
        }
        return worker;
    }

    /**
     * Removes one worker row, its document rows, and the BLOBs behind them.
     *
     * <p>{@code fk_ses_wdoc_worker} cascades the doc rows away on its own; the
     * files are deleted here, explicitly, because nothing else will.</p>
     */
    @Transactional
    public void deleteWorker(Long requestId, Long workerId, String username) {
        editableDraft(requestId, username);
        SesRequestWorker worker = workerRepository.findById(workerId)
                .orElseThrow(() -> new IllegalArgumentException("Pekerja tidak ditemukan: " + workerId));
        if (!requestId.equals(worker.getRequestId())) {
            throw new AccessDeniedException("Worker " + workerId + " does not belong to request " + requestId);
        }
        // Collect first, delete the rows, THEN delete the files. The order is
        // load-bearing: deleteFileIfUnreferenced counts the document rows
        // still pointing at each file, so it has to run once this worker's own
        // rows are gone. Deleting the files first would also mean a failure
        // half-way through left rows pointing at bytes that no longer exist.
        List<String> fileIds = new ArrayList<>();
        for (SesRequestWorkerDoc d : findWorkerDocs(workerId)) {
            if (d.getFileId() != null) {
                fileIds.add(d.getFileId());
            }
        }
        workerRepository.deleteById(workerId);
        workerRepository.flush();
        for (String fileId : fileIds) {
            deleteFileIfUnreferenced(fileId);
        }
        renumberWorkers(requestId);
    }

    /**
     * The same per-worker × per-requirement matrix as {@link #workerRows}, but
     * for the token-protected upload page.
     *
     * <p>Separate from {@code workerRows} for one reason: that method takes a
     * {@code username} and calls {@code assertOwned}, and on the upload page
     * there is no authenticated user to pass. Adding a nullable username to
     * the existing method would have put a "skip the ownership check" branch
     * inside the method whose job is to enforce it — a branch someone would
     * eventually reach from the authenticated side. Authorisation for this
     * path is the token, checked by {@code UploadTokenService.resolve} before
     * the caller ever gets a request id.</p>
     *
     * <p>The upload page must not link to {@code /file/{id}}: that endpoint
     * requires a session and a {@code FileOwnershipResolver} that admits the
     * viewer. Preview on this page goes through
     * {@code /request/upload-docs/file?token=...&fileId=...} instead, which
     * authorises against the same token.</p>
     *
     * <p><b>Two sources, merged, staging wins.</b> A staged row is what the
     * contractor sent in this session; the {@code ses_request_worker_doc} row
     * underneath it is a renewal's carried-forward document, which
     * {@code createRenewalDraft} copies with the SAME {@code file_id} as the
     * previous request. Reading only staging would make last year's
     * certificates look missing and force the contractor to re-send papers an
     * earlier submission already proved; reading only the official table would
     * hide everything sent in this session. Each staged row also carries its
     * own {@code extDocId}, because Delete acts on the staging row and must
     * not be offered for a carried-forward document that belongs to another
     * request.</p>
     */
    @Transactional(readOnly = true)
    public List<WorkerRowDto> uploadRows(Long requestId) {
        SesRequest request = findById(requestId);
        List<RequirementRow> requirements = requirementRowsFor(request.getTypeCode());

        // worker_id -> doc_code -> staged row
        Map<Long, Map<String, SesExtUploadDoc>> stagedByWorker = new HashMap<>();
        for (SesExtUploadDoc s : extUploadStagingService.activeDocs(requestId)) {
            stagedByWorker.computeIfAbsent(s.getWorkerId(), k -> new HashMap<>())
                    .put(s.getDocCode(), s);
        }

        List<WorkerRowDto> rows = new ArrayList<>();
        List<SesRequestWorker> workers = findWorkers(requestId);
        // One query for every worker's documents. This is the public
        // contractor upload page: a query per worker here is paid by someone
        // outside the intranet, on a link they were emailed.
        Map<Long, List<SesRequestWorkerDoc>> docsByWorker = findWorkerDocsByWorkerIds(
                workers.stream().map(SesRequestWorker::getWorkerId).collect(Collectors.toList()));
        for (SesRequestWorker w : workers) {
            Map<String, String> fileByCode = new HashMap<>();
            for (SesRequestWorkerDoc d : docsByWorker.getOrDefault(w.getWorkerId(), Collections.<SesRequestWorkerDoc>emptyList())) {
                fileByCode.put(d.getDocCode(), d.getFileId());
            }
            Map<String, SesExtUploadDoc> staged =
                    stagedByWorker.getOrDefault(w.getWorkerId(), Collections.<String, SesExtUploadDoc>emptyMap());

            List<WorkerDocDto> docs = new ArrayList<>();
            for (RequirementRow req : requirements) {
                SesExtUploadDoc s = staged.get(req.getDocCode());
                if (s != null) {
                    docs.add(new WorkerDocDto(req.getDocCode(), req.getDocName(), req.isMandatory(),
                            s.getFileId(), s.getExtDocId(), s.getFileName(), s.getFileSize()));
                } else {
                    docs.add(new WorkerDocDto(req.getDocCode(), req.getDocName(), req.isMandatory(),
                            fileByCode.get(req.getDocCode()), null, null, null));
                }
            }
            rows.add(new WorkerRowDto(w.getWorkerId(), w.getLineNo(), w.getWorkerName(), w.getNik(), w.getIdType(),
                    w.getBadge(), w.getJabatan(), w.getDepartment(), w.getSection(), w.getGender(), docs));
        }
        return rows;
    }

    /**
     * Stores one document uploaded through the token link.
     *
     * <p>An upsert with explicit BLOB cleanup, exactly like
     * {@link #saveWorker}'s document handling — re-uploading a file must not
     * strand the one it replaces in {@code app_file}, which is the leak
     * {@code sql/51} exists to clean up after.</p>
     *
     * <p>Documents are saved <b>one at a time, as they arrive</b>, rather than
     * all at the end. That is what makes the link resumable: the email
     * promises the visitor can close the page and come back, and a batch that
     * only persisted on final submit would quietly break that promise for
     * anyone collecting six workers' certificates over two days.</p>
     *
     * <p>{@code workerId} is re-checked against {@code requestId} before
     * anything is written. Without it, a token for request A would let its
     * holder post a {@code workerId} belonging to request B and overwrite that
     * request's documents — the one IDOR this page could have.</p>
     */
    @Transactional
    public void storeUploadedDoc(Long requestId, Long workerId, String docCode,
                                 MultipartFile file, String actor) {
        if (!isNonEmpty(file)) {
            return;
        }
        UploadTarget target = assertUploadTarget(requestId, workerId, docCode);

        String newFileId = appFileService.store(file,
                "SES_REQUEST_WORKER_DOC:" + workerId,
                target.getDocName() + " - " + target.getWorker().getWorkerName());

        attachOfficialDoc(workerId, docCode, newFileId);
    }

    /**
     * Points the official document row for (worker, docCode) at
     * {@code newFileId}, releasing whatever it pointed at before.
     *
     * <p>Extracted so the external-upload finalisation can reuse it: moving a
     * staging row into the official set is exactly this operation, with the
     * file already stored. Keeping it in one place is what stops the two paths
     * from disagreeing about the replaced-file cleanup.</p>
     */
    @Transactional
    public void attachOfficialDoc(Long workerId, String docCode, String newFileId) {
        SesRequestWorkerDoc row = null;
        for (SesRequestWorkerDoc d : findWorkerDocs(workerId)) {
            if (d.getDocCode().equals(docCode)) {
                row = d;
                break;
            }
        }
        String replaced = null;
        if (row == null) {
            row = new SesRequestWorkerDoc();
            row.setWorkerId(workerId);
            row.setDocCode(docCode);
        } else if (row.getFileId() != null && !row.getFileId().equals(newFileId)) {
            replaced = row.getFileId();
        }
        row.setFileId(newFileId);
        workerDocRepository.save(row);
        workerDocRepository.flush();
        deleteFileIfUnreferenced(replaced);
    }

    /**
     * Proves that (requestId, workerId, docCode) is a slot this upload is
     * allowed to write, and returns what the caller needs to name the file.
     *
     * <p>Two checks, both load-bearing, and both now shared between the
     * official upload path and the external staging path rather than written
     * twice:</p>
     * <ul>
     *   <li><b>The worker must belong to this request.</b> Without it, a token
     *       for request A would let its holder post a {@code workerId}
     *       belonging to request B and overwrite that request's documents —
     *       the one IDOR this flow could have. A security check that exists in
     *       two copies is a security check that will exist in one copy after
     *       the next edit.</li>
     *   <li><b>The document must be one the request type asks for.</b> A
     *       posted {@code docCode} outside the requirement list would create a
     *       row nothing ever reads and nothing ever cleans up.</li>
     * </ul>
     */
    @Transactional(readOnly = true)
    public UploadTarget assertUploadTarget(Long requestId, Long workerId, String docCode) {
        SesRequestWorker worker = workerRepository.findById(workerId)
                .orElseThrow(() -> new IllegalArgumentException("Pekerja tidak ditemukan: " + workerId));
        if (!requestId.equals(worker.getRequestId())) {
            throw new AccessDeniedException(
                    "Worker " + workerId + " does not belong to request " + requestId);
        }
        RequirementRow match = null;
        for (RequirementRow r : requirementRowsFor(typeCodeOf(requestId))) {
            if (r.getDocCode().equals(docCode)) {
                match = r;
                break;
            }
        }
        if (match == null) {
            throw new IllegalArgumentException("Dokumen tidak dikenal: " + docCode);
        }
        return new UploadTarget(worker, match.getDocName(), match.isMandatory());
    }

    /** What {@link #assertUploadTarget} proved, so the caller need not re-read it. */
    @lombok.Getter
    @lombok.AllArgsConstructor
    public static class UploadTarget {
        private final SesRequestWorker worker;
        private final String docName;
        private final boolean mandatory;
    }

    /**
     * Promotes every staged document of this request into
     * {@code ses_request_worker_doc}, then marks the staging session finished.
     *
     * <p>Lives here rather than on {@code ExtUploadStagingService} because it
     * writes the official document rows, and because the reverse arrangement
     * would need that service to inject this one — a circular bean reference
     * that Spring Boot 2.6+ refuses at startup. The staging service is a
     * one-way dependency of this class.</p>
     *
     * <p>Each file moves by a single {@code app_file.ref_id} UPDATE. No bytes
     * are copied: that is the whole reason the staging table holds a
     * {@code file_id} pointer rather than a BLOB.</p>
     */
    private void transferStagedDocs(Long requestId) {
        List<SesExtUploadDoc> staged = extUploadStagingService.activeDocs(requestId);
        for (SesExtUploadDoc row : staged) {
            appFileService.reassign(row.getFileId(), "SES_REQUEST_WORKER_DOC:" + row.getWorkerId());
            attachOfficialDoc(row.getWorkerId(), row.getDocCode(), row.getFileId());
        }
        extUploadStagingService.markFinalized(requestId);
        if (!staged.isEmpty()) {
            log.info("Transferred {} staged document(s) into ses_request_worker_doc for request {}",
                    staged.size(), requestId);
        }
    }

    private String typeCodeOf(Long requestId) {
        return findById(requestId).getTypeCode();
    }

    /**
     * Deletes an {@code app_file} row only when no document row still points
     * at it.
     *
     * <p><b>This closes a data-loss bug, not a leak.</b> The three callers
     * used to delete unconditionally, and {@code createRenewalDraft} shares
     * {@code file_id} between a renewal's document row and the row it was
     * copied from — deliberately, to avoid duplicating multi-megabyte BLOBs.
     * The two together meant that re-sending one document on a renewal, or
     * removing a worker from it, deleted the file out from under the
     * <b>original approved request</b>, whose printed sheet and approval trail
     * both still reference it. {@code createRenewalDraft}'s own javadoc
     * asserted this could not happen ("neither request can change what the
     * other sees"); it could.</p>
     *
     * <p>Guarding here rather than at each call site is the point: a check
     * repeated in three places is a check that will exist in two of them after
     * the next edit. The count runs <b>after</b> the caller has repointed or
     * removed its own row, so "still referenced" means referenced by somebody
     * else.</p>
     *
     * <p>The leak the old code was written to close stays closed for the
     * ordinary case — a file nobody else references is still deleted. What
     * survives now is a shared file, and a shared file is not an orphan;
     * {@code sql/51_orphan_app_file_cleanup.sql} remains the sweep for
     * anything that does slip through.</p>
     */
    private void deleteFileIfUnreferenced(String fileId) {
        if (fileId == null || fileId.trim().isEmpty()) {
            return;
        }
        long stillReferenced = workerDocRepository.countByFileId(fileId);
        if (stillReferenced > 0) {
            log.debug("Keeping app_file {} — still referenced by {} document row(s)", fileId, stillReferenced);
            return;
        }
        appFileService.delete(fileId);
    }

    /**
     * A request's saved workers as flat rows, for re-rendering the table after
     * a modal save.
     *
     * <p>Carries each document's {@code fileId} and human label so the table
     * can link straight to {@code /file/{id}} — the requestor's proof that the
     * upload actually landed, which is the entire reassurance the old
     * all-at-once form could never give.</p>
     */
    @Transactional(readOnly = true)
    public List<WorkerRowDto> workerRows(Long requestId, String username) {
        SesRequest request = findById(requestId);
        assertOwned(request, username);

        List<RequirementRow> requirements = requirementRowsFor(request.getTypeCode());
        List<WorkerRowDto> rows = new ArrayList<>();
        for (SesRequestWorker w : findWorkers(requestId)) {
            Map<String, String> fileByCode = new HashMap<>();
            for (SesRequestWorkerDoc d : findWorkerDocs(w.getWorkerId())) {
                fileByCode.put(d.getDocCode(), d.getFileId());
            }
            List<WorkerDocDto> docs = new ArrayList<>();
            for (RequirementRow req : requirements) {
                docs.add(new WorkerDocDto(req.getDocCode(), req.getDocName(), req.isMandatory(),
                        fileByCode.get(req.getDocCode())));
            }
            rows.add(new WorkerRowDto(w.getWorkerId(), w.getLineNo(), w.getWorkerName(), w.getNik(), w.getIdType(),
                    w.getBadge(), w.getJabatan(), w.getDepartment(), w.getSection(), w.getGender(), docs));
        }
        return rows;
    }

    @lombok.Getter
    @lombok.AllArgsConstructor
    public static class WorkerRowDto {
        private final Long workerId;
        private final Integer lineNo;
        private final String workerName;
        private final String nik;
        private final String idType;
        private final String badge;
        private final String jabatan;
        private final String department;
        private final String section;
        private final String gender;
        private final List<WorkerDocDto> docs;
    }

    @lombok.Getter
    @lombok.AllArgsConstructor
    public static class WorkerDocDto {
        private final String docCode;
        private final String docName;
        private final boolean mandatory;
        /** Null when nothing has been uploaded for this requirement yet. */
        private final String fileId;
        /**
         * The staging row's id, or null when the file is NOT in staging — a
         * renewal's carried-forward document, or any upload made through the
         * requestor's own form.
         *
         * <p>Null is what tells the upload page to offer Preview but not
         * Delete. Deleting a carried-forward row would remove a document that
         * belongs to the previous, already-approved request; only something
         * this session put there may be taken back out.</p>
         */
        private final Long extDocId;
        /** Snapshot filename, so the page can name the file without reading the BLOB. */
        private final String fileName;
        private final Long fileSize;

        /** Pre-staging shape, kept so {@link #workerRows} needs no change. */
        public WorkerDocDto(String docCode, String docName, boolean mandatory, String fileId) {
            this(docCode, docName, mandatory, fileId, null, null, null);
        }

        /** True when this file arrived in the current upload session and can be removed. */
        public boolean isDeletable() {
            return extDocId != null;
        }
    }

    /**
     * How far along the MANDATORY documents are, for the upload page's
     * progress bar.
     *
     * <p>Static and over the DTOs so it can be unit-tested without Spring —
     * the arithmetic is small but the choice inside it is not. <b>Optional
     * documents are excluded.</b> Counting them would leave the bar short of
     * 100% on a submission that is actually ready to send, which reads as
     * "something is still missing" and is the one thing a completeness
     * indicator must not get wrong.</p>
     *
     * <p>A type that demands no documents at all reports 100%, not 0%: there
     * is nothing outstanding, and an empty bar would say the opposite.</p>
     */
    public static MandatoryProgress mandatoryProgress(List<WorkerRowDto> workers) {
        int total = 0;
        int done = 0;
        if (workers != null) {
            for (WorkerRowDto w : workers) {
                if (w.getDocs() == null) {
                    continue;
                }
                for (WorkerDocDto d : w.getDocs()) {
                    if (!d.isMandatory()) {
                        continue;
                    }
                    total++;
                    if (d.getFileId() != null) {
                        done++;
                    }
                }
            }
        }
        return new MandatoryProgress(done, total);
    }

    /** Result of {@link #mandatoryProgress}. */
    @lombok.Getter
    public static class MandatoryProgress {
        private final int done;
        private final int total;
        private final int percent;

        MandatoryProgress(int done, int total) {
            this.done = done;
            this.total = total;
            this.percent = total == 0 ? 100 : done * 100 / total;
        }

        public boolean isComplete() {
            return done >= total;
        }
    }

    /** Keeps line_no contiguous after a delete, so the table numbers read 1..n. */
    private void renumberWorkers(Long requestId) {
        int lineNo = 1;
        List<SesRequestWorker> workers = findWorkers(requestId);
        for (SesRequestWorker w : workers) {
            w.setLineNo(lineNo++);
        }
        workerRepository.saveAll(workers);
    }

    /**
     * Creates a DRAFT pre-filled from a past APPROVED request of this user's.
     *
     * <p>Renewal used to be a purely client-side affair: the form was rendered
     * with the old rows in the DOM and every identity stripped, and nothing
     * was persisted until Submit. The incremental saver makes that untenable —
     * the form no longer posts worker fields at all, so those pre-filled rows
     * would simply evaporate. Persisting the copy up front means renewal is
     * just "a draft that starts out filled in", and every other path on this
     * screen works on it unchanged.</p>
     *
     * <p><b>Documents are deliberately not copied.</b> The old renewal stripped
     * {@code existingFileId} for the same reason: a renewal wants current
     * documents, not last year's. It also avoids two requests pointing at one
     * {@code app_file} row, where deleting a worker from one would pull the
     * file out from under the other.</p>
     */
    @Transactional
    public Long createRenewalDraft(Long sourceRequestId, String username) {
        SesRequest source = findById(sourceRequestId);
        assertOwned(source, username);
        if (!RequestStatus.APPROVED.equals(source.getStatus())) {
            throw new IllegalStateException("Only an approved request can be used for renewal.");
        }
        requestTypeService.assertSelectable(source.getTypeCode(), username);

        RequestSaveInput header = new RequestSaveInput();
        header.setTypeCode(source.getTypeCode());
        header.setContractNo(source.getContractNo());
        header.setContractType(source.getContractType());
        header.setContractTitle(source.getContractTitle());
        header.setZoneCode(source.getZoneCode());
        header.setInstance(source.getInstance());
        header.setAddress(source.getAddress());
        // Periods deliberately NOT copied: a renewal is for a new period, and
        // carrying the expired one forward is the single most likely thing for
        // a requestor to miss.
        List<String> evaluatorIds = new ArrayList<>();
        for (SesRequestEvaluator e : evaluatorRepository
                .findByRequestIdOrderByLineNoAscEvaluatorIdAsc(sourceRequestId)) {
            evaluatorIds.add(e.getEmplId());
        }
        header.setEvaluatorEmplIds(evaluatorIds);

        Long newId = saveDraft(header, username);

        int lineNo = 1;
        for (SesRequestWorker src : findWorkers(sourceRequestId)) {
            SesRequestWorker copy = new SesRequestWorker();
            copy.setRequestId(newId);
            copy.setLineNo(lineNo++);
            copy.setWorkerName(src.getWorkerName());
            copy.setNik(src.getNik());
            copy.setIdType(src.getIdType());
            copy.setBadge(src.getBadge());
            copy.setJabatan(src.getJabatan());
            copy.setDepartment(src.getDepartment());
            copy.setSection(src.getSection());
            copy.setGender(src.getGender());
            copy = workerRepository.save(copy);

            // Carry the documents over too. This used to be skipped
            // deliberately — a renewal was expected to collect fresh papers,
            // and the form had file inputs to collect them with. The form no
            // longer does: documents now arrive only through the upload link,
            // so dropping them here would force the contractor to re-send
            // certificates the previous submission already proved.
            //
            // The file_id is SHARED with the source request's row, not copied.
            // app_file rows are immutable once stored, the per-worker upsert
            // in saveWorker replaces rather than mutates, and the upload page
            // writes a new file_id when a document is re-sent — so neither
            // request can change what the other sees. Duplicating multi-
            // megabyte BLOBs to express "same document" would be the costlier
            // lie.
            for (SesRequestWorkerDoc srcDoc : findWorkerDocs(src.getWorkerId())) {
                if (srcDoc.getFileId() == null || srcDoc.getFileId().trim().isEmpty()) {
                    continue;
                }
                SesRequestWorkerDoc docCopy = new SesRequestWorkerDoc();
                docCopy.setWorkerId(copy.getWorkerId());
                docCopy.setDocCode(srcDoc.getDocCode());
                docCopy.setFileId(srcDoc.getFileId());
                workerDocRepository.save(docCopy);
            }
        }
        return newId;
    }

    /**
     * Final submit of a draft whose workers are ALREADY persisted.
     *
     * <p>Rebuilds a {@link RequestSaveInput} from the rows on disk, merges in
     * the header the form just posted, and hands it to
     * {@link #saveAndSubmit}. Reusing that method rather than reimplementing
     * the submit path is the whole point: every validation rule, the payment
     * handling, the supersede-cooldown and the approval trigger stay in ONE
     * place. A second copy of them would drift within a release.</p>
     *
     * <p>Each persisted document comes back as an {@code existingFileId}, so
     * {@code resolveWorkerDocFileId} carries the file forward rather than
     * asking for it again — the client never re-uploads anything at Submit.</p>
     */
    @Transactional
    public Long submitDraft(RequestSaveInput header, String username) {
        if (header.getRequestId() == null) {
            throw new IllegalArgumentException("Draf belum tersimpan — tidak ada yang bisa disubmit.");
        }
        SesRequest request = editableDraft(header.getRequestId(), username);

        List<RequestSaveInput.WorkerInput> workers = new ArrayList<>();
        for (SesRequestWorker w : findWorkers(request.getRequestId())) {
            RequestSaveInput.WorkerInput wi = new RequestSaveInput.WorkerInput();
            wi.setWorkerId(w.getWorkerId());
            wi.setWorkerName(w.getWorkerName());
            wi.setNik(w.getNik());
            wi.setIdType(w.getIdType());
            wi.setBadge(w.getBadge());
            wi.setJabatan(w.getJabatan());
            wi.setDepartment(w.getDepartment());
            wi.setSection(w.getSection());
            wi.setGender(w.getGender());

            List<RequestSaveInput.WorkerDocInput> docs = new ArrayList<>();
            for (SesRequestWorkerDoc d : findWorkerDocs(w.getWorkerId())) {
                RequestSaveInput.WorkerDocInput di = new RequestSaveInput.WorkerDocInput();
                di.setDocCode(d.getDocCode());
                di.setExistingFileId(d.getFileId());
                docs.add(di);
            }
            wi.setDocs(docs);
            workers.add(wi);
        }
        header.setWorkers(workers);
        return saveAndSubmit(header, username);
    }

    @Transactional
    public void cancel(Long requestId, String username) {
        SesRequest request = findById(requestId);
        // IDOR fix. This method took a requestId straight off the URL and
        // never checked who was asking: anyone holding SUBMIT on /request/form
        // could cancel a stranger's request. The GET side has always checked
        // (RequestController.editForm); the POST side never did.
        assertOwned(request, username);
        if (!RequestStatus.isRequestorEditable(request.getStatus())) {
            throw new IllegalStateException("Only a pending or draft request can be cancelled.");
        }
        if (request.getApprovalInstanceId() != null) {
            approvalService.cancel(request.getApprovalInstanceId(), username, "Cancelled by requestor");
        }
        request.setStatus(RequestStatus.CANCELLED);
        requestRepository.save(request);
    }


    /**
     * Replaces a request's Staf Security evaluator list.
     *
     * <p>Delete-and-reinsert rather than diff-and-merge. Safe here, unlike for
     * worker rows: nothing points at an evaluator row, it owns no uploaded
     * file, and once {@code ApprovalService#trigger()} has run the approval
     * holds its own frozen copy in {@code ses_approval_task_signoff}. Deleting
     * these rows therefore cannot disturb an approval already in flight — the
     * exact property worker rows lack, which is why the same shortcut leaked
     * BLOBs there (see {@code sql/51}).</p>
     *
     * <p>The ids are re-resolved through {@link SecurityEvaluatorService}
     * rather than trusted: that call is what rejects an {@code empl_id} that
     * is not a current candidate, what supplies the name and position copied
     * into the row, and what drops anyone the rest of the chain already asks
     * — the requestor's own manager, or a procurement approver — so the same
     * person never gets two inbox rows for one submission.</p>
     *
     * <p>An empty list is a legitimate outcome, not a failure: additional
     * routing is optional, and so is what survives the dedup. The approval
     * engine skips the consensus step rather than opening an empty one.</p>
     */
    private void saveEvaluators(Long requestId, List<String> emplIds, String username) {
        evaluatorRepository.deleteByRequestId(requestId);
        evaluatorRepository.flush();

        List<SecurityEvaluatorService.Option> resolved =
                securityEvaluatorService.resolveSelected(emplIds, username);
        int lineNo = 1;
        for (SecurityEvaluatorService.Option option : resolved) {
            SesRequestEvaluator row = new SesRequestEvaluator();
            row.setRequestId(requestId);
            row.setLineNo(lineNo++);
            row.setPosId(option.getPosId());
            row.setPosTitle(option.getPosTitle());
            row.setEmplId(option.getEmplId());
            row.setEmplName(option.getEmplName());
            row.setCreatedBy(username);
            row.setCreatedDate(new Date());
            evaluatorRepository.save(row);
        }
    }

    // ---------------------------------------------------------------
    // Validation
    // ---------------------------------------------------------------

    private SesRequestType validate(RequestSaveInput input, String username) {
        if (input.getTypeCode() == null) {
            throw new IllegalArgumentException("Type of Request is required.");
        }
        SesRequestType type = requestTypeRepository.findById(input.getTypeCode())
                .orElseThrow(() -> new IllegalArgumentException("Unknown request type: " + input.getTypeCode()));
        if (!"Y".equals(type.getActiveFlag())) {
            throw new IllegalArgumentException("Request type is not active: " + input.getTypeCode());
        }
        // Auth-type gate. The dropdown is already filtered (see
        // RequestController#populateReferenceData), but a filtered dropdown is
        // cosmetic on its own: nothing stops a crafted POST naming a typeCode
        // this account may not use. This is the check that actually enforces it.
        requestTypeService.assertSelectable(type.getTypeCode(), username);

        // Start Period is mandatory on submit. A badge with no start date is
        // not a permit anyone can act on, and the column was nullable only
        // because drafts are allowed to be incomplete — which is why this is
        // in validate() and NOT in saveDraft().
        //
        // The DB column is deliberately left nullable: rows written before
        // this rule can legitimately be null, and ALTER ... NOT NULL would
        // simply fail against them.
        if (input.getStartPeriod() == null) {
            throw new IllegalArgumentException("Start Period wajib diisi.");
        }

        // The address the upload link is sent to. Mandatory only when Need EXT Page Upload is enabled.
        if (type.isNeedExtPageUpload()) {
            String email = trimToNull(input.getEmail());
            if (email == null) {
                throw new IllegalArgumentException("Email Penerima Dokumen wajib diisi.");
            }
            if (!isPlausibleEmail(email)) {
                throw new IllegalArgumentException("Format Email Penerima Dokumen tidak valid: " + email);
            }
            if (email.length() > 150) {
                throw new IllegalArgumentException("Email Penerima Dokumen terlalu panjang (maksimal 150 karakter).");
            }
        }

        // NO evaluator rule here, on purpose. Additional routing is OPTIONAL:
        // a requestor who names nobody has made a valid choice, and the
        // approval engine skips the consensus step rather than opening one
        // with zero sign-offs (see ApprovalService.willBeSkipped). An earlier
        // revision rejected the submission here; that was wrong, and removing
        // it is the point of the change — not an omission.

        List<RequestSaveInput.WorkerInput> workers = input.getWorkers();
        if (workers == null || workers.isEmpty() || workers.size() > 25) {
            throw new IllegalArgumentException("Worker Data must have between 1 and 25 workers.");
        }

        Set<String> niksSeen = new HashSet<>();
        for (RequestSaveInput.WorkerInput w : workers) {
            String nik = w.getNik() == null ? "" : w.getNik().trim();
            validateIdentity(w.getIdType(), nik, w.getWorkerName());
            if (!niksSeen.add(nik)) {
                throw new IllegalArgumentException("Duplicate NIK within this request: " + nik);
            }
            // ck_ses_worker_gender allows only 'M'/'F' (and NULL). Validating
            // here rather than letting Oracle raise ORA-02290 keeps the message
            // readable, and stops a silently un-selected <select> (an
            // un-normalised lookup value, see applyWorkerSelection) reaching
            // the database as NULL and then rendering as "Male" on the detail
            // page, which is how this stayed invisible.
            String gender = w.getGender() == null ? "" : w.getGender().trim();
            if (!"M".equals(gender) && !"F".equals(gender)) {
                throw new IllegalArgumentException(
                        "Gender for worker \"" + w.getWorkerName() + "\" must be Male or Female.");
            }
        }

        if (input.getFinishPeriod() != null && input.getStartPeriod() != null
                && input.getFinishPeriod().before(input.getStartPeriod())) {
            throw new IllegalArgumentException("Finish Period must not be before Start Period.");
        }

        // NO mandatory-document check here, on purpose. The documents belong
        // to the contractor and arrive later through the emailed upload link,
        // so at submit time there is nothing to check and demanding it would
        // make the whole flow impossible. The rule still exists — it moved to
        // assertAllMandatoryDocsPresent, which runs when the upload page
        // submits and checks what is actually persisted rather than what a
        // form claims.

        // Proof of Payment IS required at submit time when the type demands it.
        // "Supply it later from /payment" was the previous rule; requested
        // change 2026-09-22 makes the upload a submit gate instead. An edit-
        // resubmit of a request whose payment item already carries a proof
        // passes without re-uploading — currentProofFileId is the same lookup
        // the form itself uses to render the carried-forward attachment.
        // Save as Draft never reaches this method, so drafts stay incomplete-able.
        if ("Y".equals(type.getNeedsPaymentFlag()) && !isNonEmpty(input.getPaymentFile())) {
            boolean hasExistingProof = input.getRequestId() != null
                    && paymentService.currentProofFileId(PAYMENT_BUSINESS_TYPE, input.getRequestId()).isPresent();
            if (!hasExistingProof) {
                throw new IllegalArgumentException("Bukti bayar wajib diunggah untuk jenis pengajuan \""
                        + type.getTypeName() + "\" sebelum pengajuan dikirim.");
            }
        }

        return type;
    }

    private boolean isNonEmpty(MultipartFile file) {
        return file != null && !file.isEmpty();
    }
}
