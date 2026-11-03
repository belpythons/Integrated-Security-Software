package com.badaklng.sticker.controller;

import com.badaklng.approval.entity.SesApprovalTask;
import com.badaklng.approval.service.ApprovalConfigurationException;
import com.badaklng.approval.service.ApprovalService;
import com.badaklng.platform.security.PageAccessGuard;
import com.badaklng.platform.security.SecurityUtils;
import com.badaklng.platform.system.service.AppLogService;
import com.badaklng.ses.user.UserScopeService;
import com.badaklng.ses.user.UserDisplayNameService;
import com.badaklng.sticker.entity.SesStickerRequest;
import com.badaklng.sticker.entity.SesStickerType;
import com.badaklng.sticker.model.StickerEditForm;
import com.badaklng.sticker.model.StickerListRow;
import com.badaklng.sticker.repository.SesStickerTypeRepository;
import com.badaklng.request.repository.SesWorkerLookupViewRepository;
import com.badaklng.sticker.service.StickerService;
import com.badaklng.ses.validation.DraftGroup;
import com.badaklng.ses.validation.SubmitGroup;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import com.badaklng.ses.web.PageRequests;
import org.springframework.data.domain.Page;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * "Stiker Kendaraan" screens under /sticker (read) + /sticker/form (virtual
 * CRUD/SUBMIT-grant URL — no controller mapping exists there, only the
 * app_page row, same convention as RequestController).
 */
@Controller
@RequestMapping("/sticker")
@RequiredArgsConstructor
public class StickerController {

    private final StickerService stickerService;
    private final com.badaklng.sticker.service.StickerTypeService stickerTypeService;
    private final SesStickerTypeRepository stickerTypeRepository;
    private final PageAccessGuard pageAccessGuard;
    private final AppLogService logService;
    private final ApprovalService approvalService;
    private final UserScopeService userScopeService;
    private final SesWorkerLookupViewRepository workerLookupViewRepository;
    private final UserDisplayNameService userDisplayNameService;

    @GetMapping
    public String myList(Model model,
                         @RequestParam(required = false) String q,
                         @RequestParam(required = false) String status,
                         @RequestParam(required = false) Integer page,
                         @RequestParam(required = false) Integer size) {
        model.addAttribute("pageTitle", pageAccessGuard.require(StickerListRow.LIST_URL));
        String username = SecurityUtils.getCurrentUsername();
        Page<SesStickerRequest> paged = stickerService.findMine(username, PageRequests.term(q),
                PageRequests.term(status),
                PageRequests.of(page, size, "stickerRequestId"));
        model.addAttribute("requests", toListRows(paged.getContent()));
        model.addAttribute("page", paged);
        return "sticker/list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("pageTitle", pageAccessGuard.require(StickerEditForm.FORM_URL));
        StickerEditForm form = new StickerEditForm();
        String username = SecurityUtils.getCurrentUsername();
        String displayName = userDisplayNameService.displayName(username);
        form.setRequestorName(displayName != null ? displayName : username);
        form.setBadge(username);
        workerLookupViewRepository.searchByBadgePrefix(username, PageRequest.of(0, 1)).stream().findFirst().ifPresent(w -> {
            if (w.getDepartment() != null) form.setDepartment(w.getDepartment());
            if (w.getSection() != null) form.setSection(w.getSection());
            if (w.getWorkerName() != null && !w.getWorkerName().isEmpty()) form.setRequestorName(w.getWorkerName());
        });
        model.addAttribute("stickerForm", form);
        model.addAttribute("requestStatus", (String) null);
        populateReferenceData(model);
        return "sticker/form";
    }

    @GetMapping("/{id}")
    public String editForm(@PathVariable Long id, Model model, RedirectAttributes ra) {
        model.addAttribute("pageTitle", pageAccessGuard.require(StickerEditForm.FORM_URL));
        SesStickerRequest request = stickerService.findById(id);

        String username = SecurityUtils.getCurrentUsername();
        if (request.getRequestorUserId() == null || !request.getRequestorUserId().equalsIgnoreCase(username)) {
            throw new AccessDeniedException("Anda tidak memiliki akses ke pengajuan ini.");
        }

        boolean statusOk = "PENDING".equals(request.getStatus()) || "DRAFT".equals(request.getStatus());
        boolean untouched = request.getApprovalInstanceId() == null
                || approvalService.isEditable(request.getApprovalInstanceId());
        if (!statusOk || !untouched) {
            ra.addFlashAttribute("errorMsg", statusOk
                    ? "Pengajuan ini sudah memiliki keputusan persetujuan dan tidak dapat diubah lagi."
                    : "Hanya pengajuan berstatus pending atau draft yang dapat diubah.");
            return "redirect:" + StickerListRow.LIST_URL;
        }

        stickerService.detailModel(id).forEach(model::addAttribute);
        model.addAttribute("requestStatus", request.getStatus());
        populateReferenceData(model);
        return "sticker/form";
    }

    /**
     * Opens {@code sticker/form} pre-filled from a past APPROVED sticker
     * request of this user's, as a brand new (unsaved) submission — renewal
     * shortcut so the requestor doesn't retype requestor/vehicle data every
     * time. Mirrors {@link RequestController#renewForm} exactly: reuses
     * {@link StickerService#detailModel} (same factory {@link
     * StickerEditForm#from} used by {@link #editForm}), then strips every
     * old-row identity (stickerRequestId/stickerVehicleId/
     * existingLetterFileId/existingPaymentFileId) so POST /sticker/save
     * creates a new row instead of overwriting the source request.
     */
    @GetMapping("/{id}/renew")
    public String renewForm(@PathVariable Long id, Model model, RedirectAttributes ra) {
        model.addAttribute("pageTitle", pageAccessGuard.require(StickerEditForm.FORM_URL));
        SesStickerRequest source = stickerService.findById(id);

        String username = SecurityUtils.getCurrentUsername();
        if (source.getRequestorUserId() == null || !source.getRequestorUserId().equalsIgnoreCase(username)) {
            throw new AccessDeniedException("Anda tidak memiliki akses ke pengajuan ini.");
        }
        if (!"APPROVED".equals(source.getStatus())) {
            ra.addFlashAttribute("errorMsg", "Hanya pengajuan yang sudah disetujui yang dapat digunakan untuk perpanjangan.");
            return "redirect:" + StickerListRow.LIST_URL;
        }

        Map<String, Object> detail = stickerService.detailModel(id);
        StickerEditForm form = (StickerEditForm) detail.get("stickerForm");
        form.setStickerRequestId(null);
        for (StickerEditForm.VehicleRow row : form.getVehicles()) {
            row.setStickerVehicleId(null);
            row.setExistingLetterFileId(null);
        }
        form.setExistingPaymentFileId(null);

        detail.forEach(model::addAttribute);
        model.addAttribute("stickerForm", form);
        model.addAttribute("requestStatus", (String) null);
        populateReferenceData(model);
        return "sticker/form";
    }

    /** Read-only view: request data as submitted, plus its approval steps (if triggered). */
    @GetMapping("/{id}/view")
    public String view(@PathVariable Long id, Model model) {
        model.addAttribute("pageTitle", pageAccessGuard.require(StickerListRow.LIST_URL));
        SesStickerRequest request = stickerService.findById(id);

        String username = SecurityUtils.getCurrentUsername();
        boolean isOwner = request.getRequestorUserId() != null && request.getRequestorUserId().equalsIgnoreCase(username);
        boolean isAdmin = pageAccessGuard.hasAction(StickerListRow.LIST_URL, "SUBMIT");
        boolean inScope = !isOwner && !isAdmin && isStickerRequestInScope(request, username);
        if (!isOwner && !isAdmin && !inScope) {
            throw new AccessDeniedException("Anda tidak memiliki akses ke pengajuan ini.");
        }

        stickerService.detailModel(id).forEach(model::addAttribute);
        model.addAttribute("status", request.getStatus());
        model.addAttribute("requestNo", request.getRequestNo());

        List<SesApprovalTask> tasks = request.getApprovalInstanceId() != null
                ? approvalService.getTasks(request.getApprovalInstanceId())
                : Collections.emptyList();
        model.addAttribute("tasks", tasks);
        model.addAttribute("completedByNames", approvalService.resolveCompletedByDisplay(tasks));

        return "sticker/view";
    }

    @PostMapping("/save")
    public String save(@Validated(SubmitGroup.class) @ModelAttribute("stickerForm") StickerEditForm form,
                       BindingResult bindingResult,
                       Model model, RedirectAttributes ra, HttpServletRequest request) {
        pageAccessGuard.requireAction(StickerEditForm.FORM_URL, "SUBMIT");
        if (bindingResult.hasErrors()) {
            model.addAttribute("pageTitle", pageAccessGuard.require(StickerEditForm.FORM_URL));
            model.addAttribute("errorMsg", "Mohon periksa kembali formulir: terdapat isian yang tidak valid atau belum lengkap.");
            model.addAttribute("stickerForm", form);
            model.addAttribute("requestStatus", resolveRequestStatusForForm(form.getStickerRequestId()));
            populateReferenceData(model);
            return "sticker/form";
        }

        String username = SecurityUtils.getCurrentUsername();
        boolean isCreate = form.getStickerRequestId() == null;
        try {
            Long id = stickerService.saveAndSubmit(form.toServiceInput(), username);
            logService.log(isCreate ? "SAVE" : "UPDATE", "SesStickerRequest",
                    "Saved sticker request " + id, id, "SES_STICKER_REQUEST", request);
            ra.addFlashAttribute("successMsg", "Pengajuan berhasil dikirim.");
            return "redirect:" + StickerListRow.LIST_URL;
        } catch (IllegalArgumentException | IllegalStateException | ApprovalConfigurationException ex) {
            model.addAttribute("pageTitle", pageAccessGuard.require(StickerEditForm.FORM_URL));
            model.addAttribute("errorMsg", ex.getMessage());
            model.addAttribute("stickerForm", form);
            model.addAttribute("requestStatus", resolveRequestStatusForForm(form.getStickerRequestId()));
            populateReferenceData(model);
            return "sticker/form";
        }
    }

    @PostMapping("/save-draft")
    public String saveDraft(@Validated(DraftGroup.class) @ModelAttribute("stickerForm") StickerEditForm form,
                            BindingResult bindingResult,
                            Model model, RedirectAttributes ra, HttpServletRequest request) {
        pageAccessGuard.requireAction(StickerEditForm.FORM_URL, "SUBMIT");
        if (bindingResult.hasErrors()) {
            model.addAttribute("pageTitle", pageAccessGuard.require(StickerEditForm.FORM_URL));
            model.addAttribute("errorMsg", "Mohon periksa kembali formulir: terdapat isian yang tidak valid.");
            model.addAttribute("stickerForm", form);
            model.addAttribute("requestStatus", resolveRequestStatusForForm(form.getStickerRequestId()));
            populateReferenceData(model);
            return "sticker/form";
        }

        String username = SecurityUtils.getCurrentUsername();
        boolean isCreate = form.getStickerRequestId() == null;
        try {
            Long id = stickerService.saveDraft(form.toServiceInput(), username);
            logService.log(isCreate ? "SAVE" : "UPDATE", "SesStickerRequest",
                    "Saved draft sticker request " + id, id, "SES_STICKER_REQUEST", request);
            ra.addFlashAttribute("successMsg", "Draft berhasil disimpan.");
            return "redirect:/sticker/" + id;
        } catch (IllegalArgumentException | IllegalStateException ex) {
            model.addAttribute("pageTitle", pageAccessGuard.require(StickerEditForm.FORM_URL));
            model.addAttribute("errorMsg", ex.getMessage());
            model.addAttribute("stickerForm", form);
            model.addAttribute("requestStatus", resolveRequestStatusForForm(form.getStickerRequestId()));
            populateReferenceData(model);
            return "sticker/form";
        }
    }

    @PostMapping("/{id}/cancel")
    public String cancel(@PathVariable Long id, RedirectAttributes ra, HttpServletRequest request) {
        pageAccessGuard.requireAction(StickerEditForm.FORM_URL, "SUBMIT");
        String username = SecurityUtils.getCurrentUsername();
        try {
            stickerService.cancel(id, username);
            logService.log("UPDATE", "SesStickerRequest", "Cancelled sticker request " + id, id, "SES_STICKER_REQUEST", request);
            ra.addFlashAttribute("successMsg", "Pengajuan dibatalkan.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            ra.addFlashAttribute("errorMsg", ex.getMessage());
        }
        return "redirect:" + StickerListRow.LIST_URL;
    }

    /**
     * Whether the ONE default chain for this form is currently resolvable —
     * used to disable the Submit button client-side instead of letting the
     * user fill out the whole form and only find out at commit time. Unlike
     * RequestController's per-typeCode version, there is no routing key
     * here (this form has no matchKey dropdown).
     */
    @GetMapping("/approval-chain-status")
    @ResponseBody
    public ApprovalService.ChainAvailability approvalChainStatus() {
        String username = SecurityUtils.getCurrentUsername();
        return approvalService.checkChainAvailability(StickerService.APPROVAL_FORM_CODE, null, username);
    }

    /**
     * Autocomplete endpoint for worker data (from SES_WORKER_LOOKUP_VIEW).
     * Used by the vehicle input modal to suggest driver name, badge, and NIK.
     */
    @GetMapping("/workers/search")
    @ResponseBody
    public List<WorkerSearchDto> workerSearch(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String field) {
        String term = q == null ? null : q.trim();
        if (term == null || term.isEmpty()) {
            return Collections.emptyList();
        }

        List<com.badaklng.request.entity.SesWorkerLookupView> results;
        if ("badge".equalsIgnoreCase(field)) {
            results = workerLookupViewRepository.searchByBadgePrefix(term, PageRequest.of(0, 30));
        } else if ("name".equalsIgnoreCase(field)) {
            results = workerLookupViewRepository.searchByName(term, PageRequest.of(0, 30));
        } else if ("nik".equalsIgnoreCase(field)) {
            results = workerLookupViewRepository.searchByNikPrefix(term, PageRequest.of(0, 30));
        } else {
            results = workerLookupViewRepository.searchByTerm(term, PageRequest.of(0, 30));
        }

        return results.stream()
                .map(w -> new WorkerSearchDto(w.getWorkerName(), w.getBadge(), w.getNik(),
                        w.getJabatan(), w.getDepartment(), w.getSection()))
                .collect(Collectors.toList());
    }

    /**
     * Suggestion endpoint returning recent driver history (SIM and vehicles)
     * based on badge or NIK.
     */
    @Cacheable(value = "userDisplayNames", key = "'driver_history_' + #q", unless = "#result == null")
    @GetMapping("/driver-history")
    @ResponseBody
    public StickerService.DriverHistoryDto driverHistory(@RequestParam(required = false) String q) {
        String term = q == null ? null : q.trim();
        if (term == null || term.isEmpty()) {
            return new StickerService.DriverHistoryDto();
        }
        return stickerService.findRecentDriverHistory(term);
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class WorkerSearchDto {
        private String workerName;
        private String badge;
        private String nik;
        private String jabatan;
        private String department;
        private String section;
    }

    private String resolveRequestStatusForForm(Long id) {
        return id == null ? null : stickerService.findById(id).getStatus();
    }

    private void populateReferenceData(Model model) {
        List<SesStickerType> types = stickerTypeService.findActive();
        model.addAttribute("stickerTypes", types);
        model.addAttribute("stickerBaseAmount", stickerService.baseAmount());
    }

    /**
     * Three queries for the whole list instead of three per row: the plate
     * summary used {@code findVehicles} per row and the Edit-button gate used
     * {@code isEditable}, which is itself two queries.
     */
    private List<StickerListRow> toListRows(List<SesStickerRequest> requests) {
        if (requests.isEmpty()) {
            return new ArrayList<>();
        }
        Map<Long, List<com.badaklng.sticker.entity.SesStickerVehicle>> vehiclesByRequest =
                stickerService.findVehiclesByRequestIds(requests.stream()
                        .map(SesStickerRequest::getStickerRequestId)
                        .collect(Collectors.toList()));
        List<Long> instanceIds = requests.stream()
                .map(SesStickerRequest::getApprovalInstanceId)
                .filter(id -> id != null)
                .collect(Collectors.toList());
        Map<Long, Boolean> editableByInstance = approvalService.isEditableBatch(instanceIds);
        Map<Long, String> stageByInstance = approvalService.resolveStageBatch(instanceIds);

        List<StickerListRow> rows = new ArrayList<>(requests.size());
        for (SesStickerRequest request : requests) {
            List<String> plates = vehiclesByRequest
                    .getOrDefault(request.getStickerRequestId(), java.util.Collections.emptyList())
                    .stream()
                    .map(v -> v.getPoliceNo())
                    .collect(Collectors.toList());
            String summary = plates.isEmpty() ? "-" : String.join(", ", plates);
            StickerListRow row = StickerListRow.from(request, summary);
            boolean statusOk = "PENDING".equals(request.getStatus()) || "DRAFT".equals(request.getStatus());
            boolean untouched = request.getApprovalInstanceId() == null
                    || Boolean.TRUE.equals(editableByInstance.get(request.getApprovalInstanceId()));
            row.setEditable(statusOk && untouched);
            if ("PENDING".equals(request.getStatus()) && request.getApprovalInstanceId() != null) {
                String stage = stageByInstance.get(request.getApprovalInstanceId());
                if (stage != null) {
                    row.setStatus(stage);
                }
            }
            rows.add(row);
        }
        return rows;
    }

    private boolean isStickerRequestInScope(SesStickerRequest request, String username) {
        if (request == null || request.getRequestorUserId() == null || username == null) {
            return false;
        }
        return userScopeService.resolveScope(username).getUsernames().contains(request.getRequestorUserId().trim());
    }
}
