package com.badaklng.approval.entity;

/**
 * State of one person's sign-off on a consensus task.
 *
 * <p>Deliberately NOT {@link TaskStatus}. That enum carries a fourth value,
 * {@code SKIPPED}, which is meaningless here: a task can be skipped by
 * configuration, but an individual evaluator the requestor named is either
 * still being waited on or has answered. Reusing it would have left a legal
 * value with no defined behaviour, and the DB CHECK on
 * {@code ses_approval_task_signoff.status} would have disagreed with the Java
 * type.</p>
 */
public enum SignoffStatus {

    /** Still being waited on. Only PENDING rows keep the task open. */
    PENDING,

    /** This person approved. They drop out of the inbox; the task waits on the rest. */
    APPROVED,

    /**
     * This person rejected. Ends the whole instance immediately — a consensus
     * step has no notion of outvoting a dissent.
     */
    REJECTED
}
