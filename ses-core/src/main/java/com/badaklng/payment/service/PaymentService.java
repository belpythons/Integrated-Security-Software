package com.badaklng.payment.service;

import com.badaklng.approval.service.ApprovalService;
import com.badaklng.payment.entity.PaymentHistoryAction;
import com.badaklng.payment.entity.PaymentStatus;
import com.badaklng.payment.entity.SesRequestPayment;
import com.badaklng.payment.entity.SesRequestPaymentHistory;
import com.badaklng.payment.model.PaymentListRow;
import com.badaklng.payment.model.PaymentRowData;
import com.badaklng.payment.repository.SesRequestPaymentHistoryRepository;
import com.badaklng.payment.repository.SesRequestPaymentRepository;
import com.badaklng.payment.spi.PaymentBusinessDescriptor;
import com.badaklng.platform.system.entity.AppUser;
import com.badaklng.platform.system.repository.AppUserRepository;
import com.badaklng.ses.file.AppFileService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Core service for the generalised payment module. Every business module
 * that needs proof-of-payment tracking (currently {@code com.badaklng.request}
 * and {@code com.badaklng.sticker}) plugs in via a
 * {@link PaymentBusinessDescriptor} bean, resolved here through {@code
 * ObjectProvider} (NOT {@code List} — a zero-descriptor classpath would
 * otherwise fail {@code List} constructor injection at boot, same reasoning
 * documented on {@code ApprovalFormRegistrySync}/{@code
 * ApprovalChainController}'s {@code ObjectProvider<CheckpointTaskType>|<ApprovalFormDescriptor>}).
 * {@code ses_request_payment.(business_type, business_ref_id)} is a soft
 * pointer, not an FK — see {@link SesRequestPayment}'s class javadoc.
 */
@Service
@RequiredArgsConstructor
public class PaymentService {

    private static final String CHECKPOINT_KEY = "PAYMENT_VERIFICATION";
    private static final String FILE_REF_PREFIX = "SES_PAYMENT:";

    private final SesRequestPaymentRepository paymentRepository;
    private final SesRequestPaymentHistoryRepository historyRepository;
    private final AppFileService appFileService;
    private final ApprovalService approvalService;
    private final AppUserRepository appUserRepository;
    private final ObjectProvider<PaymentBusinessDescriptor> descriptors;

    @org.springframework.beans.factory.annotation.Value("${ses.payment.verified-cache-ttl-ms:60000}")
    private long verifiedCacheTtlMs;

    private volatile List<SesRequestPayment> cachedVerified;
    private volatile long cachedVerifiedExpiresAt;

    // ---------------------------------------------------------------
    // Descriptor resolution
    // ---------------------------------------------------------------

    /** Resolves the registered descriptor for a payment's businessType. Throws if none is registered — a payment row must never point at an unregistered business type. */
    /**
     * Files a proof of payment as "Bukti Bayar - REQ-2026-0016.pdf" instead of
     * whatever the phone called it ("WhatsApp Image ....jpeg"). The name is both
     * the attachment card's title and the Content-Disposition filename, so this
     * is what makes the download tidy too.
     */
    private String proofFileName(SesRequestPayment payment) {
        String no = descriptorFor(payment.getBusinessType()).transactionNo(payment.getBusinessRefId());
        return "Bukti Bayar" + (no == null || no.trim().isEmpty() ? "" : " - " + no.trim());
    }

    public PaymentBusinessDescriptor descriptorFor(String businessType) {
        return descriptors.stream()
                .filter(d -> d.businessType().equals(businessType))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No PaymentBusinessDescriptor registered for business type: " + businessType));
    }

    // ---------------------------------------------------------------
    // List rendering
    // ---------------------------------------------------------------

    /**
     * Per-payment transaction number, owner and amount due for a whole list
     * page, keyed by {@code paymentId}.
     *
     * <p>Groups the payments by business type and asks each descriptor once
     * via {@link PaymentBusinessDescriptor#rowData}, instead of the three
     * single-record accessors per row that /payment, /payment/verify and
     * /payment/recap all used to call. Those three accessors each resolve to
     * a {@code findById} on the same business row, so the old shape cost
     * ~5 queries per row — 86 for one measured /payment render.
     *
     * <p>Several payments may point at the same business record, so the
     * descriptor is given a de-duplicated id set (see {@code InChunks}).
     */
    @Transactional(readOnly = true)
    public Map<Long, PaymentRowData> rowData(Collection<SesRequestPayment> payments) {
        Map<Long, PaymentRowData> byPaymentId = new LinkedHashMap<>();
        if (payments == null || payments.isEmpty()) {
            return byPaymentId;
        }
        Map<String, List<SesRequestPayment>> byType = payments.stream()
                .collect(Collectors.groupingBy(SesRequestPayment::getBusinessType));
        for (Map.Entry<String, List<SesRequestPayment>> entry : byType.entrySet()) {
            Map<Long, PaymentRowData> byRefId = descriptorFor(entry.getKey()).rowData(
                    entry.getValue().stream()
                            .map(SesRequestPayment::getBusinessRefId)
                            .collect(Collectors.toSet()));
            for (SesRequestPayment payment : entry.getValue()) {
                byPaymentId.put(payment.getPaymentId(), byRefId.get(payment.getBusinessRefId()));
            }
        }
        return byPaymentId;
    }

    /**
     * The payment list row shape shared by the requestor list (/payment) and
     * the officer queue (/payment/verify). Both controllers carried a
     * byte-identical private {@code toRows} before this; they now call here
     * so the batching above cannot be fixed in one and missed in the other.
     *
     * <p>{@code amount} stays null once a payment is VERIFIED — at that point
     * {@code paidAmount} is the number that matters and the configured amount
     * would only invite a mismatch on screen.
     */
    @Transactional(readOnly = true)
    public List<PaymentListRow> listRows(List<SesRequestPayment> payments) {
        if (payments == null || payments.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, PaymentRowData> data = rowData(payments);
        List<PaymentListRow> rows = new ArrayList<>(payments.size());
        for (SesRequestPayment p : payments) {
            PaymentRowData d = data.get(p.getPaymentId());
            rows.add(new PaymentListRow(
                    p.getPaymentId(), p.getBusinessType(), p.getBusinessRefId(),
                    d == null ? null : d.getTransactionNo(),
                    descriptorFor(p.getBusinessType()).displayName(),
                    p.getStatus(),
                    d == null ? null : d.getRequestorUsername(),
                    p.getCreatedDate(),
                    p.getStatus() == PaymentStatus.VERIFIED || d == null ? null : d.getAmountDue(),
                    p.getPaidAmount()));
        }
        return rows;
    }

    // ---------------------------------------------------------------
    // Finders
    // ---------------------------------------------------------------

    @Transactional(readOnly = true)
    public SesRequestPayment findById(Long paymentId) {
        return paymentRepository.findById(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("Payment not found: " + paymentId));
    }

    /** The requestor's own payment items across every registered business module, most recent first. */
    @Transactional(readOnly = true)
    public List<SesRequestPayment> findMine(String username) {
        List<SesRequestPayment> result = new ArrayList<>();
        for (PaymentBusinessDescriptor d : descriptors) {
            List<Long> refIds = d.myRefIds(username);
            if (!refIds.isEmpty()) {
                result.addAll(paymentRepository.findByBusinessTypeAndBusinessRefIdInOrderByPaymentIdDesc(d.businessType(), refIds));
            }
        }
        return result;
    }

    /** Admin review queue: items currently awaiting a decision, across every business module. */
    @Transactional(readOnly = true)
    public List<SesRequestPayment> findQueue() {
        return paymentRepository.findByStatusOrderByPaymentIdAsc(PaymentStatus.VERIFYING);
    }

    /**
     * Officer worklist on /payment — every item that still needs somebody to
     * do something, across every requestor, newest first.
     *
     * <p>Was PENDING-only, which is why a newly registered request "did not
     * show up": the moment its requestor uploaded proof it became VERIFYING
     * and dropped off this list, and a REJECTED item appeared on no admin
     * screen at all (/payment/verify lists VERIFYING only, the recap lists
     * VERIFIED only). VERIFIED and CANCELLED stay out — those are finished,
     * and the recap already reports VERIFIED.</p>
     */
    @Transactional(readOnly = true)
    public List<SesRequestPayment> findForOfficer() {
        return paymentRepository.findByStatusInOrderByPaymentIdDesc(java.util.Arrays.asList(
                PaymentStatus.PENDING, PaymentStatus.VERIFYING, PaymentStatus.REJECTED));
    }

    /** The current payment item of one business record, if it has one. */
    @Transactional(readOnly = true)
    public Optional<SesRequestPayment> findByBusiness(String businessType, Long businessRefId) {
        return paymentRepository.findFirstByBusinessTypeAndBusinessRefIdOrderByPaymentIdDesc(businessType, businessRefId);
    }

    /**
     * Cash-inflow recap source: verified items, most recently reviewed first.
     * TTL-cached — {@code /dashboard} calls this twice per hit (summary's
     * this-month total, then the 12-month cash chart) and it's a full-table
     * read, same reasoning as {@link #verifiedCacheTtlMs}.
     */
    @Transactional(readOnly = true)
    public List<SesRequestPayment> findVerified() {
        long now = System.currentTimeMillis();
        List<SesRequestPayment> cached = cachedVerified;
        if (cached != null && cachedVerifiedExpiresAt > now) {
            return cached;
        }
        List<SesRequestPayment> fresh = paymentRepository.findByStatusOrderByReviewedDateDesc(PaymentStatus.VERIFIED);
        cachedVerified = fresh;
        cachedVerifiedExpiresAt = now + verifiedCacheTtlMs;
        return fresh;
    }

    private void invalidateVerifiedCache() {
        cachedVerified = null;
    }

    /**
     * The same recap narrowed to a review-date window; either bound may be
     * null for "open at that end".
     *
     * <p>Filters {@code reviewedDate} rather than {@code createdDate}: the
     * money arrives when an officer verifies it, so a payment submitted in
     * March and verified in April belongs to April's figures.</p>
     *
     * <p>The end bound is pushed to the last instant of its day by
     * {@link FinancialReportExcelService#endOfDay}. {@code reviewedDate} is a
     * TIMESTAMP and the repository's {@code BETWEEN} is inclusive, so a bare
     * midnight bound would silently drop everything verified during the final
     * day of the range — a whole day of money missing from a report with no
     * sign that anything was left out.</p>
     */
    @Transactional(readOnly = true)
    public List<SesRequestPayment> findVerified(java.util.Date from, java.util.Date to) {
        if (from == null && to == null) {
            return findVerified();
        }
        return paymentRepository.findByStatusAndReviewedDateBetweenOrderByReviewedDateDesc(
                PaymentStatus.VERIFIED,
                from == null ? new java.util.Date(0L) : FinancialReportExcelService.startOfDay(from),
                FinancialReportExcelService.endOfDay(to));
    }

    /**
     * Total cash in — the sum of every VERIFIED payment, as a single aggregate.
     *
     * <p>Exposed as its own method so the Pengeluaran Kas screen and the
     * dashboard widget share ONE definition of "money received" with
     * {@code /payment/recap}. Two screens disagreeing about that total would be
     * worse than either being slightly stale.</p>
     */
    @Transactional(readOnly = true)
    public long totalVerifiedAmount() {
        return paymentRepository.sumPaidAmountByStatus(PaymentStatus.VERIFIED);
    }

    @Transactional(readOnly = true)
    public List<SesRequestPaymentHistory> findHistory(Long paymentId) {
        return historyRepository.findByPaymentIdOrderByHistoryIdAsc(paymentId);
    }

    /** Current proof file id for a business record's payment item, if any — used to keep an edit form's "current file" note accurate. */
    @Transactional(readOnly = true)
    public Optional<String> currentProofFileId(String businessType, Long businessRefId) {
        return paymentRepository.findFirstByBusinessTypeAndBusinessRefIdOrderByPaymentIdDesc(businessType, businessRefId)
                .map(SesRequestPayment::getProofFileId);
    }

    /** Informational only — whether approving right now would actually unlock a chain step. */
    @Transactional(readOnly = true)
    public ApprovalService.CheckpointReachability checkpointReachability(String businessType, Long businessRefId) {
        return approvalService.checkCheckpoint(businessType, businessRefId, CHECKPOINT_KEY);
    }

    // ---------------------------------------------------------------
    // Mutations
    // ---------------------------------------------------------------

    /** Find-or-create the current payment item for a business record. Idempotent — safe to call on every save. */
    @Transactional
    public SesRequestPayment ensurePayment(String businessType, Long businessRefId, String username) {
        return paymentRepository.findFirstByBusinessTypeAndBusinessRefIdOrderByPaymentIdDesc(businessType, businessRefId)
                .orElseGet(() -> {
                    SesRequestPayment payment = new SesRequestPayment();
                    payment.setBusinessType(businessType);
                    payment.setBusinessRefId(businessRefId);
                    payment.setStatus(PaymentStatus.PENDING);
                    return paymentRepository.save(payment);
                });
    }

    /**
     * Requestor uploads (or re-uploads, after a rejection) proof of
     * payment. Allowed only from PENDING/REJECTED — VERIFYING/VERIFIED
     * already have an active or accepted submission. The superseded proof
     * file (on a re-upload) is deliberately NOT deleted from app_file, so
     * the history card's SUBMITTED snapshot links stay valid forever —
     * matches the existing app-wide convention of never deleting a
     * superseded app_file row.
     */
    @Transactional
    public SesRequestPayment submitProof(Long paymentId, MultipartFile file, String username) {
        SesRequestPayment payment = findById(paymentId);
        if (payment.getStatus() != PaymentStatus.PENDING && payment.getStatus() != PaymentStatus.REJECTED) {
            throw new IllegalStateException("Cannot upload proof of payment while status is " + payment.getStatus());
        }
        String fileId = appFileService.store(file, FILE_REF_PREFIX + paymentId, proofFileName(payment));
        payment.setProofFileId(fileId);
        payment.setStatus(PaymentStatus.VERIFYING);
        payment.setRejectionReason(null);
        payment.setReviewedBy(null);
        payment.setReviewedDate(null);
        payment = paymentRepository.save(payment);

        appendHistory(payment.getPaymentId(), PaymentHistoryAction.SUBMITTED, fileId, null, null, username);
        return payment;
    }

    /**
     * Convenience path for a request form's inline "Proof of Payment"
     * upload: ensure the payment row exists, then submit its first proof —
     * the exact same logic the dedicated /payment page's upload action
     * uses, so the item starts life already VERIFYING instead of PENDING.
     */
    @Transactional
    public SesRequestPayment submitProofFromRequestForm(String businessType, Long businessRefId, MultipartFile file, String username) {
        SesRequestPayment payment = ensurePayment(businessType, businessRefId, username);
        return submitProof(payment.getPaymentId(), file, username);
    }

    /**
     * Officer-only shortcut: uploads proof AND records it verified in one
     * step, on behalf of the requestor — used when an officer pays for a
     * requestor directly instead of going through Switch User. Since the
     * officer IS the verificator here, normal verification is skipped —
     * but that means the actual amount received (normally captured on
     * {@link #approve}) has to be captured here instead, so the caller must
     * supply it up front. Allowed only from PENDING/REJECTED, same as
     * {@link #submitProof}. Writes TWO history rows
     * ({@link PaymentHistoryAction#SUBMITTED_ON_BEHALF} then {@link
     * PaymentHistoryAction#APPROVED}), both actorUserId = the OFFICER (not
     * the requestor) — this is the audit trail fix the feature exists for.
     */
    @Transactional
    public SesRequestPayment submitAndVerifyOnBehalf(Long paymentId, MultipartFile file, Long paidAmount,
                                                       String note, String officerUsername) {
        if (paidAmount == null || paidAmount < 0) {
            throw new IllegalArgumentException("A valid amount (IDR) received is required.");
        }
        SesRequestPayment payment = findById(paymentId);
        if (payment.getStatus() != PaymentStatus.PENDING && payment.getStatus() != PaymentStatus.REJECTED) {
            throw new IllegalStateException("Cannot pay on behalf while status is " + payment.getStatus());
        }
        String fileId = appFileService.store(file, FILE_REF_PREFIX + paymentId, proofFileName(payment));
        String officerUserId = resolveUserId(officerUsername);

        payment.setProofFileId(fileId);
        payment.setStatus(PaymentStatus.VERIFIED);
        payment.setPaidAmount(paidAmount);
        payment.setRejectionReason(null);
        payment.setReviewedBy(officerUserId);
        payment.setReviewedDate(new Date());
        payment = paymentRepository.save(payment);
        invalidateVerifiedCache();

        appendHistory(payment.getPaymentId(), PaymentHistoryAction.SUBMITTED_ON_BEHALF, fileId, note, null, officerUsername);
        appendHistory(payment.getPaymentId(), PaymentHistoryAction.APPROVED, null, note, paidAmount, officerUsername);

        approvalService.completeCheckpointByKey(payment.getBusinessType(), payment.getBusinessRefId(), CHECKPOINT_KEY,
                FILE_REF_PREFIX + paymentId, note, officerUserId, officerUsername);
        return payment;
    }

    /**
     * Called after an edit-resubmit cancels the old approval instance and
     * triggers a fresh one: {@link ApprovalService#trigger} always freezes
     * a brand-new PAYMENT_VERIFICATION checkpoint as PENDING for the new
     * instance, even if this record's payment was already VERIFIED in the
     * prior cycle. Left alone, that checkpoint can never be completed —
     * {@link #approve} only accepts VERIFYING — so the chain step it gates
     * would stay blocked forever despite the payment already being
     * accepted. Re-completes the new checkpoint immediately instead. No-op
     * if there's no payment yet or it isn't VERIFIED.
     */
    @Transactional
    public void carryForwardVerifiedCheckpoint(String businessType, Long businessRefId, String username) {
        paymentRepository.findFirstByBusinessTypeAndBusinessRefIdOrderByPaymentIdDesc(businessType, businessRefId)
                .filter(payment -> payment.getStatus() == PaymentStatus.VERIFIED)
                .ifPresent(payment -> {
                    String userId = resolveUserId(username);
                    approvalService.completeCheckpointByKey(businessType, businessRefId, CHECKPOINT_KEY,
                            FILE_REF_PREFIX + payment.getPaymentId(),
                            "Carried forward — already verified in a prior approval cycle", userId, username);
                });
    }

    /**
     * Approval-outcome side effect — called only when a business record's
     * whole approval flow ends REJECTED (a terminal outcome, unlike an
     * edit-resubmit's cancel-and-retrigger, so there's no future instance
     * left to carry anything forward to). Cleans up whatever's left of the
     * payment item instead of leaving it dangling:
     * <ul>
     *   <li>PENDING/VERIFYING (no money confirmed, or an unreviewed
     *       submission) — flipped to CANCELLED so it drops out of the
     *       officer's {@link #findQueue}/{@link #findAllPending} review
     *       queues for a record that's already dead.</li>
     *   <li>VERIFIED (money already confirmed received) — status left
     *       untouched on purpose; only a history entry is appended so
     *       finance can spot it needs a manual refund look.</li>
     *   <li>REJECTED (an officer already rejected the proof itself) or no
     *       payment row at all — nothing to do.</li>
     * </ul>
     */
    @Transactional
    public void onRequestRejected(String businessType, Long businessRefId) {
        paymentRepository.findFirstByBusinessTypeAndBusinessRefIdOrderByPaymentIdDesc(businessType, businessRefId).ifPresent(payment -> {
            if (payment.getStatus() == PaymentStatus.PENDING || payment.getStatus() == PaymentStatus.VERIFYING) {
                payment.setStatus(PaymentStatus.CANCELLED);
                paymentRepository.save(payment);
                appendHistory(payment.getPaymentId(), PaymentHistoryAction.CANCELLED, null,
                        "Request rejected", null, "SYSTEM");
            } else if (payment.getStatus() == PaymentStatus.VERIFIED) {
                appendHistory(payment.getPaymentId(), PaymentHistoryAction.REFUND_FLAGGED, null,
                        "Request rejected after payment was verified — review for possible refund", null, "SYSTEM");
            }
        });
    }

    /**
     * Admin approves a submitted proof of payment, recording how much money
     * actually came in (autofilled from the descriptor's amountDue by the
     * caller, but the officer may edit it before submitting — this is the
     * figure the cash-inflow recap sums). Allowed only from VERIFYING.
     * Unconditionally calls {@link ApprovalService#completeCheckpointByKey}
     * — a safe no-op (logged at WARN, never throws) if no chain step is
     * currently configured with the PAYMENT_VERIFICATION checkpoint key, so
     * approving is never blocked by chain configuration.
     */
    @Transactional
    public SesRequestPayment approve(Long paymentId, String note, Long paidAmount, String username) {
        if (paidAmount == null || paidAmount < 0) {
            throw new IllegalArgumentException("A valid amount (IDR) received is required to approve a proof of payment.");
        }
        SesRequestPayment payment = findById(paymentId);
        if (payment.getStatus() != PaymentStatus.VERIFYING) {
            throw new IllegalStateException("Cannot approve payment while status is " + payment.getStatus());
        }
        String userId = resolveUserId(username);
        payment.setStatus(PaymentStatus.VERIFIED);
        payment.setPaidAmount(paidAmount);
        payment.setReviewedBy(userId);
        payment.setReviewedDate(new Date());
        payment = paymentRepository.save(payment);
        invalidateVerifiedCache();

        appendHistory(payment.getPaymentId(), PaymentHistoryAction.APPROVED, null, null, paidAmount, username);

        approvalService.completeCheckpointByKey(payment.getBusinessType(), payment.getBusinessRefId(), CHECKPOINT_KEY,
                FILE_REF_PREFIX + paymentId, note, userId, username);
        return payment;
    }

    /**
     * Admin rejects a submitted proof of payment — the SAME row flips back
     * to REJECTED (not a new row), a reason is mandatory, and the
     * requestor must re-upload. Never touches the approval checkpoint:
     * reject can only happen while VERIFYING, i.e. before any approve ever
     * occurred for this submission cycle, so there is nothing to
     * un-complete.
     */
    @Transactional
    public SesRequestPayment reject(Long paymentId, String reason, String username) {
        if (reason == null || reason.trim().isEmpty()) {
            throw new IllegalArgumentException("A reason is required to reject a proof of payment.");
        }
        SesRequestPayment payment = findById(paymentId);
        if (payment.getStatus() != PaymentStatus.VERIFYING) {
            throw new IllegalStateException("Cannot reject payment while status is " + payment.getStatus());
        }
        String userId = resolveUserId(username);
        payment.setStatus(PaymentStatus.REJECTED);
        payment.setRejectionReason(reason);
        payment.setReviewedBy(userId);
        payment.setReviewedDate(new Date());
        payment = paymentRepository.save(payment);

        appendHistory(payment.getPaymentId(), PaymentHistoryAction.REJECTED, null, reason, null, username);
        return payment;
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private void appendHistory(Long paymentId, PaymentHistoryAction action, String proofFileId, String reason, Long amount, String username) {
        SesRequestPaymentHistory h = new SesRequestPaymentHistory();
        h.setPaymentId(paymentId);
        h.setAction(action);
        h.setProofFileId(proofFileId);
        h.setReason(reason);
        h.setAmount(amount);
        h.setActorUserId(resolveUserId(username));
        h.setActorDate(new Date());
        historyRepository.save(h);
    }

    private String resolveUserId(String username) {
        return appUserRepository.findByUsername(username).map(AppUser::getUserId).orElse(username);
    }
}
