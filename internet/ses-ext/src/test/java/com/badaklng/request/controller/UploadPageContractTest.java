package com.badaklng.request.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The rules {@code request/upload-docs.html} must keep obeying, asserted
 * against the template source.
 *
 * <p>Reading a template as text is a crude test and it is the right one here.
 * This is the only page in SES reachable without logging in, so the things
 * that must not appear on it matter more than the things that must — and
 * "must not appear" is exactly what a rendering test cannot prove, because it
 * only ever renders the cases somebody thought of. Fase 9 wrote three of these
 * rules down as prose in a javadoc; prose does not fail a build.</p>
 *
 * <p>No Spring context: a {@code @SpringBootTest} here would need the Oracle
 * connection and a live request row to render anything at all.</p>
 */
class UploadPageContractTest {

    private static Path resolvePage() {
        Path p = Paths.get("src", "main", "resources", "templates", "request", "upload-docs.html");
        if (Files.exists(p)) return p;
        p = Paths.get("ses-ext", "src", "main", "resources", "templates", "request", "upload-docs.html");
        if (Files.exists(p)) return p;
        return Paths.get("src", "main", "resources", "templates", "request", "upload-docs.html");
    }

    private static final Path PAGE = resolvePage();

    private static String page() throws Exception {
        assertTrue(Files.exists(PAGE), PAGE + " is missing");
        return new String(Files.readAllBytes(PAGE), StandardCharsets.UTF_8);
    }

    // ---------------------------------------------------------------
    // The security rules from Fase 9
    // ---------------------------------------------------------------

    @Test
    @DisplayName("The page never links to the session-protected /file/{id} endpoint")
    void neverLinksToTheAuthenticatedFileEndpoint() throws Exception {
        // Two reasons, and the second is the one that matters. /file/{id}
        // demands a session, so an anonymous visitor gets a 403 — but more
        // importantly, holding a token to UPLOAD documents must not confer the
        // right to READ documents somebody else already sent. Previews go
        // through /request/upload-docs/file, which authorises against the same
        // token and is scoped to this request.
        String html = page();

        assertFalse(html.contains("@{/file/"),
                "upload-docs.html must not render a /file/{id} link — use the token-scoped preview endpoint");
        assertTrue(html.contains("@{/request/upload-docs/file("),
                "the token-scoped preview endpoint must be what the page links to");
    }

    @Test
    @DisplayName("Every state-changing endpoint on the page carries the CSRF token")
    void csrfIsPresent() throws Exception {
        // Spring issues a CSRF token to an anonymous session as readily as to
        // a logged-in one. Disabling it "because the page is public" would be
        // backwards; so would adding AJAX endpoints that forget it.
        String html = page();

        assertTrue(html.contains("${_csrf.parameterName}"), "the form must post the CSRF token");
        assertTrue(html.contains("CSRF_PARAM"), "the AJAX helper must send the CSRF token too");
        assertTrue(html.contains("formData.append(CSRF_PARAM, CSRF_TOKEN)"),
                "every AJAX post goes through the helper that appends CSRF");
    }

    @Test
    @DisplayName("The page shows nothing the email did not already reveal")
    void noPrivilegedFields() throws Exception {
        // The recipient is outside the company. They know the request number,
        // the type and the workers they are supplying papers for. Contract
        // VALUES, payment amounts, the requestor's email address and approval
        // history are all off this page — and each is one careless th:text
        // away from being on it.
        String html = page();

        assertFalse(html.contains("paymentAmount"), "payment amounts must not appear on the public page");
        assertFalse(html.contains("amountDue"));
        assertFalse(html.contains("contractValue"));
        assertFalse(html.contains("request.email"),
                "the recipient address is not shown back; it is not theirs to audit");
        assertFalse(html.contains("approvalSummary"), "approval history must not appear on the public page");
    }

    // ---------------------------------------------------------------
    // The Fase 12 behaviour
    // ---------------------------------------------------------------

    @Test
    @DisplayName("Files auto-save per row, and a saved input is cleared")
    void autoSaveClearsTheInput() throws Exception {
        // Without the clear, a file uploaded by AJAX would ALSO ride along on
        // final submit and be stored a second time, straight into
        // ses_request_worker_doc, bypassing staging entirely.
        String html = page();

        assertTrue(html.contains("/request/upload-docs/file/save-draft"));
        assertTrue(html.contains("q(row, '.js-file').value = '';"),
                "a successfully saved input must be cleared or the file is stored twice");
    }

    @Test
    @DisplayName("Delete posts a staging id, never a file id")
    void deleteUsesStagingId() throws Exception {
        // extDocId scopes the delete to a row this session created. A fileId
        // would also match a renewal's carried-forward document, which shares
        // its app_file row with the previous approved request.
        String html = page();

        assertTrue(html.contains("/request/upload-docs/file/delete"));
        assertTrue(html.contains("fd.append('extDocId'"),
                "delete must be keyed on the staging row, not the file");
        assertFalse(html.contains("fd.append('fileId'"),
                "deleting by fileId would allow removing a carried-forward renewal document");
    }

    @Test
    @DisplayName("The one-time-token notice states 1-session requirement")
    void oneTimeNoticeIsUnambiguous() throws Exception {
        String html = page();

        assertTrue(html.contains("Tautan berlaku untuk 1 sesi"));
        assertTrue(html.contains("1 sesi"), "the 1-session promise must be stated");
        assertTrue(html.contains("Kirim Dokumen"),
                "submission instruction must be stated");
    }

    @Test
    @DisplayName("The upload size limit is read from configuration, not hard-coded")
    void sizeLimitComesFromConfig() throws Exception {
        // The template used to advertise 10 MB while AppFileService enforced
        // ${ses.upload.max-document-size:2MB}. A rejected upload that the page
        // promised would work is a support call, and the contractor cannot
        // read application.properties to find out who was lying.
        String html = page();

        assertTrue(html.contains("${maxUploadMb}"), "the advertised ceiling must come from the model");
        assertFalse(html.contains("10&nbsp;MB"), "the stale hard-coded 10 MB claim must be gone");
    }

    @Test
    @DisplayName("The logo is a real image again, and the stale comment is gone")
    void logoIsRendered() throws Exception {
        // /image/** IS in SecurityConfig's anonymous allowlist — it is the
        // first antMatchers line. The previous revision used a text wordmark
        // and a comment asserting the opposite, so the one page whose job is
        // to look legitimate was the one page with no logo.
        String html = page();

        assertTrue(html.contains("@{/image/ptb_logo.png}"));
        assertFalse(html.contains("is NOT in the anonymous permitAll list"),
                "that comment was factually wrong and must not be reinstated");
    }

    @Test
    @DisplayName("Submit form is a lightweight confirmation without multipart payload to prevent timeouts")
    void submitFormIsConfirmationWithoutMultipart() throws Exception {
        // Files are saved individually via AJAX save-draft to staging.
        // The final submit is purely confirmation to finalize the request,
        // avoiding timeouts from re-uploading large multipart payloads.
        String html = page();

        assertFalse(html.contains("enctype=\"multipart/form-data\""),
                "form must not use multipart enctype to prevent timeout on final submission");
        assertTrue(html.contains("id=\"uploadForm\""),
                "uploadForm must be present for final confirmation submit");
    }
}
