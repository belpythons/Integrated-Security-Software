package com.badaklng.approval.service;

import com.badaklng.approval.entity.CompletionRule;
import com.badaklng.approval.entity.SesApprovalInstance;
import com.badaklng.approval.entity.SesApprovalTask;
import com.badaklng.approval.entity.StepRole;
import com.badaklng.approval.entity.TaskStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The routing rules: which group opens next, and which steps a given
 * submission switches off.
 *
 * <p>Plain JUnit, no Spring. {@code nextGroup}, {@code groupOf},
 * {@code willBeSkipped} and {@code isSkippedByConfiguration} are {@code static}
 * and operate on plain entities for exactly this reason — routing logic that
 * can only be exercised through {@code @SpringBootTest} against a live ELLDEV
 * is routing logic that does not get tested.</p>
 *
 * <p>The scenario throughout is the Badge chain seeded by {@code sql/20}, which
 * is <b>fully serial</b> — one step per group — per
 * {@code docs/reference/approval flow.jpeg}:</p>
 * <pre>
 *   grp 1  step 1  Additional Routing     SECURITY_STAFF          ALL   skipped when nobody is named
 *   grp 2  step 2  Supervisor Procurement PROCUREMENT_SUPERVISOR  ANY   procurement toggle
 *   grp 3  step 3  Manager Procurement    PROCUREMENT_MANAGER     ANY   procurement toggle
 *   grp 4  step 4  Manager Pemohon        REQUESTOR_MANAGER       ANY   manager toggle
 *   grp 5  step 5  Manager Security       SECURITY_MANAGER        ANY   never skipped
 *   grp 6  step 6  Officer / Co-Officer   (none)                  ANY
 *   grp 7  step 7  Badge Retrieval        (none)                  ANY
 * </pre>
 *
 * <p>An earlier revision ran the two managers as one parallel group. The
 * parallel machinery is still here and still tested ({@link
 * #explicitParallelGroupStillOpensTogether}) — other chains may use it, and
 * in-flight instances created under the old shape must keep working — but
 * Badge itself no longer does.</p>
 */
class HybridRoutingTest {

    private static SesApprovalTask task(int stepOrder, Integer group, StepRole role, CompletionRule rule) {
        SesApprovalTask t = new SesApprovalTask();
        t.setTaskId((long) stepOrder);
        t.setStepOrder(stepOrder);
        t.setStepName("Step " + stepOrder);
        t.setParallelGroup(group);
        t.setStepRole(role);
        t.setCompletionRule(rule);
        t.setStatus(TaskStatus.PENDING);
        return t;
    }

    private static List<SesApprovalTask> badgeChain() {
        return new ArrayList<>(Arrays.asList(
                task(1, 1, StepRole.SECURITY_STAFF, CompletionRule.ALL),
                task(2, 2, StepRole.PROCUREMENT_SUPERVISOR, CompletionRule.ANY),
                task(3, 3, StepRole.PROCUREMENT_MANAGER, CompletionRule.ANY),
                task(4, 4, StepRole.REQUESTOR_MANAGER, CompletionRule.ANY),
                task(5, 5, StepRole.SECURITY_MANAGER, CompletionRule.ANY),
                task(6, 6, null, CompletionRule.ANY),
                task(7, 7, null, CompletionRule.ANY)));
    }

    private static SesApprovalInstance instance(String mgrFlag, String procFlag) {
        SesApprovalInstance i = new SesApprovalInstance();
        i.setNeedsMgrApprovalFlag(mgrFlag);
        i.setNeedsProcApprovalFlag(procFlag);
        return i;
    }

    private static ApprovalService.TriggerOptions opts(boolean mgr, boolean proc, int evaluatorCount) {
        List<ApprovalService.EvaluatorInput> evaluators = new ArrayList<>();
        for (int i = 0; i < evaluatorCount; i++) {
            evaluators.add(new ApprovalService.EvaluatorInput(
                    "10000" + i, "Evaluator " + i, "E0100" + i, "STAF SECURITY", null));
        }
        return new ApprovalService.TriggerOptions(mgr, proc, evaluators);
    }

    private static List<Integer> stepOrders(List<SesApprovalTask> tasks) {
        return tasks.stream().map(SesApprovalTask::getStepOrder).collect(Collectors.toList());
    }

    private static List<SesApprovalTask> pendingOf(List<SesApprovalTask> tasks) {
        return tasks.stream()
                .filter(t -> t.getStatus() == TaskStatus.PENDING).collect(Collectors.toList());
    }

    /**
     * Replays what {@code activateTask} would do to a whole chain: every task
     * the configuration switches off becomes SKIPPED, and what is left is what
     * {@code advance()} has to choose from.
     */
    private static List<SesApprovalTask> afterSkips(List<SesApprovalTask> tasks,
                                                    SesApprovalInstance inst,
                                                    boolean hasEvaluators) {
        for (SesApprovalTask t : tasks) {
            if (t.getStatus() == TaskStatus.PENDING
                    && ApprovalService.isSkippedByConfiguration(t, inst, hasEvaluators)) {
                t.setStatus(TaskStatus.SKIPPED);
            }
        }
        return pendingOf(tasks);
    }

    // ---------------------------------------------------------------
    // Serial ordering
    // ---------------------------------------------------------------

    @Test
    @DisplayName("The first group of the Badge chain is step 1 alone")
    void firstGroupIsStepOne() {
        assertEquals(Collections.singletonList(1), stepOrders(ApprovalService.nextGroup(badgeChain())));
    }

    @Test
    @DisplayName("Badge runs strictly one step at a time: 1 then 2 then 3 then 4 then 5")
    void badgeChainIsSerial() {
        List<SesApprovalTask> tasks = badgeChain();
        List<Integer> opened = new ArrayList<>();

        for (int guard = 0; guard < tasks.size(); guard++) {
            List<SesApprovalTask> group = ApprovalService.nextGroup(pendingOf(tasks));
            if (group.isEmpty()) {
                break;
            }
            // THE property under test: never more than one step open at once.
            assertEquals(1, group.size(), "Badge steps must not run in parallel");
            opened.add(group.get(0).getStepOrder());
            group.get(0).setStatus(TaskStatus.APPROVED);
        }

        assertEquals(Arrays.asList(1, 2, 3, 4, 5, 6, 7), opened);
    }

    @Test
    @DisplayName("Procurement is two serial rungs: the supervisor approves before the manager is asked")
    void procurementIsTwoSerialRungs() {
        List<SesApprovalTask> tasks = badgeChain();
        tasks.get(0).setStatus(TaskStatus.APPROVED);  // additional routing done

        // Supervisor alone — the manager must not see it yet.
        assertEquals(Collections.singletonList(2), stepOrders(ApprovalService.nextGroup(pendingOf(tasks))));

        tasks.get(1).setStatus(TaskStatus.APPROVED);
        assertEquals(Collections.singletonList(3), stepOrders(ApprovalService.nextGroup(pendingOf(tasks))));
    }

    @Test
    @DisplayName("A SKIPPED task settles its group just as an APPROVED one does")
    void skippedCountsAsSettled() {
        List<SesApprovalTask> tasks = badgeChain();
        tasks.get(0).setStatus(TaskStatus.APPROVED);
        tasks.get(1).setStatus(TaskStatus.SKIPPED);
        tasks.get(2).setStatus(TaskStatus.SKIPPED);

        assertEquals(Collections.singletonList(4), stepOrders(ApprovalService.nextGroup(pendingOf(tasks))));
    }

    @Test
    @DisplayName("Two steps sharing a parallel_group still open together")
    void explicitParallelGroupStillOpensTogether() {
        // Badge is serial now, but the parallel capability is what in-flight
        // instances created under the old shape still run on, and the chain
        // builder still offers it. Deleting this test would let the capability
        // rot unnoticed.
        List<SesApprovalTask> shared = Arrays.asList(
                task(1, 1, null, CompletionRule.ANY),
                task(2, 2, null, CompletionRule.ANY),
                task(3, 2, null, CompletionRule.ANY),
                task(4, 3, null, CompletionRule.ANY));

        assertEquals(Collections.singletonList(1), stepOrders(ApprovalService.nextGroup(shared)));
        shared.get(0).setStatus(TaskStatus.APPROVED);
        assertEquals(Arrays.asList(2, 3), stepOrders(ApprovalService.nextGroup(pendingOf(shared))));
    }

    @Test
    @DisplayName("A chain with no parallel_group behaves exactly like the old serial engine")
    void nullGroupFallsBackToStepOrder() {
        // This is the back-compat guarantee for every chain that existed
        // before the hybrid engine, and for every in-flight instance (whose
        // task rows are deliberately not backfilled).
        List<SesApprovalTask> serial = Arrays.asList(
                task(1, null, null, CompletionRule.ANY),
                task(2, null, null, CompletionRule.ANY),
                task(3, null, null, CompletionRule.ANY));

        assertEquals(Collections.singletonList(1), stepOrders(ApprovalService.nextGroup(serial)));
        assertEquals(1, ApprovalService.groupOf(serial.get(0)));
        assertEquals(3, ApprovalService.groupOf(serial.get(2)));
    }

    @Test
    @DisplayName("Nothing pending means no next group — the instance is finished")
    void emptyPendingYieldsEmptyGroup() {
        assertTrue(ApprovalService.nextGroup(Collections.<SesApprovalTask>emptyList()).isEmpty());
        assertTrue(ApprovalService.nextGroup(null).isEmpty());
    }

    // ---------------------------------------------------------------
    // Skip rules at submit time
    // ---------------------------------------------------------------

    @Test
    @DisplayName("One toggle governs BOTH procurement rungs")
    void procurementToggleGovernsBothRungs() {
        ApprovalService.TriggerOptions noProc = opts(true, false, 1);

        assertTrue(ApprovalService.willBeSkipped(StepRole.PROCUREMENT_SUPERVISOR, noProc));
        assertTrue(ApprovalService.willBeSkipped(StepRole.PROCUREMENT_MANAGER, noProc));
        // Half a procurement leg — supervisor asked, manager not — is the
        // failure this pairing exists to prevent.
        assertFalse(ApprovalService.willBeSkipped(StepRole.REQUESTOR_MANAGER, noProc));

        ApprovalService.TriggerOptions noMgr = opts(false, true, 1);
        assertFalse(ApprovalService.willBeSkipped(StepRole.PROCUREMENT_SUPERVISOR, noMgr));
        assertFalse(ApprovalService.willBeSkipped(StepRole.PROCUREMENT_MANAGER, noMgr));
        assertTrue(ApprovalService.willBeSkipped(StepRole.REQUESTOR_MANAGER, noMgr));
    }

    @Test
    @DisplayName("Additional routing is optional: no evaluators means the step is skipped, not rejected")
    void additionalRoutingIsOptional() {
        assertTrue(ApprovalService.willBeSkipped(StepRole.SECURITY_STAFF, opts(true, true, 0)));
        assertFalse(ApprovalService.willBeSkipped(StepRole.SECURITY_STAFF, opts(true, true, 1)));
    }

    @Test
    @DisplayName("Manager Security has no toggle and no empty-list escape — it is never skipped")
    void securityManagerIsNeverSkipped() {
        assertFalse(ApprovalService.willBeSkipped(StepRole.SECURITY_MANAGER, opts(false, false, 0)));
        assertFalse(ApprovalService.willBeSkipped(null, opts(false, false, 0)));
    }

    @Test
    @DisplayName("Defaults skip nothing a toggle controls")
    void defaultsSkipNothingConfigurable() {
        ApprovalService.TriggerOptions defaults = ApprovalService.TriggerOptions.defaults();
        assertTrue(defaults.getEvaluators().isEmpty());

        for (StepRole role : StepRole.values()) {
            if (role == StepRole.SECURITY_STAFF) {
                // Not a regression for pre-existing callers: no chain they
                // trigger carries this role, and a SECURITY_STAFF step with no
                // evaluators has nothing to do either way.
                assertTrue(ApprovalService.willBeSkipped(role, defaults));
                continue;
            }
            assertFalse(ApprovalService.willBeSkipped(role, defaults),
                    role + " must not be skipped by default");
        }
    }

    // ---------------------------------------------------------------
    // Skip rules at activation time (frozen state)
    // ---------------------------------------------------------------

    @Test
    @DisplayName("Activation reads the instance's FROZEN flags, not live configuration")
    void activationUsesFrozenFlags() {
        SesApprovalTask supv = task(2, 2, StepRole.PROCUREMENT_SUPERVISOR, CompletionRule.ANY);
        SesApprovalTask proc = task(3, 3, StepRole.PROCUREMENT_MANAGER, CompletionRule.ANY);
        SesApprovalTask mgr = task(4, 4, StepRole.REQUESTOR_MANAGER, CompletionRule.ANY);
        SesApprovalTask sec = task(5, 5, StepRole.SECURITY_MANAGER, CompletionRule.ANY);

        SesApprovalInstance both = instance("N", "N");
        assertTrue(ApprovalService.isSkippedByConfiguration(supv, both, true));
        assertTrue(ApprovalService.isSkippedByConfiguration(proc, both, true));
        assertTrue(ApprovalService.isSkippedByConfiguration(mgr, both, true));
        assertFalse(ApprovalService.isSkippedByConfiguration(sec, both, true));

        SesApprovalInstance neither = instance("Y", "Y");
        assertFalse(ApprovalService.isSkippedByConfiguration(supv, neither, true));
        assertFalse(ApprovalService.isSkippedByConfiguration(proc, neither, true));
        assertFalse(ApprovalService.isSkippedByConfiguration(mgr, neither, true));
    }

    @Test
    @DisplayName("The evaluator step is skipped from its own sign-off rows, not from an instance flag")
    void evaluatorStepSkipsOnEmptySignoffs() {
        SesApprovalTask add = task(1, 1, StepRole.SECURITY_STAFF, CompletionRule.ALL);
        SesApprovalInstance bothOn = instance("Y", "Y");

        assertTrue(ApprovalService.isSkippedByConfiguration(add, bothOn, false));
        assertFalse(ApprovalService.isSkippedByConfiguration(add, bothOn, true));
    }

    @Test
    @DisplayName("A task from before the hybrid engine is never skipped")
    void legacyTaskNeverSkipped() {
        // Tasks of in-flight instances are deliberately NOT backfilled, so
        // their step_role is null. They must keep the never-skipped behaviour
        // they were started under even if the instance carries 'N' flags.
        SesApprovalTask legacy = task(1, null, null, CompletionRule.ANY);
        assertFalse(ApprovalService.isSkippedByConfiguration(legacy, instance("N", "N"), false));
    }

    // ---------------------------------------------------------------
    // The whole flow
    // ---------------------------------------------------------------

    @Test
    @DisplayName("Direct bypass: both toggles off and nobody named lands on Manager Security")
    void directBypassLandsOnSecurityManager() {
        // The headline case of docs/reference/approval flow.jpeg: steps 1-4
        // all fall away and the FIRST task anyone ever sees is step 5.
        List<SesApprovalTask> first =
                ApprovalService.nextGroup(afterSkips(badgeChain(), instance("N", "N"), false));

        assertEquals(Collections.singletonList(5), stepOrders(first));
        assertEquals(StepRole.SECURITY_MANAGER, first.get(0).getStepRole());
    }

    @Test
    @DisplayName("Bypassing everything still leaves a human in the loop")
    void bypassNeverAutoApproves() {
        // The reason the direct bypass is safe to offer at all. If this ever
        // yields an empty group, a Badge request reaches advance() with
        // nothing PENDING and completes as APPROVED inside trigger().
        assertFalse(ApprovalService.nextGroup(
                afterSkips(badgeChain(), instance("N", "N"), false)).isEmpty());
    }

    @Test
    @DisplayName("Procurement off but routing named: step 1, then straight to Manager Pemohon")
    void procurementOffSkipsBothRungs() {
        List<SesApprovalTask> tasks = badgeChain();
        assertEquals(Collections.singletonList(1),
                stepOrders(ApprovalService.nextGroup(afterSkips(tasks, instance("Y", "N"), true))));

        tasks.get(0).setStatus(TaskStatus.APPROVED);
        assertEquals(Collections.singletonList(4),
                stepOrders(ApprovalService.nextGroup(pendingOf(tasks))));
    }
}
