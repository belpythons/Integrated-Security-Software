package com.badaklng.approval.spi;

/**
 * One checkpoint key a business module registers, so the chain editor's
 * "Checkpoint key" field offers a pick-list instead of free text an admin
 * has to guess by convention. Implement as a {@code @Component} bean (see
 * {@link SimpleCheckpointTaskType}); the approval module discovers it via
 * {@code ObjectProvider<CheckpointTaskType>} — zero compile-time dependency
 * on any business module.
 *
 * Listing aid only: ApprovalService#completeCheckpointByKey and
 * #checkCheckpoint still match by plain string equality against
 * SesApprovalStepTask.checkpointKey, so {@link #getKey()} must equal
 * exactly what the module passes to those calls.
 */
public interface CheckpointTaskType {

    /** Stable key, e.g. "SCREENING_SCHEDULE" — what completeCheckpointByKey is called with. */
    String getKey();

    /** Human-readable label shown next to the key in the chain editor dropdown. */
    String getLabel();
}
