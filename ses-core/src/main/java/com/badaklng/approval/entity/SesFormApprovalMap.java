package com.badaklng.approval.entity;

import com.badaklng.platform.audit.FullAuditEntity;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;

/**
 * Binds a form to a chain. match_key NULL = the form's DEFAULT chain; a
 * non-null match_key OVERRIDES the default for one routing value (e.g. one
 * request type). The routing value itself is never an approval-module
 * concept: it is whatever code the form module's OWN table uses.
 */
@Getter
@Setter
@Entity
@Table(name = "ses_form_approval_map")
public class SesFormApprovalMap extends FullAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ses_form_approval_map_seq")
    @SequenceGenerator(name = "ses_form_approval_map_seq", sequenceName = "SEQ_SES_FORM_APPROVAL_MAP", allocationSize = 1)
    @Column(name = "map_id")
    private Long mapId;

    @Column(name = "form_code", length = 100, nullable = false)
    private String formCode;

    /** NULL means this row is the form's default chain. */
    @Column(name = "match_key", length = 50)
    private String matchKey;

    /** Plain FK column, no @ManyToOne. */
    @Column(name = "chain_id", nullable = false)
    private Long chainId;

    @Column(name = "active_flag", length = 1, nullable = false)
    private String activeFlag = "Y";
}
