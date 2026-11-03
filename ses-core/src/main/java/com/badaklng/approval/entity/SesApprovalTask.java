package com.badaklng.approval.entity;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import java.util.Date;

/**
 * Per-instance snapshot of one chain step. Assignees are frozen into
 * SES_APPROVAL_TASK_ASSIGNEE at creation, so later chain edits never
 * retroactively change in-flight approvals.
 */
@Getter
@Setter
@Entity
@Table(name = "ses_approval_task",
       uniqueConstraints = @UniqueConstraint(name = "uq_ses_approval_task", columnNames = {"instance_id", "step_order"}))
public class SesApprovalTask {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ses_approval_task_seq")
    @SequenceGenerator(name = "ses_approval_task_seq", sequenceName = "SEQ_SES_APPROVAL_TASK", allocationSize = 1)
    @Column(name = "task_id")
    private Long taskId;

    @Column(name = "instance_id", nullable = false)
    private Long instanceId;

    /** Original step definition id (may be null for legacy/removed steps). */
    @Column(name = "step_definition_id")
    private Long stepDefinitionId;

    @Column(name = "step_order", nullable = false)
    private Integer stepOrder;

    @Column(name = "step_name", length = 200, nullable = false)
    private String stepName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private TaskStatus status = TaskStatus.PENDING;

    /** Actor who completed this task. */
    @Column(name = "completed_by", length = 36)
    private String completedBy;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "completed_date")
    private Date completedDate;

    @Column(name = "note", length = 2000)
    private String note;

    /**
     * Frozen copies of {@link SesApprovalStep}'s role, group and rule, taken at
     * {@code ApprovalService#trigger()} alongside the assignee-definition and
     * checkpoint snapshots — so a later chain edit can never re-group, re-role
     * or re-rule an instance that is already running.
     *
     * <p>A task is auto-skipped only when its role is
     * {@link StepRole#REQUESTOR_MANAGER} and the instance's
     * {@code needsMgrApprovalFlag} is {@code 'N'}, or its role is
     * {@link StepRole#PROCUREMENT_MANAGER} and {@code needsProcApprovalFlag} is
     * {@code 'N'}. The other two roles have no toggle and are never skipped.</p>
     *
     * <p>Rows written before the hybrid engine keep {@code null} / {@code ANY}
     * and are deliberately NOT backfilled. That is the whole back-compat story:
     * every in-flight instance keeps the serial, first-decision-wins,
     * never-skipped shape it was started under, so nothing can change under the
     * people working it.</p>
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "step_role", length = 30)
    private StepRole stepRole;

    /** @see SesApprovalStep#getParallelGroup() */
    @Column(name = "parallel_group")
    private Integer parallelGroup;

    /** @see SesApprovalStep#getCompletionRule() */
    @Enumerated(EnumType.STRING)
    @Column(name = "completion_rule", length = 10, nullable = false)
    private CompletionRule completionRule = CompletionRule.ANY;

}
