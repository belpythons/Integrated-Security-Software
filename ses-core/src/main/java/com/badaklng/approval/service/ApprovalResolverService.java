package com.badaklng.approval.service;

import com.badaklng.platform.system.entity.AppUser;
import com.badaklng.platform.system.repository.AppUserRepository;
import com.badaklng.approval.entity.AppPositionView;
import com.badaklng.approval.entity.AssigneeType;
import com.badaklng.approval.entity.ResolutionSource;
import com.badaklng.approval.entity.SesApprovalAssignee;
import com.badaklng.approval.entity.SesApprovalTask;
import com.badaklng.approval.entity.SesApprovalTaskAssignee;
import com.badaklng.approval.entity.SesUserExt;
import com.badaklng.approval.repository.AppPositionViewRepository;
import com.badaklng.approval.repository.SesApprovalTaskAssigneeRepository;
import com.badaklng.approval.repository.SesUserExtRepository;
import com.badaklng.request.entity.AppEmpInfoView;
import com.badaklng.request.entity.AppEmpLkpView;
import com.badaklng.request.repository.AppEmpInfoViewRepository;
import com.badaklng.request.repository.AppEmpLkpViewRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Resolves WHO can act on an approval task, at task-creation time.
 *
 * Four assignee types:
 * <ul>
 *   <li>{@code POSITION} — the current holder of a fixed position in
 *       APP_POSITION_VIEW (holder_empl_id is PJS-aware).</li>
 *   <li>{@code USER} — a fixed APP_USER targeted directly.</li>
 *   <li>{@code REQUESTOR_MANAGER} — dynamic: resolve the requestor's
 *       manager-level position selected by the employee-view NVL rule.</li>
 *   <li>{@code REPORT_TO} — dynamic: target SES_USER_EXT.report_to_pos_id
 *       directly, skipping the requestor-manager lookup entirely.</li>
 * </ul>
 *
 * Dynamic (REQUESTOR_MANAGER) resolution walks a ladder of candidate
 * positions and stops at the first one with a real holder who is not the
 * requestor:
 * <ol>
 *   <li>Requestor not in APP_EMP_INFO_VIEW nor APP_EMP_LKP_VIEW (external
 *       / local-only user) — fallback to SES_USER_EXT.report_to_pos_id
 *       (NO_POSITION_FALLBACK).</li>
 *   <li>SECTION — PB_SECTION (APP_EMP_INFO_VIEW) / SECTION_ID
 *       (APP_EMP_LKP_VIEW).</li>
 *   <li>DEPARTMENT — DEPTID / DEPT_ID.</li>
 *   <li>Above that — walk up APP_POSITION_VIEW.SUPERIOR_ID from the
 *       department head, cycle-protected, until someone answers.</li>
 * </ol>
 *
 * Steps 2-4 rest on a property of APP_POSITION_VIEW: a row's SECTION_ID /
 * DEPT_ID are themselves the POS_ID of that section's / department's head
 * position, so "the requestor's section manager" is literally
 * findByPosId(SECTION_ID). Verified against ELLDEV: 493/493 SECTION_ID and
 * 569/587 DEPT_ID values resolve to a real POS_ID.
 *
 * The requestor is excluded from every rung, which is what makes the
 * escalation necessary: a section head's own SECTION_ID points back at
 * their own position, so without this they would approve their own
 * requests (44 of 569 ELLDEV users did, before this ladder existed).
 *
 * When the ladder runs out nobody is assigned — the step resolves to zero
 * actors and ApprovalService.trigger rejects the submission with a
 * readable reason. Only the top of the org tree reaches that.
 *
 * REPORT_TO resolution is not part of the above chain: it targets
 * SES_USER_EXT.report_to_pos_id directly, with no requestor-manager
 * lookup and no manager-skip check (REPORT_TO_DIRECT).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ApprovalResolverService {

    private final AppPositionViewRepository positionRepository;
    private final SesUserExtRepository userExtRepository;
    private final SesApprovalTaskAssigneeRepository taskAssigneeRepository;
    private final AppUserRepository appUserRepository;
    private final AppEmpInfoViewRepository employeeInfoRepository;
    private final AppEmpLkpViewRepository employeeLookupRepository;

    /** Deepest org tree in ELLDEV is 5 levels; 10 is slack, not a limit anyone hits. */
    private static final int MAX_LADDER_HOPS = 10;

    /**
     * Resolves and persists the assignees for a task, from the frozen
     * assignee definitions of the step.
     *
     * @return the resolved assignee list (empty when the step is a
     *         manager-skip — no one needs to act).
     */
    public List<SesApprovalTaskAssignee> resolveTaskAssignees(SesApprovalTask task,
                                                              List<SesApprovalAssignee> definitions,
                                                              String requestorUsername) {
        List<SesApprovalTaskAssignee> resolved = new ArrayList<>();
        for (SesApprovalAssignee def : definitions) {
            dispatchAssignee(task, def, requestorUsername, resolved);
        }
        taskAssigneeRepository.saveAll(resolved);
        return resolved;
    }

    /**
     * Read-only pre-flight: resolves {@code definitions} for {@code
     * requestorUsername} exactly like {@link #resolveTaskAssignees}, but
     * never persists (the probe task carries no id). Returns one message
     * per definition that resolved to nothing (excluding manager-skip,
     * which is a valid outcome, not a failure). Empty list means every
     * definition either resolves to a real actor or a legitimate skip.
     */
    public List<String> findUnresolvableAssignees(List<SesApprovalAssignee> definitions, String requestorUsername) {
        SesApprovalTask probe = new SesApprovalTask();
        List<SesApprovalTaskAssignee> resolved = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        for (SesApprovalAssignee def : definitions) {
            int before = resolved.size();
            dispatchAssignee(probe, def, requestorUsername, resolved);
            if (resolved.size() == before) {
                problems.add(describeUnresolved(def, requestorUsername));
            }
        }
        return problems;
    }

    /**
     * Read-only resolution preview: the same dispatch as
     * {@link #resolveTaskAssignees}, on a probe task that carries no id,
     * so nothing is ever persisted. Answers "who WOULD be assigned",
     * which {@link #findUnresolvableAssignees} deliberately does not
     * expose (it only reports what failed).
     */
    public List<SesApprovalTaskAssignee> previewAssignees(List<SesApprovalAssignee> definitions,
                                                          String requestorUsername) {
        SesApprovalTask probe = new SesApprovalTask();
        List<SesApprovalTaskAssignee> resolved = new ArrayList<>();
        for (SesApprovalAssignee def : definitions) {
            dispatchAssignee(probe, def, requestorUsername, resolved);
        }
        return resolved;
    }

    private void dispatchAssignee(SesApprovalTask task, SesApprovalAssignee def,
                                  String requestorUsername, List<SesApprovalTaskAssignee> resolved) {
        switch (def.getAssigneeType()) {
            case POSITION:
                resolvePosition(task, def, resolved);
                break;
            case USER:
                resolveUser(task, def, resolved);
                break;
            case REQUESTOR_MANAGER:
                resolveRequestorManager(task, requestorUsername, resolved);
                break;
            case REPORT_TO:
                resolveReportToDirect(task, requestorUsername, resolved);
                break;
            default:
                log.warn("Unknown assignee type {} on assignee {}", def.getAssigneeType(), def.getAssigneeId());
        }
    }

    private String describeUnresolved(SesApprovalAssignee def, String requestorUsername) {
        switch (def.getAssigneeType()) {
            case POSITION:
                return describePositionFailure(def.getPosId());
            case USER:
                return "Assigned user " + def.getUserId() + " was not found in APP_USER";
            case REQUESTOR_MANAGER:
                return "No manager could be resolved for requestor " + requestorUsername + ": "
                        + describeManagerFailure(requestorUsername);
            case REPORT_TO:
                return "No report_to actor could be resolved for requestor " + requestorUsername + ": "
                        + describeReportToFailure(requestorUsername);
            default:
                return "Unknown assignee type " + def.getAssigneeType();
        }
    }

    /**
     * POSITION-specific diagnosis: distinguishes a position id that isn't
     * in APP_POSITION_VIEW at all from one that exists but has every row's
     * holder_empl_id empty (no PJS either).
     */
    private String describePositionFailure(String posId) {
        List<AppPositionView> rows = positionRepository.findByPosId(posId);
        if (rows.isEmpty()) {
            return "Position " + posId + " does not exist in APP_POSITION_VIEW";
        }
        return "Position " + posId + " has no current holder (holder_empl_id not set for any row)";
    }

    /**
     * REQUESTOR_MANAGER-specific diagnosis: names the ladder actually
     * walked by {@link #resolveRequestorManager}, so the message says
     * which positions were tried rather than just "no manager found".
     */
    private String describeManagerFailure(String requestorUsername) {
        Optional<EmployeeRouting> routing = findEmployeeRouting(requestorUsername);
        if (!routing.isPresent()) {
            return "requestor is absent from APP_EMP_INFO_VIEW and APP_EMP_LKP_VIEW, and "
                    + describeReportToFailure(requestorUsername);
        }
        List<String> ladder = managerLadder(routing.get());
        if (ladder.isEmpty()) {
            return routing.get().source + " has no section or department position";
        }
        return "no position in " + ladder + " (section, then department, then up SUPERIOR_ID)"
                + " has a current holder other than the requestor";
    }

    /**
     * REPORT_TO-specific diagnosis: no SES_USER_EXT row / blank
     * report_to_pos_id, versus a configured report_to_pos_id that doesn't
     * exist in APP_POSITION_VIEW, versus one that exists but has no
     * current holder.
     */
    private String describeReportToFailure(String requestorUsername) {
        Optional<SesUserExt> ext = findUserExtByUsername(requestorUsername);
        if (!ext.isPresent() || ext.get().getReportToPosId() == null || ext.get().getReportToPosId().trim().isEmpty()) {
            return "no report_to position configured in SES_USER_EXT";
        }
        String reportToPosId = ext.get().getReportToPosId();
        Optional<AppPositionView> pos = findPosition(reportToPosId);
        if (!pos.isPresent()) {
            return "report_to position '" + reportToPosId + "' configured in SES_USER_EXT does not exist in APP_POSITION_VIEW";
        }
        boolean hasHolder = positionRepository.findByPosId(reportToPosId).stream().anyMatch(AppPositionView::hasHolder);
        if (!hasHolder) {
            return "report_to position '" + reportToPosId + "' has no current holder";
        }
        return "report_to position '" + reportToPosId + "' should have resolved (unexpected)";
    }

    private void resolvePosition(SesApprovalTask task, SesApprovalAssignee def,
                                 List<SesApprovalTaskAssignee> resolved) {
        List<AppPositionView> positions = positionRepository.findByPosId(def.getPosId());
        if (positions.isEmpty()) {
            log.warn("Position {} not found in APP_POSITION_VIEW; task {} cannot resolve", def.getPosId(), task.getTaskId());
            return;
        }
        // A position id can be held by several people at once (co-officers
        // under the same POS_ID) — every current holder is added as an
        // equal approver; whoever acts first still resolves the step.
        int before = resolved.size();
        addAllHoldersOfPosition(task, resolved, def.getPosId(), AssigneeType.POSITION, ResolutionSource.STATIC_POSITION);
        if (resolved.size() == before) {
            log.warn("Position {} has no current holder; task {} halts", def.getPosId(), task.getTaskId());
        }
    }

    private void addAllHoldersOfPosition(SesApprovalTask task, List<SesApprovalTaskAssignee> resolved,
                                         String posId, AssigneeType type, ResolutionSource source) {
        addAllHoldersOfPosition(task, resolved, posId, type, source, null);
    }

    /**
     * Adds one resolved assignee per current holder row of {@code posId}
     * (holder_empl_id is PJS-aware per row; rows without a holder are
     * skipped, never auto-substituted).
     *
     * {@code excludeEmplId} drops the requestor from the result so dynamic
     * manager resolution can escalate past a position the requestor holds
     * themselves. Static POSITION / REPORT_TO assignees pass null: those
     * are configured explicitly by an admin and must never be silently
     * filtered out.
     */
    private void addAllHoldersOfPosition(SesApprovalTask task, List<SesApprovalTaskAssignee> resolved,
                                         String posId, AssigneeType type, ResolutionSource source,
                                         String excludeEmplId) {
        for (AppPositionView pos : positionRepository.findByPosId(posId)) {
            if (!pos.hasHolder()) {
                continue;
            }
            if (excludeEmplId != null
                    && excludeEmplId.trim().equalsIgnoreCase(pos.getHolderEmplId().trim())) {
                continue;
            }
            SesApprovalTaskAssignee a = new SesApprovalTaskAssignee();
            a.setTaskId(task.getTaskId());
            a.setAssigneeType(type);
            a.setPosId(pos.getPosId());
            a.setEmplId(pos.getHolderEmplId());
            a.setDisplayName(pos.getHolderEmplName());
            a.setEmail(pos.getHolderEmail());
            a.setResolutionSource(source);
            resolved.add(a);
        }
    }

    private void resolveUser(SesApprovalTask task, SesApprovalAssignee def,
                             List<SesApprovalTaskAssignee> resolved) {
        // Direct user targeting: the assignee references APP_USER.user_id
        // (UUID); username == employee id by convention. Resolve the
        // username so canAct() can match the logged-in principal.
        Optional<AppUser> user = def.getUserId() != null
                ? appUserRepository.findById(def.getUserId())
                : Optional.empty();
        if (!user.isPresent()) {
            log.warn("USER assignee {} not found in APP_USER; task {} cannot resolve",
                    def.getUserId(), task.getTaskId());
            return;
        }
        AppUser appUser = user.get();
        String emplId = appUser.getUsername();
        List<AppPositionView> positions = positionRepository.findByEmplId(emplId);
        SesApprovalTaskAssignee a = new SesApprovalTaskAssignee();
        a.setTaskId(task.getTaskId());
        a.setAssigneeType(AssigneeType.USER);
        a.setUserId(def.getUserId());
        a.setEmplId(emplId);
        a.setDisplayName(appUser.getFullName());
        a.setEmail(appUser.getEmail());
        if (!positions.isEmpty()) {
            AppPositionView p = positions.get(0);
            if (p.getHolderEmplName() != null) {
                a.setDisplayName(p.getHolderEmplName());
            }
            if (p.getHolderEmail() != null) {
                a.setEmail(p.getHolderEmail());
            }
        }
        a.setResolutionSource(ResolutionSource.STATIC_USER);
        resolved.add(a);
    }

    /**
     * Dynamic manager resolution; may produce zero assignees when the
     * step must be skipped (requestor is a manager / above a manager).
     */
    private void resolveRequestorManager(SesApprovalTask task, String requestorUsername,
                                         List<SesApprovalTaskAssignee> resolved) {
        if (requestorUsername == null || requestorUsername.trim().isEmpty()) {
            return;
        }
        Optional<EmployeeRouting> routing = findEmployeeRouting(requestorUsername);
        if (!routing.isPresent()) {
            resolveReportTo(task, requestorUsername, resolved, AssigneeType.REQUESTOR_MANAGER,
                    ResolutionSource.NO_POSITION_FALLBACK);
            return;
        }
        List<String> ladder = managerLadder(routing.get());
        for (String posId : ladder) {
            int before = resolved.size();
            addAllHoldersOfPosition(task, resolved, posId, AssigneeType.REQUESTOR_MANAGER,
                    ResolutionSource.DYNAMIC_MANAGER, requestorUsername);
            if (resolved.size() > before) {
                return;
            }
        }
        log.warn("No manager above user {} ({} ladder: {}); task {} cannot resolve",
                requestorUsername, routing.get().source, ladder, task.getTaskId());
    }

    /**
     * Candidate approver positions for a requestor, nearest first: section
     * head, then department head, then up SUPERIOR_ID from the department
     * head.
     *
     * Two data quirks this must survive, both real in ELLDEV: SUPERIOR_ID
     * is a blank-padded CHAR (untrimmed it matches no POS_ID at all — 0 of
     * 587), and the tree contains a self-loop (C00000's superior is
     * C00000). Deduplication doubles as the cycle guard.
     */
    private List<String> managerLadder(EmployeeRouting routing) {
        List<String> ladder = new ArrayList<>();
        addCandidate(ladder, routing.sectionPosition);
        addCandidate(ladder, routing.departmentPosition);
        String cursor = ladder.isEmpty() ? null : ladder.get(ladder.size() - 1);
        for (int hop = 0; cursor != null && hop < MAX_LADDER_HOPS; hop++) {
            Optional<AppPositionView> pos = findPosition(cursor);
            if (!pos.isPresent()) {
                break;
            }
            String superior = trimToNull(pos.get().getSuperiorId());
            if (superior == null || !addCandidate(ladder, superior)) {
                break;
            }
            cursor = superior;
        }
        return ladder;
    }

    /** @return true when {@code posId} was a new, non-blank rung. */
    private boolean addCandidate(List<String> ladder, String posId) {
        String trimmed = trimToNull(posId);
        if (trimmed == null || ladder.contains(trimmed)) {
            return false;
        }
        return ladder.add(trimmed);
    }

    private Optional<EmployeeRouting> findEmployeeRouting(String requestorUsername) {
        Optional<AppEmpInfoView> info = employeeInfoRepository.findByEmplid(requestorUsername);
        if (info.isPresent()) {
            return Optional.of(new EmployeeRouting("APP_EMP_INFO_VIEW",
                    info.get().getPbSection(), info.get().getDeptId()));
        }
        Optional<AppEmpLkpView> lookup = employeeLookupRepository.findByEmplid(requestorUsername);
        if (lookup.isPresent()) {
            return Optional.of(new EmployeeRouting("APP_EMP_LKP_VIEW",
                    lookup.get().getSectionId(), lookup.get().getDeptId()));
        }
        return Optional.empty();
    }

    /** Static so the pure {@link #decide} can use it too. */
    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static final class EmployeeRouting {
        private final String source;
        private final String sectionPosition;
        private final String departmentPosition;

        private EmployeeRouting(String source, String sectionPosition, String departmentPosition) {
            this.source = source;
            this.sectionPosition = sectionPosition;
            this.departmentPosition = departmentPosition;
        }
    }

    /**
     * REPORT_TO assignee type: targets SES_USER_EXT.report_to_pos_id
     * directly. No requestor-position lookup, no manager-title check, no
     * org-tree traversal — just the configured report-to position.
     */
    private void resolveReportToDirect(SesApprovalTask task, String requestorUsername,
                                       List<SesApprovalTaskAssignee> resolved) {
        if (requestorUsername == null || requestorUsername.trim().isEmpty()) {
            return;
        }
        resolveReportTo(task, requestorUsername, resolved, AssigneeType.REPORT_TO, ResolutionSource.REPORT_TO_DIRECT);
    }

    private void resolveReportTo(SesApprovalTask task, String requestorUsername,
                                 List<SesApprovalTaskAssignee> resolved,
                                 AssigneeType assigneeType, ResolutionSource source) {
        Optional<SesUserExt> ext = findUserExtByUsername(requestorUsername);
        if (!ext.isPresent() || ext.get().getReportToPosId() == null
                || ext.get().getReportToPosId().trim().isEmpty()) {
            log.warn("No position and no report_to configured for user {}; task {} cannot resolve",
                    requestorUsername, task.getTaskId());
            return;
        }
        String reportToPosId = ext.get().getReportToPosId();
        if (!findPosition(reportToPosId).isPresent()) {
            log.warn("report_to position {} for user {} not found; task {} cannot resolve",
                    reportToPosId, requestorUsername, task.getTaskId());
            return;
        }
        int before = resolved.size();
        addAllHoldersOfPosition(task, resolved, reportToPosId, assigneeType, source);
        if (resolved.size() == before) {
            log.warn("report_to position {} has no holder; task {} halts", reportToPosId, task.getTaskId());
        }
    }

    /**
     * SES_USER_EXT.user_id mirrors APP_USER.user_id (the UUID PK), not the
     * login username — resolve requestorUsername (== app_user.username) to
     * its APP_USER row first so this lookup actually hits the row the admin
     * UI wrote via AppUserController/SesUserExtService.
     */
    private Optional<SesUserExt> findUserExtByUsername(String requestorUsername) {
        return appUserRepository.findByUsername(requestorUsername)
                .flatMap(u -> userExtRepository.findByUserId(u.getUserId()));
    }

    /**
     * One representative row for a position id — title/superior are the
     * same across every holder row, so org-tree traversal only needs one.
     */
    private Optional<AppPositionView> findPosition(String posId) {
        List<AppPositionView> rows = positionRepository.findByPosId(posId);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    /**
     * Marks that a REQUESTOR_MANAGER step resolves to "skip" (no actor).
     */
    private void addSkipMarker(SesApprovalTask task, List<SesApprovalTaskAssignee> resolved,
                               ResolutionSource source, String requestorUsername) {
        SesApprovalTaskAssignee a = new SesApprovalTaskAssignee();
        a.setTaskId(task.getTaskId());
        a.setAssigneeType(AssigneeType.REQUESTOR_MANAGER);
        a.setEmplId(requestorUsername);
        a.setResolutionSource(source);
        resolved.add(a);
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    /**
     * true when this user may act on the task.
     *
     * <p>Authorization is <b>live for position-derived assignees</b> and
     * frozen for fixed-user ones. That split is the point: a task activated
     * while someone was PJS freezes that stand-in's {@code empl_id} onto the
     * assignee row, and when the PJS window closes the authority belongs to
     * whoever holds the position <em>now</em> — not to whoever held it the
     * moment the task happened to be created.</p>
     *
     * <p>Before 2026-09-30 this matched the frozen {@code empl_id} only, so
     * the inbox ({@code SES_APPROVAL_PENDING_VIEW}, live since 2026-09-16)
     * and the Approve button disagreed: the incoming holder saw the request
     * listed and then found no buttons on it, and a hand-crafted POST hit
     * {@code SecurityException} in {@code ApprovalService.act}.</p>
     *
     * <p>The rules, in order:</p>
     * <ol>
     *   <li>emergency approver (role 1/2) — always yes;</li>
     *   <li>every assignee marked {@code MANAGER_SKIP} — nobody needs to
     *       act, so no;</li>
     *   <li>assignee with no {@code pos_id} ({@code AssigneeType.USER}) —
     *       frozen {@code empl_id}/{@code user_id} match, unchanged;</li>
     *   <li>assignee <b>with</b> a {@code pos_id} — the frozen
     *       {@code empl_id} is IGNORED; the user must be a current holder of
     *       that position;</li>
     *   <li>…unless the position resolves to nobody at all, in which case
     *       the frozen value is honoured after all.</li>
     * </ol>
     *
     * <p>Rule 5 is load-bearing. Without it a gap in position data would
     * leave a task approvable by nobody but an emergency approver — trading
     * one stuck task for a worse one. {@code SES_APPROVAL_PENDING_VIEW} keeps
     * a {@code fallback_frozen} rung for exactly this reason, and
     * authorization has to walk the same ladder the inbox walks, or the two
     * disagree again in a new way.</p>
     *
     * <p>Consequence, intended: a former stand-in loses the button on tasks
     * frozen to them the day their PJS ends.</p>
     */
    public boolean canAct(SesApprovalTask task, String username, String userId) {
        if (isEmergencyApprover(username, userId)) {
            return true;
        }
        List<SesApprovalTaskAssignee> assignees =
                taskAssigneeRepository.findByTaskIdOrderByTaskAssigneeIdAsc(task.getTaskId());
        return decide(assignees, liveHoldersFor(assignees), username, userId);
    }

    /**
     * Current holders of every position referenced by these assignees, keyed
     * by {@code pos_id}.
     *
     * <p>A position maps to a LIST because co-officers are normal here —
     * {@code APP_POSITION_VIEW} carries one row per holder and all of them
     * are equal approvers. An entry present but EMPTY means "this position
     * exists in the assignee rows but currently has no holder", which is what
     * triggers the frozen fallback in {@link #decide}.</p>
     *
     * <p>One query per distinct position, and a step has one or two. The
     * alternative, {@code findAllRows()}, pulls ~600 rows to answer a
     * yes/no question.</p>
     */
    private Map<String, List<String>> liveHoldersFor(List<SesApprovalTaskAssignee> assignees) {
        Map<String, List<String>> holders = new HashMap<>();
        for (SesApprovalTaskAssignee a : assignees) {
            String posId = trimToNull(a.getPosId());
            if (posId == null || holders.containsKey(posId)) {
                continue;
            }
            List<String> current = new ArrayList<>();
            for (AppPositionView pos : positionRepository.findByPosId(posId)) {
                if (pos.hasHolder()) {
                    current.add(pos.getHolderEmplId().trim());
                }
            }
            holders.put(posId, current);
        }
        return holders;
    }

    /**
     * The authorization rule itself, as a pure function over already-fetched
     * data — see {@link #canAct} for what the rules mean and why.
     *
     * <p>Separated from the repository calls so it can be tested without a
     * Spring context or an Oracle connection, the same way
     * {@code StickerService.earliest} is. Authorization logic that can only
     * be exercised through {@code @SpringBootTest} against a live ELLDEV is
     * authorization logic that does not get tested.</p>
     *
     * @param liveHoldersByPosId current holder badges per position; a key
     *                           mapped to an empty list means the position
     *                           resolves to nobody right now
     */
    static boolean decide(List<SesApprovalTaskAssignee> assignees,
                          Map<String, List<String>> liveHoldersByPosId,
                          String username, String userId) {
        if (assignees == null || assignees.isEmpty()) {
            return false;
        }
        // A step marked skip (MANAGER_SKIP) should never require an actor.
        boolean allSkip = assignees.stream()
                .allMatch(a -> a.getResolutionSource() == ResolutionSource.MANAGER_SKIP);
        if (allSkip) {
            return false;
        }

        String badge = trimToNull(username);
        for (SesApprovalTaskAssignee a : assignees) {
            if (a.getResolutionSource() == ResolutionSource.MANAGER_SKIP) {
                continue;
            }
            String posId = trimToNull(a.getPosId());

            if (posId != null) {
                List<String> holders = liveHoldersByPosId == null
                        ? null : liveHoldersByPosId.get(posId);
                if (holders != null && !holders.isEmpty()) {
                    // Live holders known: they are the only authority, and the
                    // frozen empl_id on this row is deliberately not consulted.
                    if (badge != null && holders.stream().anyMatch(badge::equalsIgnoreCase)) {
                        return true;
                    }
                    continue;
                }
                // Position resolves to nobody — fall through to the frozen
                // value rather than orphan the task.
            }

            if (badge != null && badge.equalsIgnoreCase(trimToNull(a.getEmplId()))) {
                return true;
            }
            if (userId != null && userId.equals(a.getUserId())) {
                return true;
            }
        }
        return false;
    }

    /** Role IDs 1 and 2 are the emergency approval roles. */
    public boolean isEmergencyApprover(String username, String userId) {
        if ((username == null || username.trim().isEmpty()) && (userId == null || userId.trim().isEmpty())) {
            return false;
        }
        Optional<AppUser> user = userId == null
                ? appUserRepository.findByUsername(username)
                : appUserRepository.findById(userId);
        if (!user.isPresent() || !"Y".equalsIgnoreCase(user.get().getActiveFlag())) {
            return false;
        }
        AppUser loaded = appUserRepository.findByIdWithRoles(user.get().getUserId()).orElse(user.get());
        return loaded.getUserRoles() != null && loaded.getUserRoles().stream()
                .map(link -> link.getRole())
                .filter(java.util.Objects::nonNull)
                .filter(role -> "Y".equalsIgnoreCase(role.getActiveFlag()))
                .map(role -> role.getRoleId())
                .anyMatch(id -> Long.valueOf(1L).equals(id) || Long.valueOf(2L).equals(id));
    }

    /**
     * All positions, used by the chain editor's position picker.
     */
    public List<AppPositionView> findAllPositions() {
        return positionRepository.findAllRows();
    }

    /**
     * Positions of a single user, used for display/debug.
     */
    public List<AppPositionView> findPositionsOf(String username) {
        return positionRepository.findByEmplId(username);
    }

    /**
     * Who the {@code REQUESTOR_MANAGER} step would be aimed at for this
     * requestor, as badge numbers, without persisting anything.
     *
     * <p>Exists for the evaluator dedup guard: a requestor inside the Security
     * section can pick their own line manager as an additional-routing
     * evaluator, and that person would then be asked twice for the same
     * submission — once in the consensus step, once at the manager step.
     * {@link SecurityEvaluatorService} drops the duplicate, and to do that it
     * has to know which badge the manager step resolves to.</p>
     *
     * <p>Mirrors {@link #resolveRequestorManager}'s selection rule exactly:
     * nearest ladder rung that has a holder other than the requestor, with the
     * SES_USER_EXT report-to position as the fallback. A looser rule — "every
     * rung of the ladder" — would be simpler but wrong in the direction that
     * costs something: it would silently drop an evaluator who sits on a rung
     * that is never actually reached, so a person the requestor deliberately
     * named would never be asked at all.</p>
     *
     * <p>An empty set means "cannot tell". That is the safe answer here: the
     * caller drops nothing, the evaluator stays, and the worst case is a
     * duplicate ask rather than a silent omission. {@code trigger()} refuses
     * the submission separately if the manager step is genuinely unresolvable
     * (see {@link #findUnresolvableAssignees}).</p>
     */
    @Transactional(readOnly = true)
    public Set<String> previewRequestorManagerEmplIds(String requestorUsername) {
        if (requestorUsername == null || requestorUsername.trim().isEmpty()) {
            return Collections.emptySet();
        }
        Optional<EmployeeRouting> routing = findEmployeeRouting(requestorUsername);
        if (routing.isPresent()) {
            for (String posId : managerLadder(routing.get())) {
                Set<String> holders = holderEmplIdsOf(posId, requestorUsername);
                if (!holders.isEmpty()) {
                    return holders;
                }
            }
        }
        Optional<SesUserExt> ext = findUserExtByUsername(requestorUsername);
        if (ext.isPresent() && trimToNull(ext.get().getReportToPosId()) != null) {
            return holderEmplIdsOf(ext.get().getReportToPosId().trim(), null);
        }
        return Collections.emptySet();
    }

    /**
     * Current holders of {@code posId} as badge numbers, optionally excluding
     * one person — the same filter {@link #addAllHoldersOfPosition} applies, so
     * the two cannot drift apart on "does this position have anyone to ask?".
     */
    @Transactional(readOnly = true)
    public Set<String> holderEmplIdsOf(String posId, String excludeEmplId) {
        String wanted = trimToNull(posId);
        if (wanted == null) {
            return Collections.emptySet();
        }
        Set<String> holders = new LinkedHashSet<>();
        for (AppPositionView pos : positionRepository.findByPosId(wanted)) {
            if (!pos.hasHolder()) {
                continue;
            }
            String holder = pos.getHolderEmplId().trim();
            if (excludeEmplId != null && excludeEmplId.trim().equalsIgnoreCase(holder)) {
                continue;
            }
            holders.add(holder);
        }
        return holders;
    }
}
