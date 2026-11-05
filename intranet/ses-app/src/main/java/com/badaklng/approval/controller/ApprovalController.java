package com.badaklng.approval.controller;

import com.badaklng.platform.system.entity.AppUser;
import com.badaklng.platform.system.repository.AppUserRepository;
import com.badaklng.ses.user.UserDisplayNameService;
import com.badaklng.platform.security.PageAccessGuard;
import com.badaklng.platform.security.SecurityUtils;
import com.badaklng.platform.system.service.AppLogService;
import com.badaklng.approval.entity.ApprovalStatus;
import com.badaklng.approval.entity.SesApprovalInstance;
import com.badaklng.approval.entity.SesApprovalTask;
import com.badaklng.approval.entity.SesApprovalTaskCheckpoint;
import com.badaklng.approval.entity.TaskCheckpointStatus;
import com.badaklng.approval.entity.TaskStatus;
import com.badaklng.approval.model.ApprovalTaskForm;
import com.badaklng.approval.service.ApprovalFormService;
import com.badaklng.approval.service.ApprovalService;
import com.badaklng.approval.spi.ApprovalFormDescriptor;
import com.badaklng.approval.spi.CheckpointActionProvider;
import org.springframework.beans.factory.ObjectProvider;
import com.badaklng.ses.web.PageRequests;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Approver worklist + instance detail + approve/reject actions.
 */
@Controller
@RequestMapping("/approval")
public class ApprovalController {

    private final ApprovalService approvalService;
    private final ApprovalFormService approvalFormService;
    private final PageAccessGuard pageAccessGuard;
    private final AppLogService logService;
    private final AppUserRepository appUserRepository;
    private final UserDisplayNameService userDisplayNameService;
    /**
     * ObjectProvider, not List — zero implementations must still start the
     * context, same convention as ApprovalChainController's checkpoint types.
     */
    private final ObjectProvider<CheckpointActionProvider> checkpointActionProviders;

    @org.springframework.beans.factory.annotation.Autowired
    public ApprovalController(
            ApprovalService approvalService,
            ApprovalFormService approvalFormService,
            PageAccessGuard pageAccessGuard,
            AppLogService logService,
            AppUserRepository appUserRepository,
            UserDisplayNameService userDisplayNameService,
            ObjectProvider<CheckpointActionProvider> checkpointActionProviders) {
        this.approvalService = approvalService;
        this.approvalFormService = approvalFormService;
        this.pageAccessGuard = pageAccessGuard;
        this.logService = logService;
        this.appUserRepository = appUserRepository;
        this.userDisplayNameService = userDisplayNameService;
        this.checkpointActionProviders = checkpointActionProviders;
    }

    private String resolveUserId(String username) {
        if (username == null) {
            return null;
        }
        AppUser user = appUserRepository.findByUsername(username).orElse(null);
        return user != null ? user.getUserId() : username;
    }

    /** My pending approvals (approver worklist) — the /approval read page. */
    @GetMapping
    public String myApprovals(Model model,
                              @RequestParam(required = false) String q,
                              @RequestParam(required = false) String module,
                              @RequestParam(required = false) Integer page,
                              @RequestParam(required = false) Integer size) {
        model.addAttribute("pageTitle", pageAccessGuard.require(ApprovalTaskForm.LIST_URL));
        String username = SecurityUtils.getCurrentUsername();
        List<SesApprovalInstance> pending = approvalService.findMyPendingApprovalsFromView(username);
        Page<ApprovalTaskForm> paged = pageOf(approvalService.toTaskForms(pending), q, module, page, size);
        model.addAttribute("rows", paged.getContent());
        model.addAttribute("page", paged);
        model.addAttribute("currentUsername", username);
        return "approval/my";
    }

    /** My requests (submitted by me). */
    @GetMapping("/my-requests")
    public String myRequests(Model model,
                             @RequestParam(required = false) String q,
                             @RequestParam(required = false) String module,
                             @RequestParam(required = false) String status,
                             @RequestParam(required = false) Integer page,
                             @RequestParam(required = false) Integer size) {
        model.addAttribute("pageTitle", "My Requests");
        String username = SecurityUtils.getCurrentUsername();
        Page<ApprovalTaskForm> paged = pageOf(
                approvalService.toTaskForms(approvalService.findMyRequests(username)), q, module, status, page, size);
        model.addAttribute("rows", paged.getContent());
        model.addAttribute("page", paged);
        return "approval/my-requests";
    }

    /** My approval history (instances where I acted as an approver / evaluator). */
    @GetMapping("/my-history")
    public String myHistory(Model model,
                            @RequestParam(required = false) String q,
                            @RequestParam(required = false) String module,
                            @RequestParam(required = false) String status,
                            @RequestParam(required = false) Integer page,
                            @RequestParam(required = false) Integer size) {
        model.addAttribute("pageTitle", "Riwayat Persetujuan Saya");
        String username = SecurityUtils.getCurrentUsername();
        Page<ApprovalTaskForm> paged = pageOf(
                approvalService.findMyApprovalHistory(username), q, module, status, page, size);
        model.addAttribute("rows", paged.getContent());
        model.addAttribute("page", paged);
        model.addAttribute("currentUsername", username);
        return "approval/my-history";
    }

    /** Instance detail with history timeline (read page of the pair). */
    @GetMapping("/detail/{id}")
    public String detail(@PathVariable Long id, Model model) {
        model.addAttribute("pageTitle", pageAccessGuard.require(ApprovalTaskForm.DETAIL_URL));
        SesApprovalInstance instance = approvalService.getInstance(id);

        // instance/tasks/checkpointsByTask/completedByNames/history/historyActorNames —
        // the approval-summary fragment's contract, assembled once in the service
        // so the payment screens get exactly the same view of the flow.
        Map<String, Object> summary = approvalService.summaryModel(instance);
        // Splatted for THIS page's own cards (Summary, Checkpoint Tasks) and
        // passed whole for the shared fragment, which takes one argument so it
        // cannot collide with a host page's attribute names.
        summary.forEach(model::addAttribute);
        model.addAttribute("approvalSummary", summary);

        // instance.requestorUserId is a username; the page used to print it raw.
        model.addAttribute("requestorDisplayName",
                userDisplayNameService.displayName(instance.getRequestorUserId()));

        java.util.Optional<ApprovalFormDescriptor> descriptor = approvalFormService.findDescriptor(instance.getFormCode());
        if (descriptor.isPresent()) {
            descriptor.get().detailModel(instance.getBusinessRefId()).forEach(model::addAttribute);
            addDetailFragment(model, descriptor.get().detailFragment());
        }

        @SuppressWarnings("unchecked")
        List<SesApprovalTask> tasks = (List<SesApprovalTask>) summary.get("tasks");
        @SuppressWarnings("unchecked")
        Map<Long, List<SesApprovalTaskCheckpoint>> checkpointsByTask =
                (Map<Long, List<SesApprovalTaskCheckpoint>>) summary.get("checkpointsByTask");

        // Names of the checkpoints still holding Approve back, so the warning
        // can say WHICH ones instead of "every checkpoint task below".
        List<String> blockingCheckpoints = checkpointsByTask
                .getOrDefault(instance.getCurrentTaskId(), java.util.Collections.emptyList()).stream()
                .filter(c -> c.getStatus() == TaskCheckpointStatus.PENDING)
                .map(SesApprovalTaskCheckpoint::getTaskLabel)
                .collect(java.util.stream.Collectors.toList());
        model.addAttribute("blockingCheckpoints", blockingCheckpoints);
        model.addAttribute("hasPendingCheckpoints", !blockingCheckpoints.isEmpty());

        // checkpointId -> the owning module's inline form, so payment
        // verification and scheduling happen on this page instead of three
        // page loads away. Only the current step's checkpoints: a future
        // step's gate is not this approver's job yet.
        String returnTo = "/approval/detail/" + id + "#action-panel";
        Map<Long, CheckpointActionProvider.CheckpointAction> checkpointActions = new LinkedHashMap<>();
        for (SesApprovalTaskCheckpoint cp : checkpointsByTask
                .getOrDefault(instance.getCurrentTaskId(), java.util.Collections.emptyList())) {
            checkpointActionProviders.orderedStream()
                    .map(prov -> prov.actionFor(cp.getCheckpointKey(), instance.getBusinessRefType(),
                            instance.getBusinessRefId(), returnTo))
                    .filter(java.util.Optional::isPresent)
                    .map(java.util.Optional::get)
                    .findFirst()
                    .ifPresent(action -> checkpointActions.put(cp.getCheckpointId(), action));
        }
        model.addAttribute("checkpointActions", checkpointActions);

        String username = SecurityUtils.getCurrentUsername();
        String userId = resolveUserId(username);
        // Checked against every PENDING task, not just instance.currentTaskId:
        // a parallel group can have several tasks open at once (sql/55) and
        // currentTaskId only ever names the lowest-step_order one of them
        // (ApprovalService#advance). Gating the action panel on that alone
        // left the OTHER parallel approver — a real assignee, listed in their
        // own inbox — with no Approve/Reject button at all. The POST handlers
        // below were never affected: they resolve the acting task themselves
        // (ApprovalService#actionableTask) the same way, across every pending
        // task, not just the lead one.
        boolean canAct = instance.getStatus() == ApprovalStatus.PENDING
                && tasks.stream()
                        .filter(t -> t.getStatus() == TaskStatus.PENDING)
                        .anyMatch(t -> approvalService.canAct(t, username, userId));
        model.addAttribute("canAct", canAct);
        return "approval/detail";
    }

    /**
     * Approve/reject are the two endpoints that used to let an exception out.
     * {@link ApprovalService#act} throws on every state the UI cannot rule out
     * — instance already APPROVED/REJECTED (a stale tab, the back button, a
     * double submit), no active task, caller not an assignee, checkpoints
     * still open — and without a catch each of those rendered a raw 500 with
     * nothing saved and no explanation, which is what "clicking Reject errors
     * and does nothing" was. The checkpoint endpoints below already caught
     * exactly this set; these two now match them.
     */
    @PostMapping("/{id}/approve")
    public String approve(@PathVariable Long id,
                          @RequestParam(value = "note", required = false) String note,
                          RedirectAttributes ra,
                          HttpServletRequest request) {
        if (note != null && !new com.badaklng.ses.validation.SafeTextValidator().isValid(note, null)) {
            ra.addFlashAttribute("errorMsg", "Catatan mengandung karakter atau skrip yang berisiko keamanan (XSS).");
            return "redirect:/approval/detail/" + id + "#action-panel";
        }
        if (note != null && note.length() > 500) {
            ra.addFlashAttribute("errorMsg", "Catatan persetujuan maksimal 500 karakter.");
            return "redirect:/approval/detail/" + id + "#action-panel";
        }
        String username = SecurityUtils.getCurrentUsername();
        String userId = resolveUserId(username);
        try {
            approvalService.approve(id, username, userId, note);
            logService.log("APPROVE", "Approval", "Approved instance " + id, id, "SES_APPROVAL_INSTANCE", request);
            ra.addFlashAttribute("successMsg", "Persetujuan tersimpan.");
        } catch (IllegalArgumentException | IllegalStateException | SecurityException ex) {
            ra.addFlashAttribute("errorMsg", ex.getMessage());
        }
        return "redirect:/approval/detail/" + id + "#action-panel";
    }

    @PostMapping("/{id}/reject")
    public String reject(@PathVariable Long id,
                         @RequestParam(value = "note", required = false) String note,
                         RedirectAttributes ra,
                         HttpServletRequest request) {
        if (note != null && !new com.badaklng.ses.validation.SafeTextValidator().isValid(note, null)) {
            ra.addFlashAttribute("errorMsg", "Catatan mengandung karakter atau skrip yang berisiko keamanan (XSS).");
            return "redirect:/approval/detail/" + id + "#action-panel";
        }
        if (note != null && note.length() > 500) {
            ra.addFlashAttribute("errorMsg", "Catatan penolakan maksimal 500 karakter.");
            return "redirect:/approval/detail/" + id + "#action-panel";
        }
        String username = SecurityUtils.getCurrentUsername();
        try {
            approvalService.reject(id, username, resolveUserId(username), note);
            logService.log("REJECT", "Approval", "Rejected instance " + id, id, "SES_APPROVAL_INSTANCE", request);
            ra.addFlashAttribute("successMsg", "Pengajuan ditolak.");
        } catch (IllegalArgumentException | IllegalStateException | SecurityException ex) {
            ra.addFlashAttribute("errorMsg", ex.getMessage());
        }
        return "redirect:/approval/detail/" + id + "#action-panel";
    }

    @PostMapping("/{id}/checkpoint/{checkpointId}/complete")
    public String completeCheckpoint(@PathVariable Long id, @PathVariable Long checkpointId,
                                     @RequestParam(value = "note", required = false) String note,
                                     RedirectAttributes ra, HttpServletRequest request) {
        String username = SecurityUtils.getCurrentUsername();
        try {
            approvalService.completeCheckpoint(id, checkpointId, note, resolveUserId(username), username);
            logService.log("TASK_COMPLETE", "Approval", "Completed checkpoint " + checkpointId + " on instance " + id,
                    id, "SES_APPROVAL_INSTANCE", request);
            ra.addFlashAttribute("successMsg", "Checkpoint marked done.");
        } catch (IllegalArgumentException | IllegalStateException | SecurityException ex) {
            ra.addFlashAttribute("errorMsg", ex.getMessage());
        }
        return "redirect:/approval/detail/" + id + "#action-panel";
    }

    @PostMapping("/{id}/checkpoint/{checkpointId}/skip")
    public String skipCheckpoint(@PathVariable Long id, @PathVariable Long checkpointId,
                                 @RequestParam("note") String note,
                                 RedirectAttributes ra, HttpServletRequest request) {
        String username = SecurityUtils.getCurrentUsername();
        try {
            approvalService.skipCheckpoint(id, checkpointId, note, resolveUserId(username), username);
            logService.log("TASK_SKIP", "Approval", "Skipped checkpoint " + checkpointId + " on instance " + id,
                    id, "SES_APPROVAL_INSTANCE", request);
            ra.addFlashAttribute("successMsg", "Checkpoint skipped.");
        } catch (IllegalArgumentException | IllegalStateException | SecurityException ex) {
            ra.addFlashAttribute("errorMsg", ex.getMessage());
        }
        return "redirect:/approval/detail/" + id + "#action-panel";
    }

    /**
     * Splits a "template :: selector" fragment spec into two model attributes
     * instead of one. Thymeleaf's {@code th:insert}/{@code th:replace} only
     * recognise the {@code ::} fragment separator when it is literally
     * present in the TAG'S OWN MARKUP TEXT at parse time
     * (AbstractStandardFragmentInsertionTagProcessor#shouldBeWrappedAsFragmentExpression
     * scans the raw attribute text, not any runtime value) — a single Java
     * String containing "::" evaluated via {@code ${...}} gets treated as one
     * whole (bogus) template name, including the "::", and template
     * resolution throws. Splitting server-side and writing {@code
     * th:insert="${detailFragmentTemplate} :: ${detailFragmentSelector}"}
     * keeps a literal {@code ::} in the markup so Thymeleaf's parser splits
     * it correctly while both sides still resolve dynamically.
     */
    /**
     * Filters and slices a worklist that is already in memory.
     *
     * <p>These three screens cannot page in SQL: a row is assembled from
     * {@code APP_APPROVAL_VIEW}, the instance table and the owning module's
     * {@code ApprovalFormDescriptor}, and the transaction number and module
     * name a reader searches for come from that last source -- a module the
     * approval engine deliberately has no compile-time dependency on. Slicing
     * bounds what is rendered, not what is read; the reading was already cut
     * from ~7 queries per row to a fixed handful by {@code toTaskForms}.
     */
    private Page<ApprovalTaskForm> pageOf(List<ApprovalTaskForm> all, String q,
                                          Integer page, Integer size) {
        return pageOf(all, q, null, null, page, size);
    }

    private Page<ApprovalTaskForm> pageOf(List<ApprovalTaskForm> all, String q, String module,
                                          Integer page, Integer size) {
        return pageOf(all, q, module, null, page, size);
    }

    private Page<ApprovalTaskForm> pageOf(List<ApprovalTaskForm> all, String q, String module, String status,
                                          Integer page, Integer size) {
        String term = PageRequests.term(q);
        String mod = PageRequests.term(module);
        String stat = PageRequests.term(status);
        List<ApprovalTaskForm> matching = all.stream()
                .filter(r -> PageRequests.matches(term, r.getTransactionId(), r.getModule(),
                        r.getDescription(), r.getRequestorName(), r.getCurrentStepName(), r.getStatus()))
                .filter(r -> {
                    if (mod == null || mod.isEmpty()) return true;
                    if ("SES_REQUEST".equalsIgnoreCase(mod)) {
                        return r.getModule() != null && (r.getModule().toLowerCase().contains("badge") || r.getModule().toLowerCase().contains("request"));
                    }
                    if ("SES_STICKER_REQUEST".equalsIgnoreCase(mod)) {
                        return r.getModule() != null && (r.getModule().toLowerCase().contains("stiker") || r.getModule().toLowerCase().contains("sticker"));
                    }
                    return r.getModule() != null && r.getModule().toLowerCase().contains(mod.toLowerCase());
                })
                .filter(r -> {
                    if (stat == null || stat.isEmpty()) return true;
                    if ("WAITING_APPROVAL".equalsIgnoreCase(stat)) {
                        return "PENDING".equalsIgnoreCase(r.getStatus()) || "IN_PROGRESS".equalsIgnoreCase(r.getStatus()) || "WAITING_APPROVAL".equalsIgnoreCase(r.getStatus());
                    }
                    return stat.equalsIgnoreCase(r.getStatus());
                })
                .collect(java.util.stream.Collectors.toList());
        // Order is set by the service (newest instance first); the slice must
        // not re-sort it, so the Pageable deliberately carries no Sort.
        return PageRequests.slice(matching,
                org.springframework.data.domain.PageRequest.of(
                        PageRequests.safePage(page), PageRequests.safeSize(size)));
    }

    private void addDetailFragment(Model model, String fragment) {
        if (fragment == null) {
            return;
        }
        String[] parts = fragment.split("::", 2);
        model.addAttribute("detailFragmentTemplate", parts[0].trim());
        model.addAttribute("detailFragmentSelector", parts.length > 1 ? parts[1].trim() : null);
    }

}
