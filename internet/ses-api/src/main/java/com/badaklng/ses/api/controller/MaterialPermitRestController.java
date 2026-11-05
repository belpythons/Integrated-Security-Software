package com.badaklng.ses.api.controller;

import com.badaklng.request.entity.RequestStatus;
import com.badaklng.request.entity.SesRequest;
import com.badaklng.request.repository.SesRequestRepository;
import com.badaklng.ses.api.dto.ApiResponse;
import com.badaklng.ses.api.dto.material.MaterialPermitDto;
import com.badaklng.ses.file.SesAppFile;
import com.badaklng.ses.file.SesAppFileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.validation.Valid;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Controller serving the Single Page Application (SPA) for Material Permit workflows.
 */
@RestController
@RequestMapping("/material-permit")
public class MaterialPermitRestController {

    private static final Logger log = LoggerFactory.getLogger(MaterialPermitRestController.class);

    @Autowired
    private SesRequestRepository requestRepository;

    @Autowired
    private SesAppFileRepository fileRepository;

    @GetMapping("/list")
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<Map<String, Object>>> getPermitList(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String term) {

        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "requestId"));

        Page<SesRequest> resultPage = requestRepository.searchMine(username, term, pageRequest);

        List<MaterialPermitDto> items = new ArrayList<>();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");

        for (SesRequest r : resultPage.getContent()) {
            items.add(MaterialPermitDto.builder()
                    .requestNo(r.getRequestNo())
                    .vendorName(r.getContractTitle())
                    .contractNo(r.getContractNo())
                    .destinationDepartment(r.getZoneCode())
                    .vehiclePlate(r.getInstance())
                    .status(r.getStatus())
                    .validFrom(r.getStartPeriod() != null ? sdf.format(r.getStartPeriod()) : null)
                    .validTo(r.getFinishPeriod() != null ? sdf.format(r.getFinishPeriod()) : null)
                    .build());
        }

        Map<String, Object> data = new HashMap<>();
        data.put("items", items);
        data.put("totalElements", resultPage.getTotalElements());
        data.put("totalPages", resultPage.getTotalPages());
        data.put("currentPage", resultPage.getNumber());

        return ResponseEntity.ok(ApiResponse.ok(data, "Permit list retrieved successfully"));
    }

    @PostMapping("/create")
    @Transactional
    public ResponseEntity<ApiResponse<MaterialPermitDto>> createPermit(@Valid @RequestBody MaterialPermitDto dto) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();

        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd");
        String datePart = dateFormat.format(new Date());
        String randomSuffix = String.format("%04d", new Random().nextInt(10000));
        String generatedRequestNo = "REQ-MAT-" + datePart + "-" + randomSuffix;

        SesRequest req = new SesRequest();
        req.setRequestNo(generatedRequestNo);
        req.setTypeCode("MATERIAL_PERMIT");
        req.setContractNo(dto.getContractNo());
        req.setContractTitle(dto.getVendorName() != null ? dto.getVendorName() : "Material Permit");
        req.setZoneCode(dto.getDestinationDepartment() != null ? dto.getDestinationDepartment() : "WAREHOUSE");
        req.setInstance(dto.getVehiclePlate());
        req.setRequestorUserId(username);
        req.setStatus(RequestStatus.PENDING);

        try {
            SimpleDateFormat parser = new SimpleDateFormat("yyyy-MM-dd");
            if (dto.getValidFrom() != null) req.setStartPeriod(parser.parse(dto.getValidFrom()));
            if (dto.getValidTo() != null) req.setFinishPeriod(parser.parse(dto.getValidTo()));
        } catch (Exception e) {
            log.warn("Could not parse dates for request: {}", e.getMessage());
        }

        SesRequest saved = requestRepository.save(req);
        dto.setRequestNo(saved.getRequestNo());
        dto.setStatus(saved.getStatus());

        return ResponseEntity.ok(ApiResponse.ok(dto, "Material permit submitted successfully"));
    }

    @PostMapping("/upload-async")
    @Transactional
    public ResponseEntity<ApiResponse<Map<String, Object>>> uploadAttachment(@RequestParam("file") MultipartFile file)
            throws IOException {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Uploaded file is empty"));
        }

        String username = SecurityContextHolder.getContext().getAuthentication().getName();

        SesAppFile appFile = new SesAppFile();
        appFile.setFileName(file.getOriginalFilename());
        appFile.setFileType(file.getContentType());
        appFile.setFileSize(file.getSize());
        appFile.setFileData(file.getBytes());
        appFile.setCreatedBy(username);
        appFile.setCreatedDate(new Date());

        SesAppFile savedFile = fileRepository.save(appFile);

        Map<String, Object> resp = new HashMap<>();
        resp.put("fileId", savedFile.getFileId());
        resp.put("fileName", savedFile.getFileName());
        resp.put("fileSize", savedFile.getFileSize());
        resp.put("mimeType", savedFile.getFileType());

        return ResponseEntity.ok(ApiResponse.ok(resp, "File uploaded successfully"));
    }
}

