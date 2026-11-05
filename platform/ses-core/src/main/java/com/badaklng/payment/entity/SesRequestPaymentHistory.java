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
 * Append-only submit/approve/reject log for one {@link SesRequestPayment}
 * — backs the detail page's "history card" instead of a new payment row
 * (and a new list entry) per rejection cycle. Never updated after insert.
 */
@Getter
@Setter
@Entity
@Table(name = "ses_request_payment_history")
public class SesRequestPaymentHistory extends FullAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ses_request_payment_history_seq")
    @SequenceGenerator(name = "ses_request_payment_history_seq", sequenceName = "SEQ_SES_REQUEST_PAY_HISTORY", allocationSize = 1)
    @Column(name = "history_id")
    private Long historyId;

    @Column(name = "payment_id", nullable = false)
    private Long paymentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", length = 20, nullable = false)
    private PaymentHistoryAction action;

    /** Snapshot at SUBMITTED time; null for APPROVED/REJECTED rows. -> app_file.file_id, no FK. */
    @Column(name = "proof_file_id", length = 36)
    private String proofFileId;

    /** Rejection reason snapshot, REJECTED rows only. */
    @Column(name = "reason", length = 500)
    private String reason;

    /** Amount (IDR) recorded by the officer — snapshot at APPROVED time, null for SUBMITTED/REJECTED rows. */
    @Column(name = "amount")
    private Long amount;

    /** -> app_user.user_id, no FK (platform-owned). */
    @Column(name = "actor_user_id", length = 36, nullable = false)
    private String actorUserId;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "actor_date", nullable = false)
    private Date actorDate;
}
