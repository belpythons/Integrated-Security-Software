package com.badaklng.payment.entity;

/**
 * Lifecycle of one {@link SesRequestPayment} row. PENDING/REJECTED both
 * require the requestor to (re-)upload proof; VERIFYING is the admin
 * review queue; VERIFIED is terminal and triggers the approval-chain
 * checkpoint (see com.badaklng.payment.spi.PaymentApprovalBridge). CANCELLED
 * is terminal too, set automatically (never by a human) when the request's
 * whole approval flow ends REJECTED while this payment was still
 * PENDING/VERIFYING — see PaymentService#onRequestRejected. A payment that
 * had already reached VERIFIED before that rejection is left VERIFIED, not
 * CANCELLED — money already changed hands, that's a fact this status field
 * shouldn't erase.
 */
public enum PaymentStatus {
    PENDING,
    VERIFYING,
    VERIFIED,
    REJECTED,
    CANCELLED
}
