package com.badaklng.request.entity;

import com.badaklng.platform.audit.FullAuditEntity;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

/**
 * "Types of Request" — GUI-managed at /admin/request-type. Natural key
 * (typeCode), no sequence. Also the source for RequestApprovalBridge's
 * matchKeys() — adding a row here makes it selectable on the request form
 * AND as an approval override target, no redeploy required.
 */
@Getter
@Setter
@Entity
@Table(name = "ses_request_type")
public class SesRequestType extends FullAuditEntity {

    @Id
    @Column(name = "type_code", length = 30, updatable = false, nullable = false)
    private String typeCode;

    @Column(name = "type_name", length = 200, nullable = false)
    private String typeName;

    @Column(name = "needs_payment_flag", length = 1, nullable = false)
    private String needsPaymentFlag = "N";

    /** Configured amount (IDR, whole Rupiah) a requestor of this type must pay. Null = not configured. */
    @Column(name = "payment_amount")
    private Long paymentAmount;

    @Column(name = "order_by", nullable = false)
    private Integer orderBy = 0;

    @Column(name = "active_flag", length = 1, nullable = false)
    private String activeFlag = "Y";

    /**
     * {@code 'Y'} (the default) = submissions of this type go through the
     * chain's Manager Pemohon step; {@code 'N'} = that step is auto-skipped.
     *
     * <p>Note the {@code "Y"} initialiser, unlike {@link #needsPaymentFlag}'s
     * {@code "N"} — it is what makes the checkbox render CHECKED on a brand new
     * type, which is the requested default.</p>
     *
     * <p>Inert on a chain where no step carries
     * {@code ses_approval_step.step_role = 'REQUESTOR_MANAGER'}; there is
     * nothing for it to skip.</p>
     */
    @Column(name = "needs_mgr_approval_flag", length = 1, nullable = false)
    private String needsMgrApprovalFlag = "Y";

    /**
     * The same toggle for the chain's Manager Procurement step
     * ({@code ses_approval_step.step_role = 'PROCUREMENT_MANAGER'}).
     *
     * <p>Independent of {@link #needsMgrApprovalFlag}: the two steps run in
     * parallel, and switching one off leaves the other running. Switching both
     * off takes the flow straight from Staf Security to Manager Security.</p>
     */
    @Column(name = "needs_proc_approval_flag", length = 1, nullable = false)
    private String needsProcApprovalFlag = "Y";

    /**
     * Which {@code app_user.auth_type} may see and choose this type:
     * {@code ALL} (everyone), {@code LOCAL}, or {@code LDAP}.
     *
     * <p>Enforced in the application layer only — there is no DB-level
     * mechanism, the same posture this schema already takes for every
     * {@code APP_USER} reference. The rule lives in
     * {@code RequestTypeService.visibleTo}; the requestor-facing dropdown and
     * the server-side re-check on submit both go through it.</p>
     */
    @Column(name = "auth_type", length = 10, nullable = false)
    private String authType = "ALL";

    /**
     * {@code 'Y'} = submissions of this type require an external document upload step
     * via an emailed link (status WAITING_FOR_UPLOAD).
     * {@code 'N'} (the default) = worker documents are uploaded directly on the request form modal.
     */
    @Column(name = "need_ext_page_upload", length = 1, nullable = false)
    private String needExtPageUpload = "N";

    public boolean isNeedExtPageUpload() {
        return "Y".equalsIgnoreCase(needExtPageUpload);
    }
}
