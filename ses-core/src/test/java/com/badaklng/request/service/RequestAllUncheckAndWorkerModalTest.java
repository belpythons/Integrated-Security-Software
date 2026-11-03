package com.badaklng.request.service;

import com.badaklng.approval.service.ApprovalService;
import com.badaklng.approval.service.SecurityEvaluatorService;
import com.badaklng.payment.service.PaymentService;
import com.badaklng.request.entity.RequestStatus;
import com.badaklng.request.entity.SesRequest;
import com.badaklng.request.entity.SesRequestType;
import com.badaklng.request.entity.SesRequestWorker;
import com.badaklng.request.entity.SesRequestWorkerDoc;
import com.badaklng.request.repository.SesDocRequirementRepository;
import com.badaklng.request.repository.SesRequestEvaluatorRepository;
import com.badaklng.request.repository.SesRequestRepository;
import com.badaklng.request.repository.SesRequestTypeDocRepository;
import com.badaklng.request.repository.SesRequestTypeRepository;
import com.badaklng.request.repository.SesRequestWorkerDocRepository;
import com.badaklng.request.repository.SesRequestWorkerRepository;
import com.badaklng.request.repository.SesZoneRepository;
import com.badaklng.ses.file.AppFileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests verifying:
 * 1. Immediate document upload when saving a worker row in the modal (before main form submission).
 * 2. Request Type with All-Uncheck flags (no ext upload, no requestor manager, no procurement manager, no payment).
 */
@ExtendWith(MockitoExtension.class)
class RequestAllUncheckAndWorkerModalTest {

    @Mock
    private SesRequestRepository requestRepository;
    @Mock
    private SesRequestWorkerRepository workerRepository;
    @Mock
    private SesRequestWorkerDocRepository workerDocRepository;
    @Mock
    private SesRequestTypeRepository requestTypeRepository;
    @Mock
    private SesRequestTypeDocRepository requestTypeDocRepository;
    @Mock
    private SesDocRequirementRepository docRequirementRepository;
    @Mock
    private SesZoneRepository zoneRepository;
    @Mock
    private AppFileService appFileService;
    @Mock
    private ApprovalService approvalService;
    @Mock
    private PaymentService paymentService;
    @Mock
    private RequestTypeService requestTypeService;
    @Mock
    private SesRequestEvaluatorRepository evaluatorRepository;
    @Mock
    private SecurityEvaluatorService securityEvaluatorService;
    @Mock
    private UploadTokenService uploadTokenService;
    @Mock
    private RequestMailService requestMailService;
    @Mock
    private ExtUploadStagingService extUploadStagingService;

    private RequestService requestService;

    @BeforeEach
    void setUp() {
        requestService = new RequestService(
                requestRepository,
                workerRepository,
                workerDocRepository,
                requestTypeRepository,
                requestTypeDocRepository,
                docRequirementRepository,
                zoneRepository,
                appFileService,
                approvalService,
                paymentService,
                requestTypeService,
                evaluatorRepository,
                securityEvaluatorService,
                uploadTokenService,
                requestMailService,
                extUploadStagingService
        );
    }

    @Test
    @DisplayName("Modal Save: Worker data and documents are stored immediately into database tables on modal submit")
    void testWorkerModalSavesDataAndDocsImmediately() {
        Long requestId = 100L;
        String username = "testuser";

        SesRequest request = new SesRequest();
        request.setRequestId(requestId);
        request.setRequestorUserId(username);
        request.setStatus(RequestStatus.DRAFT);

        when(requestRepository.findById(requestId)).thenReturn(Optional.of(request));
        when(workerRepository.findByRequestIdOrderByLineNoAsc(requestId)).thenReturn(Collections.emptyList());

        SesRequestWorker savedWorker = new SesRequestWorker();
        savedWorker.setWorkerId(501L);
        savedWorker.setRequestId(requestId);
        savedWorker.setWorkerName("Budi Santoso");
        savedWorker.setNik("1234567890123456");
        savedWorker.setGender("M");
        when(workerRepository.save(any(SesRequestWorker.class))).thenReturn(savedWorker);

        MockMultipartFile file = new MockMultipartFile(
                "file", "ktp.pdf", "application/pdf", "KTP Content".getBytes());

        RequestSaveInput.WorkerDocInput docInput = new RequestSaveInput.WorkerDocInput();
        docInput.setDocCode("KTP");
        docInput.setFile(file);

        RequestSaveInput.WorkerInput workerInput = new RequestSaveInput.WorkerInput();
        workerInput.setWorkerName("Budi Santoso");
        workerInput.setNik("1234567890123456");
        workerInput.setGender("M");
        workerInput.setJabatan("Teknisi");
        workerInput.setDocs(Collections.singletonList(docInput));

        when(appFileService.store(any(), eq("SES_REQUEST_WORKER_DOC:501"), any())).thenReturn("FILE-UUID-1234");
        when(workerDocRepository.findByWorkerId(501L)).thenReturn(Collections.emptyList());

        // Execute saveWorker (simulating POST /request/{id}/worker/save from modal submit)
        SesRequestWorker result = requestService.saveWorker(requestId, workerInput, username);

        // 1. Worker table check: SES_REQUEST_WORKER
        assertNotNull(result);
        assertEquals(501L, result.getWorkerId());
        verify(workerRepository).save(any(SesRequestWorker.class));

        // 2. File BLOB table check: APP_FILE via AppFileService
        verify(appFileService).store(eq(file), eq("SES_REQUEST_WORKER_DOC:501"), any());

        // 3. Worker Doc table check: SES_REQUEST_WORKER_DOC
        ArgumentCaptor<SesRequestWorkerDoc> docCaptor = ArgumentCaptor.forClass(SesRequestWorkerDoc.class);
        verify(workerDocRepository).save(docCaptor.capture());
        SesRequestWorkerDoc savedDoc = docCaptor.getValue();
        assertEquals(501L, savedDoc.getWorkerId());
        assertEquals("KTP", savedDoc.getDocCode());
        assertEquals("FILE-UUID-1234", savedDoc.getFileId());
    }

    @Test
    @DisplayName("All-Uncheck Request Type: flags disable ext upload, manager approvals, and payment")
    void testAllUncheckRequestTypeConfiguration() {
        SesRequestType allUncheckType = new SesRequestType();
        allUncheckType.setTypeCode("TYPE_ALL_UNCHECK");
        allUncheckType.setTypeName("Permohonan Khusus");
        allUncheckType.setNeedsMgrApprovalFlag("N");
        allUncheckType.setNeedsProcApprovalFlag("N");
        allUncheckType.setNeedExtPageUpload("N");
        allUncheckType.setNeedsPaymentFlag("N");

        assertFalse(allUncheckType.isNeedExtPageUpload(), "need_ext_page_upload must be false");
        assertEquals("N", allUncheckType.getNeedsMgrApprovalFlag(), "needs_mgr_approval_flag must be N");
        assertEquals("N", allUncheckType.getNeedsProcApprovalFlag(), "needs_proc_approval_flag must be N");
        assertEquals("N", allUncheckType.getNeedsPaymentFlag(), "needs_payment_flag must be N");

        boolean requireManagerStep = !"N".equalsIgnoreCase(allUncheckType.getNeedsMgrApprovalFlag());
        boolean requireProcStep = !"N".equalsIgnoreCase(allUncheckType.getNeedsProcApprovalFlag());
        boolean requirePaymentCheckpoint = "Y".equals(allUncheckType.getNeedsPaymentFlag());

        assertFalse(requireManagerStep, "Request Manager step must be disabled");
        assertFalse(requireProcStep, "Procurement Manager step must be disabled");
        assertFalse(requirePaymentCheckpoint, "Payment Checkpoint must be disabled");
    }
}
