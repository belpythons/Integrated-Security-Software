package com.badaklng.payment.spi;

import com.badaklng.payment.model.PaymentRowData;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The ONLY thing a business module compiles against to plug into the
 * generalised payment engine — mirrors {@code approval.spi.ApprovalFormDescriptor}'s
 * role exactly: the payment module discovers implementations via
 * {@code ObjectProvider<PaymentBusinessDescriptor>}, keyed by
 * {@link #businessType()}, so it has zero compile-time dependency on any
 * business module.
 * <p>
 * Before this SPI existed, {@code com.badaklng.payment} depended directly on
 * {@code com.badaklng.request}'s repository/entity types (see the removed
 * class javadoc on {@code PaymentService}) because only one module ever used
 * the payment engine. A second module ("Stiker Kendaraan") now needs the
 * same submit/verify/pay-on-behalf machinery, so {@code ses_request_payment}
 * carries a soft {@code (business_type, business_ref_id)} pointer instead of
 * a real FK — same shape the approval module already uses for
 * {@code SesApprovalInstance}.
 */
public interface PaymentBusinessDescriptor {

    /** Stable business code, e.g. "SES_REQUEST" or "SES_STICKER_REQUEST". Matched against SesRequestPayment.businessType. */
    String businessType();

    /** Human-readable module name, shown in the payment list "Type" column. */
    String displayName();

    /** Human-friendly transaction number for the given business record (e.g. a request number). Null if unknown. */
    String transactionNo(Long businessRefId);

    /** app_user.username of the record's owner — used for ownership/access checks. Null if unknown. */
    String requestorUsername(Long businessRefId);

    /** Every business record id owned by this username, for "My Payments" — used as PaymentService.findMine's forward lookup. */
    List<Long> myRefIds(String username);

    /** Configured amount (IDR) currently owed for this record, or null if not computable/configured. */
    Long amountDue(Long businessRefId);

    /**
     * Bulk form of {@link #transactionNo}, {@link #requestorUsername} and
     * {@link #amountDue} for one list page.
     *
     * <p>The default implementation below just loops the three per-record
     * accessors, so a descriptor that does not override this keeps working
     * unchanged — but every implementation backed by a database SHOULD
     * override it. Each of those accessors is a {@code findById} on the same
     * row, so the loop costs 3 queries per payment: {@code /payment}
     * measured <b>86</b> queries for a single page before the two shipped
     * descriptors overrode this.
     *
     * <p>Implementations must tolerate ids that no longer exist (simply omit
     * them) and must chunk their own {@code IN} lists — Oracle rejects more
     * than 1000 expressions in one {@code IN} with ORA-01795.
     *
     * @return map keyed by business ref id; missing key = record not found
     */
    default Map<Long, PaymentRowData> rowData(Collection<Long> businessRefIds) {
        Map<Long, PaymentRowData> out = new LinkedHashMap<>();
        for (Long id : businessRefIds) {
            out.put(id, new PaymentRowData(transactionNo(id), requestorUsername(id), amountDue(id)));
        }
        return out;
    }

    /**
     * Full read-only detail data for the payment detail pages, keyed to
     * match whatever {@link #detailFragment()} expects — empty map if the
     * module has no detail view to show.
     */
    Map<String, Object> detailModel(Long businessRefId);

    /** Thymeleaf fragment expression (e.g. "request/fragments/request-detail :: detail") rendering {@link #detailModel}, or null for none. */
    String detailFragment();
}
