package com.badaklng.scheduling.entity;

/** One entry kind in the append-only {@link SesRequestScheduleHistory} log. */
public enum ScheduleHistoryAction {
    CREATED,
    RESCHEDULED,
    CANCELLED,
    ATTENDED,
    NO_SHOW
}
