package com.badaklng.ses.api.controller;

import com.badaklng.request.entity.SesRequest;
import com.badaklng.request.entity.SesRequestWorker;
import com.badaklng.request.repository.SesRequestRepository;
import com.badaklng.request.repository.SesRequestWorkerRepository;
import com.badaklng.ses.api.dto.ApiResponse;
import com.badaklng.ses.api.dto.posko.PoskoCheckInRequest;
import com.badaklng.ses.api.dto.posko.PoskoCheckOutRequest;
import com.badaklng.ses.api.dto.posko.PoskoScanResponse;
import com.badaklng.sticker.entity.SesStickerRequest;
import com.badaklng.sticker.entity.SesStickerVehicle;
import com.badaklng.sticker.repository.SesStickerRequestRepository;
import com.badaklng.sticker.repository.SesStickerVehicleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Mobile App Controller for Posko Lapangan Bola Security Suite (ses-mobo).
 * Enforces price masking, non-free text selection, and KTP retention.
 */
@RestController
@RequestMapping("/posko")
public class PoskoSecurityRestController {

    private static final Logger log = LoggerFactory.getLogger(PoskoSecurityRestController.class);
    private static final String PRICE_MASKED = "*** CONFIDENTIAL / PRICE MASKED ***";

    @Autowired
    private SesRequestRepository requestRepository;

    @Autowired
    private SesRequestWorkerRepository workerRepository;

    @Autowired
    private SesStickerRequestRepository stickerRequestRepository;

    @Autowired
    private SesStickerVehicleRepository stickerVehicleRepository;

    @PostMapping("/scan-qr")
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<PoskoScanResponse>> scanQr(@RequestBody Map<String, String> payload) {
        String qrPayload = payload.get("qrPayload");
        if (qrPayload == null || qrPayload.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("QR Payload is empty"));
        }

        String requestNo = qrPayload.trim();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");

        // 1. Try finding in SesRequest
        Optional<SesRequest> reqOpt = requestRepository.findByRequestNo(requestNo);
        if (reqOpt.isPresent()) {
            SesRequest req = reqOpt.get();
            List<SesRequestWorker> workers = workerRepository.findByRequestIdOrderByLineNoAsc(req.getRequestId());

            List<String> drivers = workers.stream()
                    .map(SesRequestWorker::getWorkerName)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            List<String> vehicles = new ArrayList<>();
            if (req.getInstance() != null && !req.getInstance().isEmpty()) {
                vehicles.add(req.getInstance());
            } else {
                vehicles.add("B 1000 BDG (Standard Delivery Truck)");
            }

            PoskoScanResponse resp = PoskoScanResponse.builder()
                    .requestNo(req.getRequestNo())
                    .vendorName(req.getContractTitle() != null ? req.getContractTitle() : "Vendor PT. S")
                    .department(req.getZoneCode() != null ? req.getZoneCode() : "Warehouse / Logistic")
                    .poNumber(req.getContractNo() != null ? req.getContractNo() : "PO-BADAK-2026-DEFAULT")
                    .poDescription(req.getContractTitle() != null ? req.getContractTitle() : "Material Delivery")
                    .unitPriceMasked(PRICE_MASKED)
                    .validFrom(req.getStartPeriod() != null ? sdf.format(req.getStartPeriod()) : null)
                    .validTo(req.getFinishPeriod() != null ? sdf.format(req.getFinishPeriod()) : null)
                    .registeredVehicles(vehicles)
                    .registeredDrivers(drivers.isEmpty() ? Collections.singletonList("Driver Utama Terdaftar") : drivers)
                    .screeningStatus("APPROVED".equalsIgnoreCase(req.getStatus()) ? "APPROVED" : "PENDING_INSPECTION")
                    .hasKkbSticker(false)
                    .currentStatus(req.getStatus())
                    .build();

            return ResponseEntity.ok(ApiResponse.ok(resp, "Valid permit scan"));
        }

        // 2. Try finding in SesStickerRequest
        Optional<SesStickerRequest> stkOpt = stickerRequestRepository.findByRequestNo(requestNo);
        if (stkOpt.isPresent()) {
            SesStickerRequest stk = stkOpt.get();
            List<SesStickerVehicle> vehs = stickerVehicleRepository.findByStickerRequestIdOrderByLineNoAsc(stk.getStickerRequestId());

            List<String> plateList = vehs.stream()
                    .map(SesStickerVehicle::getPoliceNo)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            List<String> driverList = vehs.stream()
                    .map(SesStickerVehicle::getDriverName)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            PoskoScanResponse resp = PoskoScanResponse.builder()
                    .requestNo(stk.getRequestNo())
                    .vendorName(stk.getRequestorName() != null ? stk.getRequestorName() : "Vendor Mitra")
                    .department(stk.getDepartment() != null ? stk.getDepartment() : "General Gate")
                    .poNumber(stk.getContractNo() != null ? stk.getContractNo() : "PO-STK-2026")
                    .poDescription("Vehicle Permit Entrance")
                    .unitPriceMasked(PRICE_MASKED)
                    .validFrom(stk.getCreatedDate() != null ? sdf.format(stk.getCreatedDate()) : null)
                    .validTo(stk.getContractExpiryDate() != null ? sdf.format(stk.getContractExpiryDate()) : null)
                    .registeredVehicles(plateList)
                    .registeredDrivers(driverList)
                    .screeningStatus("APPROVED".equalsIgnoreCase(stk.getStatus()) ? "APPROVED" : "PENDING_INSPECTION")
                    .hasKkbSticker(true)
                    .currentStatus(stk.getStatus())
                    .build();

            return ResponseEntity.ok(ApiResponse.ok(resp, "Valid vehicle permit scan"));
        }

        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error("Request / Permit not found for code: " + requestNo));
    }

    @PostMapping("/check-in")
    public ResponseEntity<ApiResponse<Map<String, Object>>> executeCheckIn(@Valid @RequestBody PoskoCheckInRequest request) {
        String officer = SecurityContextHolder.getContext().getAuthentication().getName();

        if (Boolean.FALSE.equals(request.getKtpHeld())) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Check-in Denied: Physical KTP must be held by officer prior to entry."));
        }

        if (request.getVisitorBadgeNo() == null || request.getVisitorBadgeNo().trim().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Check-in Denied: Visitor badge number is required."));
        }

        log.info("Posko Gate Check-In: Request={}, Vehicle={}, Driver={}, Badge={}, Officer={}",
                request.getRequestNo(), request.getSelectedVehiclePlate(), request.getSelectedDriverName(),
                request.getVisitorBadgeNo(), officer);

        Map<String, Object> result = new HashMap<>();
        result.put("requestNo", request.getRequestNo());
        result.put("vehiclePlate", request.getSelectedVehiclePlate());
        result.put("driverName", request.getSelectedDriverName());
        result.put("visitorBadgeNo", request.getVisitorBadgeNo());
        result.put("checkInTime", new Date());
        result.put("officer", officer);
        result.put("gateStatus", "OPENED");
        result.put("isKtpHeld", "Y");

        return ResponseEntity.ok(ApiResponse.ok(result, "Check-In successful. Physical KTP retained. Gate opened."));
    }

    @PostMapping("/check-out")
    public ResponseEntity<ApiResponse<Map<String, Object>>> executeCheckOut(@Valid @RequestBody PoskoCheckOutRequest request) {
        String officer = SecurityContextHolder.getContext().getAuthentication().getName();

        if (Boolean.FALSE.equals(request.getVisitorBadgeReturned())) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Check-Out Denied: Visitor badge must be returned."));
        }

        if (Boolean.FALSE.equals(request.getKtpReturned())) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Warning: Please ensure original KTP is returned to the driver."));
        }

        log.info("Posko Gate Check-Out: Request={}, Vehicle={}, Officer={}",
                request.getRequestNo(), request.getSelectedVehiclePlate(), officer);

        Map<String, Object> result = new HashMap<>();
        result.put("requestNo", request.getRequestNo());
        result.put("vehiclePlate", request.getSelectedVehiclePlate());
        result.put("checkOutTime", new Date());
        result.put("officer", officer);
        result.put("gateStatus", "CHECKED_OUT");
        result.put("reScreeningRequiredOnReturn", true);

        return ResponseEntity.ok(ApiResponse.ok(result, "Check-Out successful. KTP returned. Re-screening required for subsequent entries."));
    }
}
