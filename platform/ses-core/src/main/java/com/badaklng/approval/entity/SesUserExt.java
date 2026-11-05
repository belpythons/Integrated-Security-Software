package com.badaklng.approval.entity;

import com.badaklng.platform.audit.FullAuditEntity;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;

/**
 * SES-owned extension of APP_USER (platform table — never edited here).
 * Holds the "report to who" position used as fallback for dynamic
 * (REQUESTOR_MANAGER) resolution and for external/local users without
 * a position in APP_POSITION_VIEW.
 */
@Getter
@Setter
@Entity
@Table(name = "ses_user_ext")
public class SesUserExt extends FullAuditEntity {

    @Id
    @Column(name = "user_id", length = 36, nullable = false)
    private String userId;

    /** APP_POSITION_VIEW.POS_ID the user reports to (no DB FK; read-only view). */
    @Column(name = "report_to_pos_id", length = 10)
    private String reportToPosId;
}
