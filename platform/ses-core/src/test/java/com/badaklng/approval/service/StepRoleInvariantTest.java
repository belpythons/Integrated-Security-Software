package com.badaklng.approval.service;

import com.badaklng.approval.entity.StepRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The four chain-shape invariants in
 * {@link ApprovalChainService#validateStepRoles}.
 *
 * <p>Every one of them exists because the alternative is a chain that fails
 * SILENTLY — an approval that skips a step nobody meant to skip, a gate nobody
 * can clear, or a submission approved with no human review. A silent failure
 * is precisely the kind that needs a test, because nothing else will catch
 * it.</p>
 */
class StepRoleInvariantTest {

    private static ApprovalChainService.StepInput step(String name, StepRole role,
                                                       Integer group, String rule) {
        ApprovalChainService.StepInput s = new ApprovalChainService.StepInput();
        s.setStepName(name);
        s.setStepRole(role == null ? null : role.name());
        s.setParallelGroup(group);
        s.setCompletionRule(rule);
        return s;
    }

    /** One (empty) checkpoint list per step — the common case. */
    private static List<List<ApprovalChainService.StepTaskInput>> noCheckpoints(int steps) {
        List<List<ApprovalChainService.StepTaskInput>> out = new ArrayList<>();
        for (int i = 0; i < steps; i++) {
            out.add(Collections.<ApprovalChainService.StepTaskInput>emptyList());
        }
        return out;
    }

    private static ApprovalChainService.StepTaskInput checkpoint(String key) {
        ApprovalChainService.StepTaskInput t = new ApprovalChainService.StepTaskInput();
        t.setTaskLabel(key);
        t.setCheckpointKey(key);
        return t;
    }

    @Test
    @DisplayName("The seeded Badge chain is valid")
    void seededChainIsValid() {
        // The shape of sql/20, serial throughout, per
        // docs/reference/approval flow.jpeg.
        List<ApprovalChainService.StepInput> steps = Arrays.asList(
                step("Additional Routing (Staf Security)", StepRole.SECURITY_STAFF, 1, "ALL"),
                step("Persetujuan Supervisor Procurement", StepRole.PROCUREMENT_SUPERVISOR, 2, "ANY"),
                step("Persetujuan Manager Procurement", StepRole.PROCUREMENT_MANAGER, 3, "ANY"),
                step("Persetujuan Manager Pemohon", StepRole.REQUESTOR_MANAGER, 4, "ANY"),
                step("Persetujuan Manager Security", StepRole.SECURITY_MANAGER, 5, "ANY"),
                step("Officer / Co-Officer Approval", null, 6, "ANY"),
                step("Badge Retrieval", null, 7, "ANY"));

        List<List<ApprovalChainService.StepTaskInput>> tasks = noCheckpoints(7);
        tasks.set(5, Collections.singletonList(checkpoint("SCREENING_SCHEDULE")));
        tasks.set(6, Collections.singletonList(checkpoint("BADGE_RETRIEVAL_SCHEDULE")));

        ApprovalChainService.validateStepRoles(steps, tasks);  // must not throw
    }

    @Test
    @DisplayName("A role used twice is rejected — 'the manager step' must have one referent")
    void duplicateRoleRejected() {
        List<ApprovalChainService.StepInput> steps = Arrays.asList(
                step("A", StepRole.REQUESTOR_MANAGER, 1, "ANY"),
                step("B", StepRole.REQUESTOR_MANAGER, 2, "ANY"),
                step("C", null, 3, "ANY"));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> ApprovalChainService.validateStepRoles(steps, noCheckpoints(3)));
        assertTrue(ex.getMessage().contains("REQUESTOR_MANAGER"), ex.getMessage());
    }

    @Test
    @DisplayName("A chain of nothing but skippable steps is rejected")
    void allSkippableRejected() {
        // Left unchecked, a type with both toggles off would reach advance()
        // with no PENDING task and complete as APPROVED inside trigger() —
        // a submission approved with nobody having looked at it.
        List<ApprovalChainService.StepInput> steps = Arrays.asList(
                step("A", StepRole.REQUESTOR_MANAGER, 1, "ANY"),
                step("B", StepRole.PROCUREMENT_MANAGER, 1, "ANY"));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> ApprovalChainService.validateStepRoles(steps, noCheckpoints(2)));
        assertTrue(ex.getMessage().contains("tanpa review"), ex.getMessage());
    }

    @Test
    @DisplayName("Additional routing does not count as the surviving step")
    void securityStaffDoesNotSatisfyTheSurvivorRule() {
        // SECURITY_STAFF is skipped by an empty evaluator list rather than by
        // a toggle, but the consequence is identical: a submission naming
        // nobody, with the manager toggle off, would have nothing left to
        // approve. Counting it as non-skippable would let this chain SAVE
        // cleanly and then refuse every submission at trigger time.
        List<ApprovalChainService.StepInput> steps = Arrays.asList(
                step("Additional Routing", StepRole.SECURITY_STAFF, 1, "ALL"),
                step("Manager Pemohon", StepRole.REQUESTOR_MANAGER, 2, "ANY"));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> ApprovalChainService.validateStepRoles(steps, noCheckpoints(2)));
        assertTrue(ex.getMessage().contains("tanpa review"), ex.getMessage());
    }

    @Test
    @DisplayName("Both procurement rungs may sit in one chain — they are different roles")
    void bothProcurementRungsAllowed() {
        List<ApprovalChainService.StepInput> steps = Arrays.asList(
                step("Supervisor Procurement", StepRole.PROCUREMENT_SUPERVISOR, 1, "ANY"),
                step("Manager Procurement", StepRole.PROCUREMENT_MANAGER, 2, "ANY"),
                step("Manager Security", StepRole.SECURITY_MANAGER, 3, "ANY"));

        ApprovalChainService.validateStepRoles(steps, noCheckpoints(3));  // must not throw
    }

    @Test
    @DisplayName("One non-skippable step is enough")
    void oneNonSkippableIsEnough() {
        List<ApprovalChainService.StepInput> steps = Arrays.asList(
                step("A", StepRole.REQUESTOR_MANAGER, 1, "ANY"),
                step("B", StepRole.SECURITY_MANAGER, 2, "ANY"));

        ApprovalChainService.validateStepRoles(steps, noCheckpoints(2));  // must not throw
    }

    @Test
    @DisplayName("A skippable step may not carry a checkpoint")
    void checkpointOnSkippableStepRejected() {
        // A checkpoint frozen onto a skipped task stays PENDING forever, and
        // completeCheckpointByKey scans EVERY task of the instance, so it can
        // match that orphan and leave the real gate waiting.
        List<ApprovalChainService.StepInput> steps = Arrays.asList(
                step("Manager Pemohon", StepRole.REQUESTOR_MANAGER, 1, "ANY"),
                step("Security", StepRole.SECURITY_MANAGER, 2, "ANY"));

        List<List<ApprovalChainService.StepTaskInput>> tasks = noCheckpoints(2);
        tasks.set(0, Collections.singletonList(checkpoint("PAYMENT_VERIFICATION")));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> ApprovalChainService.validateStepRoles(steps, tasks));
        assertTrue(ex.getMessage().contains("Manager Pemohon"), ex.getMessage());
    }

    @Test
    @DisplayName("A NON-skippable step may carry a checkpoint")
    void checkpointOnNormalStepAllowed() {
        List<ApprovalChainService.StepInput> steps = Arrays.asList(
                step("Officer", null, 1, "ANY"),
                step("Security", StepRole.SECURITY_MANAGER, 2, "ANY"));

        List<List<ApprovalChainService.StepTaskInput>> tasks = noCheckpoints(2);
        tasks.set(0, Collections.singletonList(checkpoint("SCREENING_SCHEDULE")));

        ApprovalChainService.validateStepRoles(steps, tasks);  // must not throw
    }

    @Test
    @DisplayName("Two consensus steps in one parallel group are rejected")
    void twoConsensusStepsInOneGroupRejected() {
        List<ApprovalChainService.StepInput> steps = Arrays.asList(
                step("A", null, 1, "ALL"),
                step("B", null, 1, "ALL"));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> ApprovalChainService.validateStepRoles(steps, noCheckpoints(2)));
        assertTrue(ex.getMessage().contains("konsensus"), ex.getMessage());
    }

    @Test
    @DisplayName("Two consensus steps in DIFFERENT groups are fine")
    void consensusStepsInDifferentGroupsAllowed() {
        List<ApprovalChainService.StepInput> steps = Arrays.asList(
                step("A", null, 1, "ALL"),
                step("B", null, 2, "ALL"));

        ApprovalChainService.validateStepRoles(steps, noCheckpoints(2));  // must not throw
    }

    @Test
    @DisplayName("Consensus steps with no group number never collide")
    void consensusWithNullGroupNeverCollides() {
        // A null group means "a group of its own", so two of them cannot be
        // concurrent and the ambiguity the rule guards against cannot arise.
        List<ApprovalChainService.StepInput> steps = Arrays.asList(
                step("A", null, null, "ALL"),
                step("B", null, null, "ALL"));

        ApprovalChainService.validateStepRoles(steps, noCheckpoints(2));  // must not throw
    }

    @Test
    @DisplayName("StepRole.parse is lenient and fails to 'no role', never to a guess")
    void parseIsLenient() {
        assertEquals(StepRole.SECURITY_STAFF, StepRole.parse("security_staff"));
        assertEquals(StepRole.REQUESTOR_MANAGER, StepRole.parse(" REQUESTOR_MANAGER "));
        // The safe reading of a value this code does not know is "an ordinary
        // step", never "guess which special step it might be".
        assertEquals(null, StepRole.parse(null));
        assertEquals(null, StepRole.parse(""));
        assertEquals(null, StepRole.parse("NOT_A_ROLE"));
    }

    @Test
    @DisplayName("Manager Security is the only role that always runs")
    void onlySecurityManagerAlwaysRuns() {
        assertTrue(StepRole.SECURITY_STAFF.isSkippable());
        assertTrue(StepRole.REQUESTOR_MANAGER.isSkippable());
        assertTrue(StepRole.PROCUREMENT_SUPERVISOR.isSkippable());
        assertTrue(StepRole.PROCUREMENT_MANAGER.isSkippable());
        assertFalse(StepRole.SECURITY_MANAGER.isSkippable());
    }

    @Test
    @DisplayName("Both procurement rungs answer to the same toggle, and nothing else does")
    void procurementPairIsRecognised() {
        assertTrue(StepRole.PROCUREMENT_SUPERVISOR.isProcurement());
        assertTrue(StepRole.PROCUREMENT_MANAGER.isProcurement());
        assertFalse(StepRole.REQUESTOR_MANAGER.isProcurement());
        assertFalse(StepRole.SECURITY_STAFF.isProcurement());
        assertFalse(StepRole.SECURITY_MANAGER.isProcurement());
    }

    @Test
    @DisplayName("StepRole.parse knows the supervisor rung")
    void parseKnowsProcurementSupervisor() {
        // EH1200 and EH1000 are different chairs. A stored value that parsed
        // to null would make the step an ordinary one: never skipped, and
        // invisible to the procurement toggle.
        assertEquals(StepRole.PROCUREMENT_SUPERVISOR, StepRole.parse("PROCUREMENT_SUPERVISOR"));
        assertEquals(StepRole.PROCUREMENT_SUPERVISOR, StepRole.parse(" procurement_supervisor "));
    }
}
