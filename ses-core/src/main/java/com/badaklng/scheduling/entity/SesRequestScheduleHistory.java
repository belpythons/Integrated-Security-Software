package com.badaklng.scheduling.entity;

import com.badaklng.platform.audit.FullAuditEntity;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import java.util.Date;

/**
 * Append-only create/reschedule/cancel/attend/no-show log for one
 * {@link SesRequestSchedule} — backs the manage page's "history" card
 * instead of a new schedule row per cycle. Never updated after insert.
 */
@Getter
@Setter
@Entity
@Table(name = "ses_request_schedule_history")
public class SesRequestScheduleHistory extends FullAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ses_request_schedule_history_seq")
    @SequenceGenerator(name = "ses_request_schedule_history_seq", sequenceName = "SEQ_SES_REQUEST_SCHED_HIST", allocationSize = 1)
    @Column(name = "history_id")
    private Long historyId;

    @Column(name = "schedule_id", nullable = false)
    private Long scheduleId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", length = 20, nullable = false)
    private ScheduleHistoryAction action;

    /** Snapshot of the date/time this entry set, for CREATED/RESCHEDULED rows; null otherwise. */
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "scheduled_date")
    private Date scheduledDate;

    /** Note/reason snapshot at this action — admin note on CREATED/RESCHEDULED, mandatory reason on CANCELLED/NO_SHOW. */
    @Column(name = "reason", length = 500)
    private String reason;

    /** -> app_user.user_id, no FK. */
    @Column(name = "actor_user_id", length = 36, nullable = false)
    private String actorUserId;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "actor_date", nullable = false)
    private Date actorDate;

    /**
     * True when this history entry records a screening session where zero workers cleared.
     */
    public boolean isNoClearedScreening() {
        return action == ScheduleHistoryAction.NO_SHOW
                && reason != null
                && (reason.toLowerCase().contains("tidak ada peserta yang lolos")
                    || reason.startsWith("0 lolos"));
    }
}
