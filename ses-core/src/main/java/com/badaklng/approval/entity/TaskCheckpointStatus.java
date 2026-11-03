package com.badaklng.approval.entity;

/**
 * Status of a single checkpoint (gated task) on an approval task. PENDING
 * blocks Approve; DONE and SKIPPED both clear it — the two are kept
 * distinct for tracking, not gating (DONE = task actually verified,
 * SKIPPED = explicit human override).
 */
public enum TaskCheckpointStatus {
    PENDING,
    DONE,
    SKIPPED
}
