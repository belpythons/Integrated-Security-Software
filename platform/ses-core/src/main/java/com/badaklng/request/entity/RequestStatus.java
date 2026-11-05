package com.badaklng.request.entity;

/**
 * The six values {@code ses_request.status} may hold.
 *
 * <p><b>Constants, not an enum, on purpose.</b> The column is a plain
 * {@code VARCHAR2(20)} and roughly twenty call sites across this module, the
 * dashboard, the history module and six Thymeleaf templates already compare it
 * against string literals. An enum would be the better design in a greenfield
 * module; here it would mean touching every one of those sites in a change
 * whose actual subject is something else, and Thymeleaf would still be
 * comparing strings. Naming the values in one place gets most of the benefit —
 * a typo becomes a compile error at every site that opts in — at none of the
 * cost.</p>
 *
 * <p>Deliberately NOT the approval module's {@code ApprovalStatus} enum, even
 * though four of the values line up. This module must stay ignorant of
 * approval's entity package; see {@code RequestApprovalBridge}, the single
 * sanctioned exception.</p>
 */
public final class RequestStatus {

    /** Saved but not submitted. Only the requestor sees it; nothing is routed. */
    public static final String DRAFT = "DRAFT";

    /**
     * Submitted, but the worker documents have not arrived yet.
     *
     * <p>The one status with an external dependency: the request sits here
     * until whoever holds the documents opens the emailed token link and
     * uploads them. <b>No approval instance exists yet</b> — the chain is
     * triggered only once the upload completes, because an approver asked to
     * decide on a submission with no documents can only say "wait".</p>
     */
    public static final String WAITING_FOR_UPLOAD = "WAITING_FOR_UPLOAD";

    /** In the approval chain. An {@code approval_instance_id} exists from here on. */
    public static final String PENDING = "PENDING";

    /** Every step approved. Written by {@code RequestApprovalBridge}, never by this module. */
    public static final String APPROVED = "APPROVED";

    /** Some step rejected. Written by {@code RequestApprovalBridge}. */
    public static final String REJECTED = "REJECTED";

    /** Withdrawn by the requestor, or swept up by {@code RequestExpiryJob}. */
    public static final String CANCELLED = "CANCELLED";

    /**
     * Can the requestor still change this submission?
     *
     * <p>{@code WAITING_FOR_UPLOAD} counts: nobody has been asked to approve
     * anything yet, so editing the header or the worker list harms nobody.
     * It is the same reasoning that lets a PENDING request be edited while its
     * approval is still untouched — see {@code ApprovalService.isEditable},
     * which PENDING additionally has to satisfy.</p>
     */
    public static boolean isRequestorEditable(String status) {
        return DRAFT.equals(status)
                || WAITING_FOR_UPLOAD.equals(status)
                || PENDING.equals(status);
    }

    /** Nothing more will happen to this submission on its own. */
    public static boolean isTerminal(String status) {
        return APPROVED.equals(status) || REJECTED.equals(status) || CANCELLED.equals(status);
    }

    private RequestStatus() {
    }
}
