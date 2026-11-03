package com.badaklng.approval.entity;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import java.util.Date;

/**
 * Per-instance FROZEN snapshot of a SesApprovalStepTask definition, copied
 * into existence at ApprovalService#trigger() — later chain edits never
 * retroactively change an in-flight instance's checkpoints. A task's
 * checkpoints must ALL be resolved (DONE or SKIPPED) before that task can
 * be approved; Reject is never gated by this.
 */
@Getter
@Setter
@Entity
@Table(name = "ses_approval_task_chkp")
public class SesApprovalTaskCheckpoint {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ses_approval_task_chkp_seq")
    @SequenceGenerator(name = "ses_approval_task_chkp_seq", sequenceName = "SEQ_SES_APPROVAL_TASK_CHKP", allocationSize = 1)
    @Column(name = "checkpoint_id")
    private Long checkpointId;

    @Column(name = "task_id", nullable = false)
    private Long taskId;

    @Column(name = "task_label", length = 200, nullable = false)
    private String taskLabel;

    /** Frozen copy of SesApprovalStepTask#checkpointKey. */
    @Column(name = "checkpoint_key", length = 50)
    private String checkpointKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private TaskCheckpointStatus status = TaskCheckpointStatus.PENDING;

    @Column(name = "note", length = 2000)
    private String note;

    /** Set only when completed via completeCheckpointByKey (module-driven), e.g. "SES_SCREENING_SCHEDULE:1234". Null on a manual tick or a skip. */
    @Column(name = "source_ref", length = 100)
    private String sourceRef;

    /** Actor (APP_USER.user_id) who completed or skipped this checkpoint. */
    @Column(name = "completed_by", length = 36)
    private String completedBy;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "completed_date")
    private Date completedDate;
}
