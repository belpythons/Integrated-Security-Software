package com.badaklng.sticker.service;

import com.badaklng.approval.service.ApprovalService;
import com.badaklng.payment.service.PaymentService;
import com.badaklng.platform.system.service.AppParameterService;
import com.badaklng.ses.file.AppFileService;
import com.badaklng.sticker.entity.SesStickerRequest;
import com.badaklng.sticker.entity.SesStickerType;
import com.badaklng.sticker.entity.SesStickerVehicle;
import com.badaklng.sticker.repository.SesStickerRequestRepository;
import com.badaklng.sticker.repository.SesStickerTypeRepository;
import com.badaklng.sticker.repository.SesStickerVehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StickerServiceTest {

    @Mock
    private SesStickerRequestRepository requestRepository;

    @Mock
    private SesStickerVehicleRepository vehicleRepository;

    @Mock
    private SesStickerTypeRepository typeRepository;

    @Mock
    private AppFileService appFileService;

    @Mock
    private AppParameterService appParameterService;

    @Mock
    private ApprovalService approvalService;

    @Mock
    private PaymentService paymentService;

    @InjectMocks
    private StickerService stickerService;

    private SesStickerType typeCar;
    private SesStickerType typeBike;

    @BeforeEach
    void setUp() {
        typeCar = new SesStickerType();
        typeCar.setStickerTypeCode("MOBIL");
        typeCar.setStickerTypeName("Mobil Dinas / Pribadi");
        typeCar.setPrice(50000L);
        typeCar.setActiveFlag("Y");

        typeBike = new SesStickerType();
        typeBike.setStickerTypeCode("MOTOR");
        typeBike.setStickerTypeName("Sepeda Motor");
        typeBike.setPrice(20000L);
        typeBike.setActiveFlag("Y");
    }

    @Test
    void testAmountDue_calculatedCorrectlyWithBaseAmountAndVehicles() {
        when(appParameterService.getValue("SES_STICKER_BASE_AMOUNT", "0")).thenReturn("10000");

        SesStickerRequest req = new SesStickerRequest();
        req.setStickerRequestId(100L);
        req.setStickerTypeCode("MOBIL");
        when(requestRepository.findById(100L)).thenReturn(Optional.of(req));

        SesStickerVehicle v1 = new SesStickerVehicle();
        v1.setStickerVehicleId(1L);
        v1.setStickerRequestId(100L);
        v1.setStickerTypeCode("MOBIL");

        SesStickerVehicle v2 = new SesStickerVehicle();
        v2.setStickerVehicleId(2L);
        v2.setStickerRequestId(100L);
        v2.setStickerTypeCode("MOBIL");

        when(vehicleRepository.findByStickerRequestIdOrderByLineNoAsc(100L)).thenReturn(Arrays.asList(v1, v2));
        when(typeRepository.findById("MOBIL")).thenReturn(Optional.of(typeCar));

        Long total = stickerService.amountDue(100L);

        // 10000 (base) + 2 * 50000 (MOBIL) = 110000
        assertEquals(110000L, total);
    }

    @Test
    void testAmountDue_noVehiclesReturnsBaseAmount() {
        when(appParameterService.getValue("SES_STICKER_BASE_AMOUNT", "0")).thenReturn("25000");
        SesStickerRequest req = new SesStickerRequest();
        req.setStickerRequestId(200L);
        when(requestRepository.findById(200L)).thenReturn(Optional.of(req));
        when(vehicleRepository.findByStickerRequestIdOrderByLineNoAsc(200L)).thenReturn(Collections.emptyList());

        Long total = stickerService.amountDue(200L);
        assertEquals(25000L, total);
    }

    @Test
    void testFindById_notFoundThrowsException() {
        when(requestRepository.findById(999L)).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> stickerService.findById(999L));
        assertEquals("Sticker request not found: 999", ex.getMessage());
    }

    @Test
    void testCancel_pendingRequestSuccessfullyCancelled() {
        SesStickerRequest req = new SesStickerRequest();
        req.setStickerRequestId(10L);
        req.setStatus("PENDING");
        req.setApprovalInstanceId(55L);

        when(requestRepository.findById(10L)).thenReturn(Optional.of(req));

        stickerService.cancel(10L, "testuser");

        assertEquals("CANCELLED", req.getStatus());
        verify(approvalService).cancel(55L, "testuser", "Cancelled by requestor");
        verify(requestRepository).save(req);
    }

    @Test
    void testCancel_nonPendingOrDraftThrowsException() {
        SesStickerRequest req = new SesStickerRequest();
        req.setStickerRequestId(10L);
        req.setStatus("APPROVED");

        when(requestRepository.findById(10L)).thenReturn(Optional.of(req));

        assertThrows(IllegalStateException.class, () -> stickerService.cancel(10L, "testuser"));
    }
}
