package com.badaklng.approval.entity;

import com.badaklng.platform.audit.FullAuditEntity;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;

/**
 * One approval process per business submission. References the business
 * record only by type+id — the approval module never joins module tables,
 * so removing a module that uses approvals never breaks this code.
 */
@Getter
@Setter
@Entity
@Table(name = "ses_approval_instance")
public class SesApprovalInstance extends FullAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ses_approval_instance_seq")
    @SequenceGenerator(name = "ses_approval_instance_seq", sequenceName = "SEQ_SES_APPROVAL_INSTANCE", allocationSize = 1)
    @Column(name = "instance_id")
    private Long instanceId;

    /** Snapshot pointer to the chain, NO FK: the chain may be deactivated/renamed later without breaking this row. */
    @Column(name = "chain_id")
    private Long chainId;

    /** Snapshot of the chain key at trigger time. */
    @Column(name = "chain_key", length = 100, nullable = false)
    private String chainKey;

    /** Snapshot of the chain name at trigger time. */
    @Column(name = "chain_name", length = 200, nullable = false)
    private String chainName;

    /** Form code that triggered this instance, if resolved via SES_FORM_APPROVAL_MAP. */
    @Column(name = "form_code", length = 100)
    private String formCode;

    /** Routing value used to resolve the chain, if any. */
    @Column(name = "match_key", length = 50)
    private String matchKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "resolution_tier", length = 10)
    private ChainResolutionTier resolutionTier;

    @Column(name = "business_ref_type", length = 100, nullable = false)
    private String businessRefType;

    @Column(name = "business_ref_id", nullable = false)
    private Long businessRefId;

    /** APP_USER.USER_ID of the requestor (no DB FK; platform schema). */
    @Column(name = "requestor_user_id", length = 36, nullable = false)
    private String requestorUserId;

    /** Current task (step) awaiting action, if any. */
    @Column(name = "current_task_id")
    private Long currentTaskId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private ApprovalStatus status = ApprovalStatus.PENDING;

    /**
     * Frozen at {@code ApprovalService#trigger()}: did the business type behind
     * THIS submission require the Manager Pemohon step?
     *
     * <p>Deliberately a separate fact from {@link SesApprovalTask#getStepRole()},
     * which records WHICH step plays that role. Keeping them apart lets a reader
     * tell "that IS the manager step and we did need it" from "that was never
     * the manager step" — the exact question asked when tracing why a step did
     * or did not run.</p>
     *
     * <p>Frozen rather than read live so that toggling the request type tomorrow
     * can neither skip a step of an approval already running nor resurrect one
     * that has already been passed over. Defaults to {@code 'Y'} so every
     * instance created before this feature reads as "manager approval was
     * required", which is what actually happened.</p>
     */
    @Column(name = "needs_mgr_approval_flag", length = 1, nullable = false)
    private String needsMgrApprovalFlag = "Y";

    /**
     * The same freeze for the Manager Procurement step.
     *
     * @see #needsMgrApprovalFlag
     */
    @Column(name = "needs_proc_approval_flag", length = 1, nullable = false)
    private String needsProcApprovalFlag = "Y";
}
