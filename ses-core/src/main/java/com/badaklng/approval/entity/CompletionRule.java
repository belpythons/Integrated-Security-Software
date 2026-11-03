package com.badaklng.approval.entity;

/**
 * How a task decides it is finished.
 *
 * <p>Stored as a string on both {@code ses_approval_step} (the definition) and
 * {@code ses_approval_task} (the frozen copy), defaulting to {@link #ANY} —
 * which is exactly the behaviour every chain had before the hybrid routing
 * engine, and the reason no existing flow changes.</p>
 */
public enum CompletionRule {

    /**
     * First decision wins. Several assignees on one step mean "whoever acts
     * first resolves it", never "all must act". This is the original engine
     * behaviour and still covers every step except Staf Security.
     */
    ANY,

    /**
     * Consensus. The task stays PENDING until every
     * {@code ses_approval_task_signoff} row has approved; a single rejection
     * ends the whole instance immediately, same as any other rejection.
     *
     * <p>Only meaningful on a task that actually has sign-off rows. A task
     * marked {@code ALL} with no sign-offs would never be able to complete, so
     * activation treats "zero sign-offs resolved" as a configuration error
     * rather than silently approving it.</p>
     */
    ALL;

    /**
     * Parses a stored value, answering {@link #ANY} for null, blank or
     * anything unrecognised.
     *
     * <p>Fails towards {@code ANY} on purpose: an unreadable rule should leave
     * the step behaving the way every step behaved before this feature
     * existed, not silently convert it into one nobody can complete.</p>
     */
    public static CompletionRule parse(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return ANY;
        }
        try {
            return CompletionRule.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return ANY;
        }
    }
}
