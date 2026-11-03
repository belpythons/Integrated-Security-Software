package com.badaklng.scheduling.entity;

/**
 * Lifecycle of one {@link SesRequestSchedule} row. Reaching SCHEDULED never
 * completes the gated checkpoint by itself — only an explicit ATTENDED
 * resolution does (see SchedulingService#markAttended); a NO_SHOW leaves the
 * checkpoint PENDING and waits for the admin to reschedule.
 */
public enum ScheduleStatus {
    SCHEDULED,
    ATTENDED,
    NO_SHOW,
    CANCELLED
}
