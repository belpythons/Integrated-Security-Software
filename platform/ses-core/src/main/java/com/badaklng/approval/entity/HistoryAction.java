package com.badaklng.approval.entity;

/**
 * Append-only audit actions recorded in SES_APPROVAL_HISTORY.
 */
public enum HistoryAction {
    START,
    APPROVE,
    REJECT,
    SKIP,
    ADVANCE,
    COMPLETE,
    CANCEL,
    TASK_COMPLETE,
    TASK_SKIP
}
