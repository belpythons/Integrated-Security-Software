package com.badaklng.request.entity;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import java.util.Date;

/**
 * One Staf Security the requestor picked on the new-badge form.
 *
 * <p>Step 1 of the approval chain broadcasts to exactly these people and waits
 * for all of them ({@code completion_rule = ALL}).</p>
 *
 * <p><b>Why a table and not a chain assignee.</b> Every other approver in this
 * system is configuration: an admin wires a position or a user into the chain
 * at {@code /approval/chain} and it applies to every submission. This set is
 * per <em>submission</em> and chosen by the requestor, so it cannot live in
 * {@code ses_approval_assignee} — and it has to be readable BEFORE
 * {@code ApprovalService#trigger()} runs, because trigger() is what turns it
 * into {@code ses_approval_task_signoff} rows.</p>
 *
 * <p>The candidate list is resolved live from {@code APP_POSITION_VIEW} at form
 * time. What is stored here is the CHOICE, with {@code emplName} copied in:
 * the org chart may move someone out of Security tomorrow, and a submitted
 * request must still be able to say who it was sent to. No FK to any
 * {@code APP_*} object, matching the rule in {@code sql/README.md} — those
 * references are plain pointers, enforced in the application layer.</p>
 */
@Getter
@Setter
@Entity
@Table(name = "ses_request_evaluator",
       uniqueConstraints = @UniqueConstraint(name = "uq_ses_request_evaluator",
                                             columnNames = {"request_id", "empl_id"}))
public class SesRequestEvaluator {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ses_request_evaluator_seq")
    @SequenceGenerator(name = "ses_request_evaluator_seq",
                       sequenceName = "SEQ_SES_REQUEST_EVALUATOR", allocationSize = 1)
    @Column(name = "evaluator_id")
    private Long evaluatorId;

    @Column(name = "request_id", nullable = false)
    private Long requestId;

    @Column(name = "line_no", nullable = false)
    private Integer lineNo = 0;

    @Column(name = "pos_id", length = 10)
    private String posId;

    @Column(name = "pos_title", length = 100)
    private String posTitle;

    /** {@code APP_POSITION_VIEW.HOLDER_EMPL_ID} (or {@code EMPL_ID}) as it was when picked. */
    @Column(name = "empl_id", length = 20, nullable = false)
    private String emplId;

    /** Copied at pick time, so an old request can still name who it went to. */
    @Column(name = "empl_name", length = 200)
    private String emplName;

    @Column(name = "created_by", length = 50)
    private String createdBy;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "created_date")
    private Date createdDate;
}
