package com.badaklng.approval.entity;

import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Composite id for APP_APPROVAL_VIEW: TRANS_ID (task_id) alone is not
 * unique since a task can have more than one current live holder (e.g. a
 * position with co-officers) — TRANS_ID plus APPROVER_ID identifies a
 * single row.
 */
@EqualsAndHashCode
@NoArgsConstructor
public class AppApprovalViewId implements Serializable {

    private Long transId;
    private String approverId;

    public AppApprovalViewId(Long transId, String approverId) {
        this.transId = transId;
        this.approverId = approverId;
    }
}
