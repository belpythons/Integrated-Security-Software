package com.badaklng.approval.entity;

/**
 * The well-known role a chain step plays in the Badge flow — and therefore
 * which per-request-type toggle, if any, is allowed to skip it.
 *
 * <p>A step with no role ({@code null}, the default) is an ordinary step: it is
 * never skipped and carries no special activation behaviour. That is every step
 * of every chain that existed before the hybrid routing engine, which is
 * precisely why {@code null} had to remain a legal value.</p>
 *
 * <p><b>The role is a MARK, not a guess.</b> It would be tempting to infer
 * "this is the Requestor's Manager step" from the step's {@link AssigneeType}s,
 * but the seeded chains put {@code REQUESTOR_MANAGER} on the SAME step as a
 * fixed {@code USER 'admin'} fallback. An "all assignees are dynamic" rule
 * would therefore never match and the feature would be silently inert — the
 * worst possible failure mode — while an "any assignee is dynamic" rule would
 * discard a substantive approver that merely happened to share the step.
 * Neither is salvageable, so the role is configured at {@code /approval/chain}
 * and the runtime only ever reads it.</p>
 */
public enum StepRole {

    /**
     * Step 1 of the Badge flow: the Staf Security the <em>requestor</em> chose
     * on the form. Broadcast in parallel to all of them, and every one must
     * approve ({@code completion_rule = ALL}).
     *
     * <p>Unique among the roles in that its approvers come from the submission
     * ({@code ses_request_evaluator}), not from chain configuration. A step
     * carrying this role is consequently allowed to have no assignee rows at
     * all, and is exempt from the "every step needs an assignee or a
     * checkpoint" rule.</p>
     *
     * <p><b>Optional.</b> Additional routing is the requestor's choice, and
     * choosing nobody is a valid answer — the step is then SKIPPED, not
     * blocked. This is the one role skipped by an empty list rather than by a
     * request-type toggle; see {@link #isSkippable()}.</p>
     */
    SECURITY_STAFF,

    /**
     * Manager Pemohon — the requestor's own line manager, resolved per
     * submission from {@code APP_POSITION_VIEW}.
     *
     * <p>Skipped when the request type's {@code needs_mgr_approval_flag} is
     * {@code 'N'}. Runs AFTER the procurement pair, not alongside it: the
     * whole Badge flow is serial (add → Supv Proc → Mgr Proc → Mgr Pemohon →
     * Mgr Security → …).</p>
     */
    REQUESTOR_MANAGER,

    /**
     * Supervisor Procurement ({@code EH1200}), the FIRST of the two
     * procurement rungs.
     *
     * <p>Procurement is two serial steps, not one: the supervisor approves
     * before the manager is asked. Both are governed by the same request-type
     * toggle, so {@code needs_proc_approval_flag = 'N'} skips the pair.</p>
     *
     * <p>Note the position ids are NOT interchangeable, and the reference
     * diagram has them wrong — it labels both boxes {@code EH1000}. On
     * APP_POSITION_VIEW, {@code EH1200} is SUPERVISOR, PROCUREMENT SUPPORT and
     * {@code EH1000} is MANAGER, PROCUREMENT (verified against ELLDEV
     * 2026-10-06). {@code EH1000} is also the procurement SECTION id, which is
     * exactly why it reads as plausible in the wrong slot.</p>
     */
    PROCUREMENT_SUPERVISOR,

    /**
     * Manager Procurement ({@code EH1000}), the SECOND procurement rung.
     *
     * <p>Skipped when the request type's {@code needs_proc_approval_flag} is
     * {@code 'N'} — together with {@link #PROCUREMENT_SUPERVISOR}, since one
     * toggle governs the whole procurement leg.</p>
     */
    PROCUREMENT_MANAGER,

    /**
     * Manager Security ({@code E01000}) — the gate every Badge submission
     * passes through.
     *
     * <p>The ONLY role that is never skippable: no toggle switches it off and
     * nothing about the submission can empty it. That is what makes the direct
     * bypass safe — a request with both toggles {@code 'N'} and no additional
     * routing skips the first four steps and lands here, reviewed by a human
     * rather than auto-approved.</p>
     */
    SECURITY_MANAGER;

    /**
     * Whether this step may be passed over at runtime instead of being
     * approved by someone.
     *
     * <p>Two different mechanisms land here, and both count:</p>
     * <ul>
     *   <li><b>A request-type toggle.</b> {@link #REQUESTOR_MANAGER} has its
     *       own; {@link #PROCUREMENT_SUPERVISOR} and
     *       {@link #PROCUREMENT_MANAGER} share one.</li>
     *   <li><b>Nobody to ask.</b> {@link #SECURITY_STAFF} takes its approvers
     *       from the submission, and naming nobody is a legitimate choice —
     *       additional routing is optional — so the step is skipped rather
     *       than left waiting on an empty consensus.</li>
     * </ul>
     *
     * <p>The chain invariants care about the question, not the mechanism: a
     * chain needs at least one step that will run <em>whatever</em> the
     * submission looks like, and a step that might not run must carry no
     * checkpoint. Splitting the two mechanisms here would let a chain of
     * {@code SECURITY_STAFF + REQUESTOR_MANAGER} save cleanly and then refuse
     * every submission at trigger time — validation that passes and then
     * blocks everything is worse than validation that fails.</p>
     *
     * <p>Keeping the answer on the enum rather than spread across the service
     * layer is what makes that invariant checkable in one place.</p>
     */
    public boolean isSkippable() {
        // Enumerated, not "everything except SECURITY_MANAGER": a role added
        // later must default to NOT skippable. The unsafe default here is an
        // approval that silently does not happen.
        return this == SECURITY_STAFF
                || this == REQUESTOR_MANAGER
                || this == PROCUREMENT_SUPERVISOR
                || this == PROCUREMENT_MANAGER;
    }

    /**
     * Is this one of the two steps the procurement toggle governs?
     *
     * <p>Exists so the engine never has to spell the pair out at each call
     * site — and so adding a third procurement rung later is one edit here
     * rather than a hunt through the service.</p>
     */
    public boolean isProcurement() {
        return this == PROCUREMENT_SUPERVISOR || this == PROCUREMENT_MANAGER;
    }

    /**
     * Parses a stored value, tolerating null, blank and anything unrecognised
     * by answering {@code null} — "no role".
     *
     * <p>Lenient on purpose. {@code step_role} is a plain VARCHAR2 with a CHECK
     * constraint, and the safe reading of a value this code does not know is
     * "an ordinary step", never "guess which special step it might be".</p>
     */
    public static StepRole parse(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        try {
            return StepRole.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
