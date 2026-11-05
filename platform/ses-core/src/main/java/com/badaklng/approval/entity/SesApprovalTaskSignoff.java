package com.badaklng.approval.entity;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import java.util.Date;

/**
 * One person a consensus task is waiting on.
 *
 * <p>Rows exist only for a task whose {@link CompletionRule} is
 * {@link CompletionRule#ALL}. An {@code ANY} task has none at all and is
 * settled by {@link SesApprovalTask#getStatus()} alone, exactly as every task
 * was before the hybrid routing engine — which is what keeps the sticker,
 * payment and scheduling flows unchanged.</p>
 *
 * <p><b>A fourth freeze.</b> Alongside {@code ses_approval_task_assignee_def},
 * {@code ses_approval_task_assignee} and {@code ses_approval_task_chkp}, these
 * rows fix the set of people a step waits for at activation time. For step 1
 * that set comes from {@code ses_request_evaluator} — the Staf Security the
 * requestor picked on the form — so re-resolving it later would silently change
 * who a submitted request was sent to. There is deliberately no FK back to
 * {@code ses_request_evaluator} either: once the names are copied across, these
 * rows must survive independently, or deleting a draft's evaluator list could
 * rewrite the history of an approval that already ran.</p>
 *
 * <p><b>{@code emplId} is the identity that matters</b>, not {@code userId}: it
 * is what {@code APP_POSITION_VIEW} and {@code APP_EMP_INFO_VIEW} key on, and
 * what {@code SES_APPROVAL_PENDING_VIEW} matches an inbox row against.
 * {@code userId} is carried as a convenience for history rows and may be null
 * for someone who has never logged into this application.</p>
 */
@Getter
@Setter
@Entity
@Table(name = "ses_approval_task_signoff",
       uniqueConstraints = @UniqueConstraint(name = "uq_ses_appr_task_signoff",
                                             columnNames = {"task_id", "empl_id"}))
public class SesApprovalTaskSignoff {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ses_appr_task_signoff_seq")
    @SequenceGenerator(name = "ses_appr_task_signoff_seq",
                       sequenceName = "SEQ_SES_APPR_TASK_SIGNOFF", allocationSize = 1)
    @Column(name = "signoff_id")
    private Long signoffId;

    @Column(name = "task_id", nullable = false)
    private Long taskId;

    /** Employee id, frozen at activation. The key every live-holder lookup uses. */
    @Column(name = "empl_id", length = 20, nullable = false)
    private String emplId;

    /** {@code APP_USER.USER_ID}; null for someone who has never logged in here. */
    @Column(name = "user_id", length = 36)
    private String userId;

    @Column(name = "display_name", length = 200)
    private String displayName;

    @Column(name = "pos_id", length = 10)
    private String posId;

    /** Frozen position title, for the "Approver" column of the route table. */
    @Column(name = "pos_title", length = 100)
    private String posTitle;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private SignoffStatus status = SignoffStatus.PENDING;

    @Column(name = "note", length = 2000)
    private String note;

    /** {@code APP_USER.USER_ID} of whoever actually pressed the button. */
    @Column(name = "completed_by", length = 36)
    private String completedBy;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "completed_date")
    private Date completedDate;

    @Column(name = "order_by", nullable = false)
    private Integer orderBy = 0;
}
