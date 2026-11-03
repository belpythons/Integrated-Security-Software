package com.badaklng.payment.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Date;

/** One row of the /payment/recap cash-inflow recap — one per VERIFIED payment item. */
@Getter
@AllArgsConstructor
public class PaymentRecapRow {
    private final Long paymentId;
    private final String requestNo;
    private final String typeName;
    private final String requestorUserId;
    private final Long paidAmount;
    private final Date reviewedDate;
    private final String reviewedByName;
}
