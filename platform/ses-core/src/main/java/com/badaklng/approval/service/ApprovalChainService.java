package com.badaklng.approval.service;

import com.badaklng.approval.entity.AssigneeType;
import com.badaklng.approval.entity.CompletionRule;
import com.badaklng.approval.entity.SesApprovalAssignee;
import com.badaklng.approval.entity.SesApprovalChain;
import com.badaklng.approval.entity.SesApprovalStep;
import com.badaklng.approval.entity.SesApprovalStepTask;
import com.badaklng.approval.entity.StepRole;
import com.badaklng.approval.repository.SesApprovalAssigneeRepository;
import com.badaklng.approval.repository.SesApprovalChainRepository;
import com.badaklng.approval.repository.SesApprovalInstanceRepository;
import com.badaklng.approval.repository.SesApprovalStepRepository;
import com.badaklng.approval.repository.SesApprovalStepTaskRepository;
import com.badaklng.approval.repository.SesFormApprovalMapRepository;
import com.badaklng.approval.spi.CheckpointTaskType;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Full CRUD for approval chains, steps, and assignees — the entire chain
 * configuration is admin-managed at runtime, no redeploy required.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ApprovalChainService {

    private final SesApprovalChainRepository chainRepository;
    private final SesApprovalStepRepository stepRepository;
    private final SesApprovalAssigneeRepository assigneeRepository;
    private final SesApprovalStepTaskRepository stepTaskRepository;
    private final SesFormApprovalMapRepository formMapRepository;
    private final SesApprovalInstanceRepository instanceRepository;

    /**
     * ObjectProvider, NOT List — same reason ApprovalChainController uses one:
     * with zero CheckpointTaskType beans on the classpath a List injection
     * fails the context. Used only to validate that an admin-entered
     * checkpoint key is one a module will actually ever call.
     */
    private final ObjectProvider<CheckpointTaskType> checkpointTaskTypes;

    /* Keys the ordering rule below is written against. Deliberately plain
     * strings, not a compile dependency on the modules that publish them —
     * a chain with none of these is simply unconstrained. */
    private static final String PAYMENT_CHECKPOINT_KEY = "PAYMENT_VERIFICATION";
    private static final String SCREENING_CHECKPOINT_KEY = "SCREENING_SCHEDULE";
    private static final String BADGE_CHECKPOINT_KEY = "BADGE_RETRIEVAL_SCHEDULE";

    @Transactional(readOnly = true)
    public List<SesApprovalChain> findAll() {
        return chainRepository.findAllByOrderByChainNameAsc();
    }

    /** Selectable = active AND has at least one step. */
    @Transactional(readOnly = true)
    public List<SesApprovalChain> findSelectable() {
        return chainRepository.findByActiveFlagOrderByChainNameAsc("Y").stream()
                .filter(c -> stepRepository.countByChainId(c.getChainId()) > 0)
                .collect(Collectors.toList());
    }

    /**
     * Checks if the given chain contains the PAYMENT_VERIFICATION checkpoint.
     * Used to validate that paying request types are only mapped to valid chains.
     */
    @Transactional(readOnly = true)
    public boolean hasPaymentCheckpoint(Long chainId) {
        if (chainId == null) {
            return false;
        }
        List<SesApprovalStep> steps = stepRepository.findByChainIdOrderByStepOrderAsc(chainId);
        for (SesApprovalStep step : steps) {
            List<SesApprovalStepTask> tasks = stepTaskRepository
                    .findByStepIdOrderByOrderByAscStepTaskIdAsc(step.getStepId());
            for (SesApprovalStepTask task : tasks) {
                if (PAYMENT_CHECKPOINT_KEY.equals(task.getCheckpointKey())) {
                    return true;
                }
            }
        }
        return false;
    }

    @Transactional(readOnly = true)
    public SesApprovalChain findById(Long chainId) {
        return chainRepository.findById(chainId)
                .orElseThrow(() -> new IllegalArgumentException("Approval chain not found: " + chainId));
    }

    @Transactional(readOnly = true)
    public List<SesApprovalStep> findSteps(Long chainId) {
        return stepRepository.findByChainIdOrderByStepOrderAsc(chainId);
    }

    @Transactional(readOnly = true)
    public List<SesApprovalAssignee> findAssignees(Long stepId) {
        return assigneeRepository.findByStepIdOrderByAssigneeIdAsc(stepId);
    }

    @Transactional(readOnly = true)
    public List<SesApprovalStepTask> findStepTasks(Long stepId) {
        return stepTaskRepository.findByStepIdOrderByOrderByAscStepTaskIdAsc(stepId);
    }

    @Transactional(readOnly = true)
    public boolean isInUse(Long chainId) {
        return formMapRepository.countByChainIdAndActiveFlag(chainId, "Y") > 0
                || instanceRepository.countByChainId(chainId) > 0;
    }

    @Transactional
    public void setActive(Long chainId, boolean active) {
        SesApprovalChain chain = findById(chainId);
        chain.setActiveFlag(active ? "Y" : "N");
        chainRepository.save(chain);
    }

    /**
     * Guarded hard delete: refuses when an ACTIVE form mapping or any
     * approval instance still references this chain (steps/assignees
     * cascade via DB FKs when the delete does proceed).
     *
     * Mappings are counted active-only because saveMappings soft-deletes
     * (active_flag='N'); counting raw rows made every chain that had ever
     * been mapped undeletable forever. Instances are counted raw on
     * purpose — an instance is real history and must keep blocking.
     */
    @Transactional
    public void delete(Long chainId) {
        long mapCount = formMapRepository.countByChainIdAndActiveFlag(chainId, "Y");
        long instanceCount = instanceRepository.countByChainId(chainId);
        if (mapCount > 0 || instanceCount > 0) {
            throw new IllegalStateException("Cannot delete chain " + chainId + ": "
                    + mapCount + " active mapping(s), " + instanceCount + " instance(s) still reference it");
        }
        // Soft-deleted mappings are dead config as far as isInUse is
        // concerned, but FK_SES_FAM_CHAIN still points at the chain — clear
        // them here or the delete below fails with ORA-02292.
        formMapRepository.deleteByChainIdAndActiveFlag(chainId, "N");
        chainRepository.deleteById(chainId);
    }

    private void saveAssignees(Long stepId, List<AssigneeInput> assigneeInputs) {
        List<SesApprovalAssignee> existingAssignees = assigneeRepository.findByStepIdOrderByAssigneeIdAsc(stepId);
        Map<Long, SesApprovalAssignee> existingById = existingAssignees.stream()
                .collect(Collectors.toMap(SesApprovalAssignee::getAssigneeId, a -> a));
        Set<Long> keptIds = new HashSet<>();

        for (int j = 0; j < assigneeInputs.size(); j++) {
            AssigneeInput ai = assigneeInputs.get(j);
            SesApprovalAssignee a = ai.getAssigneeId() != null ? existingById.get(ai.getAssigneeId()) : null;
            if (a == null) {
                a = new SesApprovalAssignee();
                a.setStepId(stepId);
            } else {
                keptIds.add(ai.getAssigneeId());
            }
            a.setAssigneeType(ai.getAssigneeType());
            a.setPosId(ai.getPosId());
            a.setUserId(ai.getUserId());
            a.setOrderBy(j);
            assigneeRepository.save(a);
        }

        for (SesApprovalAssignee existingA : existingAssignees) {
            if (!keptIds.contains(existingA.getAssigneeId())) {
                assigneeRepository.deleteById(existingA.getAssigneeId());
            }
        }
    }

    /**
     * Chain-wide checkpoint rules, checked once the per-step rows are clean.
     *
     * <ol>
     *   <li><b>No duplicate key.</b> The same checkpoint key twice in one chain
     *       means {@code completeCheckpointByKey} resolves whichever it finds
     *       first and leaves the other PENDING forever.</li>
     *   <li><b>A payment gate needs an officer.</b> The step carrying
     *       {@code PAYMENT_VERIFICATION} must have at least one assignee.
     *       (The request was "the officer/payment step must have its checkpoint
     *       filled in first"; stated literally that would also reject the
     *       Manager and Security Manager steps of the live DEFAULT/PAYMENT
     *       chains, which correctly have assignees and no checkpoints, making
     *       those chains unsaveable. This is the same intent — never a payment
     *       gate with nobody to act on it — without breaking working
     *       configuration.)</li>
     *   <li><b>Checkpoint order.</b> Pay, then screen, then collect the badge:
     *       PAYMENT_VERIFICATION's step must not come after
     *       SCREENING_SCHEDULE's, which must not come after
     *       BADGE_RETRIEVAL_SCHEDULE's. Equal orders are fine — two gates can
     *       share one step, which is what the seeded PAYMENT chain does.</li>
     * </ol>
     *
     * Step order is the LIST INDEX here, not {@code step_order}. Callers pass
     * the chain's steps in the order they will end up in, so index is the
     * order being saved.
     */
    private void validateCheckpointPlacement(List<StepInput> stepInputs,
                                             List<List<AssigneeInput>> assigneesByStep,
                                             List<List<StepTaskInput>> tasksByStep) {
        Map<String, Integer> stepIndexByKey = new LinkedHashMap<>();
        for (int i = 0; i < tasksByStep.size(); i++) {
            for (StepTaskInput t : tasksByStep.get(i)) {
                Integer previous = stepIndexByKey.putIfAbsent(t.getCheckpointKey(), i);
                if (previous != null) {
                    throw new IllegalArgumentException("Checkpoint key \"" + t.getCheckpointKey()
                            + "\" is used on more than one step. Each key may appear once per chain.");
                }
            }
            final int stepIdx = i;
            boolean gatesPayment = tasksByStep.get(stepIdx).stream()
                    .anyMatch(t -> PAYMENT_CHECKPOINT_KEY.equals(t.getCheckpointKey()));
            if (gatesPayment && assigneesByStep.get(stepIdx).isEmpty()) {
                throw new IllegalArgumentException("Step \"" + stepInputs.get(stepIdx).getStepName().trim()
                        + "\" carries the payment checkpoint but has no assignee — add the payment officer"
                        + " so someone can act once payment is verified.");
            }
        }
        requireOrder(stepIndexByKey, PAYMENT_CHECKPOINT_KEY, SCREENING_CHECKPOINT_KEY);
        requireOrder(stepIndexByKey, SCREENING_CHECKPOINT_KEY, BADGE_CHECKPOINT_KEY);
        requireOrder(stepIndexByKey, PAYMENT_CHECKPOINT_KEY, BADGE_CHECKPOINT_KEY);
    }

    private void requireOrder(Map<String, Integer> stepIndexByKey, String earlier, String later) {
        Integer a = stepIndexByKey.get(earlier);
        Integer b = stepIndexByKey.get(later);
        if (a != null && b != null && a > b) {
            throw new IllegalArgumentException("Checkpoint \"" + earlier + "\" must not be on a step after \""
                    + later + "\" — the intended order is payment, then screening, then badge retrieval.");
        }
    }

    private void saveStepTasks(Long stepId, List<StepTaskInput> taskInputs) {
        List<SesApprovalStepTask> existingTasks = stepTaskRepository.findByStepIdOrderByOrderByAscStepTaskIdAsc(stepId);
        Map<Long, SesApprovalStepTask> existingById = existingTasks.stream()
                .collect(Collectors.toMap(SesApprovalStepTask::getStepTaskId, t -> t));
        Set<Long> keptIds = new HashSet<>();

        for (int j = 0; j < taskInputs.size(); j++) {
            StepTaskInput ti = taskInputs.get(j);
            SesApprovalStepTask t = ti.getStepTaskId() != null ? existingById.get(ti.getStepTaskId()) : null;
            if (t == null) {
                t = new SesApprovalStepTask();
                t.setStepId(stepId);
            } else {
                keptIds.add(ti.getStepTaskId());
            }
            t.setTaskLabel(ti.getTaskLabel());
            t.setCheckpointKey(ti.getCheckpointKey());
            t.setOrderBy(j);
            stepTaskRepository.save(t);
        }

        for (SesApprovalStepTask existingT : existingTasks) {
            if (!keptIds.contains(existingT.getStepTaskId())) {
                stepTaskRepository.deleteById(existingT.getStepTaskId());
            }
        }
    }

    /**
     * Validates and normalises one step's checkpoint task rows.
     *
     * <p>This used to DROP a row whose label was blank and null out an empty
     * key, silently. Silent is the problem: a half-filled row is an admin
     * mistake, and either half of it going missing produces a gate that can
     * never be satisfied — a label with no key is a checkpoint no module will
     * ever call {@code completeCheckpointByKey} for, so the step it gates sits
     * PENDING until someone notices and skips it by hand. A wholly empty row
     * (the blank template row the editor always renders) is still dropped;
     * that one is not a mistake.</p>
     *
     * <p>The key must also be one a module actually registered — an
     * unrecognised key is the same dead gate spelled differently.</p>
     */
    private List<StepTaskInput> normalizeStepTasks(String stepName, List<StepTaskInput> inputs) {
        List<StepTaskInput> result = new ArrayList<>();
        if (inputs == null) {
            return result;
        }
        Set<String> registeredKeys = checkpointTaskTypes.orderedStream()
                .map(CheckpointTaskType::getKey)
                .collect(Collectors.toSet());
        for (StepTaskInput in : inputs) {
            if (in == null) {
                continue;
            }
            String label = in.getTaskLabel() == null ? "" : in.getTaskLabel().trim();
            String key = in.getCheckpointKey() == null ? "" : in.getCheckpointKey().trim();
            if (label.isEmpty() && key.isEmpty()) {
                continue; // the editor's empty template row
            }
            if (label.isEmpty()) {
                throw new IllegalArgumentException("Step \"" + stepName + "\": checkpoint task with key \""
                        + key + "\" has no label.");
            }
            if (key.isEmpty()) {
                throw new IllegalArgumentException("Step \"" + stepName + "\": checkpoint task \"" + label
                        + "\" has no checkpoint key. Pick one from the dropdown, or remove the row.");
            }
            if (!registeredKeys.contains(key)) {
                throw new IllegalArgumentException("Step \"" + stepName + "\": checkpoint key \"" + key
                        + "\" is not registered by any module. Registered keys: "
                        + String.join(", ", new java.util.TreeSet<>(registeredKeys)));
            }
            in.setTaskLabel(label);
            in.setCheckpointKey(key);
            result.add(in);
        }
        return result;
    }

    /**
     * Drops rows with no assignee type or a blank required target value,
     * and normalises per type: POSITION keeps only posId, USER only
     * userId, REQUESTOR_MANAGER and REPORT_TO neither.
     */
    private List<AssigneeInput> normalizeAssignees(List<AssigneeInput> inputs) {
        List<AssigneeInput> result = new ArrayList<>();
        if (inputs == null) {
            return result;
        }
        for (AssigneeInput in : inputs) {
            if (in == null || in.getAssigneeType() == null) {
                continue;
            }
            if (in.getAssigneeType() == AssigneeType.POSITION) {
                if (in.getPosId() == null || in.getPosId().trim().isEmpty()) {
                    continue;
                }
                in.setPosId(in.getPosId().trim());
                in.setUserId(null);
            } else if (in.getAssigneeType() == AssigneeType.USER) {
                if (in.getUserId() == null || in.getUserId().trim().isEmpty()) {
                    continue;
                }
                in.setUserId(in.getUserId().trim());
                in.setPosId(null);
            } else {
                in.setPosId(null);
                in.setUserId(null);
            }
            result.add(in);
        }
        return result;
    }

    /**
     * Creates or updates ONLY a chain's header — key, name, description,
     * active flag — leaving its steps untouched.
     *
     * <p>The chain builder saves in two independent halves: the header posts
     * as an ordinary form, then each step is added over AJAX through
     * {@link #saveStep}. A chain with no steps yet is therefore a normal
     * intermediate state — it is what a brand new chain looks like before its
     * first step exists — which is why nothing here demands one.</p>
     *
     * <p>{@code chainKey} stays immutable after create: an approval instance
     * records by value the key it ran under, and letting the chain's key
     * change afterwards would silently rewrite that history.</p>
     */
    @Transactional
    public SesApprovalChain createOrUpdateHeader(ChainEditInput input) {
        SesApprovalChain chain;
        if (input.getChainId() == null) {
            String normalizedKey = input.getChainKey() == null ? null : input.getChainKey().trim().toUpperCase();
            if (normalizedKey == null || normalizedKey.isEmpty()) {
                throw new IllegalArgumentException("Chain key is required");
            }
            if (chainRepository.findByChainKey(normalizedKey).isPresent()) {
                throw new IllegalArgumentException("Chain key already in use: " + normalizedKey);
            }
            chain = new SesApprovalChain();
            chain.setChainKey(normalizedKey);
        } else {
            chain = findById(input.getChainId());
        }
        if (input.getChainName() == null || input.getChainName().trim().isEmpty()) {
            throw new IllegalArgumentException("Chain name is required");
        }
        chain.setChainName(input.getChainName().trim());
        chain.setDescription(input.getDescription());
        chain.setActiveFlag(input.getActiveFlag() != null ? input.getActiveFlag() : "Y");
        return chainRepository.save(chain);
    }

    // ---------------------------------------------------------------
    // Step roles
    // ---------------------------------------------------------------

    /** The step's role, parsed leniently — anything unrecognised reads as "no role". */
    private static StepRole roleOf(StepInput step) {
        return StepRole.parse(step.getStepRole());
    }

    /**
     * Four invariants on step roles, parallel groups and completion rules,
     * checked across the WHOLE chain. Each one, left unchecked, fails
     * SILENTLY rather than loudly — which is why they are enforced at save
     * time instead of being discovered later by whoever is wondering where
     * their approval went.
     *
     * <ol>
     *   <li><b>Each role at most once per chain.</b> Otherwise "the Manager
     *       Pemohon step" has no single referent and which one gets skipped is
     *       an accident of step order.</li>
     *   <li><b>At least one non-skippable step must remain.</b> A chain whose
     *       every step is skippable would, for a submission with both toggles
     *       off and no additional routing, reach {@code advance()} with no
     *       PENDING task at all and complete as APPROVED inside
     *       {@code trigger()} — a submission approved with nobody having
     *       reviewed it. "Skippable" here means skippable by ANY mechanism,
     *       the toggles and the empty-evaluator case alike; see
     *       {@link StepRole#isSkippable()}.</li>
     *   <li><b>A skippable step may carry no checkpoint.</b> Checkpoints are
     *       frozen onto the task at trigger time even when the step is skipped,
     *       and {@code completeCheckpointByKey} scans EVERY task of the
     *       instance — so it can match a checkpoint on a skipped step, mark it
     *       DONE, and leave the real gate still waiting.</li>
     *   <li><b>At most one {@code ALL} step per parallel group.</b> Two
     *       consensus steps running at the same moment make "who are we still
     *       waiting on?" ambiguous to the people sitting in the queue.</li>
     * </ol>
     *
     * <p>{@code static} and dependency-free so the whole rule set can be
     * unit-tested without a Spring context, the same treatment
     * {@code ApprovalResolverService.decide} gets. Configuration rules that can
     * only be exercised through {@code @SpringBootTest} against a live Oracle
     * are configuration rules that do not get tested.</p>
     */
    static void validateStepRoles(List<StepInput> stepInputs, List<List<StepTaskInput>> tasksByStep) {
        Set<StepRole> rolesSeen = new HashSet<>();
        Set<Integer> allRuleGroups = new HashSet<>();
        int skippable = 0;

        for (int i = 0; i < stepInputs.size(); i++) {
            StepInput step = stepInputs.get(i);
            String name = step.getStepName() == null ? "?" : step.getStepName().trim();
            StepRole role = roleOf(step);

            if (role != null && !rolesSeen.add(role)) {
                throw new IllegalArgumentException("Peran tahap \"" + role.name()
                        + "\" hanya boleh dipakai satu kali dalam satu rantai.");
            }

            if (role != null && role.isSkippable()) {
                skippable++;
                if (!tasksByStep.get(i).isEmpty()) {
                    throw new IllegalArgumentException("Tahap \"" + name
                            + "\" dapat dilewati, jadi tidak boleh membawa checkpoint: checkpoint"
                            + " pada tahap yang dilewati tidak akan pernah bisa diselesaikan.");
                }
            }

            if (CompletionRule.ALL == CompletionRule.parse(step.getCompletionRule())) {
                // A null group means "a group of its own", so it can never
                // collide with another step. Only numbered groups are checked.
                Integer group = step.getParallelGroup();
                if (group != null && !allRuleGroups.add(group)) {
                    throw new IllegalArgumentException("Grup paralel " + group
                            + " berisi lebih dari satu tahap konsensus (semua harus menyetujui);"
                            + " sisa antrean menjadi ambigu bagi pengguna.");
                }
            }
        }

        if (skippable > 0 && skippable == stepInputs.size()) {
            throw new IllegalArgumentException(
                    "Rantai tidak boleh hanya berisi tahap yang dapat dilewati — pengajuan yang"
                    + " mematikan kedua toggle dan tidak memilih additional routing akan langsung"
                    + " disetujui tanpa review sama sekali.");
        }
    }

    // ---------------------------------------------------------------
    // Per-step operations — the modal chain builder
    // ---------------------------------------------------------------
    //
    // The editor saves one step at a time over AJAX, so each of these has to
    // police the rules that are only meaningful ACROSS steps — "each role at
    // most once", "at least one non-skippable step must remain". Validating
    // only the step being touched would reopen every one of them: an admin
    // could add a second REQUESTOR_MANAGER step, or delete the last
    // non-skippable one, simply by doing it one step at a time.
    //
    // They do that by PROJECTING the chain's other steps out of the database
    // and running the cross-step validators over the result.
    //
    // What they deliberately do NOT do is re-validate the content of steps
    // nobody touched. normalizeStepTasks rejects a checkpoint key no module
    // registers any more — correct for a step being written, wrong for one
    // merely being read past: if a module is ever removed, re-validating would
    // make every OTHER step of that chain unsaveable and the stale step
    // undeletable, locking the admin out of the very screen they need to fix
    // it. Untouched steps are therefore projected raw and trusted; they are
    // already persisted, and nothing here is about to rewrite them.

    /** An untouched step's assignees, as inputs — trusted, not re-validated. */
    private List<AssigneeInput> assigneeInputsOf(Long stepId) {
        List<AssigneeInput> out = new ArrayList<>();
        for (SesApprovalAssignee a : assigneeRepository.findByStepIdOrderByAssigneeIdAsc(stepId)) {
            AssigneeInput ai = new AssigneeInput();
            ai.setAssigneeId(a.getAssigneeId());
            ai.setAssigneeType(a.getAssigneeType());
            ai.setPosId(a.getPosId());
            ai.setUserId(a.getUserId());
            out.add(ai);
        }
        return out;
    }

    /** An untouched step's checkpoints, as inputs — trusted, not re-validated. */
    private List<StepTaskInput> taskInputsOf(Long stepId) {
        List<StepTaskInput> out = new ArrayList<>();
        for (SesApprovalStepTask t : stepTaskRepository.findByStepIdOrderByOrderByAscStepTaskIdAsc(stepId)) {
            StepTaskInput ti = new StepTaskInput();
            ti.setStepTaskId(t.getStepTaskId());
            ti.setTaskLabel(t.getTaskLabel());
            ti.setCheckpointKey(t.getCheckpointKey());
            out.add(ti);
        }
        return out;
    }

    /** Projects a persisted step back into the input shape the validators read. */
    private StepInput toInput(SesApprovalStep step) {
        StepInput in = new StepInput();
        in.setStepId(step.getStepId());
        in.setStepName(step.getStepName());
        in.setStepRole(step.getStepRole() == null ? null : step.getStepRole().name());
        in.setParallelGroup(step.getParallelGroup());
        in.setCompletionRule(step.getCompletionRule() == null ? null : step.getCompletionRule().name());
        in.setAssignees(assigneeInputsOf(step.getStepId()));
        in.setTasks(taskInputsOf(step.getStepId()));
        return in;
    }

    /**
     * Creates or updates ONE step of a chain, with its assignees and
     * checkpoints. A null {@code stepId} appends to the end.
     */
    @Transactional
    public SesApprovalStep saveStep(Long chainId, StepInput input) {
        SesApprovalChain chain = findById(chainId);
        if (input.getStepName() == null || input.getStepName().trim().isEmpty()) {
            throw new IllegalArgumentException("Every step must have a name");
        }
        String name = input.getStepName().trim();

        // Only the step being written is normalised — and therefore only it is
        // held to the registered-checkpoint-key rule. See the note above.
        List<AssigneeInput> myAssignees = normalizeAssignees(input.getAssignees());
        List<StepTaskInput> myTasks = normalizeStepTasks(name, input.getTasks());
        if (myAssignees.isEmpty() && myTasks.isEmpty() && StepRole.SECURITY_STAFF != roleOf(input)) {
            // A step with neither an approver nor a checkpoint is a dead end.
            // SECURITY_STAFF is exempt: its approvers are the Staf Security the
            // requestor picks on the form, not configuration, so demanding one
            // here would force an admin to invent a placeholder the engine then
            // ignores. What is validated instead, at submit time, is that the
            // request names at least one evaluator.
            throw new IllegalArgumentException("Step \"" + name
                    + "\" must have at least one assignee, or at least one checkpoint task");
        }

        List<SesApprovalStep> existing = stepRepository.findByChainIdOrderByStepOrderAsc(chainId);
        List<StepInput> projected = new ArrayList<>();
        List<List<AssigneeInput>> assigneesByStep = new ArrayList<>();
        List<List<StepTaskInput>> tasksByStep = new ArrayList<>();
        int index = -1;
        boolean replaced = false;

        for (SesApprovalStep step : existing) {
            if (input.getStepId() != null && input.getStepId().equals(step.getStepId())) {
                index = projected.size();
                projected.add(input);
                assigneesByStep.add(myAssignees);
                tasksByStep.add(myTasks);
                replaced = true;
            } else {
                if (name.equals(step.getStepName() == null ? null : step.getStepName().trim())) {
                    throw new IllegalArgumentException("Duplicate step name: " + name);
                }
                projected.add(toInput(step));
                assigneesByStep.add(assigneeInputsOf(step.getStepId()));
                tasksByStep.add(taskInputsOf(step.getStepId()));
            }
        }
        if (input.getStepId() != null && !replaced) {
            throw new IllegalArgumentException("Step not found for this chain: " + input.getStepId());
        }
        if (input.getStepId() == null) {
            index = projected.size();
            projected.add(input);
            assigneesByStep.add(myAssignees);
            tasksByStep.add(myTasks);
        }

        validateCheckpointPlacement(projected, assigneesByStep, tasksByStep);
        validateStepRoles(projected, tasksByStep);

        SesApprovalStep step;
        if (input.getStepId() == null) {
            step = new SesApprovalStep();
            step.setChainId(chain.getChainId());
            // Appended, so it takes the next order and nothing else moves —
            // which is why this needs none of the deferred-constraint
            // renumbering that a wholesale reorder requires.
            step.setStepOrder(existing.size() + 1);
        } else {
            step = stepRepository.findById(input.getStepId())
                    .orElseThrow(() -> new IllegalArgumentException("Step not found: " + input.getStepId()));
            if (!chain.getChainId().equals(step.getChainId())) {
                // Not a "not found": the step exists, it just belongs to a
                // different chain. Saying so plainly beats letting an admin
                // move a step between chains by crafting a URL.
                throw new IllegalArgumentException(
                        "Step " + input.getStepId() + " does not belong to chain " + chainId);
            }
        }
        step.setStepName(name);
        step.setStepRole(roleOf(input));
        step.setParallelGroup(input.getParallelGroup());
        step.setCompletionRule(CompletionRule.parse(input.getCompletionRule()));
        step = stepRepository.save(step);

        saveAssignees(step.getStepId(), assigneesByStep.get(index));
        saveStepTasks(step.getStepId(), tasksByStep.get(index));
        stepRepository.flush();
        return step;
    }

    /**
     * Deletes one step and closes the gap in {@code step_order}.
     *
     * <p>Only {@link #validateStepRoles} runs here, and only over what would
     * remain. Deletion can break exactly one rule — removing the last
     * non-skippable step — and holding a delete hostage to unrelated problems
     * elsewhere in the chain would make a broken chain unfixable.</p>
     *
     * <p>A chain IS allowed to end up with zero steps: that is simply an
     * unconfigured chain, and {@code ApprovalFormService.validChain} already
     * refuses to route anything to one.</p>
     */
    @Transactional
    public void deleteStep(Long chainId, Long stepId) {
        findById(chainId);
        List<SesApprovalStep> existing = stepRepository.findByChainIdOrderByStepOrderAsc(chainId);
        List<StepInput> remaining = new ArrayList<>();
        List<List<StepTaskInput>> remainingTasks = new ArrayList<>();
        boolean found = false;
        for (SesApprovalStep step : existing) {
            if (step.getStepId().equals(stepId)) {
                found = true;
            } else {
                remaining.add(toInput(step));
                remainingTasks.add(taskInputsOf(step.getStepId()));
            }
        }
        if (!found) {
            throw new IllegalArgumentException("Step not found for this chain: " + stepId);
        }
        if (!remaining.isEmpty()) {
            validateStepRoles(remaining, remainingTasks);
        }

        // Assignees and checkpoints cascade via fk_ses_assignee_step /
        // fk_ses_step_task_step ON DELETE CASCADE.
        stepRepository.deleteById(stepId);
        stepRepository.flush();
        renumber(chainId);
    }

    /**
     * Reorders a chain's steps to the given sequence of ids.
     *
     * <p>The list must name every step of the chain exactly once. A partial
     * list would leave the unnamed steps holding stale orders that collide
     * with the renumbered ones.</p>
     *
     * <p>No role validation: reordering changes no role, no group and no
     * rule, so none of those invariants can be broken by it. Checkpoint
     * ORDER can be — pay, then screen, then collect — but that rule is
     * checked against step position, and re-running it here would again let
     * one pre-existing problem freeze the whole screen. It is enforced where
     * it is actionable: on the step save that introduces a checkpoint.</p>
     */
    @Transactional
    public void reorderSteps(Long chainId, List<Long> orderedStepIds) {
        findById(chainId);
        List<SesApprovalStep> existing = stepRepository.findByChainIdOrderByStepOrderAsc(chainId);
        Map<Long, SesApprovalStep> byId = new LinkedHashMap<>();
        for (SesApprovalStep step : existing) {
            byId.put(step.getStepId(), step);
        }
        if (orderedStepIds == null || orderedStepIds.size() != byId.size()
                || !byId.keySet().containsAll(orderedStepIds)
                || new HashSet<>(orderedStepIds).size() != orderedStepIds.size()) {
            throw new IllegalArgumentException(
                    "Urutan tahap harus menyebut setiap tahap rantai ini tepat satu kali.");
        }

        int order = 1;
        for (Long stepId : orderedStepIds) {
            byId.get(stepId).setStepOrder(order++);
        }
        stepRepository.saveAll(byId.values());
        // uq_ses_approval_step is DEFERRABLE INITIALLY DEFERRED, so the
        // intermediate states above may legitimately duplicate a step_order.
        // Flushing inside the transaction lets the constraint settle at COMMIT
        // instead of blowing up mid-loop.
        stepRepository.flush();
    }

    /** Closes gaps left by a delete, so step_order stays 1..n with no holes. */
    private void renumber(Long chainId) {
        int order = 1;
        List<SesApprovalStep> steps = stepRepository.findByChainIdOrderByStepOrderAsc(chainId);
        for (SesApprovalStep step : steps) {
            step.setStepOrder(order++);
        }
        stepRepository.saveAll(steps);
        stepRepository.flush();
    }

    // ---------------------------------------------------------------

    @Getter
    @Setter
    public static class ChainEditInput {
        private Long chainId;
        private String chainKey;
        private String chainName;
        private String description;
        private String activeFlag;
        private List<StepInput> steps;
    }

    @Getter
    @Setter
    public static class StepInput {
        private Long stepId;
        private String stepName;
        /** Raw {@link StepRole} name, or empty/unrecognised for an ordinary step. Parsed leniently. */
        private String stepRole;
        /** Steps sharing a number activate together; null = a group of its own = serial. */
        private Integer parallelGroup;
        /** Raw {@link CompletionRule} name; anything unrecognised reads as {@code ANY}. */
        private String completionRule;
        private List<AssigneeInput> assignees;
        private List<StepTaskInput> tasks;
    }

    @Getter
    @Setter
    public static class AssigneeInput {
        private Long assigneeId;
        private AssigneeType assigneeType;
        private String posId;
        private String userId;
    }

    @Getter
    @Setter
    public static class StepTaskInput {
        private Long stepTaskId;
        private String taskLabel;
        private String checkpointKey;
    }
}
