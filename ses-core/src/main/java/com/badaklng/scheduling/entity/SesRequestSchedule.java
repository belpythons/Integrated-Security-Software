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
import javax.persistence.UniqueConstraint;
import java.util.Date;

/**
 * Current-state scheduling item for one request + {@link ScheduleType}.
 * {@code requestId} is a real DB FK to {@code ses_request} (see
 * ses/sql/15_scheduling_ddl.sql), same convention as
 * {@code SesRequestPayment} — this module is intentionally request-specific,
 * not a generic soft-pointer design.
 * <p>
 * One mutable row per (request, type) for the whole lifecycle: create ->
 * reschedule/cancel/attend/no-show all update this same row (never insert a
 * new one), with every transition appended to
 * {@link SesRequestScheduleHistory}. See
 * {@link com.badaklng.scheduling.service.SchedulingService}.
 */
@Getter
@Setter
@Entity
@Table(name = "ses_request_schedule",
       uniqueConstraints = @UniqueConstraint(name = "uq_ses_schedule_req_type", columnNames = {"request_id", "schedule_type"}))
public class SesRequestSchedule extends FullAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ses_request_schedule_seq")
    @SequenceGenerator(name = "ses_request_schedule_seq", sequenceName = "SEQ_SES_REQUEST_SCHEDULE", allocationSize = 1)
    @Column(name = "schedule_id")
    private Long scheduleId;

    @Column(name = "request_id", nullable = false)
    private Long requestId;

    @Enumerated(EnumType.STRING)
    @Column(name = "schedule_type", length = 20, nullable = false)
    private ScheduleType scheduleType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private ScheduleStatus status = ScheduleStatus.SCHEDULED;

    /** Date + time (hour/minute) the requestor is expected. */
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "scheduled_date", nullable = false)
    private Date scheduledDate;

    /** Admin's free-text instructions/location for this schedule, optional. */
    @Column(name = "note", length = 500)
    private String note;

    /** Mandatory reason snapshot for the most recent CANCELLED/NO_SHOW resolution; null otherwise. */
    @Column(name = "resolution_reason", length = 500)
    private String resolutionReason;

    /** -> app_user.user_id, no FK (platform-owned). Set on attend/no-show/cancel. */
    @Column(name = "resolved_by", length = 36)
    private String resolvedBy;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "resolved_date")
    private Date resolvedDate;

    /**
     * True when this screening schedule was attended but zero workers cleared
     * (all workers failed/rejected).
     */
    public boolean isNoClearedScreening() {
        return scheduleType == ScheduleType.SCREENING
                && status == ScheduleStatus.NO_SHOW
                && resolutionReason != null
                && (resolutionReason.toLowerCase().contains("tidak ada peserta yang lolos")
                    || resolutionReason.startsWith("0 lolos"));
    }
}
