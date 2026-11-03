package com.badaklng.request.service;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Service-layer input for {@link RequestService#saveAndSubmit}. The
 * controller maps its web-binding {@code RequestEditForm} into this shape
 * — the form never binds directly to the service layer, mirroring the
 * ApprovalChainEditForm.toServiceInput() / ApprovalChainService.ChainEditInput
 * precedent.
 */
@Getter
@Setter
@NoArgsConstructor
public class RequestSaveInput {

    /** Null on create; set on edit (must be an existing PENDING request). */
    private Long requestId;

    private String typeCode;
    private String contractNo;
    private String contractType;
    private String contractTitle;
    private String zoneCode;
    private String instance;
    private String address;
    private Date startPeriod;
    private Date finishPeriod;

    /** Proof of Payment — new upload, or null if not (re)uploaded this submission. */
    private MultipartFile paymentFile;
    /** Carries forward an already-uploaded Proof of Payment across an edit. */
    private String existingPaymentFileId;

    /**
     * Where the document-upload link is sent.
     *
     * <p><b>The contractor's address, not the requestor's.</b> The worker
     * documents are held outside Badak LNG, and this is how they get in —
     * which is also why the page that link opens needs no login. Mandatory
     * for a submit; Save as Draft never validates it.</p>
     */
    private String email;

    private List<WorkerInput> workers = new ArrayList<>();

    /**
     * {@code empl_id}s of the Staf Security the requestor picked as step-1
     * evaluators. Only ids travel: the name and position are re-resolved
     * server-side through {@code SecurityEvaluatorService.resolveSelected},
     * which is also what rejects an id that is not a real candidate. Trusting
     * a posted name would let a crafted request put any text it liked into the
     * approval route.
     */
    private List<String> evaluatorEmplIds = new ArrayList<>();

    @Getter
    @Setter
    @NoArgsConstructor
    public static class WorkerInput {
        /** Not currently used by saveAndSubmit (workers are replaced by diff), kept for symmetry with the form. */
        private Long workerId;
        private String workerName;
        private String nik;
        private String idType = "NIK";
        private String badge;
        private String jabatan;
        private String department;
        private String section;
        private String gender;
        private List<WorkerDocInput> docs = new ArrayList<>();
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class WorkerDocInput {
        private String docCode;
        private MultipartFile file;
        /** Carries forward an already-uploaded file across an edit where this document wasn't re-uploaded. */
        private String existingFileId;
    }
}
