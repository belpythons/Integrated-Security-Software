package com.badaklng.request.controller;

import com.badaklng.approval.entity.SesApprovalTask;
import com.badaklng.approval.service.ApprovalConfigurationException;
import com.badaklng.approval.service.ApprovalService;
import com.badaklng.platform.security.PageAccessGuard;
import com.badaklng.platform.security.SecurityUtils;
import com.badaklng.platform.system.service.AppLogService;
import com.badaklng.request.entity.AppEmpLkpView;
import com.badaklng.request.entity.ContractV;
import com.badaklng.request.entity.RequestStatus;
import com.badaklng.request.entity.SesRequest;
import com.badaklng.request.entity.SesRequestType;
import com.badaklng.request.entity.SesZone;
import com.badaklng.request.model.RequestEditForm;
import com.badaklng.request.model.RequestListForm;
import com.badaklng.request.repository.AppEmpLkpViewRepository;
import com.badaklng.request.repository.ContractVRepository;
import com.badaklng.request.repository.SesRequestTypeRepository;
import com.badaklng.request.repository.SesWorkerLookupViewRepository;
import com.badaklng.request.repository.SesZoneRepository;
import com.badaklng.request.service.RequestDocumentService;
import com.badaklng.request.service.RequestQrCodeService;
import com.badaklng.request.service.RequestService;
import com.badaklng.approval.service.SecurityEvaluatorService;
import com.badaklng.request.service.RequestTypeService;
import com.badaklng.request.spi.RequestDetailContributor;
import com.badaklng.ses.user.UserScopeService;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import com.badaklng.ses.web.PageRequests;
import org.springframework.data.domain.Page;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.propertyeditors.StringTrimmerEditor;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindingResult;
import javax.validation.Valid;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.servlet.http.HttpServletRequest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * "New Request" screens under /request (read) + /request/form (virtual
 * CRUD/SUBMIT-grant URL — no controller mapping exists there, only the
 * app_page row, same convention as ApprovalChainController).
 */
@Controller
@RequestMapping("/request")
@RequiredArgsConstructor
public class RequestController {

    /**
     * SimpleDateFormat is not thread-safe, so this is a pattern rather than a
     * shared instance -- two concurrent requests sharing one formatter mutate
     * its internal Calendar mid-format, and the result is a silently wrong
     * date, not an exception.
     */
    private static final String ISO_DATE_PATTERN = "yyyy-MM-dd";

    private final RequestService requestService;
    private final ContractVRepository contractVRepository;
    private final AppEmpLkpViewRepository appEmpLkpViewRepository;
    private final SesRequestTypeRepository requestTypeRepository;
    private final SesZoneRepository zoneRepository;
    private final PageAccessGuard pageAccessGuard;
    private final AppLogService logService;
    private final SesWorkerLookupViewRepository workerLookupViewRepository;
    private final ApprovalService approvalService;
    private final ObjectProvider<RequestDetailContributor> detailContributors;
    private final RequestTypeService requestTypeService;
    private final SecurityEvaluatorService securityEvaluatorService;
    private final RequestDocumentService documentService;
    private final RequestQrCodeService qrCodeService;
    private final UserScopeService userScopeService;

    @GetMapping
    public String myList(Model model,
                         @RequestParam(required = false) String q,
                         @RequestParam(required = false) String status,
                         @RequestParam(required = false) String typeCode,
                         @RequestParam(required = false) Integer page,
                         @RequestParam(required = false) Integer size) {
        model.addAttribute("pageTitle", pageAccessGuard.require(RequestListForm.LIST_URL));
        String username = SecurityUtils.getCurrentUsername();
        Map<String, String> typeNames = typeNameMap();
        model.addAttribute("requestTypes", requestTypeRepository.findByActiveFlagOrderByOrderByAsc("Y"));
        // One page of rows, not the whole table. requestId DESC is the same
        // newest-first order findMine() used.
        Page<SesRequest> paged = requestService.findMine(username, PageRequests.term(q),
                PageRequests.term(status),
                PageRequests.term(typeCode),
                PageRequests.of(page, size, "requestId"));
        List<SesRequest> mine = paged.getContent();
        List<Long> instanceIds = mine.stream()
                .map(SesRequest::getApprovalInstanceId)
                .filter(id -> id != null)
                .collect(Collectors.toList());
        Map<Long, Boolean> editableByInstance = approvalService.isEditableBatch(instanceIds);
        Map<Long, String> stageByInstance = approvalService.resolveStageBatch(instanceIds);
        List<RequestListForm> rows = mine.stream()
                .map(r -> {
                    RequestListForm f = RequestListForm.from(r, typeNames.get(r.getTypeCode()));
                    // WAITING_FOR_UPLOAD counts as editable: no approver has
                    // been asked anything yet, so changing the header or the
                    // worker list costs nobody a decision. See RequestStatus.
                    boolean statusOk = RequestStatus.isRequestorEditable(r.getStatus());
                    boolean untouched = r.getApprovalInstanceId() == null
                            || Boolean.TRUE.equals(editableByInstance.get(r.getApprovalInstanceId()));
                    f.setEditable(statusOk && untouched);
                    if ("PENDING".equals(r.getStatus()) && r.getApprovalInstanceId() != null) {
                        String stage = stageByInstance.get(r.getApprovalInstanceId());
                        if (stage != null) {
                            f.setStatus(stage);
                        }
                    }
                    return f;
                })
                .collect(Collectors.toList());
        model.addAttribute("requests", rows);
        model.addAttribute("page", paged);
        return "request/list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("pageTitle", pageAccessGuard.require(RequestEditForm.FORM_URL));
        model.addAttribute("requestForm", new RequestEditForm());
        model.addAttribute("requestStatus", (String) null);
        populateReferenceData(model);
        return "request/form";
    }

    @GetMapping("/{id:\\d+}")
    public String editForm(@PathVariable Long id, Model model, RedirectAttributes ra) {
        model.addAttribute("pageTitle", pageAccessGuard.require(RequestEditForm.FORM_URL));
        SesRequest request = requestService.findById(id);

        String username = SecurityUtils.getCurrentUsername();
        if (request.getRequestorUserId() == null || !request.getRequestorUserId().equalsIgnoreCase(username)) {
            throw new AccessDeniedException("You do not have access to this request.");
        }

        boolean statusOk = RequestStatus.isRequestorEditable(request.getStatus());
        boolean untouched = request.getApprovalInstanceId() == null
                || approvalService.isEditable(request.getApprovalInstanceId());
        if (!statusOk || !untouched) {
            ra.addFlashAttribute("errorMsg", statusOk
                    ? "This request already has an approval decision recorded and can no longer be edited."
                    : "Only a pending or draft request can be edited.");
            return "redirect:" + RequestListForm.LIST_URL;
        }

        requestService.detailModel(id).forEach(model::addAttribute);
        model.addAttribute("requestStatus", request.getStatus());
        populateReferenceData(model);
        return "request/form";
    }

    /**
     * Creates a DRAFT copied from a past APPROVED request of this user's and
     * redirects to it — the renewal shortcut, so the requestor does not retype
     * worker and contract data every time.
     *
     * <p>Periods and documents are deliberately NOT carried over: a renewal is
     * for a new period, and it wants current documents. See
     * {@link RequestService#createRenewalDraft}.</p>
     */
    @GetMapping("/{id:\\d+}/renew")
    public String renewForm(@PathVariable Long id, RedirectAttributes ra, HttpServletRequest request) {
        pageAccessGuard.requireAction(RequestEditForm.FORM_URL, "SUBMIT");
        String username = SecurityUtils.getCurrentUsername();
        try {
            // Renewal now PERSISTS the copy and redirects to it, instead of
            // rendering pre-filled rows that live only in the DOM. With the
            // incremental saver the form no longer posts worker fields at all,
            // so unsaved pre-filled rows would simply evaporate on submit.
            // Making renewal "a draft that starts out filled in" means every
            // other path on this screen works on it unchanged.
            Long draftId = requestService.createRenewalDraft(id, username);
            logService.log("SAVE", "SesRequest",
                    "Created renewal draft " + draftId + " from request " + id, draftId, "SES_REQUEST", request);
            ra.addFlashAttribute("successMsg",
                    "Draf perpanjangan dibuat dari pengajuan sebelumnya. Lengkapi periode dan dokumen pekerja.");
            return "redirect:" + RequestEditForm.LIST_URL + "/" + draftId;
        } catch (IllegalArgumentException | IllegalStateException ex) {
            ra.addFlashAttribute("errorMsg", ex.getMessage());
            return "redirect:" + RequestListForm.LIST_URL;
        }
    }

    /** Read-only view: request data as submitted, plus its approval steps (if triggered). */
    @GetMapping("/{id:\\d+}/view")
    public String view(@PathVariable Long id, Model model) {
        model.addAttribute("pageTitle", pageAccessGuard.require(RequestListForm.LIST_URL));
        SesRequest request = requestService.findById(id);

        String username = SecurityUtils.getCurrentUsername();
        boolean isOwner = request.getRequestorUserId() != null && request.getRequestorUserId().equalsIgnoreCase(username);
        boolean isAdmin = pageAccessGuard.hasAction(RequestListForm.LIST_URL, "SUBMIT");
        boolean inScope = !isOwner && !isAdmin && isRequestInScope(request, username);
        if (!isOwner && !isAdmin && !inScope) {
            throw new AccessDeniedException("You do not have access to this request.");
        }

        requestService.detailModel(id).forEach(model::addAttribute);
        model.addAttribute("status", request.getStatus());
        model.addAttribute("requestNo", request.getRequestNo());

        List<SesApprovalTask> tasks = request.getApprovalInstanceId() != null
                ? approvalService.getTasks(request.getApprovalInstanceId())
                : Collections.emptyList();
        model.addAttribute("tasks", tasks);
        model.addAttribute("completedByNames", approvalService.resolveCompletedByDisplay(tasks));

        detailContributors.forEach(c -> c.contribute(id).forEach(model::addAttribute));
        model.addAttribute("approvalSummary", approvalService.summaryModel("SES_REQUEST", id));

        return "request/view";
    }

    @PostMapping("/save")
    public String save(@Valid @ModelAttribute("requestForm") RequestEditForm form,
                       BindingResult bindingResult,
                       Model model, RedirectAttributes ra, HttpServletRequest request) {
        pageAccessGuard.requireAction(RequestEditForm.FORM_URL, "SUBMIT");
        if (bindingResult.hasErrors()) {
            enrichDocLabels(form);
            model.addAttribute("pageTitle", pageAccessGuard.require(RequestEditForm.FORM_URL));
            model.addAttribute("errorMsg", "Mohon periksa kembali formulir: terdapat isian yang tidak valid atau belum lengkap.");
            model.addAttribute("requestForm", form);
            model.addAttribute("requestStatus", resolveRequestStatusForForm(form.getRequestId()));
            populateReferenceData(model);
            return "request/form";
        }

        String username = SecurityUtils.getCurrentUsername();
        boolean isCreate = form.getRequestId() == null;
        try {
            // submitDraft, not saveAndSubmit: the worker rows and their
            // documents are already on disk, saved one at a time through the
            // modal, and this POST carries only the header plus the proof of
            // payment. submitDraft rebuilds the full input from the persisted
            // rows and hands it to saveAndSubmit, so every validation rule and
            // the approval trigger stay in exactly one place.
            Long requestId = requestService.submitDraft(form.toServiceInput(), username);
            logService.log(isCreate ? "SAVE" : "UPDATE", "SesRequest",
                    "Saved request " + requestId, requestId, "SES_REQUEST", request);

            SesRequestType type = requestTypeRepository.findById(form.getTypeCode()).orElse(null);
            boolean isExtUpload = type != null && type.isNeedExtPageUpload();
            if (isExtUpload) {
                ra.addFlashAttribute("successMsg",
                        "Pengajuan tersimpan. Tautan unggah dokumen sudah dikirim ke "
                        + form.getEmail() + " — pengajuan menunggu dokumen sebelum masuk proses persetujuan.");
            } else {
                ra.addFlashAttribute("successMsg", "Pengajuan berhasil dikirim dan sedang dalam proses persetujuan.");
            }
            return "redirect:" + RequestListForm.LIST_URL;
        } catch (IllegalArgumentException | IllegalStateException | ApprovalConfigurationException ex) {
            // Re-render the SAME submitted form instead of redirecting: a redirect
            // is a fresh GET that would rebuild requestForm from scratch (blank on
            // create, or reloaded from the DB on edit) and throw away everything
            // the user typed. File inputs still can't be refilled by the browser
            // for security reasons (unavoidable), but every other field — and, on
            // an edit, already-uploaded docs via existingFileId — survives.
            enrichDocLabels(form);
            model.addAttribute("pageTitle", pageAccessGuard.require(RequestEditForm.FORM_URL));
            model.addAttribute("errorMsg", ex.getMessage());
            model.addAttribute("requestForm", form);
            model.addAttribute("requestStatus", resolveRequestStatusForForm(form.getRequestId()));
            populateReferenceData(model);
            return "request/form";
        }
    }

    /**
     * Saves the form as a {@code DRAFT} — header + whichever worker rows are
     * already complete, no approval trigger, no mandatory-document check
     * (see {@link RequestService#saveDraft}). Redirects back to the same
     * request's edit page (not the list) so the user can keep working.
     */
    @PostMapping("/save-draft")
    public String saveDraft(@ModelAttribute RequestEditForm form, Model model, RedirectAttributes ra, HttpServletRequest request) {
        pageAccessGuard.requireAction(RequestEditForm.FORM_URL, "SUBMIT");
        String username = SecurityUtils.getCurrentUsername();
        boolean isCreate = form.getRequestId() == null;
        try {
            Long requestId = requestService.saveDraft(form.toServiceInput(), username);
            logService.log(isCreate ? "SAVE" : "UPDATE", "SesRequest",
                    "Saved draft request " + requestId, requestId, "SES_REQUEST", request);
            ra.addFlashAttribute("successMsg", "Draft saved.");
            return "redirect:/request/" + requestId;
        } catch (IllegalArgumentException | IllegalStateException ex) {
            enrichDocLabels(form);
            model.addAttribute("pageTitle", pageAccessGuard.require(RequestEditForm.FORM_URL));
            model.addAttribute("errorMsg", ex.getMessage());
            model.addAttribute("requestForm", form);
            model.addAttribute("requestStatus", resolveRequestStatusForForm(form.getRequestId()));
            populateReferenceData(model);
            return "request/form";
        }
    }

    @PostMapping("/{id}/cancel")
    public String cancel(@PathVariable Long id, RedirectAttributes ra, HttpServletRequest request) {
        pageAccessGuard.requireAction(RequestEditForm.FORM_URL, "SUBMIT");
        String username = SecurityUtils.getCurrentUsername();
        try {
            requestService.cancel(id, username);
            logService.log("UPDATE", "SesRequest", "Cancelled request " + id, id, "SES_REQUEST", request);
            ra.addFlashAttribute("successMsg", "Request cancelled.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            ra.addFlashAttribute("errorMsg", ex.getMessage());
        }
        return "redirect:" + RequestListForm.LIST_URL;
    }

    /**
     * Re-issues the document-upload link for a request still waiting for
     * documents, and emails it again.
     *
     * <p>Without this a lapsed seven-day link would strand the submission:
     * the token is dead, the page refuses it, and nothing short of a database
     * edit brings it back. The requestor owns the problem, so the requestor
     * gets the button.</p>
     *
     * <p>Re-issuing kills the previous token, including any forwarded copy of
     * the previous email — which is the point as much as the side effect.
     * Ownership and status are both checked in the service.</p>
     */
    @PostMapping("/{id}/resend-upload-link")
    public String resendUploadLink(@PathVariable Long id, RedirectAttributes ra,
                                   HttpServletRequest request) {
        pageAccessGuard.requireAction(RequestEditForm.FORM_URL, "SUBMIT");
        String username = SecurityUtils.getCurrentUsername();
        try {
            requestService.resendUploadLink(id, username);
            logService.log("UPDATE", "SesRequest", "Resent upload link for request " + id,
                    id, "SES_REQUEST", request);
            ra.addFlashAttribute("successMsg",
                    "Tautan unggah dokumen sudah dikirim ulang. Tautan sebelumnya tidak berlaku lagi.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            ra.addFlashAttribute("errorMsg", ex.getMessage());
        }
        return "redirect:/request/" + id + "/view";
    }

    /**
     * Select2 AJAX search source for the Contract Number field, backed by
     * CONTRACT_V (see ses/sql/24_contract_v_dev_seed.sql) — a real
     * contract master granted read-only from another database, same
     * convention as SES_WORKER_LOOKUP_VIEW's EMP_NIK_VIEW source. JSON
     * shape per row: {@code {contractNo, contractType, contractTitle,
     * zoneCode, instance, address, startDate, finishDate}} — startDate/
     * finishDate are {@code yyyy-MM-dd} strings or null. CONTRACT_V has no
     * contractType/zoneCode/address, so those are always null (left for
     * manual entry on autofill). Capped at 20 results.
     */
    @GetMapping("/contracts")
    @ResponseBody
    public List<ContractDto> contracts(@RequestParam(required = false) String q) {
        String term = q == null ? "" : q.trim();
        return contractVRepository.searchByTerm(term, PageRequest.of(0, 20)).stream()
                .map(this::toDto).collect(Collectors.toList());
    }

    /**
     * AJAX search source for active employees backed by APP_EMP_LKP_VIEW.
     * Capped at 20 results.
     */
    @GetMapping("/employees/search")
    @ResponseBody
    public List<EmployeeSearchDto> employeeSearch(@RequestParam(required = false) String q) {
        String term = q == null ? "" : q.trim();
        return appEmpLkpViewRepository.searchByTerm(term, PageRequest.of(0, 20)).stream()
                .map(this::toEmployeeDto).collect(Collectors.toList());
    }

    /**
     * JSON document requirements for a type, for the type-change handler.
     * Shape per row: {@code {docCode, docName, mandatory}}.
     */
    // ---------------------------------------------------------------
    // Printed document
    // ---------------------------------------------------------------

    /**
     * The permit sheet as a real PDF.
     *
     * <p>Inline by default so the preview modal can show it in an
     * {@code <iframe>} with the browser's own PDF viewer — that is where the
     * zoom, page-fit and scroll controls come from, rather than being
     * reimplemented. {@code ?download=1} flips the disposition to
     * {@code attachment}.</p>
     */
    @GetMapping(value = "/{id}/document.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> documentPdf(@PathVariable Long id,
                                              @RequestParam(value = "download", required = false) String download) {
        boolean canAccess = pageAccessGuard.hasAction(RequestEditForm.FORM_URL, "READ")
                || pageAccessGuard.hasAction(RequestListForm.LIST_URL, "READ")
                || pageAccessGuard.hasAction("/history", "READ")
                || pageAccessGuard.hasAction("/approval", "READ");
        if (!canAccess) {
            pageAccessGuard.require(RequestListForm.LIST_URL);
        }
        SesRequest request = requestService.findById(id);
        assertCanReadRequest(request);

        byte[] pdf = documentService.renderPdf(id, SecurityUtils.getCurrentUsername());
        String disposition = (download != null && !download.isEmpty() ? "attachment" : "inline")
                + "; filename=\"" + documentService.fileNameFor(request) + "\"";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    /**
     * The QR image on its own.
     *
     * <p>Not used by the PDF — that inlines the bytes as a data URI, because
     * the renderer has no servlet context to fetch from. This endpoint exists
     * for the screen, where showing the code without generating a whole
     * document is cheaper and sometimes all that is wanted.</p>
     */
    @GetMapping(value = "/{id}/qr.png", produces = MediaType.IMAGE_PNG_VALUE)
    @ResponseBody
    public byte[] qrPng(@PathVariable Long id) {
        pageAccessGuard.require(RequestEditForm.FORM_URL);
        SesRequest request = requestService.findById(id);
        assertCanReadRequest(request);
        return qrCodeService.pngFor(request.getRequestNo());
    }

    /**
     * Redirects to the external public verification URL for this request.
     */
    @GetMapping("/{id:\\d+}/verify")
    public String redirectToVerify(@PathVariable Long id) {
        SesRequest request = requestService.findById(id);
        return "redirect:" + qrCodeService.verifyUrl(request.getRequestNo());
    }

    /**
     * May the current user read this request at all?
     *
     * <p>The requestor, or anyone who can see every request (the officer /
     * admin view, flagged the same way {@code /history} and {@code /dashboard}
     * flag it — a SUBMIT grant on a read-only page). Without the second arm,
     * the Security officer who has to check a permit could not open the
     * document they are being handed.</p>
     */
    private void assertCanReadRequest(SesRequest request) {
        String username = SecurityUtils.getCurrentUsername();
        boolean owner = request.getRequestorUserId() != null
                && request.getRequestorUserId().equalsIgnoreCase(username);
        boolean adminOrOfficer = pageAccessGuard.hasAction(RequestListForm.LIST_URL, "SUBMIT")
                || pageAccessGuard.hasAction("/history", "SUBMIT")
                || pageAccessGuard.hasAction("/approval", "READ");
        boolean inScope = !owner && !adminOrOfficer && isRequestInScope(request, username);
        if (!owner && !adminOrOfficer && !inScope) {
            throw new AccessDeniedException("You do not have access to this request.");
        }
    }

    private boolean isRequestInScope(SesRequest request, String username) {
        if (request == null || request.getRequestorUserId() == null || username == null) {
            return false;
        }
        return userScopeService.resolveScope(username).getUsernames().contains(request.getRequestorUserId().trim());
    }

    // ---------------------------------------------------------------
    // Incremental worker saving — the modal
    // ---------------------------------------------------------------
    //
    // Every one of these returns JSON and turns a service-layer exception into
    // a 400 carrying the message intact: each of those messages is written for
    // the requestor reading it in the modal, not for a log.
    //
    // Ownership is enforced in the service, not here, because these ids come
    // straight off the URL and the service is the only layer every caller must pass through.
    @InitBinder
    public void initBinder(WebDataBinder binder) {
        binder.registerCustomEditor(String.class, new StringTrimmerEditor(true));
    }

    /**
     * Ensures a draft exists so worker rows have something to hang off, and
     * returns its id. Called the first time the user opens the worker modal.
     */
    @PostMapping("/draft/ensure")
    @ResponseBody
    public ResponseEntity<?> ensureDraft(@Valid @ModelAttribute RequestEditForm form,
                                         BindingResult bindingResult,
                                         HttpServletRequest request) {
        pageAccessGuard.requireAction(RequestEditForm.FORM_URL, "SUBMIT");
        String username = SecurityUtils.getCurrentUsername();
        try {
            if (bindingResult.hasFieldErrors("typeCode") || form.getTypeCode() == null || form.getTypeCode().trim().isEmpty()) {
                return ResponseEntity.badRequest().body(new ErrorDto("Silakan pilih Jenis Pengajuan (Type of Request) terlebih dahulu."));
            }
            Long requestId = requestService.ensureDraft(form.toServiceInput(), username);
            logService.log("SAVE", "SesRequest", "Ensured draft " + requestId, requestId, "SES_REQUEST", request);
            return ResponseEntity.ok(java.util.Collections.singletonMap("requestId", requestId));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return ResponseEntity.badRequest().body(new ErrorDto(ex.getMessage()));
        } catch (Exception ex) {
            org.slf4j.LoggerFactory.getLogger(RequestController.class)
                    .error("Failed to ensure draft for user {}: {}", username, ex.getMessage(), ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorDto(ex.getMessage() != null ? ex.getMessage() : "Terjadi kesalahan internal server."));
        }
    }

    /**
     * Creates or updates ONE worker, with its documents, in the background.
     *
     * <p>Bound as a {@link RequestEditForm.WorkerRow} so the modal posts the
     * same field names the whole-form editor uses ({@code docs[0].docCode},
     * {@code docs[0].file}) and one mapping serves both.</p>
     */
    @PostMapping("/{id}/worker/save")
    @ResponseBody
    public ResponseEntity<?> saveWorker(@PathVariable Long id,
                                         @Valid @ModelAttribute RequestEditForm.WorkerRow worker,
                                         BindingResult bindingResult,
                                         HttpServletRequest request) {
        pageAccessGuard.requireAction(RequestEditForm.FORM_URL, "SUBMIT");
        String username = SecurityUtils.getCurrentUsername();
        if (bindingResult.hasErrors()) {
            String errorMsg = bindingResult.getFieldError() != null ? bindingResult.getFieldError().getDefaultMessage() : "Data pekerja tidak valid.";
            return ResponseEntity.badRequest().body(new ErrorDto(errorMsg));
        }
        try {
            requestService.saveWorker(id, worker.toWorkerInput(), username);
            logService.log("SAVE", "SesRequestWorker",
                    "Saved worker on request " + id, id, "SES_REQUEST", request);
            return ResponseEntity.ok(requestService.workerRows(id, username));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return ResponseEntity.badRequest().body(new ErrorDto(ex.getMessage()));
        } catch (Exception ex) {
            org.slf4j.LoggerFactory.getLogger(RequestController.class)
                    .error("Failed to save worker on request {}: {}", id, ex.getMessage(), ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorDto(ex.getMessage() != null ? ex.getMessage() : "Terjadi kesalahan saat menyimpan pekerja."));
        }
    }

    @PostMapping("/{id}/worker/{workerId}/delete")
    @ResponseBody
    public ResponseEntity<?> deleteWorker(@PathVariable Long id, @PathVariable Long workerId,
                                          HttpServletRequest request) {
        pageAccessGuard.requireAction(RequestEditForm.FORM_URL, "SUBMIT");
        String username = SecurityUtils.getCurrentUsername();
        try {
            requestService.deleteWorker(id, workerId, username);
            logService.log("DELETE", "SesRequestWorker",
                    "Deleted worker " + workerId + " of request " + id, id, "SES_REQUEST", request);
            return ResponseEntity.ok(requestService.workerRows(id, username));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return ResponseEntity.badRequest().body(new ErrorDto(ex.getMessage()));
        }
    }

    /** The request's saved workers, for re-rendering the table. */
    @GetMapping("/{id}/workers")
    @ResponseBody
    public ResponseEntity<?> workers(@PathVariable Long id) {
        pageAccessGuard.require(RequestEditForm.FORM_URL);
        try {
            return ResponseEntity.ok(requestService.workerRows(id, SecurityUtils.getCurrentUsername()));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return ResponseEntity.badRequest().body(new ErrorDto(ex.getMessage()));
        }
    }

    @Getter
    @AllArgsConstructor
    public static class ErrorDto {
        private final String message;
    }

    @GetMapping("/requirements/{typeCode}")
    @ResponseBody
    public List<RequirementDto> requirements(@PathVariable String typeCode) {
        requestTypeService.assertSelectable(typeCode, SecurityUtils.getCurrentUsername());
        return requestService.requirementRowsFor(typeCode).stream()
                .map(r -> new RequirementDto(r.getDocCode(), r.getDocName(), r.isMandatory()))
                .collect(Collectors.toList());
    }

    /**
     * Candidate Staf Security for the "Pilih Staf Security (Evaluator)"
     * multi-select on the new-badge form.
     *
     * <p>Served as JSON rather than inlined into the page because the list is
     * org-chart data that changes without a redeploy, and the same endpoint
     * backs the select2 re-render after a validation bounce.</p>
     *
     * <p>Filtered to exclude the caller: the gate that matters is on the way
     * back in — {@code SecurityEvaluatorService.resolveSelected} rejects any
     * id that is not on this list, so the picker being open costs nothing
     * beyond that self-exclusion.</p>
     */
    @GetMapping("/security-evaluators")
    @ResponseBody
    public List<EvaluatorDto> securityEvaluators() {
        pageAccessGuard.require(RequestEditForm.FORM_URL);
        return securityEvaluatorService.candidates(SecurityUtils.getCurrentUsername()).stream()
                .map(o -> new EvaluatorDto(o.getEmplId(), o.getEmplName(), o.getPosId(), o.getPosTitle(),
                        o.getDeptDesc(), o.getSectionDesc(), o.getDisplayText()))
                .collect(Collectors.toList());
    }

    /**
     * Whether submitting form "SES_REQUEST" with this type as match key
     * would currently resolve to a usable approval chain (override or
     * default) — used to disable the Submit button client-side instead of
     * letting the user hit the {@code ApprovalConfigurationException} that
     * {@link RequestService#save} would otherwise throw at commit time.
     */
    @GetMapping("/approval-chain-status/{typeCode}")
    @ResponseBody
    public ApprovalService.ChainAvailability approvalChainStatus(@PathVariable String typeCode) {
        String username = SecurityUtils.getCurrentUsername();
        requestTypeService.assertSelectable(typeCode, username);
        // Must pass the SAME skip decisions trigger() will use, or the form
        // warns "chain not configured" for a requestor with no resolvable
        // manager on a type that explicitly opted out of that approval — a
        // submission that would in fact succeed.
        //
        // The evaluator list is deliberately empty: this pre-flight runs on
        // type change, before the requestor has necessarily picked anyone, and
        // checkChainAvailability does not read it. "You have not chosen an
        // evaluator yet" is a form-validation message, not a chain-configuration
        // problem, and conflating the two would show the requestor a scary
        // "chain unconfigured" warning for something they are about to fix.
        return approvalService.checkChainAvailability("SES_REQUEST", typeCode, username,
                new ApprovalService.TriggerOptions(
                        requestTypeService.requiresManagerApproval(typeCode),
                        requestTypeService.requiresProcurementApproval(typeCode),
                        null));
    }

    /**
     * Search source for the Badge/Name/NIK worker-identity combobox (the
     * {@code worker-lookup-input} class in request/form.html — picking a result
     * autofills the WHOLE row). Matches the term against badge, name, NIK or
     * jabatan in SES_WORKER_LOOKUP_VIEW (ses/sql/23_worker_lookup_view.sql) — a
     * single pre-deduplicated view merging EMP_NIK_VIEW, APP_POSITION_VIEW,
     * active APP_PERSON Mitra rows, LDAP APP_USER rows and past
     * SES_REQUEST_WORKER rows into one row per real
     * 6-digit badge, so unlike the old 4-query stitch this never surfaces the
     * same person twice. Capped at 50 results.
     * <p>
     * Department/Section/Jabatan are deliberately NOT searched here — matching
     * a whole person against a typed department/section/position string makes
     * no sense (see {@link #departmentSearch}/{@link #sectionSearch}/
     * {@link #jabatanSearch}, each scoped to its own single column, autofilling
     * only itself).
     */
    @GetMapping("/workers/search")
    @ResponseBody
    public List<WorkerSearchDto> workerSearch(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String field) {
        String term = q == null ? null : q.trim();
        if (term == null || term.isEmpty()) {
            return new ArrayList<>();
        }

        List<com.badaklng.request.entity.SesWorkerLookupView> results;
        if ("badge".equalsIgnoreCase(field)) {
            results = workerLookupViewRepository.searchByBadgePrefix(term, PageRequest.of(0, 50));
        } else if ("nik".equalsIgnoreCase(field)) {
            results = workerLookupViewRepository.searchByNikPrefix(term, PageRequest.of(0, 50));
        } else {
            results = workerLookupViewRepository.searchByTerm(term, PageRequest.of(0, 50));
        }

        return results.stream()
                .map(w -> new WorkerSearchDto(w.getWorkerName(), w.getBadge(), w.getJabatan(),
                        w.getNik(), w.getDepartment(), w.getSection(), w.getGender()))
                .collect(Collectors.toList());
    }

    /** Department combobox — single-column search, autofills only Department. Capped at 20. */
    @Cacheable(value = "workerDepartments", key = "#q != null ? #q : ''")
    @GetMapping("/departments/search")
    @ResponseBody
    public List<String> departmentSearch(@RequestParam(required = false) String q) {
        String term = q == null ? null : q.trim();
        if (term == null || term.isEmpty()) {
            return new ArrayList<>();
        }
        return workerLookupViewRepository.searchDepartments(term, PageRequest.of(0, 20));
    }

    /** Section combobox — single-column search, autofills only Section. Capped at 20. */
    @Cacheable(value = "workerSections", key = "#q != null ? #q : ''")
    @GetMapping("/sections/search")
    @ResponseBody
    public List<String> sectionSearch(@RequestParam(required = false) String q) {
        String term = q == null ? null : q.trim();
        if (term == null || term.isEmpty()) {
            return new ArrayList<>();
        }
        return workerLookupViewRepository.searchSections(term, PageRequest.of(0, 20));
    }

    /** Jabatan combobox — single-column search, autofills only Jabatan. Capped at 20. */
    @Cacheable(value = "workerJabatans", key = "#q != null ? #q : ''")
    @GetMapping("/jabatans/search")
    @ResponseBody
    public List<String> jabatanSearch(@RequestParam(required = false) String q) {
        String term = q == null ? null : q.trim();
        if (term == null || term.isEmpty()) {
            return new ArrayList<>();
        }
        return workerLookupViewRepository.searchJabatans(term, PageRequest.of(0, 20));
    }

    /**
     * The posted form only carries {@code docCode}/{@code existingFileId}/{@code file}
     * per worker doc row (see form.html's markup contract) — {@code docName}/
     * {@code mandatory} are display-only and never posted back, so a re-rendered
     * form after a validation failure needs them refilled here the same way
     * {@link RequestEditForm#from} does for the initial edit-mode render.
     */
    private void enrichDocLabels(RequestEditForm form) {
        if (form.getTypeCode() == null || form.getWorkers() == null) {
            return;
        }
        Map<String, RequestService.RequirementRow> byCode = requestService.requirementRowsFor(form.getTypeCode())
                .stream().collect(Collectors.toMap(RequestService.RequirementRow::getDocCode, r -> r));
        for (RequestEditForm.WorkerRow w : form.getWorkers()) {
            if (w == null || w.getDocs() == null) {
                continue;
            }
            for (RequestEditForm.WorkerDocRow d : w.getDocs()) {
                RequestService.RequirementRow r = d == null ? null : byCode.get(d.getDocCode());
                if (r != null) {
                    d.setDocName(r.getDocName());
                    d.setMandatory(r.isMandatory());
                }
            }
        }
    }

    /**
     * The form's Save-as-Draft button must only show for a request that is
     * either brand new or already {@code DRAFT} — re-fetches the persisted
     * status by id (form-bound state carries no status field) for the
     * error-re-render path of both {@link #save} and {@link #saveDraft}.
     * Null on create (no id yet).
     */
    private String resolveRequestStatusForForm(Long requestId) {
        return requestId == null ? null : requestService.findById(requestId).getStatus();
    }

    private void populateReferenceData(Model model) {
        // Filtered by the current account's app_user.auth_type. One call covers
        // BOTH consumers of this attribute: the <select> in request/form.html
        // and the inlined `requestTypesData` JS array the type-change handler
        // reads needsPaymentFlag/paymentAmount from.
        //
        // NOT filtered elsewhere, deliberately: RequestTypeController's admin
        // master (an LDAP admin must still edit a LOCAL-only type),
        // RequestApprovalBridge.matchKeys() (admin routing config), and
        // typeNameMap() below (names of ALREADY-submitted requests — filtering
        // would blank out a user's own history).
        List<SesRequestType> types = requestTypeService.findSelectableFor(SecurityUtils.getCurrentUsername());
        model.addAttribute("requestTypes", types);
        List<SesZone> zones = zoneRepository.findByActiveFlagOrderByOrderByAsc("Y");
        model.addAttribute("zones", zones);
        // Rendered as <option>s server-side, so a validation bounce shows the
        // user's existing picks straight away instead of an empty select2 that
        // fills in a moment later. Same list the /security-evaluators endpoint
        // serves; one source, two delivery mechanisms.
        model.addAttribute("securityEvaluators",
                securityEvaluatorService.candidates(SecurityUtils.getCurrentUsername()));
    }

    private Map<String, String> typeNameMap() {
        Map<String, String> map = new HashMap<>();
        for (SesRequestType t : requestTypeRepository.findAll()) {
            map.put(t.getTypeCode(), t.getTypeName());
        }
        return map;
    }

    private Map<String, String> zoneNameMap() {
        Map<String, String> map = new HashMap<>();
        for (SesZone z : zoneRepository.findAll()) {
            map.put(z.getZoneCode(), z.getZoneName());
        }
        return map;
    }

    private ContractDto toDto(ContractV c) {
        return new ContractDto(
                c.getNoKontrak(),
                null,
                c.getJudulPekerjaan(),
                null,
                c.getCompany(),
                null,
                c.getTanggalMulai() != null ? new SimpleDateFormat(ISO_DATE_PATTERN).format(c.getTanggalMulai()) : null,
                c.getTanggalSelesai() != null ? new SimpleDateFormat(ISO_DATE_PATTERN).format(c.getTanggalSelesai()) : null);
    }

    @Getter
    @AllArgsConstructor
    public static class ContractDto {
        private final String contractNo;
        private final String contractType;
        private final String contractTitle;
        private final String zoneCode;
        private final String instance;
        private final String address;
        private final String startDate;
        private final String finishDate;
    }

    @Getter
    @AllArgsConstructor
    public static class RequirementDto {
        private final String docCode;
        private final String docName;
        private final boolean mandatory;
    }

    @Getter
    @AllArgsConstructor
    public static class EvaluatorDto {
        private final String emplId;
        private final String emplName;
        private final String posId;
        private final String posTitle;
        private final String deptDesc;
        private final String sectionDesc;
        private final String displayText;
    }

    private EmployeeSearchDto toEmployeeDto(AppEmpLkpView e) {
        return new EmployeeSearchDto(
                e.getEmplid(),
                e.getNameDisplay(),
                e.getDeptId(),
                e.getDeptDesc(),
                e.getSectionId(),
                e.getSectionDesc(),
                e.getPositionNbr(),
                e.getPositionDesc(),
                e.getEmail());
    }

    @Getter
    @AllArgsConstructor
    public static class EmployeeSearchDto {
        private final String emplid;
        private final String nameDisplay;
        private final String deptId;
        private final String deptDesc;
        private final String sectionId;
        private final String sectionDesc;
        private final String positionNbr;
        private final String positionDesc;
        private final String email;
    }

    @Getter
    @AllArgsConstructor
    public static class WorkerSearchDto {
        private final String name;
        private final String badge;
        private final String jabatan;
        private final String nik;
        private final String department;
        private final String section;
        private final String gender;
    }
}
