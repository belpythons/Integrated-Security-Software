package com.badaklng.history.entity;

import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Immutable;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.util.Date;

/**
 * Read-only projection over {@code SES_HISTORY_V} (see
 * {@code sql/production/261019/82_ses_history_view.sql}).
 *
 * <p>Exists so {@code /history} can page and search in the database. The
 * previous shape merged every {@code HistorySource} bean in memory — both
 * business tables read whole, sorted, and rendered in full — which is why the
 * page was 107 KB for 37 rows.
 *
 * <p>Carries no URLs. A history row's view and document links are built by the
 * owning module through {@code HistorySource}, from the raw ids below; putting
 * their shape in a shared SQL view would move module knowledge into the
 * history module, which is exactly the boundary the SPI exists to hold.
 *
 * <p>{@code @Immutable} is not decoration: a view has no primary key, so
 * Hibernate is told to treat {@link #historyKey} as the identity and never to
 * attempt a write. Adding a setter that is actually used would produce an
 * UPDATE against a view and fail at runtime, not at compile time.
 */
@Getter
@Setter
@Entity
@Immutable
@Table(name = "ses_history_v")
public class SesHistoryView {

    /**
     * {@code '<BUSINESS_TYPE>:<id>'} — synthetic, because the view unions two
     * tables whose own ids collide.
     */
    @Id
    @Column(name = "history_key", length = 120)
    private String historyKey;

    /** Matched against {@code HistorySource.businessType()} to find the owning module. */
    @Column(name = "business_type", length = 50)
    private String businessType;

    @Column(name = "business_ref_id")
    private Long businessRefId;

    /** Display name: the badge type's name, or a literal for modules with one kind. */
    @Column(name = "module_name", length = 200)
    private String moduleName;

    @Column(name = "transaction_no", length = 50)
    private String transactionNo;

    /** A USERNAME, verbatim — see {@code HistoryRow.requestorUserId}. */
    @Column(name = "requestor_user_id", length = 100)
    private String requestorUserId;

    @Column(name = "status", length = 30)
    private String status;

    @Column(name = "created_date")
    private Date createdDate;

    /** Null until the approval chain is triggered. */
    @Column(name = "approval_instance_id")
    private Long approvalInstanceId;
}
