package com.badaklng.sticker.spi;

import com.badaklng.approval.entity.ApprovalStatus;
import com.badaklng.payment.service.PaymentService;
import com.badaklng.platform.system.service.AppParameterService;
import com.badaklng.ses.text.SesText;
import com.badaklng.sticker.entity.SesStickerRequest;
import com.badaklng.sticker.repository.SesStickerRequestRepository;
import com.badaklng.sticker.service.StickerService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StickerApprovalBridgeTest {

    @Mock
    private SesStickerRequestRepository requestRepository;

    @Mock
    private StickerService stickerService;

    @Mock
    private PaymentService paymentService;

    /**
     * A real {@code SesText} over an empty {@code AppParameterService}, not a
     * mock: with no parameter rows the service hands back the caller's
     * fallback, which is exactly the behaviour these assertions describe --
     * {@code formName()} must still answer "Stiker Kendaraan" when nothing has
     * been configured. {@code @Spy} rather than a plain field so
     * {@code @InjectMocks} picks it up. Touches no database: the parameter
     * cache starts empty and only reloads on a Spring context event.
     */
    @Spy
    private SesText sesText = new SesText(new AppParameterService(null));

    @InjectMocks
    private StickerApprovalBridge bridge;

    @Test
    void testFormMetadata() {
        assertEquals("SES_STICKER_REQUEST", bridge.formCode());
        assertEquals("Stiker Kendaraan", bridge.formName());
        assertEquals("SES_STICKER_REQUEST", bridge.businessRefType());
        assertEquals("/sticker/123/view", bridge.requestorViewPath(123L));
        assertEquals("sticker/fragments/sticker-detail :: detail", bridge.detailFragment());
    }

    @Test
    void testOnCompleted_approved() {
        SesStickerRequest req = new SesStickerRequest();
        req.setStickerRequestId(100L);
        req.setStatus("PENDING");

        when(requestRepository.findById(100L)).thenReturn(Optional.of(req));

        bridge.onCompleted(100L, ApprovalStatus.APPROVED);

        assertEquals("APPROVED", req.getStatus());
        verify(requestRepository).save(req);
    }

    @Test
    void testOnCompleted_rejected_triggersPaymentCleanup() {
        SesStickerRequest req = new SesStickerRequest();
        req.setStickerRequestId(100L);
        req.setStatus("PENDING");

        when(requestRepository.findById(100L)).thenReturn(Optional.of(req));

        bridge.onCompleted(100L, ApprovalStatus.REJECTED);

        assertEquals("REJECTED", req.getStatus());
        verify(requestRepository).save(req);
        verify(paymentService).onRequestRejected("SES_STICKER_REQUEST", 100L);
    }
}
