package com.badaklng.request.entity;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;
import java.util.Date;

/**
 * One row per request that is waiting for a contractor to upload its
 * documents — the bridge between {@code SES_REQUEST} and the login-free
 * upload page. See {@code sql/65_ext_upload_staging_tables.sql}.
 *
 * <p>Carries a frozen snapshot of the few fields the public page displays
 * ({@code requestNo}, {@code typeName}) so that page needs no join into the
 * business tables, and a record of the token and the finalisation so the
 * session is auditable after the fact.</p>
 *
 * <p><b>The token columns here are a PROJECTION, never an authority.</b>
 * {@code UploadTokenService.resolve} reads {@code SES_REQUEST.UPLOAD_TOKEN}
 * and nothing else; these copies are written inside the same transaction so
 * the bridge row can be read and audited on its own. Validating a token
 * against this table instead would create two checkers that can disagree,
 * which is an authentication hole rather than redundancy.</p>
 *
 * <p>No audit columns — plain {@code @Entity}, not {@code FullAuditEntity}.
 * The upload page has no principal, so {@code AuditEntityListener} would only
 * ever stamp nulls. Same reasoning, same shape as {@link SesRequestWorkerDoc};
 * {@code createdDate} / {@code finalizedDate} are written explicitly instead.</p>
 */
@Getter
@Setter
@Entity
@Table(name = "ses_ext_upload_request")
public class SesExtUploadRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ses_ext_upload_request_seq")
    @SequenceGenerator(name = "ses_ext_upload_request_seq", sequenceName = "SEQ_SES_EXT_UPLOAD_REQ", allocationSize = 1)
    @Column(name = "ext_request_id")
    private Long extRequestId;

    /** FK to {@code ses_request}. UNIQUE — one upload session per request. */
    @Column(name = "request_id", nullable = false)
    private Long requestId;

    /** Frozen at issue time so the public page renders without a join. */
    @Column(name = "request_no", length = 30)
    private String requestNo;

    @Column(name = "type_code", length = 30)
    private String typeCode;

    @Column(name = "type_name", length = 200)
    private String typeName;

    /** The contractor's address the link was sent to. NOT the requestor's. */
    @Column(name = "recipient_email", length = 150)
    private String recipientEmail;

    /** Projection of {@code ses_request.upload_token}. Audit only — see the class javadoc. */
    @Column(name = "upload_token", length = 100)
    private String uploadToken;

    @Column(name = "token_expiry")
    private Date tokenExpiry;

    /** 'Y' once the staging rows have been transferred and the chain triggered. */
    @Column(name = "is_finalized", length = 1, nullable = false)
    private String isFinalized = "N";

    @Column(name = "created_date", nullable = false)
    private Date createdDate = new Date();

    @Column(name = "finalized_date")
    private Date finalizedDate;

    /** Reads the flag as a boolean so call sites do not repeat the literal. */
    public boolean isFinalizedFlag() {
        return "Y".equalsIgnoreCase(isFinalized);
    }
}
