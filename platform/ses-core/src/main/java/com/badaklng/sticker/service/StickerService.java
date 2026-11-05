package com.badaklng.sticker.service;

import com.badaklng.approval.service.ApprovalService;
import com.badaklng.payment.service.PaymentService;
import com.badaklng.platform.system.service.AppParameterService;
import com.badaklng.ses.file.AppFileService;
import com.badaklng.ses.jdbc.InChunks;
import com.badaklng.sticker.entity.SesStickerRequest;
import com.badaklng.sticker.entity.SesStickerType;
import com.badaklng.sticker.entity.SesStickerVehDoc;
import com.badaklng.sticker.entity.SesStickerVehicle;
import com.badaklng.sticker.model.StickerEditForm;
import com.badaklng.sticker.repository.SesStickerRequestRepository;
import com.badaklng.sticker.repository.SesStickerTypeRepository;
import com.badaklng.sticker.repository.SesStickerVehDocRepository;
import com.badaklng.sticker.repository.SesStickerVehicleRepository;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Core service for the Stiker Kendaraan (Vehicle Sticker Request) module.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StickerService {

    public static final String APPROVAL_FORM_CODE = "SES_STICKER_REQUEST";
    private static final String PAYMENT_BUSINESS_TYPE = APPROVAL_FORM_CODE;
    private static final String BASE_AMOUNT_PARAM = "SES_STICKER_BASE_AMOUNT";
    private static final long RESUBMIT_COOLDOWN_MILLIS = 10_000L;
    private static final int MAX_VEHICLES = 25;

    private final SesStickerRequestRepository requestRepository;
    private final SesStickerVehicleRepository vehicleRepository;
    private final SesStickerVehDocRepository vehDocRepository;
    private final SesStickerTypeRepository typeRepository;
    private final AppFileService appFileService;
    private final AppParameterService appParameterService;
    private final ApprovalService approvalService;
    private final PaymentService paymentService;

    @PersistenceContext
    private EntityManager entityManager;

    // ---------------------------------------------------------------
    // Finders
    // ---------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<SesStickerRequest> findMine(String username) {
        return requestRepository.findByRequestorUserIdOrderByStickerRequestIdDesc(username);
    }

    /** One page of {@link #findMine}, optionally filtered by a free-text term. */
    @Transactional(readOnly = true)
    public Page<SesStickerRequest> findMine(String username, String term, Pageable pageable) {
        return findMine(username, term, null, pageable);
    }

    /** One page of {@link #findMine}, optionally filtered by term and status. */
    @Transactional(readOnly = true)
    public Page<SesStickerRequest> findMine(String username, String term, String status, Pageable pageable) {
        return requestRepository.searchMine(username, term, status, null, pageable);
    }

    /** One page of {@link #findMine}, optionally filtered by term, status and stickerTypeCode. */
    @Transactional(readOnly = true)
    public Page<SesStickerRequest> findMine(String username, String term, String status, String stickerTypeCode, Pageable pageable) {
        return requestRepository.searchMine(username, term, status, stickerTypeCode, pageable);
    }

    @Transactional(readOnly = true)
    public List<SesStickerRequest> findAll() {
        return requestRepository.findAllByOrderByStickerRequestIdDesc();
    }

    @Transactional(readOnly = true)
    public SesStickerRequest findById(Long id) {
        return requestRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Sticker request not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<SesStickerVehicle> findVehicles(Long stickerRequestId) {
        return vehicleRepository.findByStickerRequestIdOrderByLineNoAsc(stickerRequestId);
    }

    @Transactional(readOnly = true)
    public List<SesStickerVehDoc> findVehicleDocs(Long stickerVehicleId) {
        return vehDocRepository.findByStickerVehicleId(stickerVehicleId);
    }

    /** Configured base amount (IDR) plus (price of headerStickerType * vehicleCount). */
    @Transactional(readOnly = true)
    public Long amountDue(Long stickerRequestId) {
        long base = parseBaseAmount();
        SesStickerRequest request = findById(stickerRequestId);
        List<SesStickerVehicle> vehicles = findVehicles(stickerRequestId);
        if (vehicles.isEmpty()) {
            return base;
        }

        // If request has header stickerTypeCode, calculate price from it:
        if (request.getStickerTypeCode() != null && !request.getStickerTypeCode().trim().isEmpty()) {
            Long unitPrice = typeRepository.findById(request.getStickerTypeCode())
                    .map(SesStickerType::getPrice).orElse(0L);
            return base + (unitPrice * vehicles.size());
        }

        // Fallback backward-compatibility for legacy requests
        Map<String, Long> priceByType = new HashMap<>();
        for (SesStickerType t : typeRepository.findAll()) {
            priceByType.put(t.getStickerTypeCode(), t.getPrice() != null ? t.getPrice() : 0L);
        }
        long total = base;
        for (SesStickerVehicle v : vehicles) {
            total += priceByType.getOrDefault(v.getStickerTypeCode(), 0L);
        }
        return total;
    }

    /**
     * {@link #amountDue(Long)} for many sticker requests in two queries plus
     * the one-off type-price lookup, instead of per request:
     * {@code findById} + {@code findVehicles} + maybe {@code findAll()} on
     * the type table. Used by {@code StickerPaymentDescriptor.rowData} so
     * rendering a payment list page does not scale its query count with its
     * row count.
     *
     * <p>Arithmetic is kept identical to the single-id method on purpose,
     * including the empty-vehicle shortcut and the legacy per-vehicle
     * fallback for requests with no header sticker type. Ids that do not
     * exist are absent from the result.
     */
    /**
     * Vehicles for many sticker requests, grouped by request id. The list
     * screen shows a plate summary per row, so calling
     * {@code findVehicles} per row was a query per row.
     */
    @Transactional(readOnly = true)
    public Map<Long, List<SesStickerVehicle>> findVehiclesByRequestIds(Collection<Long> stickerRequestIds) {
        Map<Long, List<SesStickerVehicle>> byRequest = new LinkedHashMap<>();
        if (stickerRequestIds == null || stickerRequestIds.isEmpty()) {
            return byRequest;
        }
        for (Long id : stickerRequestIds) {
            if (id != null) {
                byRequest.put(id, new ArrayList<>());
            }
        }
        for (SesStickerVehicle v : InChunks.flatMap(byRequest.keySet(), vehicleRepository::findByStickerRequestIdIn)) {
            byRequest.computeIfAbsent(v.getStickerRequestId(), k -> new ArrayList<>()).add(v);
        }
        return byRequest;
    }

    @Transactional(readOnly = true)
    public Map<Long, Long> amountDue(Collection<Long> stickerRequestIds) {
        Map<Long, Long> out = new LinkedHashMap<>();
        if (stickerRequestIds == null || stickerRequestIds.isEmpty()) {
            return out;
        }
        long base = parseBaseAmount();

        List<SesStickerRequest> requests = InChunks.flatMap(stickerRequestIds, requestRepository::findAllById);

        Map<Long, List<SesStickerVehicle>> vehiclesByRequest = new HashMap<>();
        List<Long> ids = requests.stream()
                .map(SesStickerRequest::getStickerRequestId)
                .collect(Collectors.toList());
        for (SesStickerVehicle v : InChunks.flatMap(ids, vehicleRepository::findByStickerRequestIdIn)) {
            vehiclesByRequest.computeIfAbsent(v.getStickerRequestId(), k -> new ArrayList<>()).add(v);
        }

        Map<String, Long> priceByType = new HashMap<>();
        for (SesStickerType t : typeRepository.findAll()) {
            priceByType.put(t.getStickerTypeCode(), t.getPrice() != null ? t.getPrice() : 0L);
        }

        for (SesStickerRequest request : requests) {
            Long id = request.getStickerRequestId();
            List<SesStickerVehicle> vehicles = vehiclesByRequest.getOrDefault(id, Collections.emptyList());
            if (vehicles.isEmpty()) {
                out.put(id, base);
                continue;
            }
            String headerType = request.getStickerTypeCode();
            if (headerType != null && !headerType.trim().isEmpty()) {
                out.put(id, base + (priceByType.getOrDefault(headerType, 0L) * vehicles.size()));
                continue;
            }
            long total = base;
            for (SesStickerVehicle v : vehicles) {
                total += priceByType.getOrDefault(v.getStickerTypeCode(), 0L);
            }
            out.put(id, total);
        }
        return out;
    }

    @Transactional(readOnly = true)
    @org.springframework.cache.annotation.Cacheable("stickerBaseAmount")
    public long baseAmount() {
        return parseBaseAmount();
    }

    private long parseBaseAmount() {
        try {
            return Long.parseLong(appParameterService.getValue(BASE_AMOUNT_PARAM, "0").trim());
        } catch (NumberFormatException e) {
            log.warn("{} is not a valid number — treating base sticker amount as 0", BASE_AMOUNT_PARAM);
            return 0L;
        }
    }

    @Transactional(readOnly = true)
    public Map<String, Object> detailModel(Long stickerRequestId) {
        Optional<SesStickerRequest> found = requestRepository.findById(stickerRequestId);
        if (!found.isPresent()) {
            return Collections.emptyMap();
        }
        SesStickerRequest request = found.get();
        List<SesStickerVehicle> vehicles = findVehicles(stickerRequestId);
        String currentProofFileId = paymentService.currentProofFileId(PAYMENT_BUSINESS_TYPE, stickerRequestId).orElse(null);

        List<Long> vehicleIds = vehicles.stream()
                .map(SesStickerVehicle::getStickerVehicleId)
                .collect(Collectors.toList());
        List<SesStickerVehDoc> allDocs = vehicleIds.isEmpty() ? Collections.emptyList()
                : vehDocRepository.findByStickerVehicleIdIn(vehicleIds);
        Map<Long, List<SesStickerVehDoc>> docsMap = allDocs.stream()
                .collect(Collectors.groupingBy(SesStickerVehDoc::getStickerVehicleId));

        Map<String, String> typeNames = new HashMap<>();
        for (SesStickerType t : typeRepository.findAll()) {
            typeNames.put(t.getStickerTypeCode(), t.getStickerTypeName());
        }

        Map<String, Object> model = new HashMap<>();
        model.put("stickerForm", StickerEditForm.from(request, vehicles, docsMap, currentProofFileId));
        model.put("stickerTypeNames", typeNames);
        model.put("stickerAmount", amountDue(stickerRequestId));
        paymentService.findByBusiness(PAYMENT_BUSINESS_TYPE, stickerRequestId).ifPresent(payment -> {
            model.put("paymentStatus", payment.getStatus() != null ? payment.getStatus().name() : null);
            model.put("paymentPaidAmount", payment.getPaidAmount());
            model.put("paymentRejectionReason", payment.getRejectionReason());
            model.put("paymentReviewedBy", payment.getReviewedBy());
            model.put("paymentReviewedDate", payment.getReviewedDate());
        });
        return model;
    }

    // ---------------------------------------------------------------
    // Driver & Vehicle History Suggestion
    // ---------------------------------------------------------------

    @Getter
    @Setter
    @NoArgsConstructor
    public static class DriverHistoryDto {
        private String badge;
        private String nik;
        private String name;
        private String simNo;
        private String simClass;
        private List<PastVehicleDto> pastVehicles = new ArrayList<>();
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class PastVehicleDto {
        private String policeNo;
        private String vehicleType;
        private String brand;
        private String color;
        private String yearMade;
        private String ownerName;
    }

    @Transactional(readOnly = true)
    public DriverHistoryDto findRecentDriverHistory(String badgeOrNik) {
        if (badgeOrNik == null || badgeOrNik.trim().isEmpty()) {
            return null;
        }
        String term = badgeOrNik.trim();
        List<SesStickerVehicle> vehicles = vehicleRepository.findTop5ByDriverBadgeOrderByStickerVehicleIdDesc(term);
        if (vehicles.isEmpty()) {
            vehicles = vehicleRepository.findTop5ByDriverNikOrderByStickerVehicleIdDesc(term);
        }
        if (vehicles.isEmpty()) {
            return null;
        }

        SesStickerVehicle latest = vehicles.get(0);
        DriverHistoryDto dto = new DriverHistoryDto();
        dto.setBadge(latest.getDriverBadge());
        dto.setNik(latest.getDriverNik());
        dto.setName(latest.getDriverName());
        dto.setSimNo(latest.getSimNo());
        dto.setSimClass(latest.getSimClass());

        Set<String> seenPlates = new HashSet<>();
        for (SesStickerVehicle v : vehicles) {
            if (v.getPoliceNo() != null && seenPlates.add(v.getPoliceNo().toUpperCase())) {
                PastVehicleDto p = new PastVehicleDto();
                p.setPoliceNo(v.getPoliceNo());
                p.setVehicleType(v.getVehicleType());
                p.setBrand(v.getBrand());
                p.setColor(v.getColor());
                p.setYearMade(v.getYearMade());
                p.setOwnerName(v.getOwnerName());
                dto.getPastVehicles().add(p);
            }
        }
        return dto;
    }

    // ---------------------------------------------------------------
    // Save + submit
    // ---------------------------------------------------------------

    @Transactional
    public Long saveAndSubmit(StickerSaveInput input, String username) {
        validate(input);

        SesStickerRequest request;
        boolean isCreate = input.getStickerRequestId() == null;
        if (isCreate) {
            request = new SesStickerRequest();
            request.setStatus("PENDING");
            request.setRequestorUserId(username);
            request.setRequestNo(nextRequestNo());
        } else {
            request = findById(input.getStickerRequestId());
            if (!"PENDING".equals(request.getStatus()) && !"DRAFT".equals(request.getStatus())) {
                throw new IllegalStateException("Hanya pengajuan berstatus pending atau draft yang dapat diubah.");
            }
            if (request.getApprovalInstanceId() != null
                    && !approvalService.isEditable(request.getApprovalInstanceId())) {
                throw new IllegalStateException("Pengajuan ini sudah memiliki keputusan persetujuan dan tidak dapat diubah lagi.");
            }
        }
        applyHeader(request, input);
        request = requestRepository.save(request);
        final Long stickerRequestId = request.getStickerRequestId();

        replaceVehicles(stickerRequestId, input.getStickerTypeCode(), input.getVehicles());

        // Proof of Payment
        paymentService.ensurePayment(PAYMENT_BUSINESS_TYPE, stickerRequestId, username);
        if (isNonEmpty(input.getPaymentFile())) {
            paymentService.submitProofFromRequestForm(PAYMENT_BUSINESS_TYPE, stickerRequestId, input.getPaymentFile(), username);
        }

        // Supersede running approval instance
        if (request.getApprovalInstanceId() != null) {
            Date lastTriggered = approvalService.getInstanceCreatedDate(request.getApprovalInstanceId());
            if (lastTriggered != null
                    && System.currentTimeMillis() - lastTriggered.getTime() < RESUBMIT_COOLDOWN_MILLIS) {
                throw new IllegalStateException("Pengajuan ini baru saja dikirim — mohon tunggu beberapa detik sebelum mengirim ulang.");
            }
            approvalService.cancel(request.getApprovalInstanceId(), username, "Superseded by request edit");
        }

        // Trigger default approval chain
        Long newInstanceId = approvalService
                .trigger(APPROVAL_FORM_CODE, PAYMENT_BUSINESS_TYPE, stickerRequestId, username)
                .getInstanceId();
        request.setApprovalInstanceId(newInstanceId);
        request.setStatus("PENDING");
        requestRepository.save(request);

        paymentService.carryForwardVerifiedCheckpoint(PAYMENT_BUSINESS_TYPE, stickerRequestId, username);

        return stickerRequestId;
    }

    @Transactional
    public Long saveDraft(StickerSaveInput input, String username) {
        if (input.getRequestorName() == null || input.getRequestorName().trim().isEmpty()) {
            throw new IllegalArgumentException("Nama Pemohon wajib diisi untuk menyimpan draft.");
        }

        SesStickerRequest request;
        boolean isCreate = input.getStickerRequestId() == null;
        if (isCreate) {
            request = new SesStickerRequest();
            request.setStatus("DRAFT");
            request.setRequestorUserId(username);
            request.setRequestNo(nextRequestNo());
        } else {
            request = findById(input.getStickerRequestId());
            if (!"DRAFT".equals(request.getStatus())) {
                throw new IllegalStateException("Hanya draft yang dapat disimpan sebagai draft.");
            }
        }
        applyHeader(request, input);
        request = requestRepository.save(request);
        final Long stickerRequestId = request.getStickerRequestId();

        replaceVehicles(stickerRequestId, input.getStickerTypeCode(), input.getVehicles());

        return stickerRequestId;
    }

    @Transactional
    public void cancel(Long stickerRequestId, String username) {
        SesStickerRequest request = findById(stickerRequestId);
        if (!"PENDING".equals(request.getStatus()) && !"DRAFT".equals(request.getStatus())) {
            throw new IllegalStateException("Hanya pengajuan berstatus pending atau draft yang dapat dibatalkan.");
        }
        if (request.getApprovalInstanceId() != null) {
            approvalService.cancel(request.getApprovalInstanceId(), username, "Cancelled by requestor");
        }
        request.setStatus("CANCELLED");
        requestRepository.save(request);
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private void applyHeader(SesStickerRequest request, StickerSaveInput input) {
        request.setStickerTypeCode(input.getStickerTypeCode());
        request.setRequestorName(input.getRequestorName());
        request.setBadge(input.getBadge());
        request.setDepartment(input.getDepartment());
        request.setSection(input.getSection());
        request.setInstance(input.getInstance());
        request.setPhoneNo(input.getPhoneNo());
        request.setWorkPhoneNo(input.getWorkPhoneNo());
        request.setAddress(input.getAddress());
        request.setContractNo(input.getContractNo());
        request.setContractExpiryDate(input.getContractExpiryDate());
    }

    private String nextRequestNo() {
        long seq = ((Number) entityManager.createNativeQuery("SELECT SEQ_SES_STICKER_REQ_NO.NEXTVAL FROM DUAL")
                .getSingleResult()).longValue();
        int year = java.time.Year.now().getValue();
        return "STK-" + year + "-" + String.format("%04d", seq);
    }

    private void replaceVehicles(Long stickerRequestId, String headerStickerTypeCode, List<StickerSaveInput.VehicleInput> vehicleInputs) {
        List<SesStickerVehicle> existing = vehicleRepository.findByStickerRequestIdOrderByLineNoAsc(stickerRequestId);
        List<Long> oldVehIds = existing.stream().map(SesStickerVehicle::getStickerVehicleId).collect(Collectors.toList());
        if (!oldVehIds.isEmpty()) {
            vehDocRepository.deleteByStickerVehicleIdIn(oldVehIds);
            vehDocRepository.flush();
        }
        vehicleRepository.deleteByStickerRequestId(stickerRequestId);
        vehicleRepository.flush();

        int lineNo = 1;
        for (StickerSaveInput.VehicleInput vi : vehicleInputs) {
            if (vi.getPoliceNo() == null || vi.getPoliceNo().trim().isEmpty()) {
                continue;
            }
            SesStickerVehicle vehicle = new SesStickerVehicle();
            vehicle.setStickerRequestId(stickerRequestId);
            vehicle.setLineNo(lineNo++);
            applyVehicleFields(vehicle, vi, headerStickerTypeCode);
            vehicle = vehicleRepository.save(vehicle);
            Long vId = vehicle.getStickerVehicleId();

            // Persist STNK document
            saveVehDoc(vId, SesStickerVehDoc.DOC_STNK, vi.getStnkFile(),
                    vi.getExistingStnkFileId() != null ? vi.getExistingStnkFileId() : vi.getExistingLetterFileId(),
                    vi.getStnkExpiryDate(), vi.getPoliceNo());

            // Persist SIM document
            saveVehDoc(vId, SesStickerVehDoc.DOC_SIM, vi.getSimFile(), vi.getExistingSimFileId(),
                    vi.getSimExpiryDate(), vi.getPoliceNo());

            // Persist KIR document
            if (isNonEmpty(vi.getKirFile()) || (vi.getExistingKirFileId() != null && !vi.getExistingKirFileId().isEmpty()) || vi.getKirExpiryDate() != null) {
                saveVehDoc(vId, SesStickerVehDoc.DOC_KIR, vi.getKirFile(), vi.getExistingKirFileId(),
                        vi.getKirExpiryDate(), vi.getPoliceNo());
            }

            // Set letter_file_id (legacy pointer) to STNK file id
            vehicle.setLetterFileId(resolveLetterFileId(vi, vId));
            vehicleRepository.save(vehicle);
        }
    }

    private void saveVehDoc(Long vehicleId, String docCode, MultipartFile file, String existingFileId, Date expiryDate, String policeNo) {
        String fileId = null;
        if (isNonEmpty(file)) {
            fileId = appFileService.store(file, "SES_STICKER_VEHICLE_DOC:" + vehicleId,
                    docCode + " - " + (policeNo == null ? "" : policeNo));
        } else if (existingFileId != null && !existingFileId.trim().isEmpty()) {
            appFileService.reassign(existingFileId.trim(), "SES_STICKER_VEHICLE_DOC:" + vehicleId);
            fileId = existingFileId.trim();
        }
        if (fileId != null || expiryDate != null) {
            SesStickerVehDoc doc = new SesStickerVehDoc();
            doc.setStickerVehicleId(vehicleId);
            doc.setDocCode(docCode);
            doc.setFileId(fileId);
            doc.setExpiryDate(expiryDate);
            vehDocRepository.save(doc);
        }
    }

    private void applyVehicleFields(SesStickerVehicle vehicle, StickerSaveInput.VehicleInput vi, String headerStickerTypeCode) {
        String typeCode = headerStickerTypeCode != null && !headerStickerTypeCode.trim().isEmpty()
                ? headerStickerTypeCode : vi.getStickerTypeCode();
        vehicle.setStickerTypeCode(typeCode);
        vehicle.setPoliceNo(vi.getPoliceNo() != null ? vi.getPoliceNo().trim().toUpperCase() : null);
        vehicle.setVehicleType(vi.getVehicleType());
        vehicle.setOwnerName(vi.getOwnerName());
        vehicle.setBrand(vi.getBrand());
        vehicle.setColor(vi.getColor());
        vehicle.setYearMade(vi.getYearMade());

        // Driver fields
        vehicle.setDriverBadge(vi.getDriverBadge());
        vehicle.setDriverName(vi.getDriverName());
        vehicle.setDriverNik(vi.getDriverNik());
        vehicle.setSimNo(vi.getSimNo());
        vehicle.setSimClass(vi.getSimClass());
    }

    private String resolveLetterFileId(StickerSaveInput.VehicleInput vi, Long vehicleId) {
        if (isNonEmpty(vi.getStnkFile())) {
            return appFileService.store(vi.getStnkFile(), "SES_STICKER_VEHICLE_DOC:" + vehicleId,
                    "STNK - " + (vi.getPoliceNo() == null ? "" : vi.getPoliceNo()));
        }
        if (isNonEmpty(vi.getLetterFile())) {
            return appFileService.store(vi.getLetterFile(), "SES_STICKER_VEHICLE_DOC:" + vehicleId,
                    "Lampiran Surat - " + (vi.getPoliceNo() == null ? "" : vi.getPoliceNo()));
        }
        if (vi.getExistingStnkFileId() != null && !vi.getExistingStnkFileId().isEmpty()) {
            appFileService.reassign(vi.getExistingStnkFileId(), "SES_STICKER_VEHICLE_DOC:" + vehicleId);
            return vi.getExistingStnkFileId();
        }
        if (vi.getExistingLetterFileId() != null && !vi.getExistingLetterFileId().isEmpty()) {
            appFileService.reassign(vi.getExistingLetterFileId(), "SES_STICKER_VEHICLE_DOC:" + vehicleId);
            return vi.getExistingLetterFileId();
        }
        return null;
    }

    private void validate(StickerSaveInput input) {
        if (input.getRequestorName() == null || input.getRequestorName().trim().isEmpty()) {
            throw new IllegalArgumentException("Nama Pemohon wajib diisi.");
        }
        if (input.getStickerTypeCode() == null || input.getStickerTypeCode().trim().isEmpty()) {
            throw new IllegalArgumentException("Jenis Stiker wajib dipilih.");
        }
        List<StickerSaveInput.VehicleInput> vehicles = input.getVehicles();
        if (vehicles == null || vehicles.isEmpty() || vehicles.size() > MAX_VEHICLES) {
            throw new IllegalArgumentException("Data Kendaraan harus berisi 1 sampai " + MAX_VEHICLES + " kendaraan.");
        }

        Set<String> activeTypes = new HashSet<>();
        for (SesStickerType t : typeRepository.findByActiveFlag("Y", Sort.unsorted())) {
            activeTypes.add(t.getStickerTypeCode());
        }
        if (!activeTypes.contains(input.getStickerTypeCode())) {
            throw new IllegalArgumentException("Jenis Stiker tidak valid atau tidak aktif: " + input.getStickerTypeCode());
        }

        Set<String> platesSeen = new HashSet<>();
        for (StickerSaveInput.VehicleInput v : vehicles) {
            if (v.getPoliceNo() == null || v.getPoliceNo().trim().isEmpty()) {
                throw new IllegalArgumentException("Nomor Polisi wajib diisi untuk setiap kendaraan.");
            }
            if (!platesSeen.add(v.getPoliceNo().trim().toUpperCase())) {
                throw new IllegalArgumentException("Nomor Polisi ganda dalam pengajuan ini: " + v.getPoliceNo());
            }
            if (v.getDriverName() == null || v.getDriverName().trim().isEmpty()) {
                throw new IllegalArgumentException("Nama Pengemudi wajib diisi untuk kendaraan " + v.getPoliceNo());
            }
        }

        // Proof of Payment is mandatory on submission
        if (!isNonEmpty(input.getPaymentFile())) {
            boolean hasExistingProof = input.getStickerRequestId() != null
                    && paymentService.currentProofFileId(PAYMENT_BUSINESS_TYPE, input.getStickerRequestId()).isPresent();
            if (!hasExistingProof) {
                throw new IllegalArgumentException("Bukti Pembayaran wajib diunggah sebelum pengajuan dikirim.");
            }
        }
    }

    private boolean isNonEmpty(MultipartFile file) {
        return file != null && !file.isEmpty();
    }
}
