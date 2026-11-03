package com.badaklng.approval.service;

import com.badaklng.approval.entity.AssigneeType;
import com.badaklng.approval.entity.ResolutionSource;
import com.badaklng.approval.entity.SesApprovalTaskAssignee;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards who may press Approve when a position changes hands.
 *
 * <p>The case this exists for, from ELLDEV: POS {@code E01000} MANAGER,
 * SECURITY — substantive holder 135847 (Bambang), stand-in 135659 (Roji)
 * whose PJS ran 24-29 Sep. A request approved during that window froze
 * Roji's badge onto {@code ses_approval_task_assignee}. On 30 Sep the
 * authority is Bambang's again, but the frozen row still said Roji, so
 * Bambang saw the request in his inbox with no buttons on it.</p>
 *
 * <p>No Spring context and no Oracle: {@link ApprovalResolverService#decide}
 * is a pure function over already-fetched data, split out of {@code canAct}
 * for exactly this reason. The three {@code @SpringBootTest} classes in this
 * repo all need a live ELLDEV and all currently fail without one — an
 * authorization rule that can only be tested that way is a rule that does
 * not get tested.</p>
 */
class ApprovalCanActTest {

    private static final String POS = "E01000";
    private static final String BAMBANG = "135847";   // substantive holder
    private static final String ROJI = "135659";      // stand-in, PJS 24-29 Sep
    private static final String STRANGER = "999999";

    private static SesApprovalTaskAssignee frozen(AssigneeType type, String posId, String emplId) {
        SesApprovalTaskAssignee a = new SesApprovalTaskAssignee();
        a.setAssigneeType(type);
        a.setPosId(posId);
        a.setEmplId(emplId);
        a.setResolutionSource(resolutionSourceFor(type));
        return a;
    }

    /** Mirrors what ApprovalResolverService actually stamps on each resolved row. */
    private static ResolutionSource resolutionSourceFor(AssigneeType type) {
        switch (type) {
            case USER:
                return ResolutionSource.STATIC_USER;
            case REQUESTOR_MANAGER:
                return ResolutionSource.DYNAMIC_MANAGER;
            case REPORT_TO:
                return ResolutionSource.REPORT_TO_DIRECT;
            default:
                return ResolutionSource.STATIC_POSITION;
        }
    }

    private static Map<String, List<String>> holders(String posId, String... badges) {
        Map<String, List<String>> m = new HashMap<>();
        m.put(posId, Arrays.asList(badges));
        return m;
    }

    // ---------------------------------------------------------------
    // Decision 1 — while a PJS is running, only the PJS may act
    // ---------------------------------------------------------------

    @Test
    void duringActivePjsOnlyTheStandInMayAct() {
        // Task frozen to Roji, and the live view agrees he is the holder.
        List<SesApprovalTaskAssignee> assignees =
                Collections.singletonList(frozen(AssigneeType.POSITION, POS, ROJI));
        Map<String, List<String>> live = holders(POS, ROJI);

        assertTrue(ApprovalResolverService.decide(assignees, live, ROJI, null),
                "the acting PJS must be able to approve");
        assertFalse(ApprovalResolverService.decide(assignees, live, BAMBANG, null),
                "the substantive holder is stood down while the PJS is running");
    }

    // ---------------------------------------------------------------
    // Decision 2 — once the PJS ends, authority moves, it is not shared
    // ---------------------------------------------------------------

    @Test
    void afterPjsEndsAuthorityMovesToTheSubstantiveHolder() {
        // Still frozen to Roji — that row is never rewritten. The live view
        // has moved on to Bambang now that the window has closed.
        List<SesApprovalTaskAssignee> assignees =
                Collections.singletonList(frozen(AssigneeType.POSITION, POS, ROJI));
        Map<String, List<String>> live = holders(POS, BAMBANG);

        assertTrue(ApprovalResolverService.decide(assignees, live, BAMBANG, null),
                "the incoming holder must get the buttons — this is the reported bug");
        assertFalse(ApprovalResolverService.decide(assignees, live, ROJI, null),
                "the former stand-in must LOSE the buttons, even though the frozen "
                        + "row still carries their badge");
    }

    @Test
    void aStrangerNeverGetsIn() {
        List<SesApprovalTaskAssignee> assignees =
                Collections.singletonList(frozen(AssigneeType.POSITION, POS, ROJI));
        assertFalse(ApprovalResolverService.decide(assignees, holders(POS, BAMBANG), STRANGER, null));
    }

    // ---------------------------------------------------------------
    // Co-holders — a position can legitimately have several
    // ---------------------------------------------------------------

    @Test
    void everyCoHolderOfThePositionMayAct() {
        List<SesApprovalTaskAssignee> assignees =
                Collections.singletonList(frozen(AssigneeType.POSITION, POS, BAMBANG));
        Map<String, List<String>> live = holders(POS, BAMBANG, "133891");

        assertTrue(ApprovalResolverService.decide(assignees, live, BAMBANG, null));
        assertTrue(ApprovalResolverService.decide(assignees, live, "133891", null),
                "co-officers are equal approvers; APP_POSITION_VIEW has one row each");
    }

    // ---------------------------------------------------------------
    // The safety net — a position that resolves to nobody
    // ---------------------------------------------------------------

    @Test
    void positionWithNoLiveHolderFallsBackToTheFrozenBadge() {
        // Vacant position / gap in org data. Without the fallback the task
        // would be approvable by nobody at all except an emergency approver.
        List<SesApprovalTaskAssignee> assignees =
                Collections.singletonList(frozen(AssigneeType.POSITION, POS, ROJI));
        Map<String, List<String>> live = holders(POS); // present, but empty

        assertTrue(ApprovalResolverService.decide(assignees, live, ROJI, null),
                "a task must never be orphaned by a gap in position data");
    }

    @Test
    void positionMissingFromTheLookupAlsoFallsBackToFrozen() {
        List<SesApprovalTaskAssignee> assignees =
                Collections.singletonList(frozen(AssigneeType.POSITION, POS, ROJI));

        assertTrue(ApprovalResolverService.decide(assignees, new HashMap<>(), ROJI, null),
                "no entry for the position is the same situation as an empty one");
    }

    // ---------------------------------------------------------------
    // USER-type assignees are untouched by any of this
    // ---------------------------------------------------------------

    @Test
    void userTypeAssigneeStaysPurelyFrozen() {
        // No pos_id, so nothing to resolve live — ApprovalResolverService
        // .resolveUser never sets one.
        List<SesApprovalTaskAssignee> assignees =
                Collections.singletonList(frozen(AssigneeType.USER, null, "admin"));

        assertTrue(ApprovalResolverService.decide(assignees, new HashMap<>(), "admin", null));
        assertFalse(ApprovalResolverService.decide(assignees, new HashMap<>(), BAMBANG, null),
                "holding some position must not grant access to a USER-targeted step");
    }

    @Test
    void userTypeAssigneeAlsoMatchesOnUserId() {
        SesApprovalTaskAssignee a = frozen(AssigneeType.USER, null, "admin");
        a.setUserId("00000000-0000-0000-0000-000000000001");
        List<SesApprovalTaskAssignee> assignees = Collections.singletonList(a);

        assertTrue(ApprovalResolverService.decide(assignees, new HashMap<>(), null,
                "00000000-0000-0000-0000-000000000001"));
        assertFalse(ApprovalResolverService.decide(assignees, new HashMap<>(), null, "some-other-uuid"));
    }

    // ---------------------------------------------------------------
    // Mixed steps — a step may carry both kinds at once
    // ---------------------------------------------------------------

    @Test
    void mixedStepAuthorisesLiveHolderAndFixedUserButNotTheFormerStandIn() {
        // The shape ELLDEV actually has on tasks 48 and 74: one POSITION row
        // plus a USER row for admin.
        List<SesApprovalTaskAssignee> assignees = Arrays.asList(
                frozen(AssigneeType.POSITION, POS, ROJI),
                frozen(AssigneeType.USER, null, "admin"));
        Map<String, List<String>> live = holders(POS, BAMBANG);

        assertTrue(ApprovalResolverService.decide(assignees, live, BAMBANG, null));
        assertTrue(ApprovalResolverService.decide(assignees, live, "admin", null));
        assertFalse(ApprovalResolverService.decide(assignees, live, ROJI, null),
                "the USER row must not accidentally rescue the former stand-in");
    }

    // ---------------------------------------------------------------
    // REQUESTOR_MANAGER carries a pos_id too, so it follows the same rule
    // ---------------------------------------------------------------

    @Test
    void requestorManagerAssigneeIsAlsoResolvedLive() {
        List<SesApprovalTaskAssignee> assignees =
                Collections.singletonList(frozen(AssigneeType.REQUESTOR_MANAGER, POS, ROJI));
        Map<String, List<String>> live = holders(POS, BAMBANG);

        assertTrue(ApprovalResolverService.decide(assignees, live, BAMBANG, null));
        assertFalse(ApprovalResolverService.decide(assignees, live, ROJI, null));
    }

    // ---------------------------------------------------------------
    // Edge cases that must not throw
    // ---------------------------------------------------------------

    @Test
    void noAssigneesMeansNobodyCanAct() {
        assertFalse(ApprovalResolverService.decide(Collections.emptyList(), new HashMap<>(), BAMBANG, null));
        assertFalse(ApprovalResolverService.decide(null, new HashMap<>(), BAMBANG, null));
    }

    @Test
    void managerSkipStepNeedsNoActor() {
        SesApprovalTaskAssignee a = frozen(AssigneeType.REQUESTOR_MANAGER, POS, ROJI);
        a.setResolutionSource(ResolutionSource.MANAGER_SKIP);

        assertFalse(ApprovalResolverService.decide(Collections.singletonList(a),
                holders(POS, BAMBANG), BAMBANG, null),
                "a skipped step should never ask anyone to act");
    }

    @Test
    void paddedIdsStillMatch() {
        // ELLDEV ids are clean today, but SUPERIOR_ID in the same view is a
        // padded CHAR, so the comparison should not depend on that luck.
        List<SesApprovalTaskAssignee> assignees =
                Collections.singletonList(frozen(AssigneeType.POSITION, "  " + POS + " ", ROJI));
        Map<String, List<String>> live = holders(POS, BAMBANG);

        assertTrue(ApprovalResolverService.decide(assignees, live, " " + BAMBANG + "  ", null));
    }

    @Test
    void nullUsernameAndUserIdCannotAct() {
        List<SesApprovalTaskAssignee> assignees =
                Collections.singletonList(frozen(AssigneeType.POSITION, POS, ROJI));
        assertFalse(ApprovalResolverService.decide(assignees, holders(POS, BAMBANG), null, null));
    }
}
