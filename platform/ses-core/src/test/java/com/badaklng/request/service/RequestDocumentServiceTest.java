package com.badaklng.request.service;

import com.badaklng.approval.service.ApprovalService;
import com.badaklng.request.entity.RequestStatus;
import com.badaklng.request.entity.SesRequest;
import com.badaklng.request.entity.SesRequestType;
import com.badaklng.request.entity.SesRequestWorker;
import com.badaklng.request.repository.SesZoneRepository;
import com.badaklng.ses.text.SesText;
import com.badaklng.ses.user.UserDisplayNameService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RequestDocumentServiceTest {

    @Mock
    private TemplateEngine templateEngine;
    @Mock
    private RequestService requestService;
    @Mock
    private RequestQrCodeService qrCodeService;
    @Mock
    private SesZoneRepository zoneRepository;
    @Mock
    private ApprovalService approvalService;
    @Mock
    private UserDisplayNameService userDisplayNameService;
    /**
     * Not stubbed: the cases below exercise isPrintable and fileNameFor, which
     * render no wording. renderPdf's text lookups are covered by
     * RequestDocumentServiceIntegrationTest, which gets the real bean.
     */
    @Mock
    private SesText sesText;

    private RequestDocumentService documentService;

    @BeforeEach
    void setUp() {
        documentService = new RequestDocumentService(
                sesText, templateEngine, requestService, qrCodeService,
                zoneRepository, approvalService, userDisplayNameService
        );
    }

    @Test
    @DisplayName("isPrintable returns true only for APPROVED requests")
    void testIsPrintable() {
        SesRequest req = new SesRequest();
        req.setStatus("APPROVED");
        assertTrue(documentService.isPrintable(req));

        req.setStatus(RequestStatus.PENDING);
        assertFalse(documentService.isPrintable(req));

        req.setStatus(RequestStatus.DRAFT);
        assertFalse(documentService.isPrintable(req));

        assertFalse(documentService.isPrintable(null));
    }

    @Test
    @DisplayName("renderPdf throws exception when request is not APPROVED")
    void testRenderPdfThrowsWhenNotApproved() {
        Long requestId = 99L;
        SesRequest req = new SesRequest();
        req.setRequestId(requestId);
        req.setStatus(RequestStatus.PENDING);

        when(requestService.findById(requestId)).thenReturn(req);

        assertThrows(IllegalStateException.class, () ->
                documentService.renderPdf(requestId, "user1"));
    }

    @Test
    @DisplayName("fileNameFor formats suggested filename correctly")
    void testFileNameFor() {
        Long requestId = 10L;
        SesRequest req = new SesRequest();
        req.setRequestId(requestId);
        req.setRequestNo("REQ-2026-0001");

        when(requestService.findById(requestId)).thenReturn(req);

        assertEquals("Permohonan-REQ-2026-0001.pdf", documentService.fileNameFor(requestId));
    }
}
