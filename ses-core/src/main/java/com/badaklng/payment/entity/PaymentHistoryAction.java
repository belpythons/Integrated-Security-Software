package com.badaklng.payment.entity;

/** One entry kind in the append-only {@link SesRequestPaymentHistory} log. */
public enum PaymentHistoryAction {
    SUBMITTED,
    /** Officer uploaded proof AND verified it in one step, on the requestor's behalf — see PaymentService#submitAndVerifyOnBehalf. */
    SUBMITTED_ON_BEHALF,
    APPROVED,
    REJECTED,
    /** System-driven, not a human action — see PaymentService#onRequestRejected. */
    CANCELLED,
    /** VERIFIED payment whose request was later rejected — flags it for manual refund review, doesn't change the payment's own status. */
    REFUND_FLAGGED
}
