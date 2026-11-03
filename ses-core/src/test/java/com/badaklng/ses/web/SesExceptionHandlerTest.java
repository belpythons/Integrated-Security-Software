package com.badaklng.ses.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure unit tests — no Spring context needed, {@link SesExceptionHandler}
 * only depends on {@link ObjectMapper}. Covers the status-code mapping Task 1
 * asked for and the HTML/JSON branch, which is the part a live-ELLDEV
 * {@code @SpringBootTest} would exercise far less precisely than a direct
 * call with a {@link MockHttpServletRequest}/{@link MockHttpServletResponse}
 * pair.
 */
class SesExceptionHandlerTest {

    private final SesExceptionHandler handler = new SesExceptionHandler(new ObjectMapper());

    @Test
    void accessDeniedRendersThe403ViewWithForbiddenStatus() throws IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/request/24/cancel");
        request.addHeader("Accept", "text/html");
        MockHttpServletResponse response = new MockHttpServletResponse();
        Model model = new ExtendedModelMap();

        String view = handler.handleAccessDenied(
                new AccessDeniedException("You do not have access to this request."), request, response, model);

        assertEquals("error/403", view);
        assertEquals(403, response.getStatus());
        assertEquals("Access Denied", model.getAttribute("pageTitle"));
        assertEquals("You do not have access to this request.", model.getAttribute("errorMessage"));
    }

    @Test
    void accessDeniedWritesJsonBodyForAjaxCallers() throws IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/request/24/cancel");
        request.addHeader("Accept", "application/json");
        MockHttpServletResponse response = new MockHttpServletResponse();
        Model model = new ExtendedModelMap();

        String view = handler.handleAccessDenied(
                new AccessDeniedException("You do not have access to this request."), request, response, model);

        assertNull(view, "a JSON-handled request signals 'response already written' via a null view name");
        assertEquals(403, response.getStatus());
        assertEquals("application/json", response.getContentType());
        String body = response.getContentAsString();
        assertTrue(body.contains("\"status\":403"), body);
        assertTrue(body.contains("You do not have access to this request."), body);
        assertTrue(body.contains("/request/24/cancel"), body);
    }

    @Test
    void accessDeniedAlsoWritesJsonForXmlHttpRequestHeaderRegardlessOfAccept() throws IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/request/24/cancel");
        request.addHeader("X-Requested-With", "XMLHttpRequest");
        MockHttpServletResponse response = new MockHttpServletResponse();

        String view = handler.handleAccessDenied(new AccessDeniedException("denied"), request, response,
                new ExtendedModelMap());

        assertNull(view);
        assertEquals("application/json", response.getContentType());
    }

    @Test
    void illegalStateRendersThe400ViewWithBadRequestStatus() throws IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/request/25/document.pdf");
        request.addHeader("Accept", "text/html");
        MockHttpServletResponse response = new MockHttpServletResponse();
        Model model = new ExtendedModelMap();

        String view = handler.handleBadRequest(
                new IllegalStateException("Dokumen hanya dapat dicetak setelah seluruh tahap persetujuan selesai."),
                request, response, model);

        assertEquals("error/400", view);
        assertEquals(400, response.getStatus());
        assertEquals("Bad Request", model.getAttribute("pageTitle"));
        assertEquals("Dokumen hanya dapat dicetak setelah seluruh tahap persetujuan selesai.",
                model.getAttribute("errorMessage"));
    }

    @Test
    void illegalArgumentAlsoMapsToBadRequest() throws IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/some/endpoint");
        request.addHeader("Accept", "application/json");
        MockHttpServletResponse response = new MockHttpServletResponse();

        String view = handler.handleBadRequest(new IllegalArgumentException("bad input"), request, response,
                new ExtendedModelMap());

        assertNull(view);
        assertEquals(400, response.getStatus());
        assertTrue(response.getContentAsString().contains("bad input"));
    }

    @Test
    void plainBrowserFetchWithWildcardAcceptGetsTheHtmlPageNotJson() throws IOException {
        // A bare fetch() with no explicit Accept header (or "*/*") should get
        // the human-readable page, not a JSON blob nobody asked for.
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/request/24/cancel");
        request.addHeader("Accept", "*/*");
        MockHttpServletResponse response = new MockHttpServletResponse();

        String view = handler.handleAccessDenied(new AccessDeniedException("denied"), request, response,
                new ExtendedModelMap());

        assertEquals("error/403", view);
        assertNotEquals("application/json", response.getContentType());
    }

    @Test
    void maxUploadSizeExceededMapsToBadRequestWithFriendlyMessage() throws IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/request/upload-docs/file/save-draft");
        request.addHeader("Accept", "application/json");
        MockHttpServletResponse response = new MockHttpServletResponse();

        String view = handler.handleMaxUploadSize(
                new org.springframework.web.multipart.MaxUploadSizeExceededException(15 * 1024 * 1024),
                request, response, new ExtendedModelMap());

        assertNull(view);
        assertEquals(400, response.getStatus());
        assertTrue(response.getContentAsString().contains("Ukuran berkas melebihi batas maksimum 10 MB."));
    }
}
