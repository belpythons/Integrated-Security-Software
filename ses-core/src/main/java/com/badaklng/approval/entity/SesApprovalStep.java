package com.badaklng.approval.entity;

import com.badaklng.platform.audit.FullAuditEntity;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;

/**
 * One step of an approval chain, in order. Steps are code-defined;
 * the GUI shows them read-only (order/name not editable).
 */
@Getter
@Setter
@Entity
@Table(name = "ses_approval_step",
       uniqueConstraints = @UniqueConstraint(name = "uq_ses_approval_step", columnNames = {"chain_id", "step_order"}))
public class SesApprovalStep extends FullAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ses_approval_step_seq")
    @SequenceGenerator(name = "ses_approval_step_seq", sequenceName = "SEQ_SES_APPROVAL_STEP", allocationSize = 1)
    @Column(name = "step_id")
    private Long stepId;

    @Column(name = "chain_id", nullable = false)
    private Long chainId;

    @Column(name = "step_order", nullable = false)
    private Integer stepOrder;

    @Column(name = "step_name", length = 200, nullable = false)
    private String stepName;

    /**
     * Which well-known role this step plays, and therefore which per-request-type
     * toggle may skip it. {@code null} (the default) is an ordinary step: never
     * skipped, no special activation behaviour.
     *
     * <p>See {@link StepRole} for why this is configured rather than inferred
     * from the step's {@link AssigneeType}s.</p>
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "step_role", length = 30)
    private StepRole stepRole;

    /**
     * Steps sharing a group number are activated together and must all settle
     * before the next group opens.
     *
     * <p>{@code null} means "a group of its own", which reproduces the original
     * strictly serial engine — and is why it is the default. In the Badge chain
     * only steps 2 and 3 (the two managers) share a number.</p>
     *
     * <p>Note the engine orders groups by this value and falls back to
     * {@code step_order} for the nulls, so a chain that never sets it behaves
     * exactly as it always did.</p>
     */
    @Column(name = "parallel_group")
    private Integer parallelGroup;

    /** {@link CompletionRule#ANY} (first decision wins) or {@link CompletionRule#ALL} (consensus). */
    @Enumerated(EnumType.STRING)
    @Column(name = "completion_rule", length = 10, nullable = false)
    private CompletionRule completionRule = CompletionRule.ANY;

    /**
     * Four invariants {@code ApprovalChainService} enforces on save, each
     * because the alternative is a <em>silent</em> failure rather than a loud one:
     *
     * <ol>
     *   <li><b>Each {@link StepRole} at most once per chain</b> — otherwise
     *       "the Manager Pemohon step" has no single referent and which one gets
     *       skipped is an accident of step order.</li>
     *   <li><b>At least one non-skippable step must remain</b> — a chain whose
     *       every step is skippable would, for a type with both toggles off,
     *       reach {@code advance()} with no PENDING task and complete as
     *       APPROVED instantly: a submission approved with nobody reviewing it.</li>
     *   <li><b>A skippable step may carry no checkpoint</b> — checkpoints are
     *       frozen onto the task even when the step is skipped, and
     *       {@code completeCheckpointByKey} scans EVERY task of the instance, so
     *       it can match that orphan and leave the real gate waiting forever.</li>
     *   <li><b>At most one {@code ALL} step per parallel group</b> — two
     *       concurrent consensus steps make "who are we still waiting on?"
     *       ambiguous to the people in the queue.</li>
     * </ol>
     */
    @Transient
    public boolean isSkippable() {
        return stepRole != null && stepRole.isSkippable();
    }
}
