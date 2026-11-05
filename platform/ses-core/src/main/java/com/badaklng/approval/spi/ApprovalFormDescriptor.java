package com.badaklng.approval.spi;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * The ONLY thing a form module compiles against to register itself with the
 * approval engine. Implement as a {@code @Component} bean; the approval
 * module discovers it via {@code ObjectProvider<ApprovalFormDescriptor>} —
 * it has zero compile-time dependency on any form module.
 */
public interface ApprovalFormDescriptor {

    /** Stable business code for this form, e.g. "SES_REQUEST". */
    String formCode();

    /** Human-readable name shown in the approval GUI. */
    String formName();

    /** Label for the routing dropdown, or null if this form has no routing values. */
    default String matchKeyLabel() {
        return null;
    }

    /**
     * Routing dropdown, read live from the form module's OWN table. Only
     * (code, label) crosses the boundary — business columns stay in the
     * module.
     */
    default List<MatchKeyOption> matchKeys() {
        return Collections.emptyList();
    }

    /**
     * Human-friendly transaction number for the given business record (e.g.
     * a request number), shown in the approver worklist. Null if the module
     * has none — callers fall back to a generic businessRefType+id label.
     */
    default String transactionId(Long businessRefId) {
        return null;
    }

    /**
     * One-line summary of what the business record is about, shown in the
     * approver worklist. Null if the module has none.
     */
    default String description(Long businessRefId) {
        return null;
    }

    /** {@link #transactionId} and {@link #description} for one row, fetched together. */
    final class FormRowData {
        private final String transactionId;
        private final String description;

        public FormRowData(String transactionId, String description) {
            this.transactionId = transactionId;
            this.description = description;
        }

        public String getTransactionId() {
            return transactionId;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * Bulk form of {@link #transactionId} and {@link #description} for one
     * worklist page.
     *
     * <p>The default loops the two single-record methods, so a descriptor
     * that does not override this keeps working — but both shipped
     * implementations resolve each of those to a {@code findById} on the
     * same business row, and {@code description} adds a second lookup for
     * the type name. That is 3 queries per worklist row, on a list the
     * navbar badge makes every page in the app touch.
     *
     * <p>Implementations must omit ids that no longer exist and must chunk
     * their own {@code IN} lists (Oracle's 1000-expression cap).
     */
    default Map<Long, FormRowData> rowData(java.util.Collection<Long> businessRefIds) {
        Map<Long, FormRowData> out = new java.util.LinkedHashMap<>();
        for (Long id : businessRefIds) {
            out.put(id, new FormRowData(transactionId(id), description(id)));
        }
        return out;
    }

    /**
     * Full read-only detail data for the approval detail page (approver
     * view), rendered via whichever fragment {@link #detailFragment()}
     * names — the request module's default keys its map
     * "requestForm"/"typeName"/"zoneName" to match
     * {@code request/fragments/request-detail :: detail}. Empty map
     * (default) when the module has no detail view to show.
     */
    default Map<String, Object> detailModel(Long businessRefId) {
        return Collections.emptyMap();
    }

    /**
     * Ordered label -> value pairs for the detail card inside the approval
     * notification email, e.g. {@code "Contract Number" -> "CONTRACT-01"}.
     * Use a {@link java.util.LinkedHashMap}; iteration order is the render
     * order. Empty map (default) renders no detail rows.
     *
     * <p>Kept separate from {@link #detailModel} on purpose. That one returns
     * form objects for a Thymeleaf fragment to walk; a mail body is plain
     * pre-rendered text assembled in Java, with a hard {@code VARCHAR2(4000)}
     * ceiling behind it (see {@code ApprovalEmailTemplateBuilder}). So the
     * contract here is narrower than it looks:</p>
     * <ul>
     *   <li><b>Omit blank values</b> rather than mapping them to a dash — the
     *       omitted rows are what keeps a full form inside the column.</li>
     *   <li><b>Pre-format</b> dates and amounts; nothing downstream formats.</li>
     *   <li><b>Summarise repeating data</b> ("Jumlah Kendaraan: 3") instead of
     *       emitting one row per child record, whose count is unbounded.</li>
     * </ul>
     */
    default Map<String, String> emailDetail(Long businessRefId) {
        return Collections.emptyMap();
    }

    /**
     * Absolute-from-context path of the requestor's OWN read-only page for
     * this record, e.g. {@code "/request/12/view"}. Null (default) when the
     * module has none.
     *
     * <p>Exists because the outcome notification goes to the requestor, and
     * {@code /approval/detail/{id}} — where every approval mail used to point
     * — is the approver's screen: an Approve/Reject panel, a checkpoint list
     * and a step timeline, none of which is the requestor's to act on. The
     * assignment notification still points there, because that screen is
     * exactly what its reader was asked to use.</p>
     */
    default String requestorViewPath(Long businessRefId) {
        return null;
    }

    /**
     * External/contractor "document recipient" email captured at submission
     * (SES_REQUEST's "Email Penerima Dokumen"), or null if the module has none
     * or it is blank. When present, the outcome mail (APPROVED/REJECTED) is
     * also sent here as an informational notice — see
     * {@code ApprovalMailService#sendOutcomeEmail}.
     */
    default String extNotifyEmail(Long businessRefId) {
        return null;
    }

    /**
     * Thymeleaf fragment expression rendering {@link #detailModel}, e.g.
     * {@code "sticker/fragments/sticker-detail :: detail"}. Defaults to the
     * request module's fragment for backward compatibility with the
     * original single-module version of this SPI — override when
     * {@link #detailModel} returns a different key shape.
     */
    default String detailFragment() {
        return "request/fragments/request-detail :: detail";
    }
}
