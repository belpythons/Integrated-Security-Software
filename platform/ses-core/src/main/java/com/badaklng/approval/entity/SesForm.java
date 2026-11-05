package com.badaklng.approval.entity;

import com.badaklng.platform.audit.FullAuditEntity;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import java.util.Date;

/**
 * Registry of form modules that use the approval engine. Rows are
 * upserted at startup from every ApprovalFormDescriptor bean found on the
 * classpath (ApprovalFormRegistrySync) — the approval module has ZERO
 * compile-time dependency on any form module, only on that interface.
 * All columns below are descriptor-synced and therefore READ-ONLY in the
 * GUI: code always wins on the next boot.
 */
@Getter
@Setter
@Entity
@Table(name = "ses_form")
public class SesForm extends FullAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ses_form_seq")
    @SequenceGenerator(name = "ses_form_seq", sequenceName = "SEQ_SES_FORM", allocationSize = 1)
    @Column(name = "form_id")
    private Long formId;

    @Column(name = "form_code", length = 100, nullable = false, unique = true)
    private String formCode;

    @Column(name = "form_name", length = 200, nullable = false)
    private String formName;

    @Column(name = "match_key_label", length = 200)
    private String matchKeyLabel;

    @Column(name = "active_flag", length = 1, nullable = false)
    private String activeFlag = "Y";

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "last_seen_date")
    private Date lastSeenDate;
}
