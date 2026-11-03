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

/**
 * One uploaded file per (worker, document requirement). Per the DDL this
 * table carries NO audit columns — plain @Entity, not FullAuditEntity —
 * matching SesApprovalTaskAssigneeDef's style for a no-audit table.
 */
@Getter
@Setter
@Entity
@Table(name = "ses_request_worker_doc")
public class SesRequestWorkerDoc {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ses_request_worker_doc_seq")
    @SequenceGenerator(name = "ses_request_worker_doc_seq", sequenceName = "SEQ_SES_REQUEST_WORKER_DOC", allocationSize = 1)
    @Column(name = "worker_doc_id")
    private Long workerDocId;

    @Column(name = "worker_id", nullable = false)
    private Long workerId;

    @Column(name = "doc_code", length = 30, nullable = false)
    private String docCode;

    /** -> app_file.file_id, no FK (shared table). */
    @Column(name = "file_id", length = 36, nullable = false)
    private String fileId;
}
