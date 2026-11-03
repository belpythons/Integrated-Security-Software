package com.badaklng.approval.service;

import com.badaklng.approval.entity.SesApprovalInstance;
import com.badaklng.approval.entity.SesApprovalTask;
import com.badaklng.approval.entity.StepRole;
import com.badaklng.approval.entity.TaskStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which {@code SKIPPED} steps the approval summary hides, and which it must
 * keep showing.
 *
 * <p>Two different events both land on {@code TaskStatus.SKIPPED}, and only
 * one of them is noise. Telling them apart is
 * {@link ApprovalService#isSkippedByConfiguration}, which the engine already
 * uses to decide what to skip in the first place — the visibility filter reads
 * the same rule rather than inventing a second "is this row interesting"
 * test that could disagree with it about the same row.</p>
 *
 * <p>These tests pin the DISTINCTION, not the rendering. The failure they
 * guard against is the easy over-correction: a blanket
 * {@code status != 'SKIPPED'} filter, which also hides the skips a human
 * decided on and leaves an approval whose outcome has no visible
 * explanation.</p>
 *
 * <p>Plain JUnit; the rule is static and takes plain entities for exactly this
 * reason.</p>
 */
class SkippedStepVisibilityTest {

    private static SesApprovalInstance instance(String mgrFlag, String procFlag) {
        SesApprovalInstance i = new SesApprovalInstance();
        i.setInstanceId(1L);
        i.setNeedsMgrApprovalFlag(mgrFlag);
        i.setNeedsProcApprovalFlag(procFlag);
        return i;
    }

    private static SesApprovalTask task(StepRole role, TaskStatus status) {
        SesApprovalTask t = new SesApprovalTask();
        t.setTaskId(10L);
        t.setStepRole(role);
        t.setStatus(status);
        return t;
    }

    /** The filter, as the template sees it: hide only a configured skip. */
    private static boolean hidden(SesApprovalTask t, SesApprovalInstance i, boolean hasEvaluators) {
        return t.getStatus() == TaskStatus.SKIPPED
                && ApprovalService.isSkippedByConfiguration(t, i, hasEvaluators);
    }

    // ---------------------------------------------------------------
    // Hidden: nobody was ever asked
    // ---------------------------------------------------------------

    @Test
    @DisplayName("A manager step the request type switched off is hidden")
    void managerStepOffIsHidden() {
        assertTrue(hidden(task(StepRole.REQUESTOR_MANAGER, TaskStatus.SKIPPED),
                instance("N", "Y"), true));
    }

    @Test
    @DisplayName("Both procurement rungs are hidden by the one toggle")
    void procurementStepsOffAreHidden() {
        // One toggle governs the pair; hiding one rung and not the other would
        // show half a leg that never ran.
        SesApprovalInstance i = instance("Y", "N");
        assertTrue(hidden(task(StepRole.PROCUREMENT_SUPERVISOR, TaskStatus.SKIPPED), i, true));
        assertTrue(hidden(task(StepRole.PROCUREMENT_MANAGER, TaskStatus.SKIPPED), i, true));
    }

    @Test
    @DisplayName("An Additional Routing step the requestor named nobody for is hidden")
    void unusedAdditionalRoutingIsHidden() {
        // Naming nobody is a legitimate choice, so this step is not a decision
        // anyone declined to make — it simply did not apply.
        assertTrue(hidden(task(StepRole.SECURITY_STAFF, TaskStatus.SKIPPED),
                instance("Y", "Y"), false));
    }

    // ---------------------------------------------------------------
    // Shown: somebody decided something
    // ---------------------------------------------------------------

    @Test
    @DisplayName("A step a REVIEWER skipped stays visible")
    void reviewerSkipStaysVisible() {
        // THE assertion in this class. A blanket status != SKIPPED filter —
        // the obvious reading of "hide skipped stages" — would hide this, and
        // then an approval could show an outcome with no record of who passed
        // the step over. The toggles are ON here, so nothing explains the skip
        // except a human.
        assertFalse(hidden(task(StepRole.REQUESTOR_MANAGER, TaskStatus.SKIPPED),
                instance("Y", "Y"), true),
                "a skip with no configuration behind it is a human decision and is audit, not noise");
    }

    @Test
    @DisplayName("Steps SKIPPED by a cancellation stay visible")
    void cancellationSkipStaysVisible() {
        // cancel() marks every remaining PENDING task SKIPPED with the cancel
        // reason. Those rows ARE the explanation of why the request stopped.
        // A role-less step is every step of every chain that predates the
        // hybrid engine, so this also covers the legacy shape.
        assertFalse(hidden(task(null, TaskStatus.SKIPPED), instance("Y", "Y"), true));
        assertFalse(hidden(task(StepRole.SECURITY_MANAGER, TaskStatus.SKIPPED),
                instance("N", "N"), false),
                "SECURITY_MANAGER is never skippable by configuration, so its skip is always a decision");
    }

    @Test
    @DisplayName("An Additional Routing step WITH evaluators that got skipped stays visible")
    void routingWithEvaluatorsSkippedStaysVisible() {
        // The requestor did name people. If the step still ended up skipped,
        // something decided that — and the reader needs to see it.
        assertFalse(hidden(task(StepRole.SECURITY_STAFF, TaskStatus.SKIPPED),
                instance("Y", "Y"), true));
    }

    // ---------------------------------------------------------------
    // Non-skipped rows are never touched
    // ---------------------------------------------------------------

    @Test
    @DisplayName("No non-SKIPPED step is ever hidden, whatever the toggles say")
    void onlySkippedRowsAreEverHidden() {
        // isSkippedByConfiguration answers "would configuration skip this
        // role", which is true for a REQUESTOR_MANAGER step even when the task
        // is sitting PENDING. The status check in the filter is what stops
        // that from hiding a live step — losing it would make the step the
        // instance is CURRENTLY waiting on vanish from the page.
        SesApprovalInstance off = instance("N", "N");
        for (TaskStatus s : new TaskStatus[]{TaskStatus.PENDING, TaskStatus.APPROVED, TaskStatus.REJECTED}) {
            assertFalse(hidden(task(StepRole.REQUESTOR_MANAGER, s), off, false),
                    s + " must never be hidden");
            assertFalse(hidden(task(StepRole.PROCUREMENT_MANAGER, s), off, false),
                    s + " must never be hidden");
        }
    }

    // ---------------------------------------------------------------
    // Relationship to the editability rule — they are NOT the same rule
    // ---------------------------------------------------------------

    @Test
    @DisplayName("Visibility is STRICTER than isReviewerDecision, deliberately")
    void visibilityIsStricterThanEditability() {
        // Worth pinning, because the two look like the same question and are
        // not. isReviewerDecision keys on the ROLE alone: any SKIPPED task
        // whose role is skippable is treated as "not a decision", without
        // consulting the instance flags. That is right for its own purpose —
        // deciding whether a submission may still be edited, where the safe
        // answer is the permissive one.
        //
        // Visibility consults the FROZEN FLAGS as well, and so is more
        // precise. The gap between them is this case: a REQUESTOR_MANAGER step
        // skipped while needs_mgr_approval_flag is still 'Y'. Configuration
        // did not skip it — the resolver did, because the requestor is
        // themselves manager-level (see activateTask's allSkip branch). That
        // is a real explanation of why a configured step did not run for this
        // particular person, so the row stays.
        //
        // Reusing isReviewerDecision for the filter would hide it, and the
        // reader would be left wondering why the chain has a Manager Pemohon
        // step that simply is not there.
        SesApprovalTask autoSkipped = task(StepRole.REQUESTOR_MANAGER, TaskStatus.SKIPPED);
        SesApprovalInstance toggleStillOn = instance("Y", "Y");

        assertFalse(ApprovalService.isReviewerDecision(autoSkipped),
                "isReviewerDecision is coarse: a skippable role's skip never counts as a decision");
        assertFalse(hidden(autoSkipped, toggleStillOn, true),
                "but visibility keeps it, because configuration is not what skipped it");
    }

    @Test
    @DisplayName("Where the two rules do overlap, they do not contradict")
    void noContradictionOnTheConfiguredCase() {
        // A genuinely configured skip: neither rule calls it a decision, and
        // it is hidden. This is the common case and the two must agree on it.
        SesApprovalTask configured = task(StepRole.PROCUREMENT_MANAGER, TaskStatus.SKIPPED);
        SesApprovalInstance toggleOff = instance("Y", "N");

        assertFalse(ApprovalService.isReviewerDecision(configured));
        assertTrue(hidden(configured, toggleOff, true));

        // And a step nothing can switch off: a decision by both rules, shown.
        SesApprovalTask securityManager = task(StepRole.SECURITY_MANAGER, TaskStatus.SKIPPED);
        assertTrue(ApprovalService.isReviewerDecision(securityManager));
        assertFalse(hidden(securityManager, toggleOff, true));
    }
}
