package com.badaklng.sticker.entity;

import com.badaklng.platform.audit.FullAuditEntity;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

/**
 * "Jenis Sticker" master — GUI-managed at /admin/sticker-type. Natural key
 * (stickerTypeCode), no sequence — same convention as SesRequestType. Each
 * type carries its own {@code price}: the total amount due for a sticker
 * request is an app_parameter-configured base amount plus the sum of each
 * vehicle row's own jenis-stiker price (see StickerService#amountDue).
 */
@Getter
@Setter
@Entity
@Table(name = "ses_sticker_type")
public class SesStickerType extends FullAuditEntity {

    @Id
    @Column(name = "sticker_type_code", length = 30, updatable = false, nullable = false)
    private String stickerTypeCode;

    @Column(name = "sticker_type_name", length = 200, nullable = false)
    private String stickerTypeName;

    /** IDR, whole Rupiah — added to the base amount for every vehicle row using this type. */
    @Column(name = "price", nullable = false)
    private Long price = 0L;

    @Column(name = "order_by", nullable = false)
    private Integer orderBy = 0;

    @Column(name = "active_flag", length = 1, nullable = false)
    private String activeFlag = "Y";
}
