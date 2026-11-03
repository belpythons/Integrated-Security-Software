package com.badaklng.approval.service;

import com.badaklng.approval.entity.AppPositionView;
import com.badaklng.approval.entity.StepRole;
import com.badaklng.approval.repository.AppPositionViewRepository;
import com.badaklng.approval.repository.SesApprovalAssigneeRepository;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The Staf Security a requestor may pick as step-1 evaluators, and the
 * server-side re-check behind that picker.
 *
 * <p>Lives in the approval module because the candidate set comes from
 * {@code APP_POSITION_VIEW}, an approval-module entity. The request module
 * consumes {@link Option} — a plain carrier with no JPA on it — rather than the
 * entity, which is the same boundary {@code RequestController} already observes
 * by talking to {@link ApprovalService} instead of to approval entities.</p>
 *
 * <p>Both the dropdown and the save path go through here, deliberately. A
 * filtered dropdown on its own is cosmetic: nothing stops a crafted POST naming
 * any {@code empl_id} at all, and an unvetted id would become a sign-off row
 * that the real Security section never agreed to be bound by.</p>
 */
@Service
@RequiredArgsConstructor
public class SecurityEvaluatorService {

    private final AppPositionViewRepository positionViewRepository;
    private final SesApprovalAssigneeRepository assigneeRepository;
    private final ApprovalResolverService resolverService;

    /**
     * The roles whose approvers the additional-routing step must not duplicate.
     *
     * <p>{@link StepRole#REQUESTOR_MANAGER} is handled separately — it has no
     * configured position, it resolves to a different chair for every
     * requestor — so only the fixed-position roles are listed here.</p>
     */
    private static final List<StepRole> DEDUPED_AGAINST = Arrays.asList(
            StepRole.PROCUREMENT_SUPERVISOR, StepRole.PROCUREMENT_MANAGER);

    /**
     * One pickable person. Several positions can resolve to the same human
     * (co-holders, or one person holding two posts), so the list is collapsed
     * by {@code emplId} — the picker offers people, not chairs.
     */
    @Getter
    @AllArgsConstructor
    public static class Option {
        private final String emplId;
        private final String emplName;
        private final String posId;
        private final String posTitle;
        private final String deptDesc;
        private final String sectionDesc;

        /** {@code NAMA — JABATAN (DEPT / SECTION)}, for the Select2 dropdown. */
        public String getDisplayText() {
            String unit = sectionDesc != null && !sectionDesc.trim().isEmpty()
                    ? deptDesc + " / " + sectionDesc
                    : deptDesc;
            String title = posTitle != null ? posTitle : posId;
            return unit != null && !unit.trim().isEmpty()
                    ? emplName + " — " + title + " (" + unit + ")"
                    : emplName + " — " + title;
        }
    }

    /**
     * Every candidate, de-duplicated by {@code emplId}, in display-name order.
     *
     * <p>The PJS-aware holder wins over the substantive incumbent when both are
     * present: {@code APP_POSITION_VIEW.HOLDER_*} already resolves to whoever
     * actually occupies the chair today (see
     * {@code sql/47_app_position_view_pjs_date_filter.sql}), and asking an
     * expired stand-in to evaluate a badge request would be a dead end.</p>
     */
    @Transactional(readOnly = true)
    public List<Option> candidates() {
        return candidates(null);
    }

    /**
     * As {@link #candidates()}, but drops {@code excludeEmplId} — the
     * requestor's own badge — so nobody can name themself as an evaluator of
     * their own submission.
     */
    @Transactional(readOnly = true)
    public List<Option> candidates(String excludeEmplId) {
        String self = excludeEmplId != null ? excludeEmplId.trim() : null;
        Map<String, Option> byEmplId = new LinkedHashMap<>();
        for (AppPositionView p : positionViewRepository.findSecurityEvaluatorCandidates()) {
            String emplId = firstNonBlank(p.getHolderEmplId(), p.getEmplId());
            if (emplId == null || (self != null && self.equalsIgnoreCase(emplId))) {
                continue;
            }
            String name = firstNonBlank(p.getHolderEmplName(), p.getEmplName());
            // putIfAbsent, not put: the query is already ordered by display
            // name, so the first row for a person is the one that sorts where
            // the user will look for them. A later row for the same person is a
            // second position they hold, not a better answer.
            byEmplId.putIfAbsent(emplId, new Option(
                    emplId,
                    name != null ? name : emplId,
                    p.getPosId(),
                    p.getPosTitle(),
                    p.getDeptDesc(),
                    p.getSectionDesc()));
        }
        return new ArrayList<>(byEmplId.values());
    }

    /**
     * Resolves the ids a requestor submitted into full {@link Option}s,
     * rejecting anything that is not currently a valid candidate.
     *
     * <p>Returns them in the order the candidate list defines rather than the
     * order they were posted, so two requests picking the same people produce
     * the same {@code line_no} ordering and the route table reads consistently.</p>
     *
     * <p><b>Private.</b> The dedup in {@link #resolveSelected(Collection, String)}
     * is not optional — a second public entry point that skips it would be
     * taken by someone eventually, and the result (one person asked twice for
     * one submission) is the exact thing the dedup exists to prevent.</p>
     *
     * @throws IllegalArgumentException if any id is not a candidate. One
     *         message for all of them: a requestor has no business
     *         enumerating the Security section by probing this endpoint.
     */
    private List<Option> resolveAgainstCandidates(Collection<String> emplIds, String excludeEmplId) {
        if (emplIds == null || emplIds.isEmpty()) {
            return new ArrayList<>();
        }
        Set<String> wanted = new LinkedHashSet<>();
        for (String raw : emplIds) {
            if (raw != null && !raw.trim().isEmpty()) {
                wanted.add(raw.trim());
            }
        }
        if (wanted.isEmpty()) {
            return new ArrayList<>();
        }

        List<Option> resolved = new ArrayList<>();
        for (Option candidate : candidates(excludeEmplId)) {
            if (wanted.remove(candidate.getEmplId())) {
                resolved.add(candidate);
            }
        }
        if (!wanted.isEmpty()) {
            throw new IllegalArgumentException(NOT_A_CANDIDATE);
        }
        return resolved;
    }

    /**
     * As {@link #resolveAgainstCandidates(Collection, String)}, but additionally drops anyone
     * the chain is going to ask anyway.
     *
     * <p><b>Why anything is dropped at all.</b> Additional routing broadcasts
     * to the people the requestor named and waits for all of them. A requestor
     * whose own line manager happens to sit in the Security section can name
     * that manager here — and the manager would then be asked twice for one
     * submission: once in the consensus step, once at the manager step. The
     * second ask is not extra scrutiny, it is a second inbox row for a
     * decision the person has already made.</p>
     *
     * <p>Two sources of collision are checked, both resolved to PEOPLE rather
     * than compared as position codes. Comparing chairs would miss the case
     * that actually bites: one human holding two posts, named here under one
     * of them and asked later under the other.</p>
     * <ul>
     *   <li>the chair the {@code REQUESTOR_MANAGER} step will resolve to for
     *       this requestor, from
     *       {@link ApprovalResolverService#previewRequestorManagerEmplIds};</li>
     *   <li>the holders of every position the procurement steps are aimed at,
     *       read from the chain configuration rather than hard-coded.</li>
     * </ul>
     *
     * <p>Dropping is silent to the engine but NOT to the reader: the Approval
     * Route table renders the sign-off rows that survive, so the requestor can
     * see exactly who was asked. An invalid id still throws — "I picked someone
     * who is not Security staff" and "I picked someone the flow already covers"
     * are different mistakes and only the first is a mistake.</p>
     */
    @Transactional(readOnly = true)
    public List<Option> resolveSelected(Collection<String> emplIds, String requestorUsername) {
        List<Option> resolved = resolveAgainstCandidates(emplIds, requestorUsername);
        if (resolved.isEmpty()) {
            return resolved;
        }

        Set<String> alreadyAsked = new LinkedHashSet<>();
        alreadyAsked.addAll(resolverService.previewRequestorManagerEmplIds(requestorUsername));
        for (String posId : assigneeRepository.findPosIdsByStepRoles(DEDUPED_AGAINST)) {
            alreadyAsked.addAll(resolverService.holderEmplIdsOf(posId, null));
        }
        if (alreadyAsked.isEmpty()) {
            return resolved;
        }

        List<Option> kept = new ArrayList<>();
        for (Option option : resolved) {
            if (!containsIgnoreCase(alreadyAsked, option.getEmplId())) {
                kept.add(option);
            }
        }
        return kept;
    }

    private static boolean containsIgnoreCase(Set<String> haystack, String needle) {
        if (needle == null) {
            return false;
        }
        String trimmed = needle.trim();
        for (String candidate : haystack) {
            if (candidate != null && candidate.trim().equalsIgnoreCase(trimmed)) {
                return true;
            }
        }
        return false;
    }

    public static final String NOT_A_CANDIDATE =
            "Staf Security yang dipilih tidak valid. Muat ulang halaman dan pilih dari daftar yang tersedia.";

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.trim().isEmpty()) {
            return a.trim();
        }
        return (b != null && !b.trim().isEmpty()) ? b.trim() : null;
    }
}
