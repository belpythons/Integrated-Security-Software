package com.badaklng.approval.service;

import com.badaklng.approval.entity.AppPositionView;
import com.badaklng.approval.entity.AssigneeType;
import com.badaklng.approval.entity.ResolutionSource;
import com.badaklng.approval.entity.SesApprovalAssignee;
import com.badaklng.approval.entity.SesApprovalTaskAssignee;
import com.badaklng.approval.repository.AppPositionViewRepository;
import com.badaklng.ses.SesApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Verifies REQUESTOR_MANAGER (PIC) resolution against the real ELLDEV org
 * data, the same way {@code ViewIntegrationTest} does for the lookup views:
 * needs {@code application-local.properties} and a live DB connection.
 *
 * The badges below were picked by querying APP_EMP_INFO_VIEW /
 * APP_POSITION_VIEW for one clean example of each rung of the ladder.
 * Assertions check the resolved POSITION rather than a person's name where
 * possible, and every case also asserts the invariant that actually
 * matters — the PIC is never the requestor — so a reorganisation makes the
 * test fail loudly rather than pass vacuously.
 *
 * <p>The top-of-tree case is resolved DYNAMICALLY (see
 * {@link #topOfTreeHolderBadge()}); it used to pin a badge and broke the first
 * time the seat changed hands. The other three rungs are still pinned, and
 * will rot the same way: they name one specific ordinary-staff / section-head
 * / department-head badge each, and a reorganisation that moves any of them
 * will fail this test. That is deliberate — those three assert a SHAPE
 * ("escalates to the department head") that a dynamically-chosen badge could
 * not pin down without reimplementing the ladder inside the test. When one
 * does break, re-pick the badge against current data; do not weaken the
 * assertion.</p>
 */
@SpringBootTest(classes = SesApplication.class)
@ActiveProfiles("local")
class ApprovalResolverManagerTest {

    /** Ordinary staff in section CB4000; section head is somebody else. */
    private static final String STAFF = "126823";
    private static final String STAFF_SECTION_POS = "CB4000";

    /** Head of section BD2000 — must escalate to department BD0000. */
    private static final String SECTION_HEAD = "132026";
    private static final String SECTION_HEAD_DEPT_POS = "BD0000";

    /** Head of department BD0000 — must escalate up SUPERIOR_ID to A00000. */
    private static final String DEPT_HEAD = "130662";
    private static final String DEPT_HEAD_SUPERIOR_POS = "A00000";

    /**
     * The sentinel {@code APP_POSITION_VIEW.SUPERIOR_ID} carries on the one
     * position that has nothing above it. Exactly one row holds it
     * (A00000, PRESIDENT DIRECTOR &amp; CEO), which is what makes it usable as
     * a lookup key.
     *
     * <p>Note what is deliberately NOT used as the root test: "SUPERIOR_ID is
     * not present as any POS_ID". That matches 25 rows on ELLDEV today, almost
     * all of them ordinary positions whose parent simply is not in the view —
     * data gaps, not roots. A sentinel that means "root" is the only honest
     * signal here.</p>
     */
    private static final String ROOT_SUPERIOR_MARKER = "TOP";

    @Autowired
    private ApprovalResolverService resolverService;

    @Autowired
    private AppPositionViewRepository positionViewRepository;

    /**
     * The badge of whoever currently sits at the top of the org tree.
     *
     * <p>Resolved from the database every run, because hard-coding it does not
     * survive contact with a real org chart. This test used to pin badge
     * {@code 135822}, and it broke: that person is still the substantive
     * {@code EMPL_ID} of A00000, but the seat's current
     * {@code HOLDER_EMPL_ID} is someone else. The resolver then correctly found
     * a manager ABOVE 135822 — the holder of their own department — and the
     * assertion "nobody sits above the CEO" failed on behaviour that was
     * right.</p>
     *
     * <p><b>HOLDER_EMPL_ID, not EMPL_ID</b>, for exactly that reason: the
     * holder is the PJS-aware occupant
     * ({@code sql/47_app_position_view_pjs_date_filter.sql}), and the ladder
     * this test exercises resolves against holders. Asking about the
     * substantive incumbent of a seat somebody else is acting in is asking
     * about a different person.</p>
     *
     * @return the badge, or {@code null} when the view carries no rooted,
     *         occupied position — in which case the test is skipped rather
     *         than passed
     */
    private String topOfTreeHolderBadge() {
        for (AppPositionView p : positionViewRepository.findAllRows()) {
            String superior = p.getSuperiorId() == null ? "" : p.getSuperiorId().trim();
            // SUPERIOR_ID is a padded CHAR in this view — trim before comparing,
            // the same trap departmentHeadEscalatesUpSuperiorId documents.
            if (!ROOT_SUPERIOR_MARKER.equalsIgnoreCase(superior) || !p.hasHolder()) {
                continue;
            }
            return p.getHolderEmplId().trim();
        }
        return null;
    }

    private List<SesApprovalTaskAssignee> resolvePicFor(String badge) {
        SesApprovalAssignee def = new SesApprovalAssignee();
        def.setAssigneeType(AssigneeType.REQUESTOR_MANAGER);
        return resolverService.previewAssignees(Collections.singletonList(def), badge);
    }

    private void assertResolvedTo(String badge, String expectedPosId) {
        List<SesApprovalTaskAssignee> pic = resolvePicFor(badge);
        assertFalse(pic.isEmpty(), "requestor " + badge + " should resolve to a PIC");
        for (SesApprovalTaskAssignee a : pic) {
            assertEquals(expectedPosId, a.getPosId(),
                    "PIC for " + badge + " should be the holder of " + expectedPosId);
            assertEquals(AssigneeType.REQUESTOR_MANAGER, a.getAssigneeType());
            assertEquals(ResolutionSource.DYNAMIC_MANAGER, a.getResolutionSource());
            assertNotEquals(badge, a.getEmplId(), "PIC must never be the requestor themselves");
        }
        System.out.println("PIC for " + badge + " -> " + pic.get(0).getPosId()
                + " " + pic.get(0).getEmplId() + " " + pic.get(0).getDisplayName());
    }

    @Test
    void sectionManagerIsThePicWhenOneExists() {
        assertResolvedTo(STAFF, STAFF_SECTION_POS);
    }

    @Test
    void sectionHeadEscalatesToTheDepartmentHead() {
        // Before the ladder existed this resolved to SECTION_HEAD himself,
        // because his own PB_SECTION (BD2000) is his own position id.
        assertResolvedTo(SECTION_HEAD, SECTION_HEAD_DEPT_POS);
    }

    @Test
    void departmentHeadEscalatesUpSuperiorId() {
        // Section and department both point back at DEPT_HEAD, so the only
        // way out is SUPERIOR_ID — which is a padded CHAR in the view and
        // resolves to nothing unless it is trimmed first.
        assertResolvedTo(DEPT_HEAD, DEPT_HEAD_SUPERIOR_POS);
    }

    @Test
    void topOfTreeResolvesToNobodyWithAReadableReason() {
        String ceo = topOfTreeHolderBadge();
        // Skipped, not passed, when the view has no rooted occupied position:
        // a vacuous pass here would hide the very regression this test exists
        // to catch.
        assumeTrue(ceo != null,
                "no position carries SUPERIOR_ID = '" + ROOT_SUPERIOR_MARKER
                        + "' with a current holder — org data cannot answer this");

        assertTrue(resolvePicFor(ceo).isEmpty(), "nobody sits above the top of the tree (" + ceo + ")");

        SesApprovalAssignee def = new SesApprovalAssignee();
        def.setAssigneeType(AssigneeType.REQUESTOR_MANAGER);
        List<String> problems =
                resolverService.findUnresolvableAssignees(Collections.singletonList(def), ceo);
        assertEquals(1, problems.size());
        assertTrue(problems.get(0).contains("SUPERIOR_ID"),
                "failure should name the ladder that was walked, got: " + problems.get(0));
        System.out.println("top-of-tree badge " + ceo + " diagnosis: " + problems.get(0));
    }

    @Test
    void resolutionIsStableAcrossEveryRungAndNeverSelfApproves() {
        for (String badge : new String[]{STAFF, SECTION_HEAD, DEPT_HEAD}) {
            for (SesApprovalTaskAssignee a : resolvePicFor(badge)) {
                assertNotEquals(badge, a.getEmplId(),
                        "self-approval regression for " + badge);
                assertNotNull(a.getEmail(), "PIC for " + badge + " should carry an email");
            }
        }
    }
}
