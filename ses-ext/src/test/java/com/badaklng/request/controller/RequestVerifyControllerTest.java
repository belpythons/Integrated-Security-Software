package com.badaklng.request.controller;

import com.badaklng.approval.service.ApprovalService;
import com.badaklng.request.entity.SesRequest;
import com.badaklng.request.entity.SesRequestType;
import com.badaklng.request.entity.SesRequestWorker;
import com.badaklng.request.repository.SesRequestRepository;
import com.badaklng.request.repository.SesRequestTypeRepository;
import com.badaklng.request.service.RequestService;
import com.badaklng.ses.user.UserDisplayNameService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RequestVerifyControllerTest {

    @Mock
    private SesRequestRepository requestRepository;
    @Mock
    private SesRequestTypeRepository requestTypeRepository;
    @Mock
    private RequestService requestService;
    @Mock
    private ApprovalService approvalService;
    @Mock
    private UserDisplayNameService userDisplayNameService;

    private RequestVerifyController controller;

    @BeforeEach
    void setUp() {
        controller = new RequestVerifyController(
                requestRepository, requestTypeRepository, requestService,
                approvalService, userDisplayNameService
        );
    }

    @Test
    @DisplayName("verify returns found=false when request number is not in system")
    void verifyNotFound() {
        when(requestRepository.findByRequestNo("UNKNOWN-REQ")).thenReturn(Optional.empty());

        Model model = new ConcurrentModel();
        String view = controller.verify("UNKNOWN-REQ", model);

        assertEquals("request/verify", view);
        assertEquals(false, model.getAttribute("found"));
    }

    @Test
    @DisplayName("verify returns found=true with request, signers, and workers populated")
    void verifyFoundPopulatesModel() {
        String reqNo = "REQ-2026-0001";
        Long reqId = 1L;
        SesRequest req = new SesRequest();
        req.setRequestId(reqId);
        req.setRequestNo(reqNo);
        req.setTypeCode("BADGE_1");
        req.setStatus("APPROVED");
        req.setRequestorUserId("contractor_user");
        req.setApprovalInstanceId(200L);
        req.setStartPeriod(new Date());
        req.setFinishPeriod(new Date());

        when(requestRepository.findByRequestNo(reqNo)).thenReturn(Optional.of(req));

        SesRequestType type = new SesRequestType();
        type.setTypeCode("BADGE_1");
        type.setTypeName("Permohonan Badge Zone-1");
        when(requestTypeRepository.findById("BADGE_1")).thenReturn(Optional.of(type));

        when(userDisplayNameService.displayName("contractor_user")).thenReturn("Gawitno");

        SesRequestWorker w1 = new SesRequestWorker();
        w1.setWorkerId(10L);
        w1.setWorkerName("Intan Nur");
        w1.setJabatan("Safetyman");
        w1.setNik("6472056103990002");
        when(requestService.findWorkers(reqId)).thenReturn(Arrays.asList(w1));

        ApprovalService.SignatureSlot procSlot = new ApprovalService.SignatureSlot(
                "Mengetahui,", "Procurement", "Ronggo A.W", false
        );
        when(approvalService.signatureSlots(200L)).thenReturn(Collections.singletonList(procSlot));

        Model model = new ConcurrentModel();
        String view = controller.verify(reqNo, model);

        assertEquals("request/verify", view);
        assertEquals(true, model.getAttribute("found"));
        assertEquals("Permohonan Badge Zone-1", model.getAttribute("documentName"));
        assertEquals("Gawitno", model.getAttribute("siteManagerName"));
        assertEquals("Ronggo A.W", model.getAttribute("procurementSigner"));
        assertEquals(true, model.getAttribute("approved"));

        @SuppressWarnings("unchecked")
        List<SesRequestWorker> workers = (List<SesRequestWorker>) model.getAttribute("workers");
        assertNotNull(workers);
        assertEquals(1, workers.size());
        assertEquals("Intan Nur", workers.get(0).getWorkerName());
    }
}
