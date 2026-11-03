package com.badaklng.request.entity;

import com.badaklng.platform.audit.FullAuditEntity;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

/**
 * Master list of document kinds a worker may need to upload (KTP, Kartu
 * Keluarga, ...). GUI-managed alongside request types.
 */
@Getter
@Setter
@Entity
@Table(name = "ses_doc_requirement")
public class SesDocRequirement extends FullAuditEntity {

    @Id
    @Column(name = "doc_code", length = 30, updatable = false, nullable = false)
    private String docCode;

    @Column(name = "doc_name", length = 200, nullable = false)
    private String docName;

    @Column(name = "doc_desc", length = 500)
    private String docDesc;

    @Column(name = "active_flag", length = 1, nullable = false)
    private String activeFlag = "Y";
}
