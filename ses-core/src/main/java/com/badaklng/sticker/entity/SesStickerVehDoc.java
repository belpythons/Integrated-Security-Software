package com.badaklng.sticker.entity;

import com.badaklng.platform.audit.FullAuditEntity;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import java.util.Date;

/**
 * One document attachment (STNK, SIM, KIR) on a {@link SesStickerVehicle}.
 * Maps to table {@code ses_sticker_veh_doc}.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "ses_sticker_veh_doc")
public class SesStickerVehDoc extends FullAuditEntity {

    public static final String DOC_STNK = "STNK";
    public static final String DOC_SIM = "SIM";
    public static final String DOC_KIR = "KIR";

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ses_sticker_veh_doc_seq")
    @SequenceGenerator(name = "ses_sticker_veh_doc_seq", sequenceName = "SEQ_SES_STICKER_VEH_DOC", allocationSize = 1)
    @Column(name = "sticker_veh_doc_id")
    private Long stickerVehDocId;

    @Column(name = "sticker_vehicle_id", nullable = false)
    private Long stickerVehicleId;

    /** STNK, SIM, KIR, etc. */
    @Column(name = "doc_code", length = 30, nullable = false)
    private String docCode;

    /** Pointer to app_file.file_id */
    @Column(name = "file_id", length = 36)
    private String fileId;

    @Column(name = "expiry_date")
    @Temporal(TemporalType.DATE)
    private Date expiryDate;
}
