package com.badaklng.payment.repository;

import com.badaklng.payment.entity.PaymentStatus;
import com.badaklng.payment.entity.SesRequestPayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SesRequestPaymentRepository extends JpaRepository<SesRequestPayment, Long> {

    /** The "current" payment item for a business record — highest paymentId wins (see ensurePayment). */
    Optional<SesRequestPayment> findFirstByBusinessTypeAndBusinessRefIdOrderByPaymentIdDesc(String businessType, Long businessRefId);

    List<SesRequestPayment> findByBusinessTypeAndBusinessRefIdInOrderByPaymentIdDesc(String businessType, List<Long> businessRefIds);

    List<SesRequestPayment> findByStatusOrderByPaymentIdAsc(PaymentStatus status);

    /** Officer "pay on behalf" candidates — every PENDING item across every requestor, newest first. */
    List<SesRequestPayment> findByStatusOrderByPaymentIdDesc(PaymentStatus status);

    /** Officer worklist: several statuses at once — see PaymentService#findForOfficer. */
    List<SesRequestPayment> findByStatusInOrderByPaymentIdDesc(java.util.Collection<PaymentStatus> statuses);

    /** Cash-inflow recap source — verified items, most recently reviewed first. */
    List<SesRequestPayment> findByStatusOrderByReviewedDateDesc(PaymentStatus status);

    /**
     * The same recap, restricted to a review-date window.
     *
     * <p>Filters on {@code reviewedDate}, not {@code createdDate}: a cash-in
     * report answers "what money arrived in this period", and the money
     * arrives when an officer verifies it. A payment submitted in March and
     * verified in April belongs to April, and using the creation date would
     * put it in a month whose total had already been reported.</p>
     *
     * <p>{@code BETWEEN} is inclusive at both ends, so callers must pass the
     * end bound at 23:59:59 of the chosen day — {@code reviewedDate} is a
     * TIMESTAMP, and a bare date would silently drop everything verified
     * after midnight on the last day of the range.</p>
     */
    List<SesRequestPayment> findByStatusAndReviewedDateBetweenOrderByReviewedDateDesc(
            PaymentStatus status, java.util.Date from, java.util.Date to);

    /**
     * Total cash in: the sum of every VERIFIED payment.
     *
     * <p>An aggregate, not {@code findVerified().stream()...sum()}. This figure
     * is rendered on the admin dashboard for every page load and again on the
     * Pengeluaran Kas screen, and the payment table grows without bound.</p>
     *
     * <p>{@code COALESCE} rather than a nullable return, so an environment with
     * no verified payments yet reads as {@code 0} and no caller has to
     * null-check a number.</p>
     */
    @org.springframework.data.jpa.repository.Query(
            "select coalesce(sum(p.paidAmount), 0) from SesRequestPayment p where p.status = :status")
    long sumPaidAmountByStatus(@org.springframework.data.repository.query.Param("status") PaymentStatus status);
}
