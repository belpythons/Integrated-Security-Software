package com.badaklng.request.entity;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import java.util.Date;

/**
 * Read-only JPA view of CONTRACT_V — the real contract master, granted
 * (read-only) from another database/schema, same convention as
 * {@link com.badaklng.approval.entity.AppPositionView}. Backs the New
 * Request form's Contract Number Select2 search + autofill.
 * <p>
 * On ELLDEV, {@code ses.CONTRACT_V} is a live view over
 * {@code CARSIS.CONTRACT_V} (see sql/33_contract_v_use_carsis_view.sql) —
 * deduped on NO_KONTRAK and NULL-key rows excluded, since the raw CARSIS
 * source carries both (see that script's header for why). Environments
 * without a CARSIS grant fall back to the static snapshot table from
 * sql/24_contract_v_dev_seed.sql (DEV-ONLY).
 * <p>
 * This entity must never be persisted, updated, or deleted. NO_KONTRAK
 * is the view's distinct key.
 */
@Getter
@Setter
@Entity
@Table(name = "CONTRACT_V")
public class ContractV {

    @Id
    @Column(name = "NO_KONTRAK", length = 20)
    private String noKontrak;

    @Column(name = "COMPANY", length = 200)
    private String company;

    @Column(name = "JUDUL_PEKERJAAN", length = 1000)
    private String judulPekerjaan;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "TANGGAL_MULAI")
    private Date tanggalMulai;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "TANGGAL_SELESAI")
    private Date tanggalSelesai;
}
