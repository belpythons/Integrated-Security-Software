package com.badaklng.payment.entity;

import com.badaklng.platform.audit.FullAuditEntity;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import java.util.Date;

/**
 * Current-state payment/proof-of-payment item for one business record.
 * {@code (businessType, businessRefId)} is a soft pointer — deliberately NOT
 * an FK (see ses/sql/26_payment_generalize_alter.sql) — resolved at the
 * application layer via {@code com.badaklng.payment.spi.PaymentBusinessDescriptor},
 * same shape the approval module uses for {@code SesApprovalInstance}. Not
 * unique on the pointer either: one business record has conceptually one
 * "current" payment item today, enforced only in
 * {@link com.badaklng.payment.service.PaymentService#ensurePayment} at the
 * application layer, so a future multi-payment-per-record model change
 * never needs a constraint drop.
 */
@Getter
@Setter
@Entity
@Table(name = "ses_request_payment")
public class SesRequestPayment extends FullAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ses_request_payment_seq")
    @SequenceGenerator(name = "ses_request_payment_seq", sequenceName = "SEQ_SES_REQUEST_PAYMENT", allocationSize = 1)
    @Column(name = "payment_id")
    private Long paymentId;

    /** e.g. "SES_REQUEST" | "SES_STICKER_REQUEST" — matches a registered PaymentBusinessDescriptor.businessType(). */
    @Column(name = "business_type", length = 30, nullable = false)
    private String businessType;

    @Column(name = "business_ref_id", nullable = false)
    private Long businessRefId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private PaymentStatus status = PaymentStatus.PENDING;

    /** -> app_file.file_id, no FK (shared table). */
    @Column(name = "proof_file_id", length = 36)
    private String proofFileId;

    /** Actual amount (IDR, whole Rupiah) the officer recorded as received — set on approve, autofilled from SesRequestType.paymentAmount but editable. */
    @Column(name = "paid_amount")
    private Long paidAmount;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    /** -> app_user.user_id, no FK (platform-owned). */
    @Column(name = "reviewed_by", length = 36)
    private String reviewedBy;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "reviewed_date")
    private Date reviewedDate;
}
