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
 * One document a contractor has uploaded but NOT yet finalised, keyed by
 * (worker, document requirement). See
 * {@code sql/65_ext_upload_staging_tables.sql}.
 *
 * <p>Separate from {@link SesRequestWorkerDoc} so a half-collected set of
 * files is distinguishable from the official document set of a submission.
 * {@code RequestService.finalizeUpload} moves these rows across — by
 * repointing {@code app_file.ref_id} via {@code AppFileService.reassign} and
 * writing the {@code ses_request_worker_doc} row, never by copying bytes.</p>
 *
 * <p><b>{@code fileId} only — this entity must never grow a BLOB field.</b>
 * The bytes stay as the single copy in {@code app_file.file_data}. Two stores
 * holding the same bytes is two stores that can hold different bytes, and
 * duplicating tens of megabytes to assert "the same document" is a more
 * expensive lie than the pointer is (the reasoning Fase 9 §5.3 already
 * applied to renewals sharing a {@code file_id}).</p>
 *
 * <p>{@code fileName} / {@code fileContentType} / {@code fileSize} are frozen
 * snapshots so the upload page can list what is already in without touching
 * the BLOB table — reading {@code app_file} to render a filename pulls the
 * {@code @Lob} column along with it.</p>
 *
 * <p>No audit columns, for the same reason as {@link SesExtUploadRequest}:
 * there is no logged-in user on the page that writes these rows.</p>
 */
@Getter
@Setter
@Entity
@Table(name = "ses_ext_upload_doc")
public class SesExtUploadDoc {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ses_ext_upload_doc_seq")
    @SequenceGenerator(name = "ses_ext_upload_doc_seq", sequenceName = "SEQ_SES_EXT_UPLOAD_DOC", allocationSize = 1)
    @Column(name = "ext_doc_id")
    private Long extDocId;

    @Column(name = "ext_request_id", nullable = false)
    private Long extRequestId;

    /**
     * Denormalised from the bridge row. Earns its place: every read on this
     * table is "the documents for request X", and the ownership re-check that
     * closes the IDOR on the upload page compares against it directly.
     */
    @Column(name = "request_id", nullable = false)
    private Long requestId;

    /** Logical FK to {@code ses_request_worker}; re-checked against {@link #requestId} before any write. */
    @Column(name = "worker_id", nullable = false)
    private Long workerId;

    /** Logical FK to {@code ses_doc_requirement.doc_code}. */
    @Column(name = "doc_code", length = 30, nullable = false)
    private String docCode;

    /** Snapshot of the requirement's display name at upload time. */
    @Column(name = "doc_name", length = 200)
    private String docName;

    /** -> {@code app_file.file_id}, no FK (shared table). */
    @Column(name = "file_id", length = 36, nullable = false)
    private String fileId;

    @Column(name = "file_name", length = 255)
    private String fileName;

    /** The SNIFFED type from {@code AppFileService}, never the client's Content-Type header. */
    @Column(name = "file_content_type", length = 100)
    private String fileContentType;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "uploaded_at", nullable = false)
    private Date uploadedAt = new Date();

    /**
     * 'N' = deleted by the contractor. Soft, so the unique index can stay
     * off it and an upload-replace-upload sequence leaves a readable trail;
     * the {@code app_file} row behind it IS hard-deleted, because an orphan
     * BLOB is the leak {@code sql/51_orphan_app_file_cleanup.sql} exists to
     * mop up.
     */
    @Column(name = "is_active", length = 1, nullable = false)
    private String isActive = "Y";

    public boolean isActiveFlag() {
        return "Y".equalsIgnoreCase(isActive);
    }
}
