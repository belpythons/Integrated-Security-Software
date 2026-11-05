package com.badaklng.request.entity;

import com.badaklng.platform.audit.FullAuditEntity;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

/**
 * "Zone Access" options. Single zone per request (plain column on
 * SES_REQUEST) — a contractor needing multiple zones files multiple
 * requests.
 */
@Getter
@Setter
@Entity
@Table(name = "ses_zone")
public class SesZone extends FullAuditEntity {

    @Id
    @Column(name = "zone_code", length = 20, updatable = false, nullable = false)
    private String zoneCode;

    @Column(name = "zone_name", length = 100, nullable = false)
    private String zoneName;

    @Column(name = "order_by", nullable = false)
    private Integer orderBy = 0;

    @Column(name = "active_flag", length = 1, nullable = false)
    private String activeFlag = "Y";
}
