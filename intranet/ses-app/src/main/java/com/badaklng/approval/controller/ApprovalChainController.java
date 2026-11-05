package com.badaklng.approval.controller;

import com.badaklng.platform.system.entity.AppUser;
import com.badaklng.platform.system.repository.AppUserRepository;
import com.badaklng.platform.security.PageAccessGuard;
import com.badaklng.platform.system.service.AppLogService;
import com.badaklng.approval.entity.AppPositionView;
import com.badaklng.approval.entity.AssigneeType;
import com.badaklng.approval.entity.StepRole;
import com.badaklng.approval.entity.SesApprovalAssignee;
import com.badaklng.approval.entity.SesApprovalChain;
import com.badaklng.approval.entity.SesApprovalStep;
import com.badaklng.approval.entity.SesApprovalStepTask;
import com.badaklng.approval.model.ApprovalChainEditForm;
import com.badaklng.approval.model.ApprovalChainForm;
import com.badaklng.approval.service.ApprovalChainService;
import com.badaklng.approval.service.ApprovalResolverService;
import com.badaklng.approval.spi.CheckpointTaskType;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Approval chain CRUD under /approval/chain (read) + /approval/chain/form
 * (virtual CRUD-grant URL — no controller mapping needs to exist there,
 * only the app_page row). Chains, steps, and assignees are fully
 * GUI-managed here; see ApprovalChainService for the replace-by-diff save
 * semantics.
 */
@Controller
@RequestMapping("/approval/chain")
@RequiredArgsConstructor
public class ApprovalChainController {

    private final ApprovalChainService chainService;
    private final ApprovalResolverService resolverService;
    private final AppUserRepository appUserRepository;
    private final PageAccessGuard pageAccessGuard;
    private final AppLogService logService;
    // ObjectProvider, NOT List: with zero CheckpointTaskType beans on the classpath
    // (the state until a business module registers one), constructor injecting a
    // List throws NoSuchBeanDefinitionException and the app fails to boot — same
    // reasoning as ApprovalFormRegistrySync's ObjectProvider<ApprovalFormDescriptor>.
    private final ObjectProvider<CheckpointTaskType> checkpointTaskTypes;

    /** Chain list — the read page of the pair. */
    @GetMapping
    public String list(Model model) {
        model.addAttribute("pageTitle", pageAccessGuard.require(ApprovalChainForm.LIST_URL));
        List<ApprovalChainForm> rows = new ArrayList<>();
        for (SesApprovalChain chain : chainService.findAll()) {
            long stepCount = chainService.findSteps(chain.getChainId()).size();
            boolean inUse = chainService.isInUse(chain.getChainId());
            rows.add(ApprovalChainForm.from(chain, stepCount, inUse));
        }
        model.addAttribute("chains", rows);
        return "approval/chain/list";
    }

    /** Blank chain editor (new chain). */
    @GetMapping("/new")
    public String newChain(Model model) {
        model.addAttribute("pageTitle", pageAccessGuard.require(ApprovalChainForm.FORM_URL));
        model.addAttribute("chainForm", new ApprovalChainEditForm());
        populateReferenceData(model);
        return "approval/chain/edit";
    }

    /** Populated chain editor (existing chain: header + all steps + assignees). */
    @GetMapping("/{id}")
    public String edit(@PathVariable Long id, Model model) {
        model.addAttribute("pageTitle", pageAccessGuard.require(ApprovalChainForm.FORM_URL));
        SesApprovalChain chain = chainService.findById(id);
        List<SesApprovalStep> steps = chainService.findSteps(chain.getChainId());
        Map<Long, List<SesApprovalAssignee>> assigneesByStepId = new LinkedHashMap<>();
        Map<Long, List<SesApprovalStepTask>> checkpointsByStepId = new LinkedHashMap<>();
        for (SesApprovalStep step : steps) {
            assigneesByStepId.put(step.getStepId(), chainService.findAssignees(step.getStepId()));
            checkpointsByStepId.put(step.getStepId(), chainService.findStepTasks(step.getStepId()));
        }
        model.addAttribute("chainForm", ApprovalChainEditForm.from(chain, steps, assigneesByStepId, checkpointsByStepId));
        // Same rows the modal's GET /steps AJAX call returns, injected server-side so
        // the step table renders on first paint instead of showing "Memuat..." until
        // that roundtrip completes.
        model.addAttribute("initialSteps", stepDtos(chain.getChainId()));
        populateReferenceData(model);
        return "approval/chain/edit";
    }

    /**
     * Saves ONLY the chain header, then lands on the editor so the admin can
     * add steps through the modal.
     *
     * <p>Separate from {@link #save} because the two halves of the modal
     * builder are persisted independently: the header as an ordinary form,
     * each step over AJAX. Posting the header through {@code /save} would hit
     * "A chain must have at least one step" — precisely the state a new chain
     * is in before its first step exists.</p>
     */
    @PostMapping("/save-header")
    public String saveHeader(@ModelAttribute ApprovalChainEditForm form, Model model,
                             RedirectAttributes ra, HttpServletRequest request) {
        pageAccessGuard.requireAction(ApprovalChainForm.FORM_URL, "SUBMIT");
        boolean isCreate = form.getChainId() == null;
        try {
            SesApprovalChain saved = chainService.createOrUpdateHeader(form.toServiceInput());
            logService.log(isCreate ? "SAVE" : "UPDATE", "ApprovalChain",
                    "Saved chain header " + saved.getChainKey(), saved.getChainId(), "SES_APPROVAL_CHAIN", request);
            ra.addFlashAttribute("successMsg", isCreate
                    ? "Rantai dibuat. Tambahkan tahapnya di bawah."
                    : "Header rantai disimpan.");
            return "redirect:" + ApprovalChainForm.LIST_URL + "/" + saved.getChainId();
        } catch (IllegalArgumentException | IllegalStateException ex) {
            model.addAttribute("pageTitle", pageAccessGuard.require(ApprovalChainForm.FORM_URL));
            model.addAttribute("errorMsg", ex.getMessage());
            model.addAttribute("chainForm", form);
            populateReferenceData(model);
            return "approval/chain/edit";
        }
    }

    // ---------------------------------------------------------------
    // Per-step AJAX — the modal chain builder
    // ---------------------------------------------------------------
    //
    // The whole-form POST /save above is unchanged and still works. These
    // endpoints exist for the modal UI, which saves one step at a time so the
    // admin sees each change land instead of holding an entire chain in an
    // unsaved form.
    //
    // All three return JSON and translate a service-layer IllegalArgumentException
    // into a 400 with the message intact, because every one of those messages
    // is written for the admin reading it, not for a log.

    /** The chain's steps, for re-rendering the table after a modal save. */
    @GetMapping("/{id}/steps")
    @ResponseBody
    public List<StepDto> steps(@PathVariable Long id) {
        pageAccessGuard.require(ApprovalChainForm.FORM_URL);
        return stepDtos(id);
    }

    /**
     * Creates or updates ONE step. {@code stepId} empty means append.
     *
     * <p>Bound as a {@link ApprovalChainEditForm.StepRow} so the modal posts
     * exactly the field names the whole-form editor already uses
     * ({@code assignees[0].assigneeType}, …) and both paths share one mapping.</p>
     */
    @PostMapping("/{id}/step/save")
    @ResponseBody
    public ResponseEntity<?> saveStep(@PathVariable Long id,
                                      @ModelAttribute ApprovalChainEditForm.StepRow step,
                                      HttpServletRequest request) {
        requireStepEdit();
        try {
            SesApprovalStep saved = chainService.saveStep(id, step.toStepInput());
            logService.log(step.getStepId() == null ? "SAVE" : "UPDATE", "ApprovalChainStep",
                    "Saved step " + saved.getStepId() + " of chain " + id, id, "SES_APPROVAL_CHAIN", request);
            return ResponseEntity.ok(stepDtos(id));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return ResponseEntity.badRequest().body(new ErrorDto(ex.getMessage()));
        }
    }

    @PostMapping("/{id}/step/{stepId}/delete")
    @ResponseBody
    public ResponseEntity<?> deleteStep(@PathVariable Long id, @PathVariable Long stepId,
                                        HttpServletRequest request) {
        pageAccessGuard.requireAction(ApprovalChainForm.FORM_URL, "DELETE");
        try {
            chainService.deleteStep(id, stepId);
            logService.log("DELETE", "ApprovalChainStep",
                    "Deleted step " + stepId + " of chain " + id, id, "SES_APPROVAL_CHAIN", request);
            return ResponseEntity.ok(stepDtos(id));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return ResponseEntity.badRequest().body(new ErrorDto(ex.getMessage()));
        }
    }

    /** Reorders the chain's steps. {@code stepIds} must name every step exactly once. */
    @PostMapping("/{id}/step/reorder")
    @ResponseBody
    public ResponseEntity<?> reorderSteps(@PathVariable Long id,
                                          @RequestParam("stepIds") List<Long> stepIds,
                                          HttpServletRequest request) {
        requireStepEdit();
        try {
            chainService.reorderSteps(id, stepIds);
            logService.log("UPDATE", "ApprovalChainStep",
                    "Reordered steps of chain " + id, id, "SES_APPROVAL_CHAIN", request);
            return ResponseEntity.ok(stepDtos(id));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return ResponseEntity.badRequest().body(new ErrorDto(ex.getMessage()));
        }
    }

    /**
     * The grant for editing a chain's steps: {@code UPDATE} <b>or</b>
     * {@code SUBMIT}.
     *
     * <p>These two endpoints used to disagree — {@code saveStep} demanded
     * SUBMIT while {@code reorderSteps} demanded UPDATE — and the editor
     * rendered the buttons for both under a single {@code CAN_EDIT} flag. So
     * each grant produced a page with one working button and one that 403s on
     * click, in opposite directions, and {@code approval/chain/list.html} let
     * a third set of users in on UPDATE alone.</p>
     *
     * <p>Add a step, rename a step, move a step: these are the same authority
     * over the same object, so they take the same grant. Accepting either code
     * rather than picking one keeps every role that could already do part of
     * this job able to do all of it — narrowing to one would silently revoke
     * access somebody has today.</p>
     *
     * <p>Falls through to {@code requireAction} to deny, rather than throwing
     * its own {@code AccessDeniedException}, so the message and the resulting
     * 403 page are byte-identical to every other denial in the application.</p>
     */
    private void requireStepEdit() {
        if (pageAccessGuard.hasAction(ApprovalChainForm.FORM_URL, "UPDATE")
                || pageAccessGuard.hasAction(ApprovalChainForm.FORM_URL, "SUBMIT")) {
            return;
        }
        pageAccessGuard.requireAction(ApprovalChainForm.FORM_URL, "UPDATE");
    }

    /**
     * Steps of a chain as flat JSON, each carrying the resolved persona names
     * the table shows under every assignee.
     *
     * <p>Personas are resolved here rather than client-side because the names
     * live in {@code APP_POSITION_VIEW} and are PJS-aware — the browser has no
     * way to work out who currently holds position {@code E01000}.</p>
     */
    private List<StepDto> stepDtos(Long chainId) {
        List<StepDto> rows = new ArrayList<>();
        for (SesApprovalStep step : chainService.findSteps(chainId)) {
            List<AssigneeDto> assignees = new ArrayList<>();
            for (SesApprovalAssignee a : chainService.findAssignees(step.getStepId())) {
                assignees.add(new AssigneeDto(
                        a.getAssigneeId(),
                        a.getAssigneeType() == null ? null : a.getAssigneeType().name(),
                        a.getPosId(),
                        a.getUserId(),
                        describeAssignee(a)));
            }
            List<CheckpointDto> checkpoints = new ArrayList<>();
            for (SesApprovalStepTask t : chainService.findStepTasks(step.getStepId())) {
                checkpoints.add(new CheckpointDto(t.getStepTaskId(), t.getTaskLabel(), t.getCheckpointKey()));
            }
            rows.add(new StepDto(
                    step.getStepId(),
                    step.getStepOrder(),
                    step.getStepName(),
                    step.getStepRole() == null ? "" : step.getStepRole().name(),
                    step.getParallelGroup(),
                    step.getCompletionRule() == null ? "ANY" : step.getCompletionRule().name(),
                    assignees,
                    checkpoints));
        }
        return rows;
    }

    /**
     * "Set persona jabatan" — who an assignee currently resolves to, in words.
     *
     * <p>A POSITION with several current holders legitimately yields several
     * names (co-officers); a position nobody holds says so rather than
     * rendering blank, because an empty cell reads as "not configured" when
     * the truth is "configured, but the chair is empty".</p>
     */
    private String describeAssignee(SesApprovalAssignee a) {
        if (a.getAssigneeType() == null) {
            return "—";
        }
        switch (a.getAssigneeType()) {
            case POSITION: {
                List<String> names = new ArrayList<>();
                for (AppPositionView p : resolverService.findAllPositions()) {
                    if (p.getPosId() != null && p.getPosId().equals(a.getPosId()) && p.hasHolder()) {
                        names.add(p.getHolderEmplName() != null ? p.getHolderEmplName() : p.getHolderEmplId());
                    }
                }
                if (names.isEmpty()) {
                    return a.getPosId() + " — belum ada pemegang jabatan";
                }
                return a.getPosId() + " — " + String.join(", ", names);
            }
            case USER: {
                return appUserRepository.findById(a.getUserId())
                        .map(u -> u.getFullName() != null ? u.getFullName() : u.getUsername())
                        .orElse(a.getUserId());
            }
            case REQUESTOR_MANAGER:
                return "Manager pemohon (dinamis, dihitung saat pengajuan)";
            case REPORT_TO:
                return "Atasan terdaftar pemohon (SES_USER_EXT.report_to_pos_id)";
            default:
                return a.getAssigneeType().name();
        }
    }

    @lombok.Getter
    @lombok.AllArgsConstructor
    public static class StepDto {
        private final Long stepId;
        private final Integer stepOrder;
        private final String stepName;
        private final String stepRole;
        private final Integer parallelGroup;
        private final String completionRule;
        private final List<AssigneeDto> assignees;
        private final List<CheckpointDto> checkpoints;
    }

    @lombok.Getter
    @lombok.AllArgsConstructor
    public static class AssigneeDto {
        private final Long assigneeId;
        private final String assigneeType;
        private final String posId;
        private final String userId;
        /** Resolved persona, PJS-aware — see describeAssignee. */
        private final String persona;
    }

    @lombok.Getter
    @lombok.AllArgsConstructor
    public static class CheckpointDto {
        private final Long stepTaskId;
        private final String taskLabel;
        private final String checkpointKey;
    }

    @lombok.Getter
    @lombok.AllArgsConstructor
    public static class ErrorDto {
        private final String message;
    }

    @PostMapping("/{id}/toggle")
    public String toggleActive(@PathVariable Long id, RedirectAttributes ra, HttpServletRequest request) {
        pageAccessGuard.requireAction(ApprovalChainForm.FORM_URL, "UPDATE");
        try {
            SesApprovalChain chain = chainService.findById(id);
            boolean newActive = !"Y".equalsIgnoreCase(chain.getActiveFlag());
            chainService.setActive(id, newActive);
            logService.log("UPDATE", "ApprovalChain",
                    "Set chain " + id + " active=" + newActive, id, "SES_APPROVAL_CHAIN", request);
            ra.addFlashAttribute("successMsg", "Chain status updated.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            ra.addFlashAttribute("errorMsg", ex.getMessage());
        }
        return "redirect:" + ApprovalChainForm.LIST_URL;
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra, HttpServletRequest request) {
        pageAccessGuard.requireAction(ApprovalChainForm.FORM_URL, "DELETE");
        try {
            chainService.delete(id);
            logService.log("DELETE", "ApprovalChain", "Deleted chain " + id, id, "SES_APPROVAL_CHAIN", request);
            ra.addFlashAttribute("successMsg", "Chain deleted.");
        } catch (IllegalStateException ex) {
            ra.addFlashAttribute("errorMsg", ex.getMessage());
        }
        return "redirect:" + ApprovalChainForm.LIST_URL;
    }

    /**
     * Reference data shared by /new and /{id}: positions, users, resolved
     * "Resolved As" display maps (current holder per position, full name
     * per user), and the assignee type enum values — copied from the
     * pre-rework edit() logic.
     */
    private void populateReferenceData(Model model) {
        // A POS_ID can have several rows (co-officers under the same
        // position) — dedupe by posId for the picker, which only needs
        // the position itself, not each individual holder.
        Map<String, AppPositionView> byPosId = new LinkedHashMap<>();
        for (AppPositionView p : resolverService.findAllPositions()) {
            byPosId.putIfAbsent(p.getPosId(), p);
        }
        List<AppPositionView> positions = new ArrayList<>(byPosId.values());
        model.addAttribute("positions", positions);
        List<AppUser> users = appUserRepository.findByActiveFlag("Y");
        model.addAttribute("users", users);

        Map<String, String> holderNames = new HashMap<>();
        for (AppPositionView p : resolverService.findAllPositions()) {
            if (p.getHolderEmplId() != null) {
                String name = p.getHolderEmplName() != null ? p.getHolderEmplName() : p.getHolderEmplId();
                holderNames.merge(p.getPosId(), name, (existing, added) -> existing + ", " + added);
            }
        }
        model.addAttribute("holderNames", holderNames);

        Map<String, String> userNames = new HashMap<>();
        for (AppUser u : users) {
            userNames.put(u.getUserId(), u.getFullName() != null ? u.getFullName() : u.getUsername());
        }
        model.addAttribute("userNames", userNames);

        model.addAttribute("assigneeTypes", AssigneeType.values());
        // The role dropdown on each step card. Enum values rather than a
        // hand-written list in the template, so adding a role can never leave
        // the editor silently unable to select it.
        model.addAttribute("stepRoles", StepRole.values());

        List<CheckpointTaskType> sortedCheckpointTypes = checkpointTaskTypes.orderedStream()
                .sorted(Comparator.comparing(CheckpointTaskType::getLabel))
                .collect(Collectors.toList());
        model.addAttribute("checkpointTaskTypes", sortedCheckpointTypes);

        // Flat {id,label} lists for the step modal, inlined into the page as
        // JSON by th:inline="javascript". Built here rather than letting the
        // template walk the entity lists because the modal renders its rows in
        // JavaScript: a Thymeleaf-rendered <option> list cannot be cloned into
        // a row that does not exist until the admin clicks "Tambah Jabatan".
        //
        // Positions are keyed by posId and labelled with the PJS-aware current
        // holder, so the admin picks a chair and can see who is sitting in it.
        List<OptionDto> positionOptions = new ArrayList<>();
        for (AppPositionView p : positions) {
            String holder = holderNames.get(p.getPosId());
            positionOptions.add(new OptionDto(p.getPosId(),
                    p.getPosId() + " — " + (p.getPosTitle() != null ? p.getPosTitle() : "?")
                            + (holder != null ? " (" + holder + ")" : " (belum ada pemegang)")));
        }
        model.addAttribute("positionOptions", positionOptions);

        List<OptionDto> userOptions = new ArrayList<>();
        for (AppUser u : users) {
            userOptions.add(new OptionDto(u.getUserId(),
                    (u.getFullName() != null ? u.getFullName() : u.getUsername()) + " (" + u.getUsername() + ")"));
        }
        model.addAttribute("userOptions", userOptions);

        List<OptionDto> checkpointOptions = new ArrayList<>();
        for (CheckpointTaskType t : sortedCheckpointTypes) {
            checkpointOptions.add(new OptionDto(t.getKey(), t.getLabel()));
        }
        model.addAttribute("checkpointOptions", checkpointOptions);
    }

    @lombok.Getter
    @lombok.AllArgsConstructor
    public static class OptionDto {
        private final String id;
        private final String label;
    }
}
