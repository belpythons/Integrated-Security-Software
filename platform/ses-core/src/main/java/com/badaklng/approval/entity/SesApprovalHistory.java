package com.badaklng.approval.entity;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import java.util.Date;

/**
 * Append-only audit trail of every action on an approval instance.
 */
@Getter
@Setter
@Entity
@Table(name = "ses_approval_history")
public class SesApprovalHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ses_approval_history_seq")
    @SequenceGenerator(name = "ses_approval_history_seq", sequenceName = "SEQ_SES_APPROVAL_HISTORY", allocationSize = 1)
    @Column(name = "history_id")
    private Long historyId;

    @Column(name = "instance_id", nullable = false)
    private Long instanceId;

    @Column(name = "task_id")
    private Long taskId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", length = 20, nullable = false)
    private HistoryAction action;

    /** Actor APP_USER.USER_ID (may be SYSTEM for auto transitions). */
    @Column(name = "actor_user_id", length = 36)
    private String actorUserId;

    /** Actor empl id (username) for display. */
    @Column(name = "actor_empl_id", length = 20)
    private String actorEmplId;

    @Column(name = "from_step_order")
    private Integer fromStepOrder;

    @Column(name = "to_step_order")
    private Integer toStepOrder;

    @Column(name = "note", length = 2000)
    private String note;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "created_date", nullable = false)
    private Date createdDate = new Date();
}
