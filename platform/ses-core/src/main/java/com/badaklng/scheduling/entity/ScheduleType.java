package com.badaklng.scheduling.entity;

/**
 * The two kinds of scheduling task the SES flow gates on. Each carries its
 * own approval-chain checkpoint key — see
 * {@code com.badaklng.scheduling.spi.SchedulingApprovalBridge}.
 */
public enum ScheduleType {

    /** Screening itself happens outside the system; this only schedules when it happens. */
    SCREENING("SCREENING_SCHEDULE", "Screening"),

    /** Last-step badge pickup. */
    BADGE_RETRIEVAL("BADGE_RETRIEVAL_SCHEDULE", "Badge Retrieval");

    private final String checkpointKey;
    private final String label;

    ScheduleType(String checkpointKey, String label) {
        this.checkpointKey = checkpointKey;
        this.label = label;
    }

    /** Matches SesApprovalStepTask#checkpointKey — see ApprovalService#completeCheckpointByKey. */
    public String getCheckpointKey() {
        return checkpointKey;
    }

    public String getLabel() {
        return label;
    }
}
