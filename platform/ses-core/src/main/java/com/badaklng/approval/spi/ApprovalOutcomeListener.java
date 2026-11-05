package com.badaklng.approval.spi;

import com.badaklng.approval.entity.ApprovalStatus;

/**
 * Callback SPI: the approval module notifies a form module when one of its
 * instances finalises (APPROVED or REJECTED). Notification happens in the
 * SAME transaction as the instance's status transition — a listener
 * exception rolls the whole transaction back, so business status can never
 * drift from the approval outcome.
 */
public interface ApprovalOutcomeListener {

    /** Must equal the businessRefType passed to ApprovalService.trigger(...). */
    String businessRefType();

    void onCompleted(Long businessRefId, ApprovalStatus finalStatus);
}
