package com.badaklng.request.entity;

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
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import java.util.Date;

/**
 * One submitted request. {@code status} is this module's OWN plain-String
 * concept — see {@link RequestStatus} for the six values — deliberately NOT
 * the approval module's {@code ApprovalStatus} enum, even though four of them
 * line up; this module must stay ignorant of approval's entity package
 * (see RequestApprovalBridge, the one exception).
 * <p>
 * {@code requestorUserId} follows the same convention already established
 * by {@code SesApprovalInstance.requestorUserId} (see ApprovalService.trigger):
 * despite the column name, the value stored/matched is the app_user
 * USERNAME, not app_user.user_id — RequestService.findMine(username) and
 * RequestController both pass SecurityUtils.getCurrentUsername() straight
 * through.
 */
@Getter
@Setter
@Entity
@Table(name = "ses_request")
public class SesRequest extends FullAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ses_request_seq")
    @SequenceGenerator(name = "ses_request_seq", sequenceName = "SEQ_SES_REQUEST", allocationSize = 1)
    @Column(name = "request_id")
    private Long requestId;

    @Column(name = "request_no", length = 30, nullable = false, unique = true)
    private String requestNo;

    @Column(name = "type_code", length = 30, nullable = false)
    private String typeCode;

    /** Freetext allowed; deliberately NOT an FK to ses_contract. */
    @Column(name = "contract_no", length = 100)
    private String contractNo;

    @Column(name = "contract_type", length = 20)
    private String contractType;

    @Column(name = "contract_title", length = 300)
    private String contractTitle;

    @Column(name = "zone_code", length = 20)
    private String zoneCode;

    @Column(name = "instance", length = 200)
    private String instance;

    @Column(name = "address", length = 500)
    private String address;

    @Temporal(TemporalType.DATE)
    @Column(name = "start_period")
    private Date startPeriod;

    @Temporal(TemporalType.DATE)
    @Column(name = "finish_period")
    private Date finishPeriod;

    /** -> app_file.file_id, no FK (shared table). */
    @Column(name = "payment_file_id", length = 36)
    private String paymentFileId;

    /** One of {@link RequestStatus} — this module's own concept. */
    @Column(name = "status", length = 20, nullable = false)
    private String status = RequestStatus.PENDING;

    /** app_user.username of the submitter (see class javadoc). */
    @Column(name = "requestor_user_id", length = 36, nullable = false)
    private String requestorUserId;

    @Column(name = "approval_instance_id")
    private Long approvalInstanceId;

    // ---------------------------------------------------------------
    // Document-upload-by-email (sql/61)
    // ---------------------------------------------------------------

    /**
     * Where the document-upload link is sent.
     *
     * <p><b>Not the requestor's address.</b> The documents belong to the
     * contractor, so this is whoever actually holds them — which is the entire
     * reason the upload page is reachable without a login. Captured on the
     * form as "Email Penerima Dokumen" and mandatory for a submission.</p>
     */
    @Column(name = "email", length = 150)
    private String email;

    /**
     * 64 hex characters from {@code SecureRandom}, or null before the request
     * has ever been submitted.
     *
     * <p><b>This is the only thing standing between the internet and this
     * request's upload page.</b> 256 bits, not a UUID: UUIDv4 carries 122 bits
     * and not every generator behind it is cryptographic. See
     * {@code UploadTokenService} — never derive this from {@code requestNo},
     * a counter, or a timestamp.</p>
     */
    @Column(name = "upload_token", length = 100)
    private String uploadToken;

    /** When the link stops working. Set to seven days out at issue time. */
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "upload_token_expiry")
    private Date uploadTokenExpiry;

    /**
     * {@code "Y"} once the documents have been submitted through the link.
     *
     * <p>Single-use is what stops a forwarded email from reopening a
     * submission that is already in the approval chain. Opening the page does
     * NOT consume the token — only submitting does, so the person uploading
     * can come back across several sittings.</p>
     */
    @Column(name = "upload_token_used", length = 1)
    private String uploadTokenUsed;
}
