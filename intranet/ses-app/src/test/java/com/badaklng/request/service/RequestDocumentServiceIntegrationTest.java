package com.badaklng.request.service;

import com.badaklng.request.entity.SesRequest;
import com.badaklng.request.repository.SesRequestRepository;
import com.badaklng.ses.SesApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Exercises {@link RequestDocumentService#renderPdf} against a real APPROVED
 * request in the dev database — the HTTP layer (security, controller) is
 * already covered by manual checks; this is the one path that needs real
 * data (worker rows, an approval instance) to mean anything.
 *
 * <p>Uses whatever APPROVED / non-APPROVED requests already exist rather than
 * creating one, so it skips (not fails) when the dev DB has none right now.</p>
 */
@SpringBootTest(classes = SesApplication.class)
@ActiveProfiles("local")
class RequestDocumentServiceIntegrationTest {

    @Autowired
    private RequestDocumentService documentService;

    @Autowired
    private SesRequestRepository requestRepository;

    @Test
    void rendersPdfForAnApprovedRequest() {
        Optional<SesRequest> approved = requestRepository
                .findAll(PageRequest.of(0, 2000)).stream()
                .filter(r -> "APPROVED".equals(r.getStatus()))
                .findFirst();
        assumeTrue(approved.isPresent(), "no APPROVED request in the dev DB right now — skipping");

        SesRequest request = approved.get();
        assertTrue(documentService.isPrintable(request));

        byte[] pdf = documentService.renderPdf(request.getRequestId(), "admin");

        assertTrue(pdf.length > 1000, "rendered PDF should have real content");
        assertEquals("%PDF", new String(pdf, 0, 4, java.nio.charset.StandardCharsets.US_ASCII));
    }

    @Test
    void refusesToRenderANonApprovedRequest() {
        Optional<SesRequest> notApproved = requestRepository
                .findAll(PageRequest.of(0, 2000)).stream()
                .filter(r -> !"APPROVED".equals(r.getStatus()))
                .findFirst();
        assumeTrue(notApproved.isPresent(), "every request in the dev DB is APPROVED — skipping");

        SesRequest request = notApproved.get();
        assertThrows(IllegalStateException.class,
                () -> documentService.renderPdf(request.getRequestId(), "admin"));
    }
}
