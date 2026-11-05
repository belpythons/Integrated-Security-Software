package com.badaklng.approval.entity;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;

/**
 * Resolved assignee of a task (frozen snapshot).
 * empl_id is the resolved person: holder of the position
 * (PJS-aware, from APP_POSITION_VIEW.holder_empl_id) or the direct
 * user's username (== empl id).
 */
@Getter
@Setter
@Entity
@Table(name = "ses_approval_task_assignee")
public class SesApprovalTaskAssignee {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ses_approval_task_assignee_seq")
    @SequenceGenerator(name = "ses_approval_task_assignee_seq", sequenceName = "SEQ_SES_APPROVAL_TASK_ASSIGNEE", allocationSize = 1)
    @Column(name = "task_assignee_id")
    private Long taskAssigneeId;

    @Column(name = "task_id", nullable = false)
    private Long taskId;

    @Enumerated(EnumType.STRING)
    @Column(name = "assignee_type", length = 20, nullable = false)
    private AssigneeType assigneeType;

    @Column(name = "pos_id", length = 10)
    private String posId;

    @Column(name = "user_id", length = 36)
    private String userId;

    /** Resolved person: holder empl id or direct user's username. */
    @Column(name = "empl_id", length = 20)
    private String emplId;

    @Column(name = "display_name", length = 200)
    private String displayName;

    @Column(name = "email", length = 100)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(name = "resolution_source", length = 30)
    private ResolutionSource resolutionSource;
}
