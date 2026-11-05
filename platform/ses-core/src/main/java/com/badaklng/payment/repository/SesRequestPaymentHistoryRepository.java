package com.badaklng.payment.repository;

import com.badaklng.payment.entity.SesRequestPaymentHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SesRequestPaymentHistoryRepository extends JpaRepository<SesRequestPaymentHistory, Long> {

    List<SesRequestPaymentHistory> findByPaymentIdOrderByHistoryIdAsc(Long paymentId);
}
