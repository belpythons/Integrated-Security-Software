package com.badaklng.approval.repository;

import com.badaklng.approval.entity.AppPositionView;
import com.badaklng.approval.entity.AppPositionViewId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Read-only repository over APP_POSITION_VIEW.
 * The view is owned by another schema in production — only reads allowed.
 */
@Repository
public interface AppPositionViewRepository extends JpaRepository<AppPositionView, AppPositionViewId> {

    /** A position id can resolve to several rows — one per current co-holder. */
    List<AppPositionView> findByPosId(String posId);

    /** A person may hold several positions (multiple rows with same EMPL_ID). */
    @Query("SELECT p FROM AppPositionView p WHERE p.emplId = :emplId")
    List<AppPositionView> findByEmplId(@Param("emplId") String emplId);

    /** Substring, case-insensitive match on EMPL_ID — used by the worker-name search
     * to also surface positions with no matching APP_USER row. */
    List<AppPositionView> findByEmplIdContainingIgnoreCase(String emplId);

    /** Substring, case-insensitive match on EMPL_NAME — lets the worker-name search
     * also match by the person's name, not just EMPL_ID/username. */
    List<AppPositionView> findByEmplNameContainingIgnoreCase(String emplName);

    @Query("SELECT p FROM AppPositionView p WHERE p.holderEmplId = :emplId")
    List<AppPositionView> findByHolderEmplId(@Param("emplId") String emplId);

    /** Substring, case-insensitive match on HOLDER_EMPL_ID — used by the worker-name
     * search so the PJS-aware current holder's badge is searchable too. */
    List<AppPositionView> findByHolderEmplIdContainingIgnoreCase(String holderEmplId);

    /** Substring, case-insensitive match on HOLDER_EMPL_NAME — lets the worker-name
     * search match by the position's ACTUAL current holder's name (PJS-aware), not
     * just the definitive-assigned EMPL_NAME. */
    List<AppPositionView> findByHolderEmplNameContainingIgnoreCase(String holderEmplName);

    /** Substring, case-insensitive match on POS_TITLE — worker-name search's
     * "by position" source. */
    List<AppPositionView> findByPosTitleContainingIgnoreCase(String posTitle);

    /** All rows needed for superior/descendant traversal (small org trees). */
    @Query("SELECT p FROM AppPositionView p")
    List<AppPositionView> findAllRows();

    @Query("SELECT p FROM AppPositionView p WHERE p.superiorId = :posId")
    List<AppPositionView> findDirectSubordinates(@Param("posId") String posId);

    /**
     * Candidate evaluators for the "Additional Routing (Evaluator)" picker on
     * the new-badge form — company-wide, not limited to any one section.
     *
     * <ul>
     *   <li>{@code POS_ID <> SECTION_ID} and {@code POS_ID <> DEPT_ID} —
     *       exclude Section Managers and Dept Heads. Those are management
     *       chairs, already covered elsewhere in the approval chain; a staff
     *       member reporting to them is the intended evaluator, not the
     *       manager themself.</li>
     *   <li>{@code SECTION_ID IS NULL} is allowed through (not excluded) so
     *       staff reporting directly to a department, with no section in
     *       between, still qualify.</li>
     *   <li>{@code HOLDER_EMPL_ID LIKE '1%' OR EMPL_ID LIKE '1%'} — permanent
     *       employees. A leading '1' is this org's badge convention for them;
     *       Mitra badges start with '3' (see {@code sql/23}'s APP_PERSON
     *       filter). Tested against the PJS-aware holder as well as the
     *       substantive incumbent, so an acting officer is eligible.</li>
     * </ul>
     *
     * <p>Every criterion is a fixed literal: no user input reaches this query,
     * so there is nothing to inject even in principle.</p>
     *
     * <p>Rows with no resolvable person are dropped here rather than filtered
     * by the caller — a picker entry with a blank name is not a choice anyone
     * can make sensibly. Ordering is by the displayed name so the dropdown
     * reads alphabetically; a position with co-holders legitimately yields
     * several rows, which is the same multiplicity {@code findByPosId} returns
     * and is expected, not a duplicate.</p>
     */
    @Query("SELECT p FROM AppPositionView p "
         + "WHERE (p.sectionId IS NULL OR p.posId <> p.sectionId) "
         + "AND (p.deptId IS NULL OR p.posId <> p.deptId) "
         + "AND (p.holderEmplId LIKE '1%' OR p.emplId LIKE '1%') "
         + "AND COALESCE(p.holderEmplId, p.emplId) IS NOT NULL "
         + "ORDER BY COALESCE(p.holderEmplName, p.emplName) ASC")
    List<AppPositionView> findSecurityEvaluatorCandidates();
}
