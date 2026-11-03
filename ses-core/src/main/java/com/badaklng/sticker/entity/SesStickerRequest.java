package com.badaklng.sticker.entity;

import com.badaklng.platform.audit.FullAuditEntity;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;

/**
 * One submitted Vehicle Sticker ("Stiker Kendaraan") request. Mirrors
 * {@code com.badaklng.request.entity.SesRequest}'s conventions: {@code
 * status} is this module's OWN plain-String concept, deliberately NOT the
 * approval module's {@code ApprovalStatus} enum; {@code requestorUserId}
 * stores the app_user USERNAME (not user_id) despite the column name.
 */
@Getter
@Setter
@Entity
@Table(name = "ses_sticker_request")
public class SesStickerRequest extends FullAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ses_sticker_req_seq")
    @SequenceGenerator(name = "ses_sticker_req_seq", sequenceName = "SEQ_SES_STICKER_REQ", allocationSize = 1)
    @Column(name = "sticker_request_id")
    private Long stickerRequestId;

    @Column(name = "request_no", length = 30, nullable = false, unique = true)
    private String requestNo;

    @Column(name = "requestor_name", length = 200, nullable = false)
    private String requestorName;

    @Column(name = "badge", length = 50)
    private String badge;

    @Column(name = "department", length = 200)
    private String department;

    @Column(name = "section", length = 200)
    private String section;

    @Column(name = "instance", length = 200)
    private String instance;

    @Column(name = "phone_no", length = 50)
    private String phoneNo;

    @Column(name = "work_phone_no", length = 50)
    private String workPhoneNo;

    @Column(name = "address", length = 500)
    private String address;

    /** "DRAFT" | "PENDING" | "APPROVED" | "REJECTED" | "CANCELLED" — this module's own concept. */
    @Column(name = "status", length = 20, nullable = false)
    private String status = "PENDING";

    /** app_user.username of the submitter (see class javadoc). */
    @Column(name = "requestor_user_id", length = 36, nullable = false)
    private String requestorUserId;

    /** -> ses_sticker_type.sticker_type_code (natural key, single uniform type per batch request). */
    @Column(name = "sticker_type_code", length = 30)
    private String stickerTypeCode;

    @Column(name = "contract_no", length = 100)
    private String contractNo;

    @Column(name = "contract_expiry_date")
    private java.util.Date contractExpiryDate;

    @Column(name = "requestor_type", length = 30)
    private String requestorType;

    @Column(name = "approval_instance_id")
    private Long approvalInstanceId;
}
