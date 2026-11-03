package com.badaklng.approval.entity;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import java.util.Date;

/**
 * Read-only JPA view of APP_APPROVAL_VIEW — SES's unified pending-approval
 * inbox (sql/36_app_approval_view_ses.sql), built on
 * SES_APPROVAL_PENDING_VIEW (sql/35). One row per (approval task, current
 * live holder) — see that script's header for the live-holder resolution
 * order (PJS via APP_POSITION_TMP_VIEW, then APP_EMP_INFO_VIEW, then the
 * frozen ses_approval_task_assignee value as a last resort).
 * <p>
 * This entity must never be persisted, updated, or deleted.
 */
@Getter
@Setter
@Entity
@IdClass(AppApprovalViewId.class)
@Table(name = "APP_APPROVAL_VIEW")
public class AppApprovalView {

    @Id
    @Column(name = "TRANS_ID")
    private Long transId;

    @Column(name = "TRANS_CAT", length = 100)
    private String transCat;

    @Column(name = "TRANS_DESC", length = 4000)
    private String transDesc;

    @Id
    @Column(name = "APPROVER_ID", length = 20)
    private String approverId;

    @Column(name = "APPROVER_NAME", length = 200)
    private String approverName;

    @Column(name = "REQ_BY", length = 36)
    private String reqBy;

    @Column(name = "REQ_BY_NAME", length = 200)
    private String reqByName;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "REQ_DATE")
    private Date reqDate;

    @Column(name = "URL_DETAIL", length = 200)
    private String urlDetail;

    @Column(name = "URL_APPRV", length = 200)
    private String urlApprv;

    @Column(name = "IS_SIMPLE_APPRV")
    private Integer isSimpleApprv;
}
