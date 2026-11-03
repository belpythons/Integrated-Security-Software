package com.badaklng.scheduling.model;

import com.badaklng.scheduling.entity.SesRequestSchedule;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

/** One day cell of the /scheduling/calendar month grid. */
@Getter
@AllArgsConstructor
public class CalendarDay {
    /** Day-of-month number, or null for a leading/trailing filler cell outside the shown month. */
    private final Integer dayOfMonth;
    private final boolean inMonth;
    private final boolean today;
    private final List<SesRequestSchedule> schedules;
}
