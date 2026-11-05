package com.badaklng.request.entity;

import com.badaklng.platform.audit.FullAuditEntity;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.IdClass;
import javax.persistence.Table;

/**
 * Which documents a request type demands, PER WORKER. Composite PK
 * (typeCode, docCode) via @IdClass, following platform's
 * AppCat/AppCatId pattern exactly. Unlike AppCat (which is not
 * audit-tracked), this table DOES carry the four audit columns per the
 * DDL, so — unlike the AppCat reference — this entity extends
 * FullAuditEntity as well as using @IdClass; the two are independent
 * (FullAuditEntity is a @MappedSuperclass contributing plain columns,
 * @IdClass only concerns the @Id-annotated fields).
 */
@Getter
@Setter
@Entity
@Table(name = "ses_request_type_doc")
@IdClass(SesRequestTypeDocId.class)
public class SesRequestTypeDoc extends FullAuditEntity {

    @Id
    @Column(name = "type_code", length = 30, updatable = false)
    private String typeCode;

    @Id
    @Column(name = "doc_code", length = 30, updatable = false)
    private String docCode;

    @Column(name = "mandatory_flag", length = 1, nullable = false)
    private String mandatoryFlag = "Y";

    @Column(name = "order_by", nullable = false)
    private Integer orderBy = 0;
}
