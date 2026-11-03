package com.badaklng.sticker.entity;

import com.badaklng.platform.audit.FullAuditEntity;
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
 * One "Data Kendaraan" row on a {@link SesStickerRequest}. Mirrors {@code
 * com.badaklng.request.entity.SesRequestWorker}'s shape — a real FK to its
 * header, full audit columns (unlike the no-audit
 * {@code SesRequestWorkerDoc}, since this table plays the "one repeat-row
 * per request" role {@code SesRequestWorker} plays, not the "one file per
 * row" role {@code SesRequestWorkerDoc} plays).
 */
@Getter
@Setter
@Entity
@Table(name = "ses_sticker_vehicle")
public class SesStickerVehicle extends FullAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ses_sticker_vehicle_seq")
    @SequenceGenerator(name = "ses_sticker_vehicle_seq", sequenceName = "SEQ_SES_STICKER_VEHICLE", allocationSize = 1)
    @Column(name = "sticker_vehicle_id")
    private Long stickerVehicleId;

    @Column(name = "sticker_request_id", nullable = false)
    private Long stickerRequestId;

    @Column(name = "line_no", nullable = false)
    private Integer lineNo;

    /** -> ses_sticker_type.sticker_type_code, no FK (same soft-pointer convention as ses_request.type_code). */
    @Column(name = "sticker_type_code", length = 30, nullable = false)
    private String stickerTypeCode;

    /** Nomor Polisi. */
    @Column(name = "police_no", length = 30, nullable = false)
    private String policeNo;

    /** Jenis Kendaraan (free text — e.g. "Sedan", "Truk"). */
    @Column(name = "vehicle_type", length = 100)
    private String vehicleType;

    /** Atas Nama. */
    @Column(name = "owner_name", length = 200)
    private String ownerName;

    /** Merk. */
    @Column(name = "brand", length = 100)
    private String brand;

    /** Warna. */
    @Column(name = "color", length = 50)
    private String color;

    /** Tahun Buat — free text (4-digit year), not modeled as a DATE since only the year is meaningful. */
    @Column(name = "year_made", length = 4)
    private String yearMade;

    /** Lampiran Surat — -> app_file.file_id, no FK (shared table). */
    @Column(name = "letter_file_id", length = 36)
    private String letterFileId;

    /** Driver / Worker information (connected from SES_WORKER_LOOKUP_VIEW). */
    @Column(name = "driver_badge", length = 50)
    private String driverBadge;

    @Column(name = "driver_name", length = 200)
    private String driverName;

    @Column(name = "driver_nik", length = 16)
    private String driverNik;

    @Column(name = "sim_no", length = 50)
    private String simNo;

    @Column(name = "sim_class", length = 10)
    private String simClass;

    @Column(name = "valid_from")
    @javax.persistence.Temporal(javax.persistence.TemporalType.DATE)
    private java.util.Date validFrom;

    @Column(name = "valid_to")
    @javax.persistence.Temporal(javax.persistence.TemporalType.DATE)
    private java.util.Date validTo;
}
