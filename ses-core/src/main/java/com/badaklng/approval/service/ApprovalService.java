package com.badaklng.approval.service;

import com.badaklng.platform.system.entity.AppUser;
import com.badaklng.platform.system.repository.AppUserRepository;
import com.badaklng.approval.entity.AppPositionView;
import com.badaklng.approval.entity.ApprovalStatus;
import com.badaklng.approval.entity.AssigneeType;
import com.badaklng.approval.entity.CompletionRule;
import com.badaklng.approval.entity.HistoryAction;
import com.badaklng.approval.entity.ResolutionSource;
import com.badaklng.approval.entity.SesApprovalAssignee;
import com.badaklng.approval.entity.SesApprovalChain;
import com.badaklng.approval.entity.SesApprovalHistory;
import com.badaklng.approval.entity.SesApprovalInstance;
import com.badaklng.approval.entity.SesApprovalStep;
import com.badaklng.approval.entity.SesApprovalStepTask;
import com.badaklng.approval.entity.SesApprovalTask;
import com.badaklng.approval.entity.SesApprovalTaskAssignee;
import com.badaklng.approval.entity.SesApprovalTaskAssigneeDef;
import com.badaklng.approval.entity.SesApprovalTaskCheckpoint;
import com.badaklng.approval.entity.SesApprovalTaskSignoff;
import com.badaklng.approval.entity.SignoffStatus;
import com.badaklng.approval.entity.TaskCheckpointStatus;
import com.badaklng.approval.entity.StepRole;
import com.badaklng.approval.entity.TaskStatus;
import com.badaklng.approval.model.ApprovalTaskForm;
import com.badaklng.approval.spi.ApprovalFormDescriptor;
import com.badaklng.approval.repository.SesApprovalAssigneeRepository;
import com.badaklng.approval.repository.SesApprovalHistoryRepository;
import com.badaklng.approval.repository.SesApprovalInstanceRepository;
import com.badaklng.approval.repository.SesApprovalStepRepository;
import com.badaklng.approval.repository.SesApprovalStepTaskRepository;
import com.badaklng.approval.repository.SesApprovalTaskAssigneeDefRepository;
import com.badaklng.approval.repository.SesApprovalTaskAssigneeRepository;
import com.badaklng.approval.repository.SesApprovalTaskCheckpointRepository;
import com.badaklng.approval.repository.AppPositionViewRepository;
import com.badaklng.approval.repository.SesApprovalTaskSignoffRepository;
import com.badaklng.approval.repository.SesApprovalTaskRepository;
import com.badaklng.approval.repository.AppApprovalViewRepository;
import com.badaklng.approval.spi.ApprovalOutcomeListener;
import com.badaklng.request.entity.AppEmpLkpView;
import com.badaklng.request.repository.AppEmpLkpViewRepository;
import com.badaklng.ses.jdbc.InChunks;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Generic approval runtime engine.
 *
 * Modules that need approval call {@link #trigger(String, String, Long, String)}
 * (or the match-key overload); the approval module never references module
 * code — chain routing is resolved through {@link ApprovalFormService}.
 * A rejection at any step ends the whole flow (REJECTED is terminal).
 * Approvers act first-come-first-serve within a step — any one of the
 * step's assignees resolves it.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ApprovalService {

    private final SesApprovalStepRepository stepRepository;
    private final SesApprovalStepTaskRepository stepTaskRepository;
    private final SesApprovalAssigneeRepository assigneeRepository;
    private final SesApprovalInstanceRepository instanceRepository;
    private final SesApprovalTaskRepository taskRepository;
    private final SesApprovalTaskAssigneeRepository taskAssigneeRepository;
    private final SesApprovalTaskAssigneeDefRepository taskAssigneeDefRepository;
    private final SesApprovalTaskCheckpointRepository checkpointRepository;
    private final SesApprovalTaskSignoffRepository signoffRepository;
    private final AppPositionViewRepository positionViewRepository;
    private final SesApprovalHistoryRepository historyRepository;
    private final ApprovalResolverService resolverService;
    private final AppUserRepository appUserRepository;
    private final ApprovalFormService formService;
    private final ObjectProvider<ApprovalOutcomeListener> outcomeListeners;
    private final AppApprovalViewRepository appApprovalViewRepository;
    private final ApprovalMailService approvalMailService;
    private final com.badaklng.ses.user.UserDisplayNameService userDisplayNameService;
    private final AppEmpLkpViewRepository empLkpViewRepository;

    /**
     * Cheap pre-flight check for callers (form UIs) that want to know
     * whether {@link #trigger} would succeed for a given form/matchKey
     * before letting the user submit — same resolution path as trigger(),
     * but swallows {@link ApprovalConfigurationException} into an
     * {@link ChainAvailability#available} = false with a one-line reason,
     * instead of throwing.
     *
     * <p>Also dry-runs every step's assignees against {@code
     * requestorUsername} (covers REQUESTOR_MANAGER and REPORT_TO — both
     * can silently fail to resolve for a user with no position and no
     * SES_USER_EXT.report_to_pos_id), so this catches per-requestor
     * config gaps that a chain-only check would miss. The returned
     * {@code reasons} distinguish "not configured at all" from "configured,
     * but step N can't find an actor for THIS requestor" — see
     * {@link ApprovalResolverService#findUnresolvableAssignees} for the
     * per-assignee-type diagnosis (missing position holder, unresolvable
     * manager, unconfigured report_to, etc.).
     */
    @Transactional(readOnly = true)
    @Cacheable(value = "approvalChainStatus", key = "#formCode + '_' + #matchKey + '_' + #requestorUsername")
    public ChainAvailability checkChainAvailability(String formCode, String matchKey, String requestorUsername) {
        return checkChainAvailability(formCode, matchKey, requestorUsername, TriggerOptions.defaults());
    }

    /**
     * As above, but told up front which of this submission's optional steps
     * will actually run.
     *
     * <p>Needed because the pre-flight and {@link #trigger} MUST agree. A
     * requestor with no resolvable manager submitting a type whose
     * {@code needs_mgr_approval_flag} is {@code 'N'} would otherwise be warned
     * that the chain is unconfigured, and - worse, if the warning were ignored
     * - be rejected by {@code trigger}'s own hard guard, over a step that was
     * never going to run for them. Both paths go through the same
     * {@link #findUnresolvableSteps}, so they cannot disagree.</p>
     *
     * <p>Only the two skip flags of {@code opts} are read here; the evaluator
     * list is irrelevant to whether the chain CAN resolve, and the pre-flight
     * runs before the requestor has necessarily picked anyone.</p>
     */
    @Transactional(readOnly = true)
    public ChainAvailability checkChainAvailability(String formCode, String matchKey, String requestorUsername,
                                                    TriggerOptions opts) {
        try {
            ApprovalFormService.ChainResolution resolution = formService.resolveChain(formCode, matchKey);
            List<String> problems = findUnresolvableSteps(resolution.getChain(), requestorUsername, opts);
            return new ChainAvailability(problems.isEmpty(), problems);
        } catch (ApprovalConfigurationException e) {
            return new ChainAvailability(false, Collections.singletonList(e.getMessage()));
        }
    }

    /** {@link #checkChainAvailability}'s result: available, and if not, why. */
    @Getter
    @AllArgsConstructor
    public static class ChainAvailability {
        private final boolean available;
        private final List<String> reasons;
    }

    /**
     * Whether NO reviewer decision of any kind has been recorded on this
     * instance yet — i.e. cancelling it and starting a fresh one (what an
     * edit-resubmit does, see RequestService#saveAndSubmit) wouldn't
     * silently discard or paper over someone's work. Two independent things
     * can each make this false on their own, even while step 1's task is
     * still PENDING:
     * <ul>
     *   <li>Any task already APPROVED/REJECTED, or SKIPPED for a reason other
     *       than the configured manager-step skip. On a linear chain that is
     *       equivalent to the old "step 1 is no longer PENDING" test - you
     *       cannot approve step 2 without step 1 - but it also exempts the one
     *       SKIPPED that is NOT a reviewer decision: a step auto-skipped
     *       because the business type opted out of Requestor's-Manager
     *       approval. Without that exemption every submission of a no-manager
     *       type would be permanently uneditable from the instant it was
     *       created, since trigger() marks that step SKIPPED before it
     *       returns.</li>
     *   <li>Any checkpoint anywhere on the instance is DONE — e.g. an
     *       officer already verified the payment via {@link #completeCheckpointByKey}
     *       (PaymentService#approve). That doesn't touch the task's own
     *       status, so it's invisible to the step-1 check alone, but it's
     *       still a decision made against the request's current data (e.g.
     *       an amount autofilled from the worker count) — resubmitting
     *       after an edit that changes that data would otherwise carry the
     *       old decision forward (see PaymentService#carryForwardVerifiedCheckpoint)
     *       without anyone re-checking it against the new data.</li>
     * </ul>
     * True if the instance has no step-1 task at all (not triggered yet).
     */
    @Transactional(readOnly = true)
    public boolean isEditable(Long instanceId) {
        List<SesApprovalTask> tasks = taskRepository.findByInstanceIdOrderByStepOrderAsc(instanceId);
        if (tasks.stream().anyMatch(ApprovalService::isReviewerDecision)) {
            return false;
        }
        List<Long> taskIds = tasks.stream()
                .map(SesApprovalTask::getTaskId)
                .collect(Collectors.toList());
        return !checkpointRepository.existsByTaskIdInAndStatus(taskIds, TaskCheckpointStatus.DONE);
    }

    /**
     * {@link #isEditable} for a whole list page in two queries.
     *
     * <p>Same rule, same outcome, evaluated from two bulk reads instead of
     * two per row. Instances absent from the map (or a null id) are
     * editable, matching the single-id method's "no step-1 task at all means
     * not triggered yet" behaviour.
     */
    @Transactional(readOnly = true)
    public Map<Long, Boolean> isEditableBatch(Collection<Long> instanceIds) {
        Map<Long, Boolean> editable = new HashMap<>();
        if (instanceIds == null || instanceIds.isEmpty()) {
            return editable;
        }
        List<Long> ids = instanceIds.stream()
                .filter(id -> id != null)
                .distinct()
                .collect(Collectors.toList());
        for (Long id : ids) {
            editable.put(id, true);
        }

        Map<Long, List<SesApprovalTask>> tasksByInstance = new HashMap<>();
        for (SesApprovalTask t : InChunks.flatMap(ids, taskRepository::findByInstanceIdIn)) {
            tasksByInstance.computeIfAbsent(t.getInstanceId(), k -> new ArrayList<>()).add(t);
        }

        List<Long> allTaskIds = new ArrayList<>();
        for (Map.Entry<Long, List<SesApprovalTask>> entry : tasksByInstance.entrySet()) {
            if (entry.getValue().stream().anyMatch(ApprovalService::isReviewerDecision)) {
                editable.put(entry.getKey(), false);
                continue;
            }
            entry.getValue().forEach(t -> allTaskIds.add(t.getTaskId()));
        }

        Set<Long> tasksWithDoneCheckpoint = InChunks.flatMap(allTaskIds,
                        chunk -> checkpointRepository.findByTaskIdInAndStatus(chunk, TaskCheckpointStatus.DONE))
                .stream()
                .map(SesApprovalTaskCheckpoint::getTaskId)
                .collect(Collectors.toSet());
        if (!tasksWithDoneCheckpoint.isEmpty()) {
            for (Map.Entry<Long, List<SesApprovalTask>> entry : tasksByInstance.entrySet()) {
                if (Boolean.FALSE.equals(editable.get(entry.getKey()))) {
                    continue;
                }
                boolean blocked = entry.getValue().stream()
                        .anyMatch(t -> tasksWithDoneCheckpoint.contains(t.getTaskId()));
                if (blocked) {
                    editable.put(entry.getKey(), false);
                }
            }
        }
        return editable;
    }

    /**
     * Resolves the current fine-grained stage code for a collection of approval instances.
     * Maps instanceId -> WAITING_PAYMENT | WAITING_SCREENING | WAITING_RETRIEVAL | WAITING_APPROVAL.
     */
    @Transactional(readOnly = true)
    public Map<Long, String> resolveStageBatch(Collection<Long> instanceIds) {
        Map<Long, String> stages = new HashMap<>();
        if (instanceIds == null || instanceIds.isEmpty()) {
            return stages;
        }
        List<Long> ids = instanceIds.stream()
                .filter(id -> id != null)
                .distinct()
                .collect(Collectors.toList());
        if (ids.isEmpty()) {
            return stages;
        }

        List<SesApprovalInstance> instances = InChunks.flatMap(ids, instanceRepository::findAllById);
        Map<Long, Long> currentTaskIdByInstance = new HashMap<>();
        List<Long> currentTaskIds = new ArrayList<>();
        for (SesApprovalInstance inst : instances) {
            stages.put(inst.getInstanceId(), "WAITING_APPROVAL");
            if (inst.getCurrentTaskId() != null) {
                currentTaskIdByInstance.put(inst.getInstanceId(), inst.getCurrentTaskId());
                currentTaskIds.add(inst.getCurrentTaskId());
            }
        }

        if (currentTaskIds.isEmpty()) {
            return stages;
        }

        List<SesApprovalTaskCheckpoint> pendingChks = InChunks.flatMap(currentTaskIds,
                chunk -> checkpointRepository.findByTaskIdInAndStatus(chunk, TaskCheckpointStatus.PENDING));

        Map<Long, List<SesApprovalTaskCheckpoint>> chkByTask = new HashMap<>();
        for (SesApprovalTaskCheckpoint chk : pendingChks) {
            chkByTask.computeIfAbsent(chk.getTaskId(), k -> new ArrayList<>()).add(chk);
        }

        for (Map.Entry<Long, Long> entry : currentTaskIdByInstance.entrySet()) {
            Long instanceId = entry.getKey();
            Long taskId = entry.getValue();
            List<SesApprovalTaskCheckpoint> chks = chkByTask.get(taskId);
            if (chks != null && !chks.isEmpty()) {
                boolean hasPayment = chks.stream().anyMatch(c -> "PAYMENT_VERIFICATION".equalsIgnoreCase(c.getCheckpointKey()));
                boolean hasScreening = chks.stream().anyMatch(c -> "SCREENING_SCHEDULE".equalsIgnoreCase(c.getCheckpointKey()));
                boolean hasRetrieval = chks.stream().anyMatch(c -> "BADGE_RETRIEVAL_SCHEDULE".equalsIgnoreCase(c.getCheckpointKey()));

                if (hasPayment) {
                    stages.put(instanceId, "WAITING_PAYMENT");
                } else if (hasScreening) {
                    stages.put(instanceId, "WAITING_SCREENING");
                } else if (hasRetrieval) {
                    stages.put(instanceId, "WAITING_RETRIEVAL");
                } else {
                    stages.put(instanceId, "WAITING_APPROVAL");
                }
            } else {
                stages.put(instanceId, "WAITING_APPROVAL");
            }
        }

        return stages;
    }

    /**
     * Does this task record a decision a human (or the engine acting for one)
     * actually made? Static and side-effect free so the rule can be unit-tested
     * without Spring or Oracle - the same treatment
     * {@code ApprovalResolverService.decide} gets.
     *
     * <p>SKIPPED is the interesting case. It normally IS a decision: a step the
     * engine passed over after resolving it, such as a manager-level requestor
     * needing no manager approval. The one exception is the CONFIGURED
     * role skip - a skipped task whose {@link StepRole} is one the business
     * type switched off means nobody was ever asked, because the type said the
     * step does not apply. Counting that as a decision would make every such
     * request uneditable the moment it is submitted.</p>
     */
    static boolean isReviewerDecision(SesApprovalTask task) {
        if (task.getStatus() == TaskStatus.APPROVED || task.getStatus() == TaskStatus.REJECTED) {
            return true;
        }
        return task.getStatus() == TaskStatus.SKIPPED
                && !(task.getStepRole() != null && task.getStepRole().isSkippable());
    }

    /**
     * Dry-runs every step of {@code chain} for {@code requestorUsername},
     * without persisting anything. Returns one message per step that
     * cannot be resolved for this requestor; empty means the whole chain
     * is safe to trigger for them right now.
     */
    private List<String> findUnresolvableSteps(SesApprovalChain chain, String requestorUsername,
                                               TriggerOptions opts) {
        List<String> problems = new ArrayList<>();
        List<SesApprovalStep> steps = stepRepository.findByChainIdOrderByStepOrderAsc(chain.getChainId());
        for (SesApprovalStep step : steps) {
            if (willBeSkipped(step.getStepRole(), opts)) {
                // This step will be SKIPPED without anyone ever being resolved
                // for it, so whether it CAN be resolved is irrelevant. Checking
                // it anyway is exactly how a requestor with no manager in
                // APP_POSITION_VIEW would be blocked from submitting a type
                // that explicitly opted out of manager approval - the very case
                // the toggle exists for.
                continue;
            }
            if (StepRole.SECURITY_STAFF == step.getStepRole()) {
                // This step has no assignee definitions by design — its
                // approvers come from the submission, not the chain. Running
                // the resolver over its (empty) definitions would report it as
                // unresolvable and block every submission. The equivalent
                // check for it is "the caller named at least one evaluator",
                // made in trigger() where the evaluator list is in hand.
                continue;
            }
            List<SesApprovalAssignee> defs = assigneeRepository.findByStepIdOrderByAssigneeIdAsc(step.getStepId());
            for (String problem : resolverService.findUnresolvableAssignees(defs, requestorUsername)) {
                problems.add("Step '" + step.getStepName() + "': " + problem);
            }
        }
        return problems;
    }

    /**
     * What the calling business service knows about THIS submission that the
     * chain alone cannot answer.
     *
     * <p>A value object rather than more positional booleans: there are now
     * two toggles and a list, and
     * {@code trigger(a, b, c, d, e, true, false, list)} is a call nobody can
     * read or safely reorder.</p>
     *
     * <p>Passed in by the caller rather than discovered through
     * {@code ApprovalFormDescriptor}, on purpose. These are properties of this
     * submission, not of the form: the request module already holds the
     * {@code SesRequestType} and the chosen evaluators in hand from its own
     * validation, so asking an SPI would mean re-reading rows it just wrote.
     * It also keeps business vocabulary ("needs manager approval") out of a
     * descriptor contract that carries only routing, display and detail
     * concerns.</p>
     *
     * <p>{@link #defaults()} is every pre-existing behaviour: both manager
     * steps required, no evaluators. Every older {@code trigger} overload
     * delegates with it, so sticker, payment and scheduling are untouched.</p>
     */
    @Getter
    public static final class TriggerOptions {
        private final boolean requireRequestorManagerStep;
        private final boolean requireProcurementManagerStep;
        private final List<EvaluatorInput> evaluators;

        /**
         * Does this submission have to pass a payment gate before anything
         * else?
         *
         * <p>Lives here for the reason the class javadoc already gives: it is
         * a property of THIS submission, not of the form. The request module
         * holds the {@code SesRequestType} in hand and knows
         * {@code needs_payment_flag}; the approval module has no business
         * vocabulary for "needs payment" and should not grow one.</p>
         *
         * <p>Defaults to {@code false} on every pre-existing overload, so no
         * current caller changes behaviour.</p>
         */
        private final boolean requirePaymentCheckpoint;

        public TriggerOptions(boolean requireRequestorManagerStep,
                              boolean requireProcurementManagerStep,
                              List<EvaluatorInput> evaluators) {
            this(requireRequestorManagerStep, requireProcurementManagerStep, evaluators, false);
        }

        public TriggerOptions(boolean requireRequestorManagerStep,
                              boolean requireProcurementManagerStep,
                              List<EvaluatorInput> evaluators,
                              boolean requirePaymentCheckpoint) {
            this.requireRequestorManagerStep = requireRequestorManagerStep;
            this.requireProcurementManagerStep = requireProcurementManagerStep;
            this.evaluators = evaluators == null ? Collections.<EvaluatorInput>emptyList() : evaluators;
            this.requirePaymentCheckpoint = requirePaymentCheckpoint;
        }

        public static TriggerOptions defaults() {
            return new TriggerOptions(true, true, Collections.<EvaluatorInput>emptyList(), false);
        }
    }

    /**
     * One person a consensus step must wait for, as the caller knows them.
     *
     * <p>A plain carrier so a business module can name its chosen approvers
     * without this module importing that module's entities, and without that
     * module importing {@code SesApprovalTaskSignoff}.</p>
     *
     * <p>{@code emplId} is the load-bearing field — it is what
     * {@code ApprovalResolverService.decide} compares a logged-in username
     * against (username IS the badge here) and what
     * {@code SES_APPROVAL_PENDING_VIEW} matches an inbox row on.
     * {@code userId} is optional and may be null for someone who has never
     * logged into this application.</p>
     */
    @Getter
    @AllArgsConstructor
    public static final class EvaluatorInput {
        private final String emplId;
        private final String displayName;
        private final String posId;
        private final String posTitle;
        private final String userId;
    }

    /**
     * Will this step be skipped, given what the caller said about this
     * submission?
     *
     * <p>Only the two manager roles have a toggle. {@code SECURITY_STAFF} and
     * {@code SECURITY_MANAGER} are never skippable, which is what guarantees
     * a chain shaped like the Badge flow can never auto-approve.</p>
     */
    /**
     * The checkpoint key that gates payment verification.
     *
     * <p>Duplicated as a literal in {@code ApprovalChainService},
     * {@code PaymentService} and {@code PaymentApprovalBridge} already. Not
     * unified into one shared constant here on purpose: the approval module
     * must not depend on the payment module (the whole point of the
     * {@code CheckpointTaskType} SPI), and the string is the contract between
     * them — the same way {@code SCREENING_SCHEDULE} is spelled out in both
     * the scheduling module and {@code ApprovalChainService}.</p>
     */
    private static final String PAYMENT_CHECKPOINT_KEY = "PAYMENT_VERIFICATION";

    /**
     * Does any step of this chain carry {@code key} as a checkpoint task?
     *
     * <p>Reads the chain DEFINITION ({@code ses_approval_step_task}), not the
     * frozen instance rows — it has to, because it runs before
     * {@code trigger()} has frozen anything.</p>
     */
    private boolean chainHasCheckpoint(List<SesApprovalStep> steps, String key) {
        for (SesApprovalStep step : steps) {
            for (SesApprovalStepTask def : stepTaskRepository
                    .findByStepIdOrderByOrderByAscStepTaskIdAsc(step.getStepId())) {
                if (key.equals(def.getCheckpointKey())) {
                    return true;
                }
            }
        }
        return false;
    }

    static boolean willBeSkipped(StepRole role, TriggerOptions opts) {
        if (role == null) {
            return false;
        }
        if (role == StepRole.REQUESTOR_MANAGER) {
            return !opts.isRequireRequestorManagerStep();
        }
        if (role.isProcurement()) {
            // ONE toggle governs both procurement rungs. Running the
            // supervisor while skipping the manager (or the reverse) would be
            // a half-flow nobody asked for.
            return !opts.isRequireProcurementManagerStep();
        }
        if (role == StepRole.SECURITY_STAFF) {
            // Additional routing is OPTIONAL. A requestor who names nobody has
            // not misconfigured anything — there is simply nothing for this
            // step to do, so it is skipped rather than left waiting forever on
            // a consensus with no participants.
            return opts.getEvaluators().isEmpty();
        }
        return false;
    }

    /**
     * The same question at activation time, answered from the instance's
     * FROZEN state rather than from live configuration.
     *
     * <p>Frozen, so flipping a request type's toggle tomorrow can neither skip
     * a step of an approval already running nor resurrect one already passed
     * over. A task whose role is null — every task of every instance created
     * before the hybrid engine — answers {@code false}, which is exactly the
     * never-skipped behaviour those instances started under.</p>
     *
     * <p>{@code hasEvaluators} is the one input that does NOT live on the
     * instance: whether the requestor named anyone for additional routing is
     * recorded in the task's {@code ses_approval_task_signoff} rows. It is
     * passed in rather than read here so the whole rule set stays a pure
     * function — testable without Spring or Oracle, the same treatment
     * {@code ApprovalResolverService.decide} gets. It is only consulted for
     * {@link StepRole#SECURITY_STAFF}; every other role ignores it.</p>
     */
    static boolean isSkippedByConfiguration(SesApprovalTask task, SesApprovalInstance instance,
                                            boolean hasEvaluators) {
        StepRole role = task.getStepRole();
        if (role == StepRole.REQUESTOR_MANAGER) {
            return "N".equalsIgnoreCase(instance.getNeedsMgrApprovalFlag());
        }
        if (role != null && role.isProcurement()) {
            // One toggle, both rungs — see willBeSkipped.
            return "N".equalsIgnoreCase(instance.getNeedsProcApprovalFlag());
        }
        if (role == StepRole.SECURITY_STAFF) {
            return !hasEvaluators;
        }
        return false;
    }

    /**
     * The instance-bound form: answers the same question, reading the
     * evaluator count from the frozen sign-off rows.
     *
     * <p>Only hits the database when the role actually depends on the answer,
     * so the common case stays free.</p>
     */
    private boolean isSkippedByConfiguration(SesApprovalTask task, SesApprovalInstance instance) {
        boolean hasEvaluators = StepRole.SECURITY_STAFF != task.getStepRole()
                || signoffRepository.countByTaskId(task.getTaskId()) > 0;
        return isSkippedByConfiguration(task, instance, hasEvaluators);
    }

    /**
     * Human-readable reason for the skip, for the Catatan column of the route
     * table.
     *
     * <p>Worded per role on purpose: "this kind of request does not use this
     * step" and "you did not add anyone here" are very different answers to
     * give someone asking why a step did not run.</p>
     */
    private static String skipReason(StepRole role) {
        if (role == StepRole.SECURITY_STAFF) {
            return "Dilewati otomatis: tidak ada additional routing yang dipilih pemohon";
        }
        if (role == StepRole.PROCUREMENT_SUPERVISOR) {
            return "Dilewati otomatis: jenis pengajuan ini tidak memerlukan persetujuan Supervisor Procurement";
        }
        if (role == StepRole.PROCUREMENT_MANAGER) {
            return "Dilewati otomatis: jenis pengajuan ini tidak memerlukan persetujuan Manager Procurement";
        }
        return "Dilewati otomatis: jenis pengajuan ini tidak memerlukan persetujuan Manager Pemohon";
    }

    /**
     * Group ordering key. A null {@code parallel_group} means "a group of its
     * own", so it falls back to the step order — which reproduces the original
     * strictly serial engine for every chain that never sets a group, and for
     * every instance created before the column existed.
     */
    static int groupOf(SesApprovalTask task) {
        return task.getParallelGroup() != null ? task.getParallelGroup() : task.getStepOrder();
    }

    private static int groupOf(SesApprovalStep step) {
        return step.getParallelGroup() != null ? step.getParallelGroup() : step.getStepOrder();
    }

    /**
     * The next GROUP to open: every PENDING task sharing the lowest group
     * number.
     *
     * <p>All of them activate at once and all of them must settle before the
     * group after opens. A chain that never sets {@code parallel_group} has one
     * task per group ({@link #groupOf} falls back to {@code step_order}), so
     * this returns a single-element list and the behaviour is exactly the
     * serial engine it replaced.</p>
     *
     * <p>Pure and {@code static} so the routing rule can be unit-tested without
     * a Spring context or an Oracle connection — the same treatment
     * {@code ApprovalResolverService.decide} gets, and for the same reason:
     * routing logic that can only be exercised through {@code @SpringBootTest}
     * against a live ELLDEV is routing logic that does not get tested.</p>
     *
     * @param pending the instance's PENDING tasks, in step order
     * @return the members of the lowest group, in step order; empty if nothing
     *         is pending
     */
    static List<SesApprovalTask> nextGroup(List<SesApprovalTask> pending) {
        if (pending == null || pending.isEmpty()) {
            return Collections.emptyList();
        }
        int lowest = pending.stream().mapToInt(ApprovalService::groupOf).min().getAsInt();
        return pending.stream()
                .filter(t -> groupOf(t) == lowest)
                .collect(Collectors.toList());
    }

    /**
     * Is this task in the lowest group of its instance that still has PENDING
     * tasks — i.e. is its step actually open right now?
     *
     * <p>The question {@code task == instance.currentTaskId} used to answer.
     * With a parallel group that pointer names only the lead, so the old test
     * would say "no" to a perfectly active sibling.</p>
     */
    private boolean isInActiveGroup(SesApprovalTask task) {
        List<SesApprovalTask> pending = taskRepository
                .findByInstanceIdOrderByStepOrderAsc(task.getInstanceId()).stream()
                .filter(t -> t.getStatus() == TaskStatus.PENDING)
                .collect(Collectors.toList());
        if (pending.isEmpty()) {
            return false;
        }
        int activeGroup = pending.stream().mapToInt(ApprovalService::groupOf).min().getAsInt();
        return groupOf(task) == activeGroup;
    }

    // ---------------------------------------------------------------
    // Trigger
    // ---------------------------------------------------------------

    /**
     * Starts an approval process for a business record, resolving the
     * chain to use via {@link ApprovalFormService#resolveChain}.
     *
     * @param formCode        registered ApprovalFormDescriptor.formCode()
     * @param matchKey        routing value, or null to use the form's default chain
     * @param businessRefType module-provided business reference type
     * @param businessRefId   module-provided business reference id
     * @param requestorUsername app_user.username (== empl id) of the submitter
     * @return the created instance
     */
    @Transactional
    public SesApprovalInstance trigger(String formCode, String matchKey, String businessRefType, Long businessRefId,
                                       String requestorUsername) {
        return trigger(formCode, matchKey, businessRefType, businessRefId, requestorUsername, true);
    }

    /**
     * As above, but told whether this submission needs the chain's
     * Requestor's-Manager step at all.
     *
     * <p>Passed in by the calling business service rather than discovered
     * through {@code ApprovalFormDescriptor}, on purpose. The answer is a
     * property of THIS submission, not of the form: the request module already
     * holds the {@code SesRequestType} in hand from its own validation, so
     * asking an SPI would mean re-reading rows the caller just wrote. It also
     * keeps business vocabulary ("needs manager approval") out of a descriptor
     * contract that deliberately carries only routing, display and detail
     * concerns. Every pre-existing overload delegates here with {@code true},
     * so no existing caller changes behaviour.</p>
     *
     * @param requireRequestorManagerStep {@code false} skips the chain's step
     *        marked {@code step_role = 'REQUESTOR_MANAGER'}, if it has one.
     *        Inert on a chain with no such step - there is nothing to skip.
     */
    @Transactional
    public SesApprovalInstance trigger(String formCode, String matchKey, String businessRefType, Long businessRefId,
                                       String requestorUsername, boolean requireRequestorManagerStep) {
        return trigger(formCode, matchKey, businessRefType, businessRefId, requestorUsername,
                new TriggerOptions(requireRequestorManagerStep, true, Collections.<EvaluatorInput>emptyList()));
    }

    /**
     * The real one: starts an approval knowing everything the calling business
     * service knows about this particular submission — which optional manager
     * steps it needs, and who it named as consensus approvers.
     *
     * @see TriggerOptions
     */
    @Transactional
    public SesApprovalInstance trigger(String formCode, String matchKey, String businessRefType, Long businessRefId,
                                       String requestorUsername, TriggerOptions opts) {
        ApprovalFormService.ChainResolution resolution = formService.resolveChain(formCode, matchKey);
        SesApprovalChain chain = resolution.getChain();

        List<SesApprovalStep> steps = stepRepository.findByChainIdOrderByStepOrderAsc(chain.getChainId());
        if (steps.isEmpty()) {
            // Defense in depth: resolveChain()'s validChain() check already
            // guarantees at least one step.
            throw new IllegalStateException("Approval chain has no steps: " + chain.getChainKey());
        }

        // Hard guard: chainAvailable() is only an advisory pre-check the UI
        // may skip or race; validate every step (not just the first one
        // activated below) can actually resolve an actor for this
        // requestor before anything is persisted. Catches REQUESTOR_MANAGER
        // / REPORT_TO steps with no position and no report_to configured
        // that would otherwise silently strand the instance later.
        List<String> unresolvable = findUnresolvableSteps(chain, requestorUsername, opts);
        if (!unresolvable.isEmpty()) {
            throw new ApprovalConfigurationException("Approval chain '" + chain.getChainName()
                    + "' cannot be started for requestor " + requestorUsername + ": "
                    + String.join("; ", unresolvable));
        }

        // A consensus step with nobody to wait for can never complete: it would
        // sit PENDING with zero sign-offs, invisible to every inbox.
        //
        // This does NOT fire for the SECURITY_STAFF step with no evaluators —
        // that is the normal, supported case now that additional routing is
        // optional, and willBeSkipped() already reports such a step as
        // skipped, so it drops out of the stream below. What remains is the
        // genuinely broken shape: some OTHER role carrying completion rule
        // ALL, which has no source of sign-offs at all and would strand the
        // instance. Only a chain edited straight in the database can reach it.
        boolean needsEvaluators = steps.stream()
                .anyMatch(s -> CompletionRule.ALL == s.getCompletionRule() && !willBeSkipped(s.getStepRole(), opts));
        if (needsEvaluators && opts.getEvaluators().isEmpty()) {
            throw new ApprovalConfigurationException("Approval chain '" + chain.getChainName()
                    + "' has a consensus step that takes its approvers from nowhere: step role"
                    + " SECURITY_STAFF is the only one fed by the submission. Perbaiki konfigurasi"
                    + " rantai persetujuan.");
        }

        // Degenerate-chain guard. ApprovalChainService refuses to SAVE a chain
        // whose every step is skippable, but a chain edited straight in the
        // database could still get here. Without this, advance() would find no
        // PENDING task at all and complete the instance as APPROVED inside
        // trigger() itself - a submission approved with nobody having looked at
        // it. Refuse loudly instead.
        if (steps.stream().allMatch(s -> willBeSkipped(s.getStepRole(), opts))) {
            throw new ApprovalConfigurationException("Approval chain '" + chain.getChainName()
                    + "' has no step left once this request type's skipped steps are removed"
                    + " - it would be approved with no review at all. Add a step, or turn"
                    + " one of the approval toggles back on for this type.");
        }

        // Payment gate guard. A paying request type whose chain carries no
        // PAYMENT_VERIFICATION checkpoint is approved and scheduled without
        // anyone ever looking at the proof of payment.
        //
        // This is NOT a theoretical hole, it is the live misconfiguration this
        // guard was written for. needs_payment_flag has no influence on chain
        // selection: routing is SES_FORM_APPROVAL_MAP keyed on the type code
        // alone, and sql/README.md records that the mapping is "deliberately
        // left unseeded for the admin to wire at /approval/map". Map a paying
        // type to BADGE_DEFAULT and everything downstream stays quiet about
        // it — PaymentService.approve calls completeCheckpointByKey, which
        // finds no matching checkpoint and WARN-no-ops (see its javadoc), so
        // the only trace is a log line nobody reads.
        //
        // Refusing here, before a single row is persisted, is the same
        // treatment every other broken chain shape above gets. Auto-injecting
        // the missing checkpoint was the alternative and is worse: the running
        // instance would then not match its configured chain, so /approval/chain
        // would describe a flow that is not the one executing, and the real
        // misconfiguration would never be noticed or fixed.
        if (opts.isRequirePaymentCheckpoint() && !chainHasCheckpoint(steps, PAYMENT_CHECKPOINT_KEY)) {
            throw new ApprovalConfigurationException("Jenis pengajuan '" + matchKey
                    + "' membutuhkan pembayaran, tetapi rantai persetujuan '" + chain.getChainName()
                    + "' tidak memuat checkpoint " + PAYMENT_CHECKPOINT_KEY + "."
                    + " Tanpa checkpoint itu pengajuan akan lolos ke tahap screening tanpa"
                    + " verifikasi bukti bayar. Perbaiki pemetaan jenis pengajuan di"
                    + " /approval/map, atau tambahkan checkpoint tersebut pada rantai di"
                    + " /approval/chain.");
        }

        SesApprovalInstance instance = new SesApprovalInstance();
        instance.setChainId(chain.getChainId());
        instance.setChainKey(chain.getChainKey());
        instance.setChainName(chain.getChainName());
        instance.setFormCode(formCode);
        instance.setMatchKey(resolution.getMatchKeyUsed());
        instance.setResolutionTier(resolution.getTier());
        instance.setBusinessRefType(businessRefType);
        instance.setBusinessRefId(businessRefId);
        instance.setRequestorUserId(requestorUsername);
        instance.setStatus(ApprovalStatus.PENDING);
        // FREEZE #1 - the business decisions for this submission. Read back by
        // activateTask(); never consulted from the live request type again, so
        // an admin flipping a toggle later cannot change a running instance.
        instance.setNeedsMgrApprovalFlag(opts.isRequireRequestorManagerStep() ? "Y" : "N");
        instance.setNeedsProcApprovalFlag(opts.isRequireProcurementManagerStep() ? "Y" : "N");
        instance = instanceRepository.save(instance);

        logHistory(instance.getInstanceId(), null, HistoryAction.START,
                requestorUsername, requestorUsername, null, 1,
                "Approval started for " + businessRefType + " #" + businessRefId + " — form=" + formCode
                        + ", matchKey=" + resolution.getMatchKeyUsed() + ", tier=" + resolution.getTier()
                        + ", chain=" + chain.getChainName() + " (id " + chain.getChainId() + ")"
                        + ", mgrStep=" + (opts.isRequireRequestorManagerStep() ? "required" : "skipped")
                        + ", procStep=" + (opts.isRequireProcurementManagerStep() ? "required" : "skipped")
                        + ", evaluators=" + opts.getEvaluators().size());

        // Snapshot all steps as tasks, and freeze each step's CURRENT
        // assignee definitions into SES_APPROVAL_TASK_ASSIGNEE_DEF — the
        // freeze fix: later chain edits (including step deletion) never
        // retroactively change this in-flight instance.
        for (SesApprovalStep step : steps) {
            StepRole role = step.getStepRole();
            if (role == StepRole.REQUESTOR_MANAGER && !opts.isRequireRequestorManagerStep()) {
                continue;
            }
            if (role != null && role.isProcurement() && !opts.isRequireProcurementManagerStep()) {
                continue;
            }

            SesApprovalTask task = new SesApprovalTask();
            task.setInstanceId(instance.getInstanceId());
            task.setStepDefinitionId(step.getStepId());
            task.setStepOrder(step.getStepOrder());
            task.setStepName(step.getStepName());
            task.setStatus(TaskStatus.PENDING);
            // FREEZE #2 - WHICH role this step played, which group it belonged
            // to, and how it decides it is done. Snapshotted the same way the
            // assignee definitions and checkpoints below are: a later chain
            // edit must never re-group, re-role or re-rule an instance that is
            // already running.
            task.setStepRole(step.getStepRole());
            task.setParallelGroup(step.getParallelGroup());
            task.setCompletionRule(step.getCompletionRule() == null
                    ? CompletionRule.ANY : step.getCompletionRule());
            task = taskRepository.save(task);

            List<SesApprovalAssignee> defs = assigneeRepository.findByStepIdOrderByAssigneeIdAsc(step.getStepId());
            for (SesApprovalAssignee def : defs) {
                SesApprovalTaskAssigneeDef snap = new SesApprovalTaskAssigneeDef();
                snap.setTaskId(task.getTaskId());
                snap.setAssigneeType(def.getAssigneeType());
                snap.setPosId(def.getPosId());
                snap.setUserId(def.getUserId());
                snap.setOrderBy(def.getOrderBy() != null ? def.getOrderBy() : 0);
                taskAssigneeDefRepository.save(snap);
            }

            // Freeze this step's checkpoint (gated task) definitions the same
            // way — an admin editing/deleting them later must never mutate
            // this in-flight instance. Zero rows here = no gate, unchanged.
            List<SesApprovalStepTask> checkpointDefs = stepTaskRepository.findByStepIdOrderByOrderByAscStepTaskIdAsc(step.getStepId());
            for (SesApprovalStepTask def : checkpointDefs) {
                SesApprovalTaskCheckpoint checkpoint = new SesApprovalTaskCheckpoint();
                checkpoint.setTaskId(task.getTaskId());
                checkpoint.setTaskLabel(def.getTaskLabel());
                checkpoint.setCheckpointKey(def.getCheckpointKey());
                checkpoint.setStatus(TaskCheckpointStatus.PENDING);
                checkpointRepository.save(checkpoint);
            }

            // FREEZE #4 - who a consensus step is waiting for. Written here,
            // at trigger time, alongside every other freeze, rather than at
            // activation: the evaluator list only exists in the caller's hand
            // right now, and a consensus step that is not step 1 is activated
            // later by advance(), which has no way to get it back.
            if (CompletionRule.ALL == task.getCompletionRule()) {
                int order = 0;
                for (EvaluatorInput ev : opts.getEvaluators()) {
                    SesApprovalTaskSignoff signoff = new SesApprovalTaskSignoff();
                    signoff.setTaskId(task.getTaskId());
                    signoff.setEmplId(ev.getEmplId());
                    signoff.setUserId(ev.getUserId());
                    signoff.setDisplayName(ev.getDisplayName());
                    signoff.setPosId(ev.getPosId());
                    signoff.setPosTitle(ev.getPosTitle());
                    signoff.setStatus(SignoffStatus.PENDING);
                    signoff.setOrderBy(order++);
                    signoffRepository.save(signoff);
                }
            }
        }

        // Activate the whole FIRST GROUP, not just step 1. For a serial chain
        // (every parallel_group null) the first group is exactly step 1, which
        // is why this is a generalisation and not a behaviour change.
        advance(instance);
        return instance;
    }

    /**
     * Convenience overload for the common case: no match-key override, use
     * the form's default chain.
     */
    @Transactional
    public SesApprovalInstance trigger(String formCode, String businessRefType, Long businessRefId,
                                       String requestorUsername) {
        return trigger(formCode, null, businessRefType, businessRefId, requestorUsername);
    }

    // ---------------------------------------------------------------
    // Approve / Reject
    // ---------------------------------------------------------------

    /**
     * Approves the current task of an instance as the given user.
     */
    @Transactional
    public SesApprovalInstance approve(Long instanceId, String username, String userId, String note) {
        return act(instanceId, username, userId, note, true);
    }

    /**
     * Rejects the current task of an instance — ends the whole flow.
     */
    @Transactional
    public SesApprovalInstance reject(Long instanceId, String username, String userId, String note) {
        return act(instanceId, username, userId, note, false);
    }

    private SesApprovalInstance act(Long instanceId, String username, String userId, String note,
                                    boolean approve) {
        SesApprovalInstance instance = instanceRepository.findById(instanceId)
                .orElseThrow(() -> new IllegalArgumentException("Approval instance not found: " + instanceId));
        if (instance.getStatus() != ApprovalStatus.PENDING) {
            throw new IllegalStateException("Approval instance " + instanceId + " is already " + instance.getStatus());
        }
        SesApprovalTask task = actionableTask(instance, username, userId);

        if (approve) {
            List<SesApprovalTaskCheckpoint> openCheckpoints = checkpointRepository.findByTaskIdAndStatus(task.getTaskId(), TaskCheckpointStatus.PENDING);
            if (!openCheckpoints.isEmpty()) {
                throw new IllegalStateException("Cannot approve: " + openCheckpoints.size()
                        + " checkpoint task(s) not resolved: " + openCheckpoints.stream()
                        .map(SesApprovalTaskCheckpoint::getTaskLabel).collect(Collectors.joining(", ")));
            }

            if (CompletionRule.ALL == task.getCompletionRule()) {
                // Consensus: record THIS person's sign-off. The task itself
                // only closes once nobody is left pending.
                SesApprovalTaskSignoff mine = requireSignoff(task, username);
                mine.setStatus(SignoffStatus.APPROVED);
                mine.setNote(note);
                mine.setCompletedBy(userId);
                mine.setCompletedDate(new Date());
                signoffRepository.save(mine);

                // Drop them out of the inbox immediately. The view already
                // subtracts settled sign-offs (sql/55), but the task's mirrored
                // assignee row would otherwise keep matching them until the
                // whole task closed.
                removeMirroredAssignee(task, username);

                long stillWaiting = signoffRepository.countByTaskIdAndStatus(task.getTaskId(), SignoffStatus.PENDING);
                logHistory(instance.getInstanceId(), task.getTaskId(), HistoryAction.APPROVE,
                        userId, username, task.getStepOrder(), task.getStepOrder(),
                        stillWaiting > 0
                                ? "Disetujui oleh " + username + "; menunggu " + stillWaiting + " orang lagi"
                                : note);
                invalidateInboxCache(username);

                if (stillWaiting > 0) {
                    // Not done yet. Everything below — closing the task,
                    // advancing the instance — waits for the last signatory.
                    return instance;
                }

                task.setStatus(TaskStatus.APPROVED);
                task.setCompletedBy(userId);
                task.setCompletedDate(new Date());
                task.setNote(note);
                taskRepository.save(task);
                logHistory(instance.getInstanceId(), task.getTaskId(), HistoryAction.TASK_COMPLETE,
                        userId, username, task.getStepOrder(), task.getStepOrder(),
                        "Semua evaluator telah menyetujui tahap ini");
                advance(instance);
                return instance;
            }

            task.setStatus(TaskStatus.APPROVED);
            task.setCompletedBy(userId);
            task.setCompletedDate(new Date());
            task.setNote(note);
            taskRepository.save(task);

            logHistory(instance.getInstanceId(), task.getTaskId(), HistoryAction.APPROVE,
                    userId, username, task.getStepOrder(), task.getStepOrder(), note);

            advance(instance);
        } else {
            // A rejection ends the whole instance, from any step and from any
            // member of a consensus step. There is deliberately no notion of
            // outvoting a dissent: if one of the named Staf Security refuses,
            // the submission is refused.
            if (CompletionRule.ALL == task.getCompletionRule()) {
                SesApprovalTaskSignoff mine = requireSignoff(task, username);
                mine.setStatus(SignoffStatus.REJECTED);
                mine.setNote(note);
                mine.setCompletedBy(userId);
                mine.setCompletedDate(new Date());
                signoffRepository.save(mine);
            }

            task.setStatus(TaskStatus.REJECTED);
            task.setCompletedBy(userId);
            task.setCompletedDate(new Date());
            task.setNote(note);
            taskRepository.save(task);

            instance.setStatus(ApprovalStatus.REJECTED);
            instance.setCurrentTaskId(task.getTaskId());
            instanceRepository.save(instance);

            logHistory(instance.getInstanceId(), task.getTaskId(), HistoryAction.REJECT,
                    userId, username, task.getStepOrder(), task.getStepOrder(), note);

            notifyOutcomeListeners(instance);
        }
        invalidateInboxCache(username);
        return instance;
    }

    /**
     * The PENDING task of this instance that this user may act on.
     *
     * <p>Replaces the old {@code currentTask(instance)} lookup, which assumed
     * exactly one task was ever open. With a parallel group, Manager Pemohon
     * and Manager Procurement are both open at once and
     * {@code current_task_id} can only name one of them — resolving by it
     * would make the other manager's Approve button throw "not an assignee"
     * on their own task.</p>
     *
     * <p>Scans in step order, so a user who somehow qualifies for two open
     * tasks acts on the earlier one first. That is a corner case (it needs one
     * person to be both the requestor's manager and the procurement manager),
     * but picking deterministically beats picking arbitrarily.</p>
     */
    private SesApprovalTask actionableTask(SesApprovalInstance instance, String username, String userId) {
        List<SesApprovalTask> pending = taskRepository
                .findByInstanceIdOrderByStepOrderAsc(instance.getInstanceId()).stream()
                .filter(t -> t.getStatus() == TaskStatus.PENDING)
                .collect(Collectors.toList());
        if (pending.isEmpty()) {
            throw new IllegalStateException("Approval instance has no active task");
        }
        for (SesApprovalTask candidate : pending) {
            if (resolverService.canAct(candidate, username, userId)) {
                return candidate;
            }
        }
        throw new SecurityException("User is not an assignee of any open approval step");
    }

    /**
     * This user's sign-off row on a consensus task.
     *
     * <p>Absent means they are not one of the people it is waiting for. That
     * is reachable despite {@code canAct} having just passed — an emergency
     * approver (role 1/2) is authorised on every task but has no sign-off row
     * of their own. Rather than inventing one, which would silently add a name
     * to the frozen list of who was asked, this refuses: an emergency approver
     * who needs to force a consensus step past should reject it or have the
     * chain changed, not quietly join the panel.</p>
     */
    private SesApprovalTaskSignoff requireSignoff(SesApprovalTask task, String username) {
        String badge = username == null ? null : username.trim();
        SesApprovalTaskSignoff mine = badge == null ? null
                : signoffRepository.findByTaskIdAndEmplId(task.getTaskId(), badge).orElse(null);
        if (mine == null) {
            throw new SecurityException("Tahap \"" + task.getStepName()
                    + "\" menunggu persetujuan dari daftar evaluator yang dipilih pemohon,"
                    + " dan Anda tidak termasuk di dalamnya.");
        }
        if (mine.getStatus() != SignoffStatus.PENDING) {
            throw new IllegalStateException("Anda sudah memberikan keputusan pada tahap ini.");
        }
        return mine;
    }

    /** Takes one settled signatory out of the mirrored assignee rows, so their inbox clears. */
    private void removeMirroredAssignee(SesApprovalTask task, String username) {
        String badge = username == null ? null : username.trim();
        if (badge == null) {
            return;
        }
        for (SesApprovalTaskAssignee a : taskAssigneeRepository.findByTaskIdOrderByTaskAssigneeIdAsc(task.getTaskId())) {
            if (badge.equalsIgnoreCase(a.getEmplId() == null ? null : a.getEmplId().trim())) {
                taskAssigneeRepository.delete(a);
            }
        }
    }

    // ---------------------------------------------------------------
    // Checkpoint tasks (gated tasks on the current step)
    // ---------------------------------------------------------------

    /** Checkpoints of a task (step instance), in definition order. */
    @Transactional(readOnly = true)
    public List<SesApprovalTaskCheckpoint> getCheckpoints(Long taskId) {
        return checkpointRepository.findByTaskIdOrderByCheckpointIdAsc(taskId);
    }

    /**
     * Manual "mark done" — same authorization as approve/reject
     * (resolverService.canAct): the approver of this step attests the task
     * (screening scheduled, payment proof collected, etc.) actually
     * happened, even though it happened outside this system. sourceRef is
     * left null — that column is reserved for module-driven completions via
     * {@link #completeCheckpointByKey}.
     */
    @Transactional
    public void completeCheckpoint(Long instanceId, Long checkpointId, String note, String userId, String username) {
        SesApprovalTaskCheckpoint checkpoint = resolveOwnCheckpoint(instanceId, checkpointId, username, userId);
        checkpoint.setStatus(TaskCheckpointStatus.DONE);
        checkpoint.setNote(note);
        checkpoint.setCompletedBy(userId);
        checkpoint.setCompletedDate(new Date());
        checkpointRepository.save(checkpoint);

        SesApprovalTask task = taskRepository.findById(checkpoint.getTaskId())
                .orElseThrow(() -> new IllegalStateException("Task not found: " + checkpoint.getTaskId()));
        logHistory(instanceId, task.getTaskId(), HistoryAction.TASK_COMPLETE, userId, username,
                task.getStepOrder(), task.getStepOrder(), "Checkpoint completed: " + checkpoint.getTaskLabel()
                        + (note != null && !note.trim().isEmpty() ? " — " + note : ""));
        autoAdvanceUnassignedTask(task);
    }

    /**
     * Explicit human override for special circumstances: the task was done
     * outside the system with no way to prove it through the normal
     * (module-driven or manual-complete) path, so the approver bypasses the
     * gate instead. A reason is mandatory — unlike completeCheckpoint's
     * optional note — so the audit trail always explains a bypass.
     */
    @Transactional
    public void skipCheckpoint(Long instanceId, Long checkpointId, String note, String userId, String username) {
        if (note == null || note.trim().isEmpty()) {
            throw new IllegalArgumentException("A reason is required to skip a checkpoint task");
        }
        SesApprovalTaskCheckpoint checkpoint = resolveOwnCheckpoint(instanceId, checkpointId, username, userId);
        checkpoint.setStatus(TaskCheckpointStatus.SKIPPED);
        checkpoint.setNote(note);
        checkpoint.setCompletedBy(userId);
        checkpoint.setCompletedDate(new Date());
        checkpointRepository.save(checkpoint);

        SesApprovalTask task = taskRepository.findById(checkpoint.getTaskId())
                .orElseThrow(() -> new IllegalStateException("Task not found: " + checkpoint.getTaskId()));
        logHistory(instanceId, task.getTaskId(), HistoryAction.TASK_SKIP, userId, username,
                task.getStepOrder(), task.getStepOrder(), "Checkpoint skipped: " + checkpoint.getTaskLabel() + " — " + note);
        autoAdvanceUnassignedTask(task);
    }

    private SesApprovalTaskCheckpoint resolveOwnCheckpoint(Long instanceId, Long checkpointId, String username, String userId) {
        SesApprovalInstance instance = getInstance(instanceId);
        SesApprovalTask task = currentTask(instance);
        if (!resolverService.canAct(task, username, userId)) {
            throw new SecurityException("User is not an assignee of the current approval step");
        }
        SesApprovalTaskCheckpoint checkpoint = checkpointRepository.findById(checkpointId)
                .orElseThrow(() -> new IllegalArgumentException("Checkpoint not found: " + checkpointId));
        if (!checkpoint.getTaskId().equals(task.getTaskId())) {
            throw new IllegalStateException("Checkpoint " + checkpointId + " does not belong to the current step");
        }
        if (checkpoint.getStatus() != TaskCheckpointStatus.PENDING) {
            throw new IllegalStateException("Checkpoint was already resolved: " + checkpoint.getStatus());
        }
        return checkpoint;
    }

    /**
     * Module-driven completion: a business module (e.g. a screening
     * schedule module) calls this the moment its own action satisfies a
     * checkpoint, instead of requiring a manual tick. Matches by
     * businessRefType/businessRefId (the same soft pointer the module
     * already owns from calling trigger()) + checkpointKey (the free-text
     * convention agreed between the module and the chain admin) — zero
     * compile-time coupling either direction.
     *
     * Searches EVERY step's frozen checkpoint row for this key, not just
     * the instance's current step — checkpoint rows for every step are
     * frozen PENDING at trigger() time (see {@link #trigger}), so the row a
     * future step gates on already exists even before the flow reaches it.
     * This lets a module satisfy its precondition early — e.g. payment
     * verified while the instance is still on an earlier manager-approval
     * step — and have the gate already open by the time that step becomes
     * current, instead of requiring the precondition to land exactly while
     * its step is active.
     *
     * Idempotent-safe no-op (logged at WARN, never throws) when no matching
     * PENDING checkpoint is found anywhere on the instance — covers "already
     * completed" and "key not configured on this chain" — without failing
     * the caller's own transaction (e.g. saving a schedule shouldn't roll
     * back just because the approval flow isn't there yet).
     */
    @Transactional
    public void completeCheckpointByKey(String businessRefType, Long businessRefId, String checkpointKey,
                                        String sourceRef, String note, String userId, String username) {
        Optional<SesApprovalInstance> instanceOpt = instanceRepository
                .findByBusinessRefTypeAndBusinessRefIdAndStatus(businessRefType, businessRefId, ApprovalStatus.PENDING)
                .stream().findFirst();
        if (!instanceOpt.isPresent()) {
            log.warn("completeCheckpointByKey: no PENDING instance for {}#{}", businessRefType, businessRefId);
            return;
        }
        SesApprovalInstance instance = instanceOpt.get();

        List<SesApprovalTask> tasks = taskRepository.findByInstanceIdOrderByStepOrderAsc(instance.getInstanceId());
        SesApprovalTaskCheckpoint checkpoint = null;
        for (SesApprovalTask t : tasks) {
            Optional<SesApprovalTaskCheckpoint> found =
                    checkpointRepository.findFirstByTaskIdAndCheckpointKey(t.getTaskId(), checkpointKey);
            if (found.isPresent() && found.get().getStatus() == TaskCheckpointStatus.PENDING) {
                checkpoint = found.get();
                break;
            }
        }
        if (checkpoint == null) {
            log.warn("completeCheckpointByKey: no PENDING checkpoint key={} anywhere on instance {}",
                    checkpointKey, instance.getInstanceId());
            return;
        }
        checkpoint.setStatus(TaskCheckpointStatus.DONE);
        checkpoint.setSourceRef(sourceRef);
        checkpoint.setNote(note);
        checkpoint.setCompletedBy(userId);
        checkpoint.setCompletedDate(new Date());
        checkpointRepository.save(checkpoint);

        final Long resolvedTaskId = checkpoint.getTaskId();
        SesApprovalTask task = taskRepository.findById(resolvedTaskId)
                .orElseThrow(() -> new IllegalStateException("Task not found: " + resolvedTaskId));
        logHistory(instance.getInstanceId(), task.getTaskId(), HistoryAction.TASK_COMPLETE, userId, username,
                task.getStepOrder(), task.getStepOrder(), "Checkpoint completed: " + checkpoint.getTaskLabel()
                        + " (via " + sourceRef + ")");
        autoAdvanceUnassignedTask(task);
    }

    /**
     * Module-driven hard-fail: a business module (e.g. a failed screening
     * appointment) determines the whole request can no longer proceed and
     * rejects the instance outright, on its own initiative — same terminal
     * effect as a human {@link #reject}, but not gated by {@code
     * resolverService.canAct} since there is no acting assignee here.
     * Rejects whichever task is CURRENT at the moment of the call.
     * <p>
     * Idempotent-safe no-op (logged at WARN, never throws) when no PENDING
     * instance is found for this business ref — same contract as {@link
     * #completeCheckpointByKey} — so the caller's own transaction (e.g.
     * saving a no-show) never rolls back just because the approval flow
     * already ended some other way.
     */
    @Transactional
    public void rejectByBusinessRef(String businessRefType, Long businessRefId, String reason,
                                    String userId, String username) {
        Optional<SesApprovalInstance> instanceOpt = instanceRepository
                .findByBusinessRefTypeAndBusinessRefIdAndStatus(businessRefType, businessRefId, ApprovalStatus.PENDING)
                .stream().max(Comparator.comparing(SesApprovalInstance::getInstanceId));
        if (!instanceOpt.isPresent()) {
            log.warn("rejectByBusinessRef: no PENDING instance for {}#{}", businessRefType, businessRefId);
            return;
        }
        SesApprovalInstance instance = instanceOpt.get();
        SesApprovalTask task = currentTask(instance);

        task.setStatus(TaskStatus.REJECTED);
        task.setCompletedBy(userId);
        task.setCompletedDate(new Date());
        task.setNote(reason);
        taskRepository.save(task);

        instance.setStatus(ApprovalStatus.REJECTED);
        instance.setCurrentTaskId(task.getTaskId());
        instanceRepository.save(instance);

        logHistory(instance.getInstanceId(), task.getTaskId(), HistoryAction.REJECT, userId, username,
                task.getStepOrder(), task.getStepOrder(), reason);

        notifyOutcomeListeners(instance);
    }

    /**
     * Read-only guard for a business module UI (e.g. "set schedule" form) to
     * check BEFORE letting a user act, so a checkpoint tied to a future step
     * can't be satisfied ahead of time — checkpoints are frozen PENDING for
     * every step at trigger() time, not just the current one, so PENDING
     * alone is not enough signal; this also checks the task is the
     * instance's CURRENT task.
     */
    @Transactional(readOnly = true)
    public CheckpointReachability checkCheckpoint(String businessRefType, Long businessRefId, String checkpointKey) {
        // A business ref can carry more than one instance over time (a
        // rejected/cancelled instance followed by a resubmission), and
        // PENDING is the only non-terminal ApprovalStatus, so the current
        // one — if any — is the latest PENDING instance, not just "the"
        // instance for this ref.
        Optional<SesApprovalInstance> instanceOpt =
                instanceRepository.findByBusinessRefTypeAndBusinessRefIdAndStatus(
                        businessRefType, businessRefId, ApprovalStatus.PENDING)
                        .stream().max(Comparator.comparing(SesApprovalInstance::getInstanceId));
        if (!instanceOpt.isPresent()) {
            return CheckpointReachability.NOT_FOUND;
        }
        SesApprovalInstance instance = instanceOpt.get();
        List<SesApprovalTask> tasks = taskRepository.findByInstanceIdOrderByStepOrderAsc(instance.getInstanceId());
        for (SesApprovalTask task : tasks) {
            Optional<SesApprovalTaskCheckpoint> checkpointOpt =
                    checkpointRepository.findFirstByTaskIdAndCheckpointKey(task.getTaskId(), checkpointKey);
            if (!checkpointOpt.isPresent()) {
                continue;
            }
            SesApprovalTaskCheckpoint checkpoint = checkpointOpt.get();
            if (checkpoint.getStatus() == TaskCheckpointStatus.DONE) {
                return CheckpointReachability.ALREADY_DONE;
            }
            if (checkpoint.getStatus() == TaskCheckpointStatus.SKIPPED) {
                return CheckpointReachability.ALREADY_SKIPPED;
            }
            // PENDING: only "reachable" if this is the task currently active.
            boolean isCurrent = task.getTaskId().equals(instance.getCurrentTaskId());
            return isCurrent ? CheckpointReachability.ACTIVE : CheckpointReachability.NOT_YET_REACHED;
        }
        return CheckpointReachability.NOT_FOUND;
    }

    public enum CheckpointReachability {
        NOT_FOUND, NOT_YET_REACHED, ACTIVE, ALREADY_DONE, ALREADY_SKIPPED
    }

    /**
     * All instances currently blocked waiting on {@code checkpointKey} — the
     * checkpoint is PENDING and its task is the instance's CURRENT task.
     * Lets a business module (e.g. scheduling) build its own admin queue of
     * "requests that just reached my gate" without polling every instance
     * one by one through {@link #checkCheckpoint}. businessRefType filters
     * out any other module that happens to reuse the same checkpoint key.
     */
    @Transactional(readOnly = true)
    public List<SesApprovalInstance> findActiveByCheckpointKey(String businessRefType, String checkpointKey) {
        List<SesApprovalTaskCheckpoint> pending =
                checkpointRepository.findByCheckpointKeyAndStatus(checkpointKey, TaskCheckpointStatus.PENDING);
        if (pending.isEmpty()) {
            return new ArrayList<>();
        }

        // Three queries regardless of how many checkpoints are pending. The
        // per-checkpoint shape (findById task + findById instance) ran once
        // per ScheduleType from /scheduling/queue, so the admin queue paid it
        // twice over: 48 queries measured for one render.
        Map<Long, SesApprovalTask> taskById = new HashMap<>();
        for (SesApprovalTask t : InChunks.flatMap(
                pending.stream().map(SesApprovalTaskCheckpoint::getTaskId).collect(Collectors.toList()),
                taskRepository::findAllById)) {
            taskById.put(t.getTaskId(), t);
        }
        Map<Long, SesApprovalInstance> instanceById = new HashMap<>();
        for (SesApprovalInstance i : InChunks.flatMap(
                taskById.values().stream().map(SesApprovalTask::getInstanceId).collect(Collectors.toList()),
                instanceRepository::findAllById)) {
            instanceById.put(i.getInstanceId(), i);
        }

        List<SesApprovalInstance> result = new ArrayList<>();
        for (SesApprovalTaskCheckpoint cp : pending) {
            SesApprovalTask task = taskById.get(cp.getTaskId());
            if (task == null) {
                continue;
            }
            SesApprovalInstance instance = instanceById.get(task.getInstanceId());
            if (instance == null || !businessRefType.equals(instance.getBusinessRefType())
                    || instance.getStatus() != ApprovalStatus.PENDING
                    || !task.getTaskId().equals(instance.getCurrentTaskId())) {
                continue;
            }
            result.add(instance);
        }
        return result;
    }

    // ---------------------------------------------------------------
    // Requestor notifications driven by a business module
    // ---------------------------------------------------------------

    /**
     * Mails the requestor of a business record about something their module
     * just did — scheduling is the first caller. The module knows what
     * happened; only this module knows which instance the record belongs to
     * and how to reach its requestor, which is exactly the split
     * {@link #completeCheckpointByKey} already uses.
     *
     * <p>A record with no approval instance is a silent no-op: informing the
     * requestor is a nicety, never a reason to fail the action that triggered
     * it. The mail is queued, not sent, so this adds no SMTP latency or
     * failure mode to the caller's transaction.</p>
     */
    @Transactional
    public void notifyRequestorInfo(String businessRefType, Long businessRefId, ApprovalStatus palette,
                                    String headline, String intro, String statusLabel,
                                    Map<String, String> extraDetail, String note, String mailKeySuffix) {
        SesApprovalInstance instance = instanceRepository
                .findFirstByBusinessRefTypeAndBusinessRefIdOrderByInstanceIdDesc(businessRefType, businessRefId)
                .orElse(null);
        if (instance == null) {
            log.warn("notifyRequestorInfo: no approval instance for {} #{} — '{}' not sent",
                    businessRefType, businessRefId, headline);
            return;
        }
        approvalMailService.sendRequestorInfoEmail(instance, palette, headline, intro, statusLabel,
                extraDetail, note, mailKeySuffix);
    }

    // ---------------------------------------------------------------
    // Read-only summary for business screens
    // ---------------------------------------------------------------

    /**
     * Everything {@code approval/fragments/approval-summary :: card} needs,
     * ready to splat into a model — the same way a
     * {@code PaymentBusinessDescriptor}'s detailModel is consumed.
     *
     * <p>Exists so a business screen can answer "who has already approved
     * this, and where is it stuck" without reimplementing the assembly. The
     * payment verification page had none of this: it showed an officer a
     * proof of payment with no sight of the approval flow it belongs to.</p>
     *
     * <p>Returns {@code instance = null} (not an empty map) when the record
     * was never routed through a chain, so the fragment can render its own
     * "no approval flow yet" line and callers need no branch.</p>
     */
    @Transactional(readOnly = true)
    public Map<String, Object> summaryModel(String businessRefType, Long businessRefId) {
        SesApprovalInstance instance = instanceRepository
                .findFirstByBusinessRefTypeAndBusinessRefIdOrderByInstanceIdDesc(businessRefType, businessRefId)
                .orElse(null);
        return summaryModel(instance);
    }

    /** As above, for a caller that already has the instance in hand. */
    @Transactional(readOnly = true)
    public Map<String, Object> summaryModel(SesApprovalInstance instance) {
        Map<String, Object> model = new LinkedHashMap<>();
        model.put("instance", instance);
        if (instance == null) {
            model.put("tasks", Collections.emptyList());
            model.put("checkpointsByTask", Collections.emptyMap());
            model.put("assigneeNamesByTask", Collections.emptyMap());
            model.put("signoffsByTask", Collections.emptyMap());
            model.put("approverTitlesByTask", Collections.emptyMap());
            model.put("approverDepartmentsByTask", Collections.emptyMap());
            model.put("completedByNames", Collections.emptyMap());
            model.put("history", Collections.emptyList());
            model.put("historyActorNames", Collections.emptyMap());
            return model;
        }

        List<SesApprovalTask> tasks = getTasks(instance.getInstanceId());
        List<Long> taskIds = tasks.stream().map(SesApprovalTask::getTaskId).collect(Collectors.toList());

        // One batched read of the frozen assignee rows, reused twice below:
        // once to name who a step was aimed at, once to name who closed it.
        // Previously resolveCompletedByDisplay(t) issued its own
        // getTaskAssignees(taskId) per step.
        Map<Long, List<SesApprovalTaskAssignee>> frozenByTask = new LinkedHashMap<>();
        if (!taskIds.isEmpty()) {
            for (SesApprovalTaskAssignee a : taskAssigneeRepository.findByTaskIdIn(taskIds)) {
                frozenByTask.computeIfAbsent(a.getTaskId(), k -> new ArrayList<>()).add(a);
            }
        }

        // Per-person sign-offs of every consensus step, batched the same way
        // the frozen assignees are. An ANY step simply has no rows, so the map
        // entry is an empty list and the route table renders it as an ordinary
        // single-approver row.
        //
        // Built BEFORE buildAssigneeNames, which needs it: "did the requestor
        // name anyone?" is the question that decides whether a SECURITY_STAFF
        // step was skipped, and these rows are where that answer lives.
        // Reading it from here costs nothing; the per-task count query would
        // add one round trip per step of every card.
        Map<Long, List<SesApprovalTaskSignoff>> signoffsByTask = new LinkedHashMap<>();
        if (!taskIds.isEmpty()) {
            for (SesApprovalTaskSignoff so : signoffRepository.findByTaskIdIn(taskIds)) {
                signoffsByTask.computeIfAbsent(so.getTaskId(), k -> new ArrayList<>()).add(so);
            }
        }

        Map<Long, List<String>> assigneeNamesByTask =
                buildAssigneeNames(instance, tasks, frozenByTask, signoffsByTask);

        Map<Long, List<SesApprovalTaskCheckpoint>> checkpointsByTask = new LinkedHashMap<>();
        Map<Long, String> completedByNames = new LinkedHashMap<>();
        Map<Long, String> approverTitlesByTask = new LinkedHashMap<>();
        Map<Long, String> approverDepartmentsByTask = new LinkedHashMap<>();
        for (SesApprovalTask t : tasks) {
            checkpointsByTask.put(t.getTaskId(), getCheckpoints(t.getTaskId()));
            completedByNames.put(t.getTaskId(), resolveCompletedByDisplay(t,
                    frozenByTask.getOrDefault(t.getTaskId(), Collections.emptyList())));
            approverTitlesByTask.put(t.getTaskId(), approverTitle(t,
                    frozenByTask.getOrDefault(t.getTaskId(), Collections.emptyList())));
            approverDepartmentsByTask.put(t.getTaskId(), approverDepartment(t,
                    frozenByTask.getOrDefault(t.getTaskId(), Collections.emptyList())));
        }

        List<SesApprovalHistory> history = getHistory(instance.getInstanceId());
        Map<Long, String> historyActorNames = new LinkedHashMap<>();
        for (SesApprovalHistory h : history) {
            String ref = h.getActorEmplId() != null ? h.getActorEmplId() : h.getActorUserId();
            historyActorNames.put(h.getHistoryId(), userDisplayNameService.displayName(ref));
        }

        model.put("tasks", tasks);
        model.put("visibleTasks", visibleTasks(instance, tasks, signoffsByTask));
        model.put("checkpointsByTask", checkpointsByTask);
        model.put("assigneeNamesByTask", assigneeNamesByTask);
        model.put("signoffsByTask", signoffsByTask);
        model.put("approverTitlesByTask", approverTitlesByTask);
        model.put("approverDepartmentsByTask", approverDepartmentsByTask);
        model.put("completedByNames", completedByNames);
        model.put("history", history);
        model.put("historyActorNames", historyActorNames);
        return model;
    }

    /**
     * The steps worth showing a reader: everything except a step nobody was
     * ever asked about.
     *
     * <p>Two different things both land on {@code TaskStatus.SKIPPED}, and
     * only one of them is noise:</p>
     * <ul>
     *   <li><b>A configured skip.</b> The request type switched the step off
     *       ({@code needs_mgr_approval_flag}, {@code needs_proc_approval_flag})
     *       or the requestor named nobody for additional routing. Nobody was
     *       ever asked, nobody decided anything, and the row exists only
     *       because the chain has a slot for it. <b>Hidden.</b></li>
     *   <li><b>A reviewer's skip, or a cancellation.</b> Someone passed the
     *       step over, or the whole instance was cancelled and its remaining
     *       tasks were marked SKIPPED with the cancel reason. Those are
     *       records of a decision. <b>Shown</b>, with their existing
     *       "Dilewati" badge — hiding them would leave an approval whose
     *       outcome has no visible explanation.</li>
     * </ul>
     *
     * <p>The test is {@link #isSkippedByConfiguration}, the rule the engine
     * already uses to decide which steps to skip in the first place, and which
     * {@link #isReviewerDecision} already relies on for the same distinction.
     * Writing a second "is this row interesting" rule here would be two rules
     * that can disagree about the same row.</p>
     *
     * <p>Returned as a separate list rather than filtering {@code tasks} in
     * place: {@code tasks} is what the action panel and the checkpoint lookups
     * are keyed on, and a template that iterates the filtered list must still
     * be able to resolve a current task that happens to be hidden.</p>
     */
    private List<SesApprovalTask> visibleTasks(SesApprovalInstance instance, List<SesApprovalTask> tasks,
                                               Map<Long, List<SesApprovalTaskSignoff>> signoffsByTask) {
        List<SesApprovalTask> visible = new ArrayList<>();
        for (SesApprovalTask t : tasks) {
            boolean hasEvaluators = StepRole.SECURITY_STAFF != t.getStepRole()
                    || !signoffsByTask.getOrDefault(t.getTaskId(), Collections.emptyList()).isEmpty();
            if (t.getStatus() == TaskStatus.SKIPPED
                    && isSkippedByConfiguration(t, instance, hasEvaluators)) {
                continue;
            }
            visible.add(t);
        }
        return visible;
    }

    /**
     * The bold line at the top of the route table's Approver cell: the JOB
     * this step is aimed at, not the person filling it.
     *
     * <p>Prefers a frozen position title, because that is what the step was
     * actually configured against and it stays true after the holder changes.
     * Falls back to the step name, which is the only thing a step with no
     * position assignee has — {@code REQUESTOR_MANAGER} resolves to a
     * different job for every requestor, so there is no single title to print.</p>
     *
     * <p>{@code APP_POSITION_VIEW.POS_TITLE} is not stored on
     * {@code ses_approval_task_assignee}, so this reads the title live for a
     * position assignee. That is a deliberate inconsistency with the rest of
     * the freeze: a job title is the kind of thing that gets corrected for
     * spelling, and showing the corrected one is right, whereas showing a
     * different PERSON would not be.</p>
     */
    private String approverTitle(SesApprovalTask task, List<SesApprovalTaskAssignee> frozen) {
        for (SesApprovalTaskAssignee a : frozen) {
            String posId = a.getPosId() == null ? null : a.getPosId().trim();
            if (posId == null || posId.isEmpty()) {
                continue;
            }
            for (AppPositionView p : positionViewRepository.findByPosId(posId)) {
                if (p.getPosTitle() != null && !p.getPosTitle().trim().isEmpty()) {
                    return p.getPosTitle().trim().toUpperCase();
                }
            }
            return posId;
        }
        return task.getStepName() == null ? "" : task.getStepName().toUpperCase();
    }

    private String approverDepartment(SesApprovalTask task, List<SesApprovalTaskAssignee> frozen) {
        for (SesApprovalTaskAssignee a : frozen) {
            String posId = a.getPosId() == null ? null : a.getPosId().trim();
            if (posId != null && !posId.isEmpty()) {
                for (AppPositionView p : positionViewRepository.findByPosId(posId)) {
                    if (p.getDeptDesc() != null && !p.getDeptDesc().trim().isEmpty()) {
                        return p.getDeptDesc().trim().toUpperCase();
                    }
                }
            }
            String emplId = a.getEmplId() == null ? null : a.getEmplId().trim();
            if (emplId != null && !emplId.isEmpty()) {
                Optional<AppEmpLkpView> emp = empLkpViewRepository.findByEmplid(emplId);
                if (emp.isPresent() && emp.get().getDeptDesc() != null && !emp.get().getDeptDesc().trim().isEmpty()) {
                    return emp.get().getDeptDesc().trim().toUpperCase();
                }
            }
        }
        return null;
    }

    // ---------------------------------------------------------------
    // Printed document — signature blocks
    // ---------------------------------------------------------------

    /**
     * One printable signature block per well-known approval role, in the order
     * a permit sheet shows them.
     *
     * <p>A block is a <b>name and a job title above an empty space</b>. The
     * sheet is signed in ink after it is printed; SES does not capture, store
     * or render a signature image anywhere. What the application asserts is
     * the approval itself — who decided, in what role, and when — and that is
     * what the block prints and what {@code /verify/{requestNo}} confirms.</p>
     *
     * <p>Lives here rather than in the request module because it reads
     * approval tasks and roles, which the request module is not allowed to
     * import. It returns a plain carrier instead.</p>
     *
     * <p>Only the three roles that sign the sheet are returned; the
     * requestor's own block is added by the caller, which knows who they are.
     * A role the chain does not contain is omitted entirely — a sheet should
     * not show a signature line for an approval that was never part of this
     * flow. A role the chain DOES contain but the submission skipped is
     * returned with {@code skipped = true}, so the sheet can say so instead of
     * leaving a reader wondering why a line is blank.</p>
     *
     * <p><b>{@link StepRole#PROCUREMENT_SUPERVISOR} is deliberately absent.</b>
     * Procurement runs as two serial approvals, but the permit sheet carries
     * four blocks by agreed format — Manager Security, Procurement, Manager
     * Pemohon, Pemohon — and the procurement block belongs to the rung that
     * closes the leg. Adding a fifth block would change a document format the
     * business signed off on, and nothing is hidden by leaving it out: the
     * Approval Route table on screen lists every step, supervisor included,
     * with who acted and when.</p>
     */
    @Transactional(readOnly = true)
    public List<SignatureSlot> signatureSlots(Long instanceId) {
        List<SignatureSlot> slots = new ArrayList<>();
        SesApprovalInstance instance = instanceRepository.findById(instanceId).orElse(null);
        if (instance == null) {
            return slots;
        }
        List<SesApprovalTask> tasks = getTasks(instanceId);

        addSlot(slots, instance, tasks, StepRole.SECURITY_MANAGER, "Menyetujui,", "Manager Security");
        addSlot(slots, instance, tasks, StepRole.PROCUREMENT_MANAGER, "Mengetahui,", "Procurement");
        addSlot(slots, instance, tasks, StepRole.REQUESTOR_MANAGER, "Mengetahui,", "Manager Pemohon");
        return slots;
    }

    private void addSlot(List<SignatureSlot> slots, SesApprovalInstance instance,
                         List<SesApprovalTask> tasks, StepRole role, String caption, String label) {
        SesApprovalTask task = tasks.stream()
                .filter(t -> role == t.getStepRole())
                .findFirst().orElse(null);
        if (task == null) {
            return;
        }
        boolean skipped = task.getStatus() == TaskStatus.SKIPPED;
        String signer = skipped ? null : resolveCompletedByDisplay(task,
                taskAssigneeRepository.findByTaskIdOrderByTaskAssigneeIdAsc(task.getTaskId()));
        slots.add(new SignatureSlot(caption, label, signer, skipped));
    }

    /** One signature block of a printed document. See {@link #signatureSlots}. */
    @Getter
    @AllArgsConstructor
    public static class SignatureSlot {
        private final String caption;
        private final String roleLabel;
        /** Who approved this step, printed under the ruled line they sign on. */
        private final String signerName;
        private final boolean skipped;
    }

    /** One approved signer block for printed documents. */
    @Getter
    @AllArgsConstructor
    public static class ApprovedSigner {
        private final String signerName;
        private final String roleTitle;
    }

    /**
     * All approvers who actually completed/approved a step in this instance,
     * in step order. Includes consensus signoffs if any.
     */
    @Transactional(readOnly = true)
    public List<ApprovedSigner> approvedSigners(Long instanceId) {
        List<ApprovedSigner> signers = new ArrayList<>();
        if (instanceId == null) {
            return signers;
        }
        SesApprovalInstance instance = instanceRepository.findById(instanceId).orElse(null);
        if (instance == null) {
            return signers;
        }
        List<SesApprovalTask> tasks = getTasks(instanceId);
        List<Long> taskIds = tasks.stream().map(SesApprovalTask::getTaskId).collect(Collectors.toList());
        Map<Long, List<SesApprovalTaskAssignee>> frozenByTask = new LinkedHashMap<>();
        if (!taskIds.isEmpty()) {
            for (SesApprovalTaskAssignee a : taskAssigneeRepository.findByTaskIdIn(taskIds)) {
                frozenByTask.computeIfAbsent(a.getTaskId(), k -> new ArrayList<>()).add(a);
            }
        }

        for (SesApprovalTask t : tasks) {
            if (t.getStatus() != TaskStatus.APPROVED) {
                continue;
            }
            List<SesApprovalTaskAssignee> frozen = frozenByTask.getOrDefault(t.getTaskId(), Collections.emptyList());
            String title = approverTitle(t, frozen);

            List<SesApprovalTaskSignoff> signoffs = signoffRepository.findByTaskIdOrdered(t.getTaskId());
            List<SesApprovalTaskSignoff> approvedSignoffs = signoffs.stream()
                    .filter(s -> s.getStatus() == SignoffStatus.APPROVED)
                    .collect(Collectors.toList());

            if (!approvedSignoffs.isEmpty()) {
                for (SesApprovalTaskSignoff so : approvedSignoffs) {
                    String name = so.getDisplayName() != null && !so.getDisplayName().trim().isEmpty()
                            ? so.getDisplayName().trim()
                            : userDisplayNameService.displayName(so.getEmplId());
                    signers.add(new ApprovedSigner(name, title));
                }
            } else {
                String signerName = resolveCompletedByDisplay(t, frozen);
                if (signerName != null && !signerName.trim().isEmpty()) {
                    signers.add(new ApprovedSigner(signerName, title));
                }
            }
        }
        return signers;
    }

    /**
     * Resolves the position title of the requestor's manager (e.g. "MANAGER, PROCUREMENT (EH1000)").
     * Falls back to "Manager Pemohon" if unresolvable.
     */
    @Transactional(readOnly = true)
    public String resolveRequestorManagerPosition(Long instanceId, String requestorUsername) {
        if (instanceId != null) {
            List<SesApprovalTask> tasks = getTasks(instanceId);
            for (SesApprovalTask task : tasks) {
                if (task.getStepRole() == StepRole.REQUESTOR_MANAGER) {
                    List<SesApprovalTaskAssignee> assignees =
                            taskAssigneeRepository.findByTaskIdOrderByTaskAssigneeIdAsc(task.getTaskId());
                    for (SesApprovalTaskAssignee a : assignees) {
                        String posId = a.getPosId() != null ? a.getPosId().trim() : null;
                        if (posId != null && !posId.isEmpty()) {
                            String formatted = formatPosition(posId);
                            if (formatted != null) {
                                return formatted;
                            }
                        }
                    }
                }
            }
        }

        if (requestorUsername != null && !requestorUsername.trim().isEmpty()) {
            List<AppPositionView> positions = positionViewRepository.findByEmplId(requestorUsername.trim());
            for (AppPositionView pv : positions) {
                String supPosId = pv.getSuperiorId() != null ? pv.getSuperiorId().trim() : null;
                if (supPosId != null && !supPosId.isEmpty()) {
                    String formatted = formatPosition(supPosId);
                    if (formatted != null) {
                        return formatted;
                    }
                }
                String deptPosId = pv.getDeptId() != null ? pv.getDeptId().trim() : null;
                if (deptPosId != null && !deptPosId.isEmpty()) {
                    String formatted = formatPosition(deptPosId);
                    if (formatted != null) {
                        return formatted;
                    }
                }
            }
        }

        return "Manager Pemohon";
    }

    private String formatPosition(String posId) {
        List<AppPositionView> pvList = positionViewRepository.findByPosId(posId);
        for (AppPositionView p : pvList) {
            if (p.getPosTitle() != null && !p.getPosTitle().trim().isEmpty()) {
                String title = p.getPosTitle().trim().toUpperCase();
                if (title.contains(posId)) {
                    return title;
                }
                return title + " (" + posId + ")";
            }
        }
        return posId;
    }

    // ---------------------------------------------------------------
    // Cancel
    // ---------------------------------------------------------------

    /**
     * Cancels a still-PENDING instance: remaining PENDING tasks are marked
     * SKIPPED, the instance becomes CANCELLED, and a CANCEL history row is
     * written. A no-op (not an error) when the instance is already
     * terminal — cancelling twice is harmless.
     *
     * Cancellation is NOT a completion outcome: ApprovalOutcomeListeners
     * are deliberately NOT notified here — only APPROVED/REJECTED are
     * "outcomes" a business module needs to react to; a request that
     * cancelled itself already knows it's cancelled.
     */
    @Transactional
    public void cancel(Long instanceId, String actorUsername, String reason) {
        SesApprovalInstance instance = instanceRepository.findById(instanceId)
                .orElseThrow(() -> new IllegalArgumentException("Approval instance not found: " + instanceId));
        if (instance.getStatus() != ApprovalStatus.PENDING) {
            return;
        }
        List<SesApprovalTask> tasks = taskRepository.findByInstanceIdOrderByStepOrderAsc(instanceId);
        for (SesApprovalTask t : tasks) {
            if (t.getStatus() == TaskStatus.PENDING) {
                t.setStatus(TaskStatus.SKIPPED);
                t.setNote(reason);
                taskRepository.save(t);
            }
        }
        instance.setStatus(ApprovalStatus.CANCELLED);
        instanceRepository.save(instance);
        logHistory(instanceId, instance.getCurrentTaskId(), HistoryAction.CANCEL,
                actorUsername, actorUsername, null, null, reason);
    }

    // ---------------------------------------------------------------
    // Advance / skip
    // ---------------------------------------------------------------

    private void advance(SesApprovalInstance instance) {
        List<SesApprovalTask> tasks = taskRepository.findByInstanceIdOrderByStepOrderAsc(instance.getInstanceId());
        List<SesApprovalTask> pending = tasks.stream()
                .filter(t -> t.getStatus() == TaskStatus.PENDING)
                .collect(Collectors.toList());

        if (pending.isEmpty()) {
            // Every task settled -> instance APPROVED.
            instance.setStatus(ApprovalStatus.APPROVED);
            instance.setCurrentTaskId(null);
            instanceRepository.save(instance);
            logHistory(instance.getInstanceId(), null, HistoryAction.COMPLETE,
                    instance.getRequestorUserId(), null, null, null,
                    "All steps approved; instance completed");
            notifyOutcomeListeners(instance);
            return;
        }

        List<SesApprovalTask> group = nextGroup(pending);
        int nextGroup = groupOf(group.get(0));

        // Still "the current task" for anything that reads it — detail screens
        // highlighting tahap saat ini, and rejectByBusinessRef picking a task
        // to attribute a system rejection to. With a parallel group it can only
        // ever name one of them, which is why SES_APPROVAL_PENDING_VIEW no
        // longer filters on it (sql/55).
        SesApprovalTask lead = group.get(0);
        if (!lead.getTaskId().equals(instance.getCurrentTaskId())) {
            instance.setCurrentTaskId(lead.getTaskId());
            instanceRepository.save(instance);
            logHistory(instance.getInstanceId(), lead.getTaskId(), HistoryAction.ADVANCE,
                    instance.getRequestorUserId(), null, lead.getStepOrder(), lead.getStepOrder(),
                    group.size() == 1
                            ? "Moved to step " + lead.getStepOrder() + " (" + lead.getStepName() + ")"
                            : "Moved to parallel group " + nextGroup + ": " + group.stream()
                                    .map(t -> t.getStepOrder() + " (" + t.getStepName() + ")")
                                    .collect(Collectors.joining(", ")));
        }

        // activateTask may SKIP its task, which calls advance() again. Iterate
        // over a snapshot and re-check status: by the time a later member of
        // this group is reached, a recursive advance may already have settled
        // it, and activating a task twice would re-resolve and duplicate its
        // frozen assignee rows.
        for (SesApprovalTask task : group) {
            SesApprovalTask fresh = taskRepository.findById(task.getTaskId()).orElse(null);
            if (fresh == null || fresh.getStatus() != TaskStatus.PENDING) {
                continue;
            }
            if (!taskAssigneeRepository.findByTaskIdOrderByTaskAssigneeIdAsc(fresh.getTaskId()).isEmpty()) {
                continue; // already activated by an earlier pass
            }
            activateTask(instance, fresh, instance.getRequestorUserId());
        }
    }

    // ---------------------------------------------------------------
    // "Ditujukan Kepada" — who each step is aimed at
    // ---------------------------------------------------------------

    /**
     * taskId -&gt; the people this step is (or would be) aimed at, for the
     * approval summary card.
     *
     * <p>Two different sources, deliberately:</p>
     * <ul>
     *   <li><b>Already-activated tasks</b> answer from the FROZEN
     *       {@code ses_approval_task_assignee} rows. That is the historically
     *       honest answer and the only one that survives the holder of a
     *       position changing afterwards — re-resolving an old step live would
     *       quietly rewrite who was asked.</li>
     *   <li><b>Not-yet-activated tasks</b> have no frozen rows yet, only frozen
     *       DEFINITIONS, so they are previewed through
     *       {@link ApprovalResolverService#previewAssignees}, which resolves
     *       exactly like activation does but persists nothing.</li>
     * </ul>
     *
     * <p>A task skipped by the configured manager-step rule returns an EMPTY
     * list: "nobody, by configuration". Previewing a step nobody will ever be
     * asked to act on would put a real person's name against a step that did
     * not run.</p>
     *
     * <p>Cost: two batched queries for the whole instance (the caller already
     * made one of them), plus at most one resolver pass per not-yet-activated
     * step. Within a call, REQUESTOR_MANAGER/REPORT_TO resolution is memoised —
     * it depends only on the requestor, not on the step — and POSITION lookups
     * are memoised by {@code posId}, so two steps naming the same position cost
     * one lookup. Each preview is wrapped: a read-only detail page must not
     * return 500 because one org-chart row went missing.</p>
     */
    private Map<Long, List<String>> buildAssigneeNames(SesApprovalInstance instance,
                                                       List<SesApprovalTask> tasks,
                                                       Map<Long, List<SesApprovalTaskAssignee>> frozenByTask,
                                                       Map<Long, List<SesApprovalTaskSignoff>> signoffsByTask) {
        Map<Long, List<String>> result = new LinkedHashMap<>();
        if (tasks.isEmpty()) {
            return result;
        }

        // Which tasks still need a live preview? Everything with no frozen row
        // and no reason to stay blank.
        List<Long> needPreview = new ArrayList<>();
        for (SesApprovalTask t : tasks) {
            boolean hasEvaluators = !signoffsByTask
                    .getOrDefault(t.getTaskId(), Collections.emptyList()).isEmpty();
            if (isSkippedByConfiguration(t, instance, hasEvaluators)) {
                // Nobody, by configuration. Previewing a step nobody will ever
                // be asked to act on would put a real person's name against a
                // step that did not run.
                result.put(t.getTaskId(), Collections.emptyList());
                continue;
            }
            List<SesApprovalTaskAssignee> frozen = frozenByTask.get(t.getTaskId());
            if (frozen != null && !frozen.isEmpty()) {
                result.put(t.getTaskId(), namesOfFrozen(frozen));
            } else {
                needPreview.add(t.getTaskId());
            }
        }
        if (needPreview.isEmpty()) {
            return orderedBy(tasks, result);
        }

        Map<Long, List<SesApprovalTaskAssigneeDef>> defsByTask = new LinkedHashMap<>();
        for (SesApprovalTaskAssigneeDef d : taskAssigneeDefRepository.findByTaskIdIn(needPreview)) {
            defsByTask.computeIfAbsent(d.getTaskId(), k -> new ArrayList<>()).add(d);
        }

        Map<String, List<String>> previewCache = new HashMap<>();
        for (Long taskId : needPreview) {
            List<SesApprovalTaskAssigneeDef> defs = defsByTask.get(taskId);
            if (defs == null || defs.isEmpty()) {
                // Checkpoint-only step: no human is ever asked.
                result.put(taskId, Collections.emptyList());
                continue;
            }
            List<String> names = new ArrayList<>();
            for (SesApprovalTaskAssigneeDef d : defs) {
                names.addAll(previewOne(d, instance.getRequestorUserId(), previewCache));
            }
            result.put(taskId, dedupe(names));
        }
        return orderedBy(tasks, result);
    }

    /** Names off frozen rows: the stored display name, falling back to the badge. */
    private List<String> namesOfFrozen(List<SesApprovalTaskAssignee> frozen) {
        List<String> names = new ArrayList<>();
        List<String> unresolved = new ArrayList<>();
        for (SesApprovalTaskAssignee a : frozen) {
            if (a.getDisplayName() != null && !a.getDisplayName().trim().isEmpty()) {
                names.add(a.getDisplayName().trim());
            } else if (a.getEmplId() != null && !a.getEmplId().trim().isEmpty()) {
                unresolved.add(a.getEmplId().trim());
            }
        }
        if (!unresolved.isEmpty()) {
            Map<String, String> resolved = userDisplayNameService.displayNames(unresolved);
            for (String ref : unresolved) {
                names.add(resolved.getOrDefault(ref, ref));
            }
        }
        return dedupe(names);
    }

    /**
     * Live preview of ONE frozen definition, memoised per call.
     *
     * <p>The cache key is what the answer actually depends on: a position id for
     * POSITION, the fixed user id for USER, and the assignee TYPE alone for
     * REQUESTOR_MANAGER / REPORT_TO — those resolve off the requestor, who is
     * the same for every step of the instance, so the manager ladder is walked
     * once no matter how many steps reference it.</p>
     */
    private List<String> previewOne(SesApprovalTaskAssigneeDef def, String requestorUsername,
                                    Map<String, List<String>> cache) {
        String key = def.getAssigneeType().name() + "|"
                + (def.getPosId() == null ? "" : def.getPosId()) + "|"
                + (def.getUserId() == null ? "" : def.getUserId());
        List<String> cached = cache.get(key);
        if (cached != null) {
            return cached;
        }

        List<String> names;
        try {
            SesApprovalAssignee carrier = new SesApprovalAssignee();
            carrier.setAssigneeType(def.getAssigneeType());
            carrier.setPosId(def.getPosId());
            carrier.setUserId(def.getUserId());
            // Never persisted — the same transient-carrier trick activateTask
            // uses, so ApprovalResolverService is called completely unchanged.
            names = namesOfFrozen(resolverService.previewAssignees(
                    Collections.singletonList(carrier), requestorUsername));
        } catch (RuntimeException ex) {
            // A missing APP_POSITION_VIEW row, a requestor with no position, a
            // broken org chart: all real, and none of them a reason for a
            // read-only detail page to return 500. Fall back to naming the
            // target generically.
            log.warn("Could not preview assignee {} (pos={}, user={}) for requestor {}: {}",
                    def.getAssigneeType(), def.getPosId(), def.getUserId(), requestorUsername, ex.toString());
            names = Collections.singletonList(genericLabel(def));
        }
        if (names.isEmpty()) {
            names = Collections.singletonList(genericLabel(def));
        }
        cache.put(key, names);
        return names;
    }

    /** What to show when a target cannot be named: the role, not a blank cell. */
    private static String genericLabel(SesApprovalTaskAssigneeDef def) {
        switch (def.getAssigneeType()) {
            case REQUESTOR_MANAGER:
                return "Manager Pemohon (belum dapat ditentukan)";
            case REPORT_TO:
                return "Atasan Langsung (belum dapat ditentukan)";
            case POSITION:
                return "Posisi " + (def.getPosId() == null ? "?" : def.getPosId()) + " (belum ada pemangku)";
            default:
                return "Pengguna " + (def.getUserId() == null ? "?" : def.getUserId());
        }
    }

    private static List<String> dedupe(List<String> names) {
        return new ArrayList<>(new LinkedHashSet<>(names));
    }

    /** Re-emits the map in step order, so the template iterates predictably. */
    private static Map<Long, List<String>> orderedBy(List<SesApprovalTask> tasks, Map<Long, List<String>> raw) {
        Map<Long, List<String>> ordered = new LinkedHashMap<>();
        for (SesApprovalTask t : tasks) {
            ordered.put(t.getTaskId(), raw.getOrDefault(t.getTaskId(), Collections.emptyList()));
        }
        return ordered;
    }

    /**
     * Resolves assignees for a newly-activated task and, if the step
     * resolves to a manager-skip, auto-advances past it.
     *
     * Reads from the FROZEN task_assignee_def snapshot (never the live
     * SES_APPROVAL_ASSIGNEE rows), mapping each def into a transient,
     * unpersisted SesApprovalAssignee so ApprovalResolverService — which is
     * NOT modified — can be called completely unchanged.
     */
    private void activateTask(SesApprovalInstance instance, SesApprovalTask task, String requestorUsername) {
        // Configured manager-step skip, checked BEFORE anything is resolved.
        //
        // Deliberately NOT routed through ApprovalResolverService's MANAGER_SKIP
        // marker, even though that path would give the downstream handling for
        // free: addSkipMarker persists ses_approval_task_assignee rows carrying
        // empl_id = the requestor. Those rows are what SES_APPROVAL_PENDING_VIEW
        // (sql/35) and APP_APPROVAL_VIEW (sql/36) join on, so the requestor
        // would show up in the approval inbox - and in the navbar badge - for
        // their own submission. Short-circuiting writes no assignee rows at all,
        // and ApprovalResolverService.decide already denies an empty assignee
        // list, so a hand-crafted POST to approve this task still hits
        // SecurityException in act(). Same safety, no polluted data.
        if (isSkippedByConfiguration(task, instance)) {
            skipTask(instance, task, requestorUsername, skipReason(task.getStepRole()));
            return;
        }

        // A consensus step takes its approvers from the frozen sign-off rows,
        // not from the chain. Those rows are also mirrored into
        // ses_approval_task_assignee, because that is the table
        // SES_APPROVAL_PENDING_VIEW resolves an inbox row against and the
        // table ApprovalResolverService.canAct reads — writing only the
        // sign-offs would leave the task invisible to everyone it is waiting
        // for, and unapprovable by all of them.
        if (CompletionRule.ALL == task.getCompletionRule()) {
            activateConsensusTask(instance, task);
            return;
        }

        List<SesApprovalTaskAssigneeDef> defs =
                taskAssigneeDefRepository.findByTaskIdOrderByOrderByAscTaskAssigneeDefIdAsc(task.getTaskId());
        if (defs.isEmpty()) {
            // No assignees configured — this step is checkpoint-only and can
            // never be approved by a human. It clears the moment every
            // checkpoint on it resolves; see autoAdvanceUnassignedTask.
            autoAdvanceUnassignedTask(task);
            return;
        }

        List<SesApprovalAssignee> transientDefs = new ArrayList<>();
        for (SesApprovalTaskAssigneeDef d : defs) {
            SesApprovalAssignee a = new SesApprovalAssignee();
            a.setStepId(task.getStepDefinitionId());
            a.setAssigneeType(d.getAssigneeType());
            a.setPosId(d.getPosId());
            a.setUserId(d.getUserId());
            // DO NOT persist — this must stay transient, it's only a
            // shape-compatible carrier for ApprovalResolverService.
            transientDefs.add(a);
        }

        List<SesApprovalTaskAssignee> resolved = resolverService.resolveTaskAssignees(task, transientDefs, requestorUsername);

        boolean allSkip = !resolved.isEmpty()
                && resolved.stream().allMatch(a -> a.getResolutionSource() == ResolutionSource.MANAGER_SKIP);

        if (allSkip) {
            // Requestor is a manager / above a manager: skip this step.
            skipTask(instance, task, requestorUsername,
                    "Auto-skipped: requestor is manager-level (no direct manager approval needed)");
        } else if (!resolved.isEmpty()) {
            approvalMailService.sendTaskAssignmentEmail(instance, task, resolved);
            resolved.forEach(a -> invalidateInboxCache(a.getEmplId()));
        }
    }

    /**
     * Opens a {@link CompletionRule#ALL} step: mirrors its still-PENDING
     * sign-off rows into {@code ses_approval_task_assignee} and notifies
     * everyone it is waiting for.
     *
     * <p>Mirrored rather than joined because the rest of the engine, the inbox
     * view and the mail service all speak {@code ses_approval_task_assignee}.
     * Teaching every one of them about a second source of approvers would be a
     * far wider change than copying four fields here, and would leave two
     * places that could disagree about who may act.</p>
     *
     * <p>{@code resolutionSource} is {@code STATIC_USER}: these people were
     * named outright, exactly like a {@code USER} assignee, and are NOT
     * re-resolved from a position. That matters for
     * {@code ApprovalResolverService.decide}, which treats a row carrying a
     * {@code pos_id} as live-resolved and a row without one as frozen —
     * frozen is correct here. The requestor picked these specific people;
     * substituting whoever holds their chair tomorrow would silently change
     * who was asked.</p>
     *
     * <p>Only PENDING sign-offs are mirrored. Re-activation cannot happen
     * today (advance() guards against it), but if it ever did, someone who had
     * already approved must not reappear in the queue.</p>
     */
    private void activateConsensusTask(SesApprovalInstance instance, SesApprovalTask task) {
        List<SesApprovalTaskSignoff> signoffs =
                signoffRepository.findByTaskIdOrdered(task.getTaskId());
        List<SesApprovalTaskAssignee> mirrored = new ArrayList<>();
        for (SesApprovalTaskSignoff s : signoffs) {
            if (s.getStatus() != SignoffStatus.PENDING) {
                continue;
            }
            SesApprovalTaskAssignee a = new SesApprovalTaskAssignee();
            a.setTaskId(task.getTaskId());
            a.setAssigneeType(AssigneeType.USER);
            a.setEmplId(s.getEmplId());
            a.setUserId(s.getUserId());
            a.setDisplayName(s.getDisplayName());
            a.setResolutionSource(ResolutionSource.STATIC_USER);
            mirrored.add(taskAssigneeRepository.save(a));
        }

        if (mirrored.isEmpty()) {
            // Nobody to wait for. activateTask() skips a SECURITY_STAFF step
            // with no sign-offs before it ever gets here, and trigger()
            // refuses any other ALL step at submit time — so reaching this
            // means the rows were removed underneath a running instance.
            // Failing loudly beats a task that silently waits forever.
            throw new IllegalStateException("Consensus step '" + task.getStepName()
                    + "' (task " + task.getTaskId() + ") has nobody to wait for.");
        }

        approvalMailService.sendTaskAssignmentEmail(instance, task, mirrored);
        mirrored.forEach(a -> invalidateInboxCache(a.getEmplId()));
    }

    /**
     * Marks one task SKIPPED, records why, and moves the instance on.
     *
     * <p>Extracted so the two reasons a step can be skipped without anyone
     * acting on it - the requestor is already manager-level, or the business
     * type opted out of manager approval entirely - write the same shape of
     * row and the same shape of history entry, differing only in the wording a
     * reader actually sees. That wording matters: both render in the
     * <b>Catatan</b> column of {@code approval-summary.html} beside the same
     * "DILEWATI" badge, and "the requestor outranks this step" and "this kind
     * of request does not use this step" are very different explanations to
     * give someone asking why a step did not run.</p>
     */
    private void skipTask(SesApprovalInstance instance, SesApprovalTask task, String actorUsername, String reason) {
        task.setStatus(TaskStatus.SKIPPED);
        task.setNote(reason);
        taskRepository.save(task);
        logHistory(instance.getInstanceId(), task.getTaskId(), HistoryAction.SKIP,
                actorUsername, actorUsername, task.getStepOrder(), task.getStepOrder(), reason);
        advance(instance);
    }

    /**
     * A step with zero assignees can never be approved by a human — the
     * only path off it is every checkpoint on it resolving (DONE/SKIPPED).
     * Called both at activation and after any checkpoint resolution
     * ({@link #completeCheckpoint}, {@link #skipCheckpoint}, {@link
     * #completeCheckpointByKey}) so the task auto-completes the instant its
     * last gate clears, instead of sitting PENDING forever with nobody able
     * to call {@link #approve}.
     * <p>
     * Guarded on the task being in the instance's currently ACTIVE GROUP:
     * {@link #completeCheckpointByKey} may resolve a checkpoint belonging to
     * a FUTURE step ahead of time (its documented early-satisfaction
     * behavior) — that must never auto-approve a step that hasn't been
     * reached yet, so this only fires once the step is actually active.
     * <p>
     * The guard used to be {@code task == instance.currentTaskId}, which a
     * parallel group breaks: {@code current_task_id} names only the group's
     * lead, so a checkpoint-only step sharing a group with it would never
     * auto-complete and the whole instance would stall. "Is this task in the
     * lowest group that still has PENDING tasks?" is the same question the
     * single-task engine was really asking.
     */
    private void autoAdvanceUnassignedTask(SesApprovalTask task) {
        if (task.getStatus() != TaskStatus.PENDING) {
            return;
        }
        SesApprovalInstance instance = instanceRepository.findById(task.getInstanceId()).orElse(null);
        if (instance == null || instance.getStatus() != ApprovalStatus.PENDING || !isInActiveGroup(task)) {
            return;
        }
        List<SesApprovalTaskAssigneeDef> defs =
                taskAssigneeDefRepository.findByTaskIdOrderByOrderByAscTaskAssigneeDefIdAsc(task.getTaskId());
        if (!defs.isEmpty()) {
            return;
        }
        List<SesApprovalTaskCheckpoint> pending =
                checkpointRepository.findByTaskIdAndStatus(task.getTaskId(), TaskCheckpointStatus.PENDING);
        if (!pending.isEmpty()) {
            log.info("Task {} has no assignees; waiting on {} pending checkpoint(s) before it can auto-complete",
                    task.getTaskId(), pending.size());
            return;
        }

        task.setStatus(TaskStatus.APPROVED);
        task.setNote("Auto-completed: step has no assignees, all checkpoint task(s) resolved");
        taskRepository.save(task);

        logHistory(instance.getInstanceId(), task.getTaskId(), HistoryAction.APPROVE,
                "SYSTEM", "SYSTEM", task.getStepOrder(), task.getStepOrder(),
                "Step auto-approved: no assignees configured, all checkpoint task(s) resolved");

        advance(instance);
    }

    // ---------------------------------------------------------------
    // Outcome notification
    // ---------------------------------------------------------------

    /**
     * Notifies every ApprovalOutcomeListener whose businessRefType matches
     * this instance, in the SAME transaction as the status transition.
     * Deliberately NOT wrapped in a try/catch that swallows exceptions: a
     * listener failure must roll the whole transaction back, so business
     * status can never drift from the approval outcome.
     */
    private void notifyOutcomeListeners(SesApprovalInstance instance) {
        outcomeListeners.forEach(listener -> {
            if (listener.businessRefType().equals(instance.getBusinessRefType())) {
                listener.onCompleted(instance.getBusinessRefId(), instance.getStatus());
            }
        });
    }

    // ---------------------------------------------------------------
    // Query helpers
    // ---------------------------------------------------------------

    /**
     * The task {@code current_task_id} names, or — when that is stale — the
     * lowest-order task still PENDING.
     *
     * <p>The fallback matters now that a group can hold several open tasks:
     * {@code current_task_id} names the group's lead, and if the lead settles
     * first while a sibling is still open, the pointer is pointing at a closed
     * task. Callers that need "some open task of this instance" (a
     * system-driven rejection, a checkpoint guard) should still get one.</p>
     *
     * <p>For anything driven by a PERSON, use
     * {@link #actionableTask(SesApprovalInstance, String, String)} instead —
     * it picks the task that user may actually act on, which with a parallel
     * group is often not the lead.</p>
     */
    private SesApprovalTask currentTask(SesApprovalInstance instance) {
        if (instance.getCurrentTaskId() != null) {
            SesApprovalTask named = taskRepository.findById(instance.getCurrentTaskId()).orElse(null);
            if (named != null && named.getStatus() == TaskStatus.PENDING) {
                return named;
            }
        }
        return taskRepository.findByInstanceIdOrderByStepOrderAsc(instance.getInstanceId()).stream()
                .filter(t -> t.getStatus() == TaskStatus.PENDING)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Approval instance has no active task"));
    }

    private void logHistory(Long instanceId, Long taskId, HistoryAction action,
                            String actorUserId, String actorEmplId,
                            Integer fromStep, Integer toStep, String note) {
        SesApprovalHistory h = new SesApprovalHistory();
        h.setInstanceId(instanceId);
        h.setTaskId(taskId);
        h.setAction(action);
        h.setActorUserId(actorUserId);
        h.setActorEmplId(actorEmplId);
        h.setFromStepOrder(fromStep);
        h.setToStepOrder(toStep);
        h.setNote(note);
        historyRepository.save(h);
    }

    public SesApprovalInstance getInstance(Long instanceId) {
        return instanceRepository.findById(instanceId)
                .orElseThrow(() -> new IllegalArgumentException("Approval instance not found: " + instanceId));
    }

    /**
     * When an instance was started — plain Date, not the entity itself, so
     * callers outside this module (e.g. a resubmit-cooldown guard) don't
     * need to import approval entity types. Null if the instance is gone.
     */
    @Transactional(readOnly = true)
    public Date getInstanceCreatedDate(Long instanceId) {
        return instanceRepository.findById(instanceId).map(SesApprovalInstance::getCreatedDate).orElse(null);
    }

    public List<SesApprovalTask> getTasks(Long instanceId) {
        return taskRepository.findByInstanceIdOrderByStepOrderAsc(instanceId);
    }

    public List<SesApprovalTaskAssignee> getTaskAssignees(Long taskId) {
        return taskAssigneeRepository.findByTaskIdOrderByTaskAssigneeIdAsc(taskId);
    }

    /** Authorization used by the approval detail page and action methods. */
    @Transactional(readOnly = true)
    public boolean canAct(SesApprovalTask task, String username, String userId) {
        return resolverService.canAct(task, username, userId);
    }

    public List<SesApprovalHistory> getHistory(Long instanceId) {
        return historyRepository.findByInstanceIdOrderByHistoryIdAsc(instanceId);
    }

    /**
     * Display name for a completed task's actor: the position holder's name
     * (snapshot on the resolved assignee) when the actor happens to be on a
     * POSITION assignee row, otherwise their real name from APP_USER.
     *
     * <p>The final fallback used to be the bare username — a badge number.
     * That was harmless while only a frozen assignee could act, because the
     * snapshot lookup almost always hit. Since 2026-09-30 authorization is
     * live for position-derived assignees ({@code
     * ApprovalResolverService.canAct}), so the person who approves is
     * routinely NOT on any frozen row: an incoming holder approving a task
     * frozen to the previous PJS matches nothing here. Falling back to the
     * badge would have printed "135847" where the approval history used to
     * print a name.</p>
     *
     * <p>{@code UserDisplayNameService} is the same resolver the approval
     * mail already uses for the actor, so the page and the email now answer
     * "who approved this" the same way.</p>
     */
    public String resolveCompletedByDisplay(SesApprovalTask task) {
        if (task.getCompletedBy() == null) {
            return null;
        }
        return resolveCompletedByDisplay(task, getTaskAssignees(task.getTaskId()));
    }

    /**
     * "Who closed this step" for every task of one instance, keyed by task
     * id, with the frozen assignee rows read once.
     *
     * <p>The single-task overload above fetches its own
     * {@code getTaskAssignees(taskId)}, so calling it in a loop -- which both
     * the request and the sticker detail pages did -- is a query per step.
     * {@code summaryModel} already batches exactly this way internally
     * (see the comment at its {@code findByTaskIdIn} call); these two callers
     * simply never got the batched path.
     */
    @Transactional(readOnly = true)
    public Map<Long, String> resolveCompletedByDisplay(List<SesApprovalTask> tasks) {
        Map<Long, String> byTask = new LinkedHashMap<>();
        if (tasks == null || tasks.isEmpty()) {
            return byTask;
        }
        Map<Long, List<SesApprovalTaskAssignee>> frozenByTask = new LinkedHashMap<>();
        List<Long> taskIds = tasks.stream().map(SesApprovalTask::getTaskId).collect(Collectors.toList());
        for (SesApprovalTaskAssignee a : InChunks.flatMap(taskIds, taskAssigneeRepository::findByTaskIdIn)) {
            frozenByTask.computeIfAbsent(a.getTaskId(), k -> new ArrayList<>()).add(a);
        }
        for (SesApprovalTask t : tasks) {
            byTask.put(t.getTaskId(), t.getCompletedBy() == null ? null
                    : resolveCompletedByDisplay(t,
                            frozenByTask.getOrDefault(t.getTaskId(), Collections.emptyList())));
        }
        return byTask;
    }

    /**
     * As above, for a caller that has already fetched this task's frozen
     * assignees. Identical result; it just does not issue the per-task select
     * again. {@link #summaryModel} renders one row per step and would otherwise
     * repeat that query once per step.
     */
    public String resolveCompletedByDisplay(SesApprovalTask task, List<SesApprovalTaskAssignee> frozenAssignees) {
        if (task.getCompletedBy() == null) {
            return null;
        }
        AppUser user = appUserRepository.findById(task.getCompletedBy()).orElse(null);
        String username = user != null ? user.getUsername() : task.getCompletedBy();
        return frozenAssignees.stream()
                .filter(a -> username.equalsIgnoreCase(a.getEmplId()))
                .filter(a -> a.getAssigneeType() == AssigneeType.POSITION)
                .map(SesApprovalTaskAssignee::getDisplayName)
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElseGet(() -> userDisplayNameService.displayName(username));
    }

    /**
     * Worklist: instances where the current task's assignees include the
     * given user. Deny-by-default: only the current step's assignees act.
     */
    @Transactional(readOnly = true)
    public List<SesApprovalInstance> findMyPendingApprovals(String username) {
        List<SesApprovalInstance> all = instanceRepository.findByStatusOrderByInstanceIdDesc(ApprovalStatus.PENDING);
        List<SesApprovalInstance> mine = new java.util.ArrayList<>();
        Optional<AppUser> user = appUserRepository.findByUsername(username);

        List<Long> currentTaskIds = all.stream()
                .map(SesApprovalInstance::getCurrentTaskId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, SesApprovalTask> taskById = new HashMap<>();
        for (SesApprovalTask t : InChunks.flatMap(currentTaskIds, taskRepository::findAllById)) {
            taskById.put(t.getTaskId(), t);
        }

        String userId = user.map(AppUser::getUserId).orElse(null);
        for (SesApprovalInstance instance : all) {
            if (instance.getCurrentTaskId() == null) {
                continue;
            }
            SesApprovalTask task = taskById.get(instance.getCurrentTaskId());
            if (task == null) {
                continue;
            }
            if (resolverService.canAct(task, username, userId)) {
                mine.add(instance);
            }
        }
        return mine;
    }

    /**
     * Same worklist as {@link #findMyPendingApprovals}, but sourced from
     * {@code APP_APPROVAL_VIEW} (sql/36) instead of looping every PENDING
     * instance in Java. {@code username} doubles as the EMPLID (see
     * sql/34's username=EMPLID convention) — system accounts (admin/sysadm/
     * test/...) never match a row since APPROVER_ID is always an EMPLID.
     * <p>
     * This only changes WHICH instances the inbox displays; it does not
     * replace {@link ApprovalResolverService#canAct} as the authorization
     * check for the actual approve/reject/checkpoint actions — those stay
     * independent (see sql/README.md #30).
     */
    @Transactional(readOnly = true)
    public List<SesApprovalInstance> findMyPendingApprovalsFromView(String username) {
        return cachedInbox(username);
    }

    /** Row count in APP_APPROVAL_VIEW for this EMPLID — backs the navbar badge. */
    @Transactional(readOnly = true)
    public long countMyPendingApprovalsFromView(String username) {
        return cachedInbox(username).size();
    }

    /**
     * {@code pendingApprovalCount()} (navbar badge, {@code ApprovalGlobalModelAttributes})
     * and {@code myApprovals()} ({@code ApprovalController}) both call into
     * this on EVERY page load for EVERY logged-in user — each hit was its
     * own live query through {@code APP_APPROVAL_VIEW} (a view joining
     * {@code ses_approval_task_assignee} -> {@code APP_POSITION_TMP_VIEW}/
     * {@code APP_EMP_INFO_VIEW}, ~100ms measured standalone). A short
     * in-memory TTL cache, shared by both callers, turns "2 live queries per
     * page" into "at most 1 query per user per TTL window". Diagnosed
     * 2026-09-17 — see docs for the response-time measurements that led here.
     * <p>
     * Deliberately plain {@code ConcurrentHashMap} + timestamp, not a
     * caching library — one map, one TTL, no eviction policy needed at this
     * scale. Invalidated explicitly (see {@link #invalidateInboxCache}) right
     * after an action changes what a user's inbox should show, rather than
     * relying only on TTL expiry, so a user who just approved/rejected sees
     * their own list update immediately instead of possibly stale for up to
     * {@code ses.approval.inbox-cache-ttl-ms}.
     */
    private final java.util.Map<String, CachedInbox> pendingApprovalsCache = new java.util.concurrent.ConcurrentHashMap<>();

    @org.springframework.beans.factory.annotation.Value("${ses.approval.inbox-cache-ttl-ms:30000}")
    private long inboxCacheTtlMs;

    private static final class CachedInbox {
        final List<SesApprovalInstance> instances;
        final long expiresAt;

        CachedInbox(List<SesApprovalInstance> instances, long expiresAt) {
            this.instances = instances;
            this.expiresAt = expiresAt;
        }
    }

    private List<SesApprovalInstance> cachedInbox(String username) {
        long now = System.currentTimeMillis();
        CachedInbox cached = pendingApprovalsCache.get(username);
        if (cached != null && cached.expiresAt > now) {
            return cached.instances;
        }
        List<SesApprovalInstance> fresh = loadMyPendingApprovalsFromView(username);
        pendingApprovalsCache.put(username, new CachedInbox(fresh, now + inboxCacheTtlMs));
        return fresh;
    }

    private void invalidateInboxCache(String username) {
        if (username != null) {
            pendingApprovalsCache.remove(username);
        }
    }

    private List<SesApprovalInstance> loadMyPendingApprovalsFromView(String username) {
        Optional<AppUser> user = appUserRepository.findByUsername(username);
        if (resolverService.isEmergencyApprover(username, user.map(AppUser::getUserId).orElse(null))) {
            // APP_APPROVAL_VIEW contains employee approver IDs only. Emergency
            // roles are broader, so use the same authorization path as actions
            // to build their complete inbox.
            return findMyPendingApprovals(username);
        }
        // Three queries total: the view, then the tasks it names, then their
        // instances. This was a findById per task followed by a findById per
        // instance -- and the TTL cache above only bounds how OFTEN this runs,
        // not what it costs when it does.
        List<Long> taskIds = appApprovalViewRepository.findByApproverId(username).stream()
                .map(com.badaklng.approval.entity.AppApprovalView::getTransId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        List<Long> instanceIds = InChunks.flatMap(taskIds, taskRepository::findAllById).stream()
                .map(SesApprovalTask::getInstanceId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        return InChunks.flatMap(instanceIds, instanceRepository::findAllById).stream()
                .filter(i -> i.getStatus() == ApprovalStatus.PENDING)
                .collect(Collectors.toList());
    }

    /**
     * Instances requested by a user.
     */
    @Transactional(readOnly = true)
    public List<SesApprovalInstance> findMyRequests(String username) {
        return instanceRepository.findByRequestorUserIdOrderByInstanceIdDesc(username);
    }

    /**
     * Checks if a user is authorized to act on the active pending step of a business entity.
     * Used by checkpoint actions (payment verification, scheduling) on the approval detail page.
     */
    @Transactional(readOnly = true)
    public boolean canActOnBusiness(String businessType, Long businessRefId, String username) {
        String userId = username != null
                ? appUserRepository.findByUsername(username.trim()).map(AppUser::getUserId).orElse(null)
                : null;
        return canActOnBusiness(businessType, businessRefId, username, userId);
    }

    @Transactional(readOnly = true)
    public boolean canActOnBusiness(String businessType, Long businessRefId, String username, String userId) {
        if (businessType == null || businessRefId == null) {
            return false;
        }
        if (resolverService.isEmergencyApprover(username, userId)) {
            return true;
        }
        Optional<SesApprovalInstance> instOpt = instanceRepository.findFirstByBusinessRefTypeAndBusinessRefIdOrderByInstanceIdDesc(businessType, businessRefId);
        if (!instOpt.isPresent() || instOpt.get().getStatus() != ApprovalStatus.PENDING) {
            return false;
        }
        SesApprovalInstance instance = instOpt.get();
        List<SesApprovalTask> tasks = taskRepository.findByInstanceIdOrderByStepOrderAsc(instance.getInstanceId());
        return tasks.stream()
                .filter(t -> t.getStatus() == TaskStatus.PENDING)
                .anyMatch(t -> resolverService.canAct(t, username, userId));
    }

    /**
     * Instance IDs where the user was an actor, assigned approver, or evaluator.
     */
    @Transactional(readOnly = true)
    public Set<Long> findInvolvedInstanceIds(String username) {
        if (username == null || username.trim().isEmpty()) {
            return Collections.emptySet();
        }
        String trimmed = username.trim();
        Optional<AppUser> user = appUserRepository.findByUsername(trimmed);
        String userId = user.map(AppUser::getUserId).orElse(null);

        Set<Long> ids = new LinkedHashSet<>();
        ids.addAll(historyRepository.findDistinctInstanceIdsByActor(trimmed, userId));
        ids.addAll(signoffRepository.findDistinctInstanceIdsBySignoffUser(trimmed, userId));
        ids.addAll(taskAssigneeRepository.findDistinctInstanceIdsByAssignee(trimmed, userId));
        return ids;
    }

    /**
     * Worklist of instances previously acted on / approved by this user.
     */
    @Transactional(readOnly = true)
    public List<ApprovalTaskForm> findMyApprovalHistory(String username) {
        if (username == null || username.trim().isEmpty()) {
            return Collections.emptyList();
        }
        Set<Long> instanceIds = findInvolvedInstanceIds(username);
        if (instanceIds.isEmpty()) {
            return Collections.emptyList();
        }
        // One query for the instances, then one batched enrichment pass --
        // this used to be a findById per involved instance plus a findById
        // per current task plus the ~3 queries toTaskForm costs, per row.
        List<SesApprovalInstance> instances =
                InChunks.flatMap(instanceIds, instanceRepository::findAllById);
        instances.sort(Comparator.comparing(SesApprovalInstance::getInstanceId).reversed());
        return toTaskForms(instances);
    }


    /**
     * Builds the worklist row for an instance, enriching it with the
     * source module's display data (transaction number, description) via
     * its registered {@link com.badaklng.approval.spi.ApprovalFormDescriptor}
     * — looked up by formCode so this stays agnostic of any business module.
     */
    @Transactional(readOnly = true)
    public ApprovalTaskForm toTaskForm(SesApprovalInstance instance, String currentStepName) {
        String module = null;
        String transactionId = null;
        String description = null;
        Optional<ApprovalFormDescriptor> descriptor = formService.findDescriptor(instance.getFormCode());
        if (descriptor.isPresent()) {
            module = descriptor.get().formName();
            transactionId = descriptor.get().transactionId(instance.getBusinessRefId());
            description = descriptor.get().description(instance.getBusinessRefId());
        }
        // One lookup for the name the worklist shows. Resolving it through
        // UserDisplayNameService instead would repeat this same query for
        // every row.
        //
        // It used to serve a second purpose — handing the APP_USER UUID to the
        // form so a "Requestor ID" column could print it. That column is gone
        // and so is the field; the worklist links on instanceId and has never
        // needed the user's primary key.
        AppUser requestor = appUserRepository.findByUsername(instance.getRequestorUserId()).orElse(null);
        ApprovalTaskForm form = ApprovalTaskForm.from(instance, currentStepName, module,
                transactionId, description);
        form.setRequestorName(requestorDisplayName(requestor, instance.getRequestorUserId()));
        return form;
    }

    /**
     * {@link #toTaskForm} for a whole worklist, with a fixed query budget
     * instead of one proportional to the row count.
     *
     * <p>Replaces the per-row shape the three worklist screens shared:
     * {@code getTasks(instanceId)} to find the one task whose id the
     * instance already holds, then {@code transactionId} and
     * {@code description} (each a {@code findById} on the same business row,
     * plus a type lookup), then {@code findByUsername} for the requestor's
     * name. Roughly 5 queries per row; {@code /approval} measured 36 for one
     * page.
     *
     * <p>Now: one {@code findAllById} for the current tasks, one bulk call
     * per distinct form code, and one batched display-name lookup.
     */
    @Transactional(readOnly = true)
    public List<ApprovalTaskForm> toTaskForms(List<SesApprovalInstance> instances) {
        if (instances == null || instances.isEmpty()) {
            return new ArrayList<>();
        }

        // The step name only ever comes from instance.currentTaskId, so fetch
        // those tasks directly rather than every task of every instance.
        Map<Long, String> stepNameByTaskId = new HashMap<>();
        List<Long> currentTaskIds = instances.stream()
                .map(SesApprovalInstance::getCurrentTaskId)
                .filter(id -> id != null)
                .distinct()
                .collect(Collectors.toList());
        for (SesApprovalTask t : InChunks.flatMap(currentTaskIds, taskRepository::findAllById)) {
            stepNameByTaskId.put(t.getTaskId(), t.getStepName());
        }

        Map<String, Map<Long, ApprovalFormDescriptor.FormRowData>> dataByForm = new HashMap<>();
        Map<String, String> formNameByCode = new HashMap<>();
        Map<String, List<SesApprovalInstance>> byFormCode = instances.stream()
                .filter(i -> i.getFormCode() != null)
                .collect(Collectors.groupingBy(SesApprovalInstance::getFormCode));
        for (Map.Entry<String, List<SesApprovalInstance>> entry : byFormCode.entrySet()) {
            Optional<ApprovalFormDescriptor> descriptor = formService.findDescriptor(entry.getKey());
            if (!descriptor.isPresent()) {
                continue;
            }
            formNameByCode.put(entry.getKey(), descriptor.get().formName());
            dataByForm.put(entry.getKey(), descriptor.get().rowData(entry.getValue().stream()
                    .map(SesApprovalInstance::getBusinessRefId)
                    .filter(id -> id != null)
                    .collect(Collectors.toSet())));
        }

        Map<String, String> names = userDisplayNameService.displayNames(instances.stream()
                .map(SesApprovalInstance::getRequestorUserId)
                .filter(ref -> ref != null)
                .collect(Collectors.toSet()));

        List<ApprovalTaskForm> rows = new ArrayList<>(instances.size());
        for (SesApprovalInstance instance : instances) {
            String stepName = instance.getCurrentTaskId() == null
                    ? "" : stepNameByTaskId.getOrDefault(instance.getCurrentTaskId(), "");
            Map<Long, ApprovalFormDescriptor.FormRowData> forForm =
                    dataByForm.getOrDefault(instance.getFormCode(), Collections.emptyMap());
            ApprovalFormDescriptor.FormRowData d = forForm.get(instance.getBusinessRefId());
            ApprovalTaskForm form = ApprovalTaskForm.from(instance, stepName,
                    formNameByCode.get(instance.getFormCode()),
                    d == null ? null : d.getTransactionId(),
                    d == null ? null : d.getDescription());
            String ref = instance.getRequestorUserId();
            form.setRequestorName(ref == null ? null : names.getOrDefault(ref, ref));
            rows.add(form);
        }
        return rows;
    }

    /** Full name, then username, then the stored reference — never blank. */
    private static String requestorDisplayName(AppUser user, String fallback) {
        if (user == null) {
            return fallback;
        }
        if (user.getFullName() != null && !user.getFullName().trim().isEmpty()) {
            return user.getFullName().trim();
        }
        return user.getUsername() != null && !user.getUsername().trim().isEmpty()
                ? user.getUsername().trim() : fallback;
    }
}
