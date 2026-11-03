package com.badaklng.request.entity;

import com.badaklng.platform.audit.FullAuditEntity;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import java.util.Date;

/**
 * Worker Data table rows. UNIQUE(request_id, nik) at the DB level blocks
 * listing the same person twice on one request; NIK format is validated in
 * RequestService, not by a DB CHECK.
 */
@Getter
@Setter
@Entity
@Table(name = "ses_request_worker")
public class SesRequestWorker extends FullAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ses_request_worker_seq")
    @SequenceGenerator(name = "ses_request_worker_seq", sequenceName = "SEQ_SES_REQUEST_WORKER", allocationSize = 1)
    @Column(name = "worker_id")
    private Long workerId;

    @Column(name = "request_id", nullable = false)
    private Long requestId;

    @Column(name = "line_no", nullable = false)
    private Integer lineNo;

    @Column(name = "worker_name", length = 200, nullable = false)
    private String workerName;

    @Column(name = "nik", length = 30, nullable = false)
    private String nik;

    /** Identity type carried by {@link #nik}: NIK (16-digit) or PASSPORT (alphanumeric). */
    @Column(name = "id_type", length = 10, nullable = false)
    private String idType = "NIK";

    /** Badge/employee id — free-text, optionally autofilled from APP_POSITION_VIEW.HOLDER_EMPL_ID. */
    @Column(name = "badge", length = 50)
    private String badge;

    @Column(name = "jabatan", length = 200)
    private String jabatan;

    /** Free-text, optionally autofilled from APP_POSITION_VIEW.DEPT_DESC. */
    @Column(name = "department", length = 200)
    private String department;

    /** Free-text, optionally autofilled from APP_POSITION_VIEW.SECTION_DESC. */
    @Column(name = "section", length = 200)
    private String section;

    @Column(name = "gender", length = 1)
    private String gender;

    /**
     * This worker's screening outcome — see {@link ScreeningStatus}.
     *
     * <p>Screening used to be recorded per REQUEST: {@code ses_request_schedule}
     * is unique on {@code (request_id, schedule_type)}, so one submission of
     * six workers had a single ATTENDED/NO_SHOW covering all six. Since a
     * screening NO_SHOW rejects the entire request, an officer facing five
     * attendees and one absentee had no honest answer available.</p>
     *
     * <p>⚠️ Reset to {@code PENDING} by an edit-resubmit, because
     * {@code saveAndSubmit} deletes and re-inserts the worker rows. Harmless
     * today — a request stops being requestor-editable before screening is
     * reachable — but it is the kind of coupling that stops being harmless
     * without anyone noticing.</p>
     */
    @Column(name = "screening_status", length = 20, nullable = false)
    private String screeningStatus = ScreeningStatus.PENDING;

    @Column(name = "screening_date")
    @Temporal(TemporalType.DATE)
    private Date screeningDate;

    @Column(name = "screening_reason", length = 500)
    private String screeningReason;
}
