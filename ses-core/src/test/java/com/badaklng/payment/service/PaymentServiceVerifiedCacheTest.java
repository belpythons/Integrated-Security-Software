package com.badaklng.payment.service;

import com.badaklng.approval.service.ApprovalService;
import com.badaklng.payment.entity.PaymentStatus;
import com.badaklng.payment.entity.SesRequestPayment;
import com.badaklng.payment.repository.SesRequestPaymentHistoryRepository;
import com.badaklng.payment.repository.SesRequestPaymentRepository;
import com.badaklng.payment.spi.PaymentBusinessDescriptor;
import com.badaklng.platform.system.repository.AppUserRepository;
import com.badaklng.ses.file.AppFileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the TTL cache added around {@link PaymentService#findVerified()} —
 * a money-path read that /dashboard calls twice per hit (summary + cash
 * chart) and that must not serve stale data past a payment being approved.
 */
@ExtendWith(MockitoExtension.class)
class PaymentServiceVerifiedCacheTest {

    @Mock
    private SesRequestPaymentRepository paymentRepository;
    @Mock
    private SesRequestPaymentHistoryRepository historyRepository;
    @Mock
    private AppFileService appFileService;
    @Mock
    private ApprovalService approvalService;
    @Mock
    private AppUserRepository appUserRepository;

    @SuppressWarnings("unchecked")
    private final ObjectProvider<PaymentBusinessDescriptor> descriptors = mock(ObjectProvider.class);

    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService(paymentRepository, historyRepository, appFileService,
                approvalService, appUserRepository, descriptors);
        ReflectionTestUtils.setField(paymentService, "verifiedCacheTtlMs", 60_000L);
    }

    @Test
    @DisplayName("findVerified() only hits the repository once across repeated calls within the TTL")
    void testFindVerifiedIsCachedWithinTtl() {
        when(paymentRepository.findByStatusOrderByReviewedDateDesc(PaymentStatus.VERIFIED))
                .thenReturn(Collections.emptyList());

        paymentService.findVerified();
        paymentService.findVerified();

        verify(paymentRepository, times(1)).findByStatusOrderByReviewedDateDesc(PaymentStatus.VERIFIED);
    }

    @Test
    @DisplayName("approve() invalidates the cache so the next findVerified() re-queries")
    void testApproveInvalidatesVerifiedCache() {
        when(paymentRepository.findByStatusOrderByReviewedDateDesc(PaymentStatus.VERIFIED))
                .thenReturn(Collections.emptyList());

        SesRequestPayment payment = new SesRequestPayment();
        payment.setPaymentId(1L);
        payment.setStatus(PaymentStatus.VERIFYING);
        payment.setBusinessType("STICKER");
        payment.setBusinessRefId(5L);
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(payment)).thenReturn(payment);
        when(appUserRepository.findByUsername("officer1")).thenReturn(Optional.empty());

        paymentService.findVerified();
        paymentService.approve(1L, "ok", 10_000L, "officer1");
        paymentService.findVerified();

        verify(paymentRepository, times(2)).findByStatusOrderByReviewedDateDesc(PaymentStatus.VERIFIED);
        assertEquals(PaymentStatus.VERIFIED, payment.getStatus());
    }
}
