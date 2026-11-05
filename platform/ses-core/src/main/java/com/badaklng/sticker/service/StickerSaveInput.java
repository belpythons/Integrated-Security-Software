package com.badaklng.sticker.service;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Service-layer input for {@link StickerService#saveAndSubmit}/{@link
 * StickerService#saveDraft}. The controller maps its web-binding {@code
 * StickerEditForm} into this shape — mirrors {@code
 * com.badaklng.request.service.RequestSaveInput}'s role exactly.
 */
@Getter
@Setter
@NoArgsConstructor
public class StickerSaveInput {

    /** Null on create; set on edit (must be an existing PENDING/DRAFT request). */
    private Long stickerRequestId;

    /** Single uniform sticker type for all vehicles in this batch submission. */
    private String stickerTypeCode;

    private String requestorName;
    private String badge;
    private String department;
    private String section;
    private String instance;
    private String phoneNo;
    private String workPhoneNo;
    private String address;
    private String contractNo;
    private Date contractExpiryDate;

    /** Proof of Payment — new upload, or null if not (re)uploaded this submission. */
    private MultipartFile paymentFile;

    private List<VehicleInput> vehicles = new ArrayList<>();

    @Getter
    @Setter
    @NoArgsConstructor
    public static class VehicleInput {
        private Long stickerVehicleId;
        private String stickerTypeCode;
        private String policeNo;
        private String vehicleType;
        private String ownerName;
        private String brand;
        private String color;
        private String yearMade;

        // Driver details
        private String driverBadge;
        private String driverName;
        private String driverNik;
        private String simNo;
        private String simClass;

        // Documents & Expiry
        private Date stnkExpiryDate;
        private MultipartFile stnkFile;
        private String existingStnkFileId;

        private Date simExpiryDate;
        private MultipartFile simFile;
        private String existingSimFileId;

        private String kirNo;
        private Date kirExpiryDate;
        private MultipartFile kirFile;
        private String existingKirFileId;

        // Legacy / fallback letter file
        private MultipartFile letterFile;
        private String existingLetterFileId;
    }
}
