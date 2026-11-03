package com.badaklng.payment.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * The three per-record fields a payment list row needs from its owning
 * business module, fetched in bulk.
 *
 * <p>This exists because the three single-record accessors on
 * {@code PaymentBusinessDescriptor} ({@code transactionNo},
 * {@code requestorUsername}, {@code amountDue}) were being called once per
 * row while rendering {@code /payment}, {@code /payment/verify} and
 * {@code /payment/recap}, and every one of them resolves to a
 * {@code findById} on the same business row. Measured on the live schema:
 * {@code /payment} issued <b>86</b> queries for one page, most of them the
 * identical primary-key lookup repeated three times per payment.
 *
 * <p>Keeping the per-record methods is deliberate — single-item paths
 * ({@code requireOwner}, the detail pages) are correct and clearer with
 * them. Only list rendering goes through
 * {@code PaymentBusinessDescriptor.rowData}.
 */
@Getter
@AllArgsConstructor
public class PaymentRowData {

    /** Human-friendly transaction number, e.g. a request number. Null if unknown. */
    private final String transactionNo;

    /** {@code app_user.username} of the record's owner. Null if unknown. */
    private final String requestorUsername;

    /** Configured amount (IDR) currently owed, or null if not computable/configured. */
    private final Long amountDue;
}
