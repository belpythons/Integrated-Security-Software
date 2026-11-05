package com.badaklng.approval.entity;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;

/**
 * Per-task snapshot of the STEP'S ASSIGNEE DEFINITIONS (not the resolved
 * people), copied from SES_APPROVAL_ASSIGNEE at trigger time. This keeps a
 * running instance honest when an admin later edits or deletes a step:
 * activateTask() reads from here, never from the live SES_APPROVAL_ASSIGNEE
 * rows.
 */
@Getter
@Setter
@Entity
@Table(name = "ses_approval_task_assignee_def")
public class SesApprovalTaskAssigneeDef {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ses_appr_task_assignee_def_seq")
    @SequenceGenerator(name = "ses_appr_task_assignee_def_seq", sequenceName = "SEQ_SES_APPR_TASK_ASSIGNEE_DEF", allocationSize = 1)
    @Column(name = "task_assignee_def_id")
    private Long taskAssigneeDefId;

    @Column(name = "task_id", nullable = false)
    private Long taskId;

    @Enumerated(EnumType.STRING)
    @Column(name = "assignee_type", length = 20, nullable = false)
    private AssigneeType assigneeType;

    @Column(name = "pos_id", length = 10)
    private String posId;

    @Column(name = "user_id", length = 36)
    private String userId;

    @Column(name = "order_by", nullable = false)
    private Integer orderBy = 0;
}
