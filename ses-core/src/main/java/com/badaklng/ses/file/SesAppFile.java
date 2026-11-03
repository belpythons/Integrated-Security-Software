package com.badaklng.ses.file;

import com.badaklng.platform.audit.FullAuditEntity;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Lob;
import javax.persistence.PrePersist;
import javax.persistence.Table;
import java.util.UUID;

/**
 * SES-owned mapping of the shared {@code app_file} table.
 * <p>
 * Platform's {@code com.badaklng.platform.system.entity.AppFile} maps only the
 * original six columns and does not extend {@link FullAuditEntity}, so it cannot
 * populate {@code created_by}/{@code created_date}/{@code updated_by}/{@code updated_date}.
 * Platform is a read-only Maven artifact and is not edited here. This entity is the
 * ses-owned replacement used by the whole app (approval attachments, request module
 * worker documents, proof of payment, ...) so that {@code AuditEntityListener} can
 * populate the audit columns from the logged-in user. Platform's {@code AppFile} /
 * {@code AppFileRepository} stay unused within {@code ses}.
 */
@Getter
@Setter
@Entity
@Table(name = "app_file")
public class SesAppFile extends FullAuditEntity {

    @Id
    @Column(name = "file_id", length = 36, updatable = false, nullable = false)
    private String fileId;

    /** Caller-chosen convention {@code "<OWNER_TABLE>:<pk>"}; not FK'd. */
    @Column(name = "ref_id", length = 100)
    private String refId;

    @Column(name = "file_name", length = 255, nullable = false)
    private String fileName;

    /** The SNIFFED content type (magic-number detection), never the raw client Content-Type header. */
    @Column(name = "file_type", length = 100, nullable = false)
    private String fileType;

    @Lob
    @Column(name = "file_data", nullable = false)
    private byte[] fileData;

    /**
     * Byte count. Platform's AppFile.java uses Double for file_size, which is wrong for a
     * byte count; this entity uses Long, matching the DDL's NUMBER column.
     */
    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    /**
     * Mirrors platform's AppFile.generateId() pattern. FullAuditEntity's own
     * AuditEntityListener (registered via @EntityListeners on the mapped superclass)
     * still fires its own @PrePersist/@PreUpdate callbacks alongside this instance
     * callback — JPA runs entity-listener callbacks and the entity's own lifecycle
     * callback methods independently, so there is no conflict as long as this method
     * doesn't reuse the listener's method name.
     */
    @PrePersist
    private void generateId() {
        if (fileId == null) {
            fileId = UUID.randomUUID().toString();
        }
    }
}
