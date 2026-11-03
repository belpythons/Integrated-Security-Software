package com.badaklng.history.spi;

import com.badaklng.history.model.HistoryRow;

import java.util.List;

/**
 * The ONLY thing a business module compiles against to appear on the
 * Riwayat / History screen (admin/officer-only, {@code /history}) and the
 * dashboard's "5 Pengajuan Terbaru" table — mirrors {@code
 * approval.spi.ApprovalFormDescriptor}/{@code payment.spi.PaymentBusinessDescriptor}'s
 * loose-coupling role exactly: the history module discovers implementations
 * via {@code ObjectProvider<HistorySource>}, with zero compile-time
 * dependency on any business module.
 * <p>
 * Deliberately a single bulk method rather than one-row-at-a-time lookups
 * (unlike the payment/approval SPIs) — a history feed is inherently a bulk
 * listing, and a per-row round trip per business module would be an N+1
 * query pattern for no benefit.
 */
public interface HistorySource {

    List<HistoryRow> allRows();

    /**
     * Stable business code, matching {@code SES_HISTORY_V.BUSINESS_TYPE} —
     * how the paged feed finds the module that owns a view row.
     *
     * <p>Defaults to null so an existing implementation still compiles; a
     * module that does not answer this simply never matches a view row, and
     * its rows are skipped by the paged feed while {@link #allRows()} keeps
     * working. That is deliberate: silently dropping a module from a listing
     * is worse than a module that opts out explicitly.
     */
    default String businessType() {
        return null;
    }

    /**
     * Read-only detail URL for one record, or null when nothing is viewable
     * yet (a DRAFT whose approval instance was never triggered).
     *
     * <p>This stays in the owning module on purpose. {@code SES_HISTORY_V}
     * supplies the ids; the shape of the link -- {@code /approval/detail/{id}}
     * once a chain exists, the module's own view page before that -- is the
     * module's business, and putting it in a shared SQL view would move that
     * knowledge across the boundary this SPI exists to hold.
     *
     * <p>Pure string formatting: no query, so the paged feed stays free of the
     * per-row lookups the class javadoc above warns against.
     */
    default String viewUrl(Long businessRefId, Long approvalInstanceId) {
        return null;
    }

    /** Document download URL for one record, or null when there is none. */
    default String documentUrl(Long businessRefId, String status) {
        return null;
    }
}
