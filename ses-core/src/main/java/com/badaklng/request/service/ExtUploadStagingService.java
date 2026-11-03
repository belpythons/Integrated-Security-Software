package com.badaklng.request.service;

import com.badaklng.request.entity.SesExtUploadDoc;
import com.badaklng.request.entity.SesExtUploadRequest;
import com.badaklng.request.entity.SesRequest;
import com.badaklng.request.repository.SesExtUploadDocRepository;
import com.badaklng.request.repository.SesExtUploadRequestRepository;
import com.badaklng.ses.file.AppFileService;
import com.badaklng.ses.file.SesAppFileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.Date;
import java.util.List;
import java.util.Optional;

/**
 * The lifecycle of one external upload session: open it at submit, accept and
 * remove documents one at a time while the contractor works, and hand the
 * finished set over when they submit.
 *
 * <p>Separate from {@link RequestService} rather than another 200 lines inside
 * a class already past 1400 — and it follows the module's existing split
 * ({@code UploadTokenService}, {@code RequestMailService},
 * {@code RequestDocumentService} are each their own service).</p>
 *
 * <p><b>Staging exists so a half-collected set is distinguishable from a
 * finished one.</b> Before this, contractors wrote straight into
 * {@code ses_request_worker_doc} — the official document table — and the only
 * thing separating "three of six certificates are in" from "the contractor
 * says this is complete" was the request's status, not the document data.</p>
 *
 * <h2>Why this class injects no {@link RequestService}</h2>
 *
 * <p>{@code RequestService.finalizeUpload} calls into here, so injecting it
 * back would be a circular bean reference — which Spring Boot 2.6+ refuses at
 * startup by default, turning a tidy-looking dependency into a context that
 * will not boot. The dependency therefore runs one way only: {@code
 * RequestService} → this.</p>
 *
 * <p>The ownership check still cannot be skipped. {@link #saveDraftDoc} does
 * not take a {@code workerId}; it takes a {@link RequestService.UploadTarget},
 * which is only obtainable from {@code RequestService.assertUploadTarget} —
 * the method that proves the worker belongs to this request and the document
 * is one the type asks for. Referring to that TYPE is a compile-time
 * dependency, not a bean one, so the guard is mandatory by signature while the
 * wiring stays acyclic. A caller cannot forget a check it has no way to
 * fabricate the result of.</p>
 *
 * <p><b>Bytes are never copied.</b> {@code AppFileService.store} writes them
 * once under a staging {@code ref_id}; finalisation repoints
 * {@code app_file.ref_id} with {@code reassign}. "Transfer" means one UPDATE
 * of a 100-character column.</p>
 *
 * <p><b>No {@code SecurityUtils} in this class, ever.</b> Everything here runs
 * for an anonymous visitor holding a token. There is no principal, so any
 * check built on one would return null and read as passing.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExtUploadStagingService {

    /**
     * {@code app_file.ref_id} prefix while a file is still in staging.
     *
     * <p>Deliberately claimed by NO {@link com.badaklng.ses.file.FileOwnershipResolver},
     * which makes {@code /file/{id}} return 403 for every logged-in user for
     * as long as the file is unfinalised. That is the wanted answer, not an
     * oversight: a document the contractor has not submitted yet is not part
     * of the request, and the only page allowed to show it is the token-scoped
     * upload page itself. Finalisation repoints the ref_id to
     * {@code SES_REQUEST_WORKER_DOC:<workerId>}, at which point the existing
     * {@code RequestFileOwnershipResolver} starts admitting the requestor and
     * the current approver.</p>
     */
    public static final String STAGING_REF_PREFIX = "SES_EXT_UPLOAD_DOC:";

    static final String ACTIVE = "Y";
    static final String INACTIVE = "N";

    private final SesExtUploadRequestRepository bridgeRepository;
    private final SesExtUploadDocRepository docRepository;
    private final AppFileService appFileService;

    // ---------------------------------------------------------------
    // Session
    // ---------------------------------------------------------------

    /**
     * Creates or refreshes the bridge row for a request now waiting on
     * documents.
     *
     * <p>Upsert on {@code request_id}, matching the table's UNIQUE constraint:
     * re-sending the link is the same session with a new token, not a second
     * session. A second row would also leave the documents already collected
     * with two candidate parents.</p>
     *
     * <p>Idempotent on purpose — {@code saveAndSubmit} can re-enter the
     * external-upload branch (Fase 10 §3), and calling this twice must not
     * disturb documents already in.</p>
     */
    @Transactional
    public SesExtUploadRequest openSession(SesRequest request, String typeName) {
        SesExtUploadRequest bridge = bridgeRepository.findByRequestId(request.getRequestId())
                .orElseGet(SesExtUploadRequest::new);

        bridge.setRequestId(request.getRequestId());
        bridge.setRequestNo(request.getRequestNo());
        bridge.setTypeCode(request.getTypeCode());
        bridge.setTypeName(typeName);
        bridge.setRecipientEmail(request.getEmail());
        bridge.setUploadToken(request.getUploadToken());
        bridge.setTokenExpiry(request.getUploadTokenExpiry());
        // Re-opening a session that was somehow already finalised would be an
        // upstream bug, but clearing the flag is the safe direction: a session
        // marked finished that still accepts uploads is worse than one marked
        // unfinished that did not need to be.
        bridge.setIsFinalized(INACTIVE);
        bridge.setFinalizedDate(null);
        return bridgeRepository.save(bridge);
    }

    @Transactional(readOnly = true)
    public Optional<SesExtUploadRequest> findSession(Long requestId) {
        return bridgeRepository.findByRequestId(requestId);
    }

    // ---------------------------------------------------------------
    // Per-file save / delete
    // ---------------------------------------------------------------

    /**
     * Stores one file as the contractor picks it, replacing whatever was in
     * that slot.
     *
     * <p>Replacement is a soft-delete plus an insert, not an UPDATE, so the
     * order in which documents arrived stays readable. The {@code app_file}
     * row behind the replaced staging row IS hard-deleted, unguarded: nothing
     * else can reference a staging file — its {@code ref_id} prefix is private
     * to this flow and the row was written by this flow — so the shared-file
     * hazard {@code RequestService.deleteFileIfUnreferenced} exists for cannot
     * arise here.</p>
     *
     * <p>⚠️ The soft-delete is flushed before the insert. The unique index
     * {@code ux_ses_ext_upload_doc_active} is function-based over
     * {@code is_active='Y'}; without the flush Hibernate may order the INSERT
     * first and collide (ORA-00001) with the very row this call is
     * discarding.</p>
     *
     * @param target what {@code RequestService.assertUploadTarget} proved —
     *               see the class javadoc for why this is a parameter and not
     *               an injected call
     */
    @Transactional
    public SesExtUploadDoc saveDraftDoc(Long requestId, RequestService.UploadTarget target,
                                        String docCode, MultipartFile file) {
        SesExtUploadRequest bridge = bridgeRepository.findByRequestId(requestId)
                .orElseThrow(() -> new IllegalStateException(
                        "Sesi unggah untuk pengajuan ini belum dibuka."));
        if (bridge.isFinalizedFlag()) {
            throw new IllegalStateException("Dokumen pengajuan ini sudah dikirim final.");
        }

        String fileId = appFileService.store(file,
                STAGING_REF_PREFIX + requestId,
                target.getDocName() + " - " + target.getWorker().getWorkerName());
        // Metadata only -- the three columns below. appFileService.get() would
        // pull file_data back across the wire (per-file ceiling 20 MB) for the
        // bytes we literally just sent, on the public contractor upload path.
        SesAppFileRepository.FileMeta stored = appFileService.getMeta(fileId);

        Long workerId = target.getWorker().getWorkerId();
        Optional<SesExtUploadDoc> previous = docRepository
                .findByRequestIdAndWorkerIdAndDocCodeAndIsActive(requestId, workerId, docCode, ACTIVE);
        if (previous.isPresent()) {
            SesExtUploadDoc old = previous.get();
            old.setIsActive(INACTIVE);
            docRepository.save(old);
            docRepository.flush();
            appFileService.delete(old.getFileId());
        }

        SesExtUploadDoc row = new SesExtUploadDoc();
        row.setExtRequestId(bridge.getExtRequestId());
        row.setRequestId(requestId);
        row.setWorkerId(workerId);
        row.setDocCode(docCode);
        row.setDocName(target.getDocName());
        row.setFileId(fileId);
        row.setFileName(stored.getFileName());
        row.setFileContentType(stored.getFileType());
        row.setFileSize(stored.getFileSize());
        row.setUploadedAt(new Date());
        row.setIsActive(ACTIVE);
        return docRepository.save(row);
    }

    /**
     * Removes one staged document.
     *
     * <p>{@code requestId} is a parameter and is compared against the row's
     * own, rather than being read off it. The caller proved a token for
     * exactly ONE request, so a posted {@code extDocId} belonging to another
     * must be refused; trusting the row's own {@code requestId} would make the
     * check tautological. The refusal reuses the not-found message so a prober
     * cannot tell "no such document" from "not yours".</p>
     */
    @Transactional
    public void deleteDraftDoc(Long requestId, Long extDocId) {
        SesExtUploadDoc row = docRepository.findById(extDocId)
                .orElseThrow(() -> new IllegalArgumentException("Dokumen tidak ditemukan: " + extDocId));
        if (!requestId.equals(row.getRequestId())) {
            throw new IllegalArgumentException("Dokumen tidak ditemukan: " + extDocId);
        }
        if (!row.isActiveFlag()) {
            return;
        }
        row.setIsActive(INACTIVE);
        docRepository.save(row);
        docRepository.flush();
        appFileService.delete(row.getFileId());
    }

    @Transactional(readOnly = true)
    public List<SesExtUploadDoc> activeDocs(Long requestId) {
        return docRepository.findByRequestIdAndIsActiveOrderByWorkerIdAscDocCodeAsc(requestId, ACTIVE);
    }

    /** Backs the token-scoped preview endpoint's "is this file yours" check. */
    @Transactional(readOnly = true)
    public Optional<SesExtUploadDoc> findActiveByFileId(Long requestId, String fileId) {
        return docRepository.findByRequestIdAndFileIdAndIsActive(requestId, fileId, ACTIVE);
    }

    // ---------------------------------------------------------------
    // Finalisation
    // ---------------------------------------------------------------

    /**
     * Marks the session finished.
     *
     * <p>Called by {@code RequestService.finalizeUpload} <b>after</b> it has
     * repointed every staged file and written the official rows, and inside
     * that method's transaction — so if the mandatory-document check then
     * fails, this flag rolls back with everything else.</p>
     *
     * <p>Staging rows are left ACTIVE rather than soft-deleted. After the
     * transfer they point at the same {@code file_id} as the official rows, so
     * the two cannot disagree, and keeping them is the record of what arrived
     * when. {@code is_finalized} on the bridge is what says the session is
     * over.</p>
     */
    @Transactional
    public void markFinalized(Long requestId) {
        bridgeRepository.findByRequestId(requestId).ifPresent(bridge -> {
            bridge.setIsFinalized(ACTIVE);
            bridge.setFinalizedDate(new Date());
            bridgeRepository.save(bridge);
        });
    }
}
