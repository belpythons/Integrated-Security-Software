package com.badaklng.approval.entity;

import com.badaklng.platform.audit.FullAuditEntity;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;

/**
 * WHO may approve a step — the only part of the approval system that
 * admins edit. Multiple assignees on one step act first-come-first-serve.
 */
@Getter
@Setter
@Entity
@Table(name = "ses_approval_assignee")
public class SesApprovalAssignee extends FullAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ses_approval_assignee_seq")
    @SequenceGenerator(name = "ses_approval_assignee_seq", sequenceName = "SEQ_SES_APPROVAL_ASSIGNEE", allocationSize = 1)
    @Column(name = "assignee_id")
    private Long assigneeId;

    @Column(name = "step_id", nullable = false)
    private Long stepId;

    @Enumerated(EnumType.STRING)
    @Column(name = "assignee_type", length = 20, nullable = false)
    private AssigneeType assigneeType;

    /** POSITION -> APP_POSITION_VIEW.POS_ID (no DB FK; read-only view). */
    @Column(name = "pos_id", length = 10)
    private String posId;

    /** USER -> APP_USER.USER_ID (no DB FK; platform schema). */
    @Column(name = "user_id", length = 36)
    private String userId;

    /** Display order in the chain editor only; has no bearing on resolution. */
    @Column(name = "order_by", nullable = false)
    private Integer orderBy = 0;
}
