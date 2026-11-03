package com.badaklng.approval.entity;

import com.badaklng.platform.audit.FullAuditEntity;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;

/**
 * One approval chain. Fully GUI-managed: admins create, rename,
 * activate/deactivate, and delete chains.
 */
@Getter
@Setter
@Entity
@Table(name = "ses_approval_chain")
public class SesApprovalChain extends FullAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ses_approval_chain_seq")
    @SequenceGenerator(name = "ses_approval_chain_seq", sequenceName = "SEQ_SES_APPROVAL_CHAIN", allocationSize = 1)
    @Column(name = "chain_id")
    private Long chainId;

    @Column(name = "chain_key", length = 100, nullable = false, unique = true)
    private String chainKey;

    @Column(name = "chain_name", length = 200, nullable = false)
    private String chainName;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "active_flag", length = 1, nullable = false)
    private String activeFlag = "Y";
}
