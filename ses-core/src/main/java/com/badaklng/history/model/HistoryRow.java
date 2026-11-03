package com.badaklng.history.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Date;

/** One row of the Riwayat / History screen and the dashboard's "5 Pengajuan Terbaru" table. */
@Getter
@AllArgsConstructor
public class HistoryRow {
    /** e.g. "SES_REQUEST" | "SES_STICKER_REQUEST" — for filtering/badging, not a link key. */
    private final String businessType;
    /** Owning module's display name, e.g. "Pengajuan Baru" / "Stiker Kendaraan". */
    private final String moduleName;
    private final String transactionNo;
    /**
     * The owning {@code requestor_user_id} — a USERNAME, verbatim. This is the
     * field to filter on; {@link #requestorDisplay} is for reading, not for
     * matching, and the two stopped being the same string the moment the
     * screens started showing real names.
     */
    private final String requestorUserId;
    /** Requestor's real name, resolved via {@code UserDisplayNameService}. */
    private final String requestorDisplay;
    /** This module's own plain-String status concept — "DRAFT"/"PENDING"/"APPROVED"/"REJECTED"/"CANCELLED". */
    private final String status;
    private final Date createdDate;
    /** Read-only detail page URL, or null when nothing is viewable yet (e.g. a DRAFT with no approval instance triggered). */
    private final String viewUrl;
    /** Optional document download/preview URL (e.g. "/request/{id}/document.pdf"), or null if unavailable. */
    private final String documentUrl;
    /** Approval instance ID if triggered, used for checking approver involvement. */
    private final Long instanceId;

    public HistoryRow(String businessType, String moduleName, String transactionNo,
                      String requestorUserId, String requestorDisplay, String status,
                      Date createdDate, String viewUrl, String documentUrl) {
        this(businessType, moduleName, transactionNo, requestorUserId, requestorDisplay,
                status, createdDate, viewUrl, documentUrl, null);
    }

    public HistoryRow(String businessType, String moduleName, String transactionNo,
                      String requestorUserId, String requestorDisplay, String status,
                      Date createdDate, String viewUrl) {
        this(businessType, moduleName, transactionNo, requestorUserId, requestorDisplay,
                status, createdDate, viewUrl, null, null);
    }
}

