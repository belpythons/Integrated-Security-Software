package com.badaklng.approval.entity;

import com.badaklng.platform.audit.FullAuditEntity;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;

/**
 * Definition of one CHECKPOINT (gated task) required on a step before it
 * can be approved. Fully GUI-managed, sibling of SesApprovalAssignee — a
 * step with no rows here has no gate.
 */
@Getter
@Setter
@Entity
@Table(name = "ses_approval_step_task")
public class SesApprovalStepTask extends FullAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ses_approval_step_task_seq")
    @SequenceGenerator(name = "ses_approval_step_task_seq", sequenceName = "SEQ_SES_APPROVAL_STEP_TASK", allocationSize = 1)
    @Column(name = "step_task_id")
    private Long stepTaskId;

    @Column(name = "step_id", nullable = false)
    private Long stepId;

    @Column(name = "task_label", length = 200, nullable = false)
    private String taskLabel;

    /**
     * Optional free-text convention key a business module can call
     * ApprovalService#completeCheckpointByKey with, to auto-satisfy this
     * checkpoint instead of requiring a manual tick. Null = purely manual.
     */
    @Column(name = "checkpoint_key", length = 50)
    private String checkpointKey;

    /** Display order in the chain editor only; also used as save order. */
    @Column(name = "order_by", nullable = false)
    private Integer orderBy = 0;
}
