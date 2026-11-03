package com.badaklng.request.entity;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

/**
 * Read-only JPA view of SES_WORKER_LOOKUP_VIEW (see
 * ses/sql/23_worker_lookup_view.sql) — one deduplicated row per real
 * 6-digit employee badge, merged across EMP_NIK_VIEW, APP_POSITION_VIEW,
 * active APP_PERSON Mitra rows, LDAP APP_USER rows and past
 * SES_REQUEST_WORKER rows. Badgeless rows
 * (freetext LOCAL users, old unbadged worker rows) pass through unmerged.
 * <p>
 * Like {@link com.badaklng.approval.entity.AppPositionView}, this must
 * never be persisted, updated, or deleted — it is a query-only source for
 * the Worker Data table's autofill combobox.
 */
@Getter
@Setter
@Entity
@Table(name = "SES_WORKER_LOOKUP_VIEW")
public class SesWorkerLookupView {

    /** Synthetic — assigned by the view's ROW_NUMBER(), not a stable business key. */
    @Id
    @Column(name = "LOOKUP_ID")
    private Long lookupId;

    /** Null for badgeless rows. */
    @Column(name = "BADGE", length = 50)
    private String badge;

    @Column(name = "WORKER_NAME", length = 200)
    private String workerName;

    @Column(name = "NIK", length = 20)
    private String nik;

    @Column(name = "JABATAN", length = 200)
    private String jabatan;

    @Column(name = "DEPARTMENT", length = 200)
    private String department;

    @Column(name = "SECTION", length = 200)
    private String section;

    @Column(name = "GENDER", length = 1)
    private String gender;
}
