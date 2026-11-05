package com.badaklng.approval.entity;

/**
 * Lifecycle status of an approval instance.
 * REJECTED and CANCELLED are terminal — a rejection at any step, or an
 * explicit cancellation, ends the whole flow.
 */
public enum ApprovalStatus {
    PENDING,
    APPROVED,
    REJECTED,
    CANCELLED
}
