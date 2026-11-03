package com.badaklng.scheduling.model;

import com.badaklng.scheduling.entity.ScheduleType;
import com.badaklng.scheduling.entity.SesRequestSchedule;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * One row of the admin scheduling queue (/scheduling/queue): a request
 * whose approval instance is currently blocked on {@code scheduleType}'s
 * checkpoint. {@code schedule} is null when nothing has been scheduled yet
 * for this cycle — the manage page renders a "Set Schedule" form instead of
 * the reschedule/attend/no-show actions in that case.
 */
@Getter
@AllArgsConstructor
public class ScheduleQueueRow {
    private final Long requestId;
    private final String requestNo;
    private final String typeName;
    private final String requestorUserId;
    private final ScheduleType scheduleType;
    private final SesRequestSchedule schedule;
}
