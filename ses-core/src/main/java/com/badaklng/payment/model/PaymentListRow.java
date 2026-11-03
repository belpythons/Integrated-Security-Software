package com.badaklng.payment.model;

import com.badaklng.payment.entity.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Date;

/** List row DTO shared by the requestor list (/payment) and the admin queue (/payment/verify). */
@Getter
@AllArgsConstructor
public class PaymentListRow {
    private final Long paymentId;
    private final String businessType;
    private final Long businessRefId;
    private final String transactionNo;
    /** Owning module's display name, e.g. "Pengajuan Baru" / "Stiker Kendaraan". */
    private final String moduleName;
    private final PaymentStatus status;
    private final String requestorUserId;
    private final Date createdDate;
    /** Configured amount (IDR) currently due — null if not configured/computable. */
    private final Long amount;
    /** Officer-recorded actual amount received — null until verified. */
    private final Long paidAmount;
}
