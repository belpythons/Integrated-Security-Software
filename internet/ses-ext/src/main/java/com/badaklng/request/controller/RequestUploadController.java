package com.badaklng.request.controller;

import com.badaklng.request.entity.SesExtUploadDoc;
import com.badaklng.request.entity.SesRequest;
import com.badaklng.request.entity.SesRequestType;
import com.badaklng.request.repository.SesRequestTypeRepository;
import com.badaklng.request.service.ExtUploadStagingService;
import com.badaklng.request.service.RequestService;
import com.badaklng.request.service.UploadTokenService;
import com.badaklng.ses.file.AppFileService;
import com.badaklng.ses.file.SesAppFile;
import com.badaklng.ses.user.UserDisplayNameService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.unit.DataSize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The document-upload page a contractor reaches from the emailed link.
 *
 * <p><b>The only unauthenticated page in SES.</b> It has to be: the worker
 * documents are held by the contractor, who has no account here and never
 * will. Everything that usually protects a controller is therefore absent —
 * no {@code SecurityUtils.getCurrentUsername()}, no {@code PageAccessGuard},
 * no {@code APP_PAGE} row, no ownership check against a session. The 256-bit
 * token in the query string is the whole of the authorisation, and
 * {@link UploadTokenService#resolve} is where it is judged.</p>
 *
 * <p>Three consequences worth stating plainly, because each is a thing a
 * future change could quietly undo:</p>
 * <ul>
 *   <li><b>Never call {@code SecurityUtils} from here.</b> There is no
 *       principal; it would return null and any check built on it would read
 *       as passing.</li>
 *   <li><b>Never render a generic {@code /file/{id}} link.</b> That endpoint demands a
 *       session. For document previews on this page, use {@code /request/upload-docs/file?token=...&fileId=...}
 *       which validates against the token.</li>
 *   <li><b>Never show anything the email did not already reveal.</b> The
 *       recipient knows the request number, the type and the worker list
 *       because they were asked to supply papers for them. Contract values,
 *       the requestor's email, payment amounts and approval history stay off
 *       this page.</li>
 * </ul>
 *
 * <p>CSRF stays on. Spring issues a token to an anonymous session as readily
 * as to a logged-in one, and Thymeleaf's {@code th:action} puts it in the
 * form, so the protection costs nothing here and disabling it "because the
 * page is public" would be exactly backwards.</p>
 */
@Controller
@RequestMapping("/request/upload-docs")
@Slf4j
public class RequestUploadController {

    /**
     * Prefix of the multipart field names the form posts, e.g.
     * {@code doc-4821-MEDICAL}. Parsed rather than bound to a form object
     * because the shape is a matrix — workers × requirements — whose
     * dimensions are only known at render time.
     */
    private static final String FIELD_PREFIX = "doc-";

    private final RequestService requestService;
    private final UploadTokenService uploadTokenService;
    private final SesRequestTypeRepository requestTypeRepository;
    private final UserDisplayNameService userDisplayNameService;
    private final AppFileService appFileService;
    private final ExtUploadStagingService extUploadStagingService;

    public RequestUploadController(RequestService requestService,
                                   UploadTokenService uploadTokenService,
                                   SesRequestTypeRepository requestTypeRepository,
                                   UserDisplayNameService userDisplayNameService,
                                   AppFileService appFileService,
                                   ExtUploadStagingService extUploadStagingService) {
        this.requestService = requestService;
        this.uploadTokenService = uploadTokenService;
        this.requestTypeRepository = requestTypeRepository;
        this.userDisplayNameService = userDisplayNameService;
        this.appFileService = appFileService;
        this.extUploadStagingService = extUploadStagingService;
    }

    /** Same property {@code AppFileService} enforces, so the page cannot advertise a different ceiling. */
    @Value("${ses.upload.max-document-size:20MB}")
    private DataSize maxDocumentSize;

    /**
     * Renders the upload form, or the refusal page.
     *
     * <p>Opening the link does NOT consume the token — only submitting does.
     * Someone chasing six workers' medical certificates will open this page,
     * close it and come back, and the email promises exactly that.</p>
     */
    @GetMapping
    public String form(@RequestParam(value = "token", required = false) String token, Model model, HttpServletRequest request) {
        String sessionId = request.getSession(true).getId();
        Optional<SesRequest> found = uploadTokenService.resolve(token, sessionId);
        if (!found.isPresent()) {
            return refuse(model);
        }
        populate(model, found.get(), token);
        return "request/upload-docs";
    }

    /**
     * Saves ONE file the moment the contractor picks it, and answers with JSON
     * the page uses to swap that row into its "uploaded" state.
     *
     * <p>This is what makes the session genuinely resumable. The form used to
     * persist nothing until Submit, so a visitor collecting six workers'
     * certificates over two days had to keep the tab alive or start again —
     * while the email promised they could close the page and come back.</p>
     *
     * <p>The token is re-resolved here, exactly as in {@link #submit}: a
     * hidden field is a claim, not an authorisation, and the request may have
     * been cancelled or swept away between page load and this upload.</p>
     */
    @PostMapping("/file/save-draft")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> saveDraftFile(
            @RequestParam(value = "token", required = false) String token,
            @RequestParam("workerId") Long workerId,
            @RequestParam("docCode") String docCode,
            @RequestParam("file") MultipartFile file,
            HttpServletRequest request) {

        String sessionId = request.getSession(true).getId();
        Optional<SesRequest> found = uploadTokenService.resolve(token, sessionId);
        if (!found.isPresent()) {
            return refuseJson();
        }
        Long requestId = found.get().getRequestId();

        try {
            // assertUploadTarget is the ownership guard: the worker must belong
            // to THIS request and the document must be one the type asks for.
            // Its result is also the only way to obtain an UploadTarget, which
            // saveDraftDoc requires — so the check cannot be skipped by a
            // future caller.
            RequestService.UploadTarget target =
                    requestService.assertUploadTarget(requestId, workerId, docCode);
            SesExtUploadDoc saved = extUploadStagingService.saveDraftDoc(requestId, target, docCode, file);

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("ok", true);
            body.put("extDocId", saved.getExtDocId());
            body.put("workerId", saved.getWorkerId());
            body.put("docCode", saved.getDocCode());
            body.put("fileName", saved.getFileName());
            body.put("fileSize", saved.getFileSize());
            body.put("fileId", saved.getFileId());
            return ResponseEntity.ok(body);
        } catch (AccessDeniedException ex) {
            // An IDOR attempt, not a user mistake. Same wording as a genuine
            // rejection so a prober learns nothing from the difference.
            log.warn("Upload target rejected for request {}: {}", requestId, ex.getMessage());
            return errorJson("Dokumen tidak dapat disimpan.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            log.warn("Draft upload rejected for request {}: {}", requestId, ex.getMessage());
            return errorJson(ex.getMessage());
        }
    }

    /**
     * Removes one file the contractor already uploaded in this session.
     *
     * <p>Only staged rows can be removed, which is why the page sends an
     * {@code extDocId} rather than a {@code fileId}. A renewal's
     * carried-forward document has no staging row and gets no Delete button:
     * it shares its {@code app_file} row with the previous, already-approved
     * request, and removing it here would reach into that request's
     * documents.</p>
     */
    @PostMapping("/file/delete")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> deleteDraftFile(
            @RequestParam(value = "token", required = false) String token,
            @RequestParam("extDocId") Long extDocId,
            HttpServletRequest request) {

        String sessionId = request.getSession(true).getId();
        Optional<SesRequest> found = uploadTokenService.resolve(token, sessionId);
        if (!found.isPresent()) {
            return refuseJson();
        }
        try {
            extUploadStagingService.deleteDraftDoc(found.get().getRequestId(), extDocId);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("ok", true);
            return ResponseEntity.ok(body);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return errorJson(ex.getMessage());
        }
    }

    /**
     * Streams an already uploaded document for preview on the upload form.
     * Protected by the same upload token as the form itself, and constrained
     * to files belonging to this specific request.
     *
     * <p>Two sources are admitted, because the page shows both: a row in this
     * session's staging table, or a document already on the request (a
     * renewal's carried-forward file). Staging is checked first and cheaply;
     * the fall-back walks {@link RequestService#uploadRows}, which is the same
     * list the page itself was rendered from — so anything visible is
     * previewable and nothing else is.</p>
     */
    @GetMapping("/file")
    public void previewFile(@RequestParam(value = "token", required = false) String token,
                            @RequestParam("fileId") String fileId,
                            HttpServletRequest request,
                            HttpServletResponse response) throws IOException {
        String sessionId = request.getSession(true).getId();
        Optional<SesRequest> found = uploadTokenService.resolve(token, sessionId);
        if (!found.isPresent()) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        SesRequest req = found.get();
        boolean belongsToRequest =
                extUploadStagingService.findActiveByFileId(req.getRequestId(), fileId).isPresent()
                        || requestService.uploadRows(req.getRequestId()).stream()
                                .flatMap(w -> w.getDocs().stream())
                                .anyMatch(d -> fileId.equals(d.getFileId()));
        if (!belongsToRequest) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        SesAppFile file = appFileService.get(fileId);
        String type = file.getFileType();
        boolean previewable = type != null && (type.startsWith("image/") || type.equals("application/pdf"));
        String disposition = previewable ? "inline" : "attachment";
        response.setContentType(type != null ? type : "application/octet-stream");
        response.setHeader("Content-Disposition", disposition + "; filename=\"" + file.getFileName() + "\"");
        response.setContentLengthLong(file.getFileSize());
        response.getOutputStream().write(file.getFileData());
        response.getOutputStream().flush();
    }

    /**
     * Accepts the documents, then starts the approval chain.
     *
     * <p>The token is re-resolved here rather than trusted from a hidden
     * field carried over from the GET: the request may have been cancelled,
     * swept away by {@code RequestExpiryJob}, or already completed from
     * another tab in the minutes the visitor spent filling the form.</p>
     *
     * <p>Files are persisted BEFORE completeness is judged, and a failure of
     * that judgement re-renders the form rather than discarding the upload.
     * Someone who got five of six certificates should keep the five.</p>
     */
    @PostMapping
    public String submit(@RequestParam(value = "token", required = false) String token,
                         HttpServletRequest request,
                         Model model) {
        String sessionId = request.getSession(true).getId();
        Optional<SesRequest> found = uploadTokenService.resolve(token, sessionId);
        if (!found.isPresent()) {
            return refuse(model);
        }
        SesRequest req = found.get();
        Long requestId = req.getRequestId();

        // Audit label, not a username. ses_request_worker_doc has no audit
        // columns, but app_file does, and "who uploaded this" must not read as
        // a real account that never touched it.
        String actor = "UPLOAD:" + req.getRequestNo();

        // All documents are uploaded via AJAX save-draft to staging (SES_EXT_UPLOAD_DOC).
        // The final submit is purely a confirmation that finalizes the request,
        // eliminating timeout risks from re-streaming large multipart payloads.
        try {
            requestService.finalizeUpload(requestId, actor);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            // Re-render the form if a mandatory document is still missing.
            populate(model, requestService.findById(requestId), token);
            model.addAttribute("errorMsg", ex.getMessage());
            return "request/upload-docs";
        }

        model.addAttribute("requestNo", req.getRequestNo());
        return "request/upload-success";
    }

    /**
     * Walks the multipart parts and stores every non-empty one.
     *
     * <p>Field names carry the routing: {@code doc-<workerId>-<docCode>}. A
     * doc code may itself contain a hyphen, so the split is on the FIRST two
     * separators only — {@code indexOf}, not {@code String.split}, which would
     * mangle {@code SURAT-SEHAT} into three pieces.</p>
     */
    private void storeSubmittedFiles(HttpServletRequest request, Long requestId, String actor) {
        if (!(request instanceof MultipartHttpServletRequest)) {
            return;
        }
        Map<String, MultipartFile> files = ((MultipartHttpServletRequest) request).getFileMap();
        for (Map.Entry<String, MultipartFile> e : files.entrySet()) {
            String name = e.getKey();
            if (name == null || !name.startsWith(FIELD_PREFIX)) {
                continue;
            }
            MultipartFile file = e.getValue();
            if (file == null || file.isEmpty()) {
                continue;
            }
            String rest = name.substring(FIELD_PREFIX.length());
            int dash = rest.indexOf('-');
            if (dash <= 0 || dash == rest.length() - 1) {
                continue;
            }
            Long workerId;
            try {
                workerId = Long.valueOf(rest.substring(0, dash));
            } catch (NumberFormatException ex) {
                continue;
            }
            String docCode = rest.substring(dash + 1);
            requestService.storeUploadedDoc(requestId, workerId, docCode, file, actor);
        }
    }

    /** The model every render of the form needs. */
    private void populate(Model model, SesRequest request, String token) {
        SesRequestType type = requestTypeRepository.findById(request.getTypeCode()).orElse(null);
        List<RequestService.WorkerRowDto> workers = requestService.uploadRows(request.getRequestId());

        RequestService.MandatoryProgress progress = RequestService.mandatoryProgress(workers);

        model.addAttribute("token", token);
        model.addAttribute("request", request);
        model.addAttribute("typeName", type != null ? type.getTypeName() : request.getTypeCode());
        model.addAttribute("requestorName",
                userDisplayNameService.displayName(request.getRequestorUserId()));
        model.addAttribute("workers", workers);
        model.addAttribute("validDays", UploadTokenService.VALID_DAYS);
        model.addAttribute("mandatoryTotal", progress.getTotal());
        model.addAttribute("mandatoryDone", progress.getDone());
        model.addAttribute("mandatoryPercent", progress.getPercent());
        // Advertised in the page's help text. Read from configuration rather
        // than hard-coded: the template used to claim 10 MB while
        // AppFileService enforced 2 MB, which turns a rejected upload into a
        // support call.
        model.addAttribute("maxUploadMb", maxDocumentSize.toMegabytes());
    }

    /** A token that no longer works, in the shape the AJAX callers expect. */
    private ResponseEntity<Map<String, Object>> refuseJson() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", false);
        // Same single message as the HTML refusal page, for the same reason:
        // distinguishing "no such token" from "expired" confirms to a stranger
        // that a token existed.
        body.put("message", "Tautan ini sudah tidak berlaku. Muat ulang halaman.");
        body.put("expired", true);
        return ResponseEntity.status(HttpServletResponse.SC_FORBIDDEN).body(body);
    }

    private ResponseEntity<Map<String, Object>> errorJson(String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", false);
        body.put("message", message == null ? "Dokumen tidak dapat disimpan." : message);
        return ResponseEntity.badRequest().body(body);
    }

    /**
     * One refusal for every reason a token can fail.
     *
     * <p>No such token, already used, expired, request moved on — the page
     * says the same thing for all four. Telling a stranger which one happened
     * confirms that a token existed, which is the only thing a guesser would
     * actually learn.</p>
     */
    private String refuse(Model model) {
        model.addAttribute("validDays", UploadTokenService.VALID_DAYS);
        return "request/upload-invalid";
    }
}
