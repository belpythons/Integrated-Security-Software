package com.badaklng.approval.entity;

/**
 * Status of an individual approval task (one step of one instance).
 */
public enum TaskStatus {
    PENDING,
    APPROVED,
    REJECTED,
    SKIPPED
}
