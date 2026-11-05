package com.badaklng.sticker.model;

import com.badaklng.platform.model.BaseForm;
import com.badaklng.sticker.entity.SesStickerRequest;
import com.badaklng.sticker.entity.SesStickerVehDoc;
import com.badaklng.sticker.entity.SesStickerVehicle;
import com.badaklng.sticker.service.StickerSaveInput;
import com.badaklng.ses.validation.DraftGroup;
import com.badaklng.ses.validation.SafeText;
import com.badaklng.ses.validation.SubmitGroup;
import com.badaklng.ses.validation.ValidFileExtension;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.multipart.MultipartFile;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Web binding target for the Stiker Kendaraan form, posted with Spring
 * indexed binding: {@code vehicles[i].*} — mirrors {@code
 * com.badaklng.request.model.RequestEditForm}'s shape exactly. Also reused,
 * read-only, as the payment/approval detail pages' display model.
 */
@Getter
@Setter
@NoArgsConstructor
public class StickerEditForm extends BaseForm {

    public static final String LIST_URL = "/sticker";
    public static final String FORM_URL = "/sticker/form";

    /** Null on create. */
    private Long stickerRequestId;

    /** Single uniform sticker type for all vehicles in this batch submission. */
    @NotBlank(groups = SubmitGroup.class, message = "Jenis stiker wajib dipilih")
    @Size(max = 30, message = "Kode jenis stiker maksimal 30 karakter")
    private String stickerTypeCode;

    @NotBlank(groups = SubmitGroup.class, message = "Nama pemohon wajib diisi")
    @Size(max = 100, message = "Nama pemohon maksimal 100 karakter")
    @SafeText
    private String requestorName;

    @NotBlank(groups = SubmitGroup.class, message = "Nomor badge wajib diisi")
    @Pattern(regexp = "^[A-Za-z0-9_-]+$", groups = {DraftGroup.class, SubmitGroup.class}, message = "Format badge hanya boleh huruf, angka, dan strip")
    @Size(max = 30, message = "Nomor badge maksimal 30 karakter")
    private String badge;

    @Size(max = 100, message = "Departemen maksimal 100 karakter")
    @SafeText
    private String department;

    @Size(max = 100, message = "Seksi maksimal 100 karakter")
    @SafeText
    private String section;

    @Size(max = 100, message = "Instansi maksimal 100 karakter")
    @SafeText
    private String instance;

    @Pattern(regexp = "^$|^[0-9+()\\-\\s]*$", message = "Format nomor telepon tidak valid")
    @Size(max = 30, message = "Nomor telepon maksimal 30 karakter")
    private String phoneNo;

    @Pattern(regexp = "^$|^[0-9+()\\-\\s]*$", message = "Format nomor telepon kantor tidak valid")
    @Size(max = 30, message = "Nomor telepon kantor maksimal 30 karakter")
    private String workPhoneNo;

    @Size(max = 255, message = "Alamat maksimal 255 karakter")
    @SafeText
    private String address;

    @Size(max = 100, message = "Nomor kontrak maksimal 100 karakter")
    @SafeText
    private String contractNo;

    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private Date contractExpiryDate;

    /** Proof of Payment — new upload. */
    @ValidFileExtension(message = "Bukti pembayaran harus berformat PDF, JPG, atau PNG")
    private MultipartFile paymentFile;

    /** Carries forward an already-uploaded Proof of Payment across an edit. */
    private String existingPaymentFileId;

    @Valid
    @NotEmpty(groups = SubmitGroup.class, message = "Minimal 1 kendaraan harus didaftarkan")
    private List<VehicleRow> vehicles = new ArrayList<>();

    public static StickerEditForm from(SesStickerRequest request, List<SesStickerVehicle> vehicles,
                                        String currentPaymentFileId) {
        return from(request, vehicles, Collections.emptyMap(), currentPaymentFileId);
    }

    public static StickerEditForm from(SesStickerRequest request, List<SesStickerVehicle> vehicles,
                                        Map<Long, List<SesStickerVehDoc>> docsMap,
                                        String currentPaymentFileId) {
        StickerEditForm f = new StickerEditForm();
        f.setStickerRequestId(request.getStickerRequestId());
        f.setStickerTypeCode(request.getStickerTypeCode());
        f.setRequestorName(request.getRequestorName());
        f.setBadge(request.getBadge());
        f.setDepartment(request.getDepartment());
        f.setSection(request.getSection());
        f.setInstance(request.getInstance());
        f.setPhoneNo(request.getPhoneNo());
        f.setWorkPhoneNo(request.getWorkPhoneNo());
        f.setAddress(request.getAddress());
        f.setContractNo(request.getContractNo());
        f.setContractExpiryDate(request.getContractExpiryDate());
        f.setExistingPaymentFileId(currentPaymentFileId);

        List<VehicleRow> rows = new ArrayList<>();
        if (vehicles != null) {
            for (SesStickerVehicle v : vehicles) {
                VehicleRow row = new VehicleRow();
                row.setStickerVehicleId(v.getStickerVehicleId());
                row.setStickerTypeCode(v.getStickerTypeCode() != null ? v.getStickerTypeCode() : request.getStickerTypeCode());
                row.setPoliceNo(v.getPoliceNo());
                row.setVehicleType(v.getVehicleType());
                row.setOwnerName(v.getOwnerName());
                row.setBrand(v.getBrand());
                row.setColor(v.getColor());
                row.setYearMade(v.getYearMade());
                row.setExistingLetterFileId(v.getLetterFileId());

                // Driver data
                row.setDriverBadge(v.getDriverBadge());
                row.setDriverName(v.getDriverName());
                row.setDriverNik(v.getDriverNik());
                row.setSimNo(v.getSimNo());
                row.setSimClass(v.getSimClass());

                // Documents from SES_STICKER_VEH_DOC
                if (docsMap != null && v.getStickerVehicleId() != null && docsMap.containsKey(v.getStickerVehicleId())) {
                    for (SesStickerVehDoc doc : docsMap.get(v.getStickerVehicleId())) {
                        if (SesStickerVehDoc.DOC_STNK.equalsIgnoreCase(doc.getDocCode())) {
                            row.setExistingStnkFileId(doc.getFileId());
                            row.setStnkExpiryDate(doc.getExpiryDate());
                        } else if (SesStickerVehDoc.DOC_SIM.equalsIgnoreCase(doc.getDocCode())) {
                            row.setExistingSimFileId(doc.getFileId());
                            row.setSimExpiryDate(doc.getExpiryDate());
                        } else if (SesStickerVehDoc.DOC_KIR.equalsIgnoreCase(doc.getDocCode())) {
                            row.setExistingKirFileId(doc.getFileId());
                            row.setKirExpiryDate(doc.getExpiryDate());
                        }
                    }
                }
                // Fallback: if existingStnkFileId not populated but letterFileId exists, treat letterFileId as STNK
                if (row.getExistingStnkFileId() == null && v.getLetterFileId() != null) {
                    row.setExistingStnkFileId(v.getLetterFileId());
                }

                rows.add(row);
            }
        }
        f.setVehicles(rows);
        return f;
    }

    public StickerSaveInput toServiceInput() {
        StickerSaveInput input = new StickerSaveInput();
        input.setStickerRequestId(stickerRequestId);
        input.setStickerTypeCode(stickerTypeCode);
        input.setRequestorName(requestorName);
        input.setBadge(badge);
        input.setDepartment(department);
        input.setSection(section);
        input.setInstance(instance);
        input.setPhoneNo(phoneNo);
        input.setWorkPhoneNo(workPhoneNo);
        input.setAddress(address);
        input.setContractNo(contractNo);
        input.setContractExpiryDate(contractExpiryDate);
        input.setPaymentFile(paymentFile);

        List<StickerSaveInput.VehicleInput> vehicleInputs = new ArrayList<>();
        if (vehicles != null) {
            for (VehicleRow row : vehicles) {
                if (row == null) {
                    continue;
                }
                StickerSaveInput.VehicleInput vi = new StickerSaveInput.VehicleInput();
                vi.setStickerVehicleId(row.getStickerVehicleId());
                vi.setStickerTypeCode(row.getStickerTypeCode() != null ? row.getStickerTypeCode() : stickerTypeCode);
                vi.setPoliceNo(row.getPoliceNo());
                vi.setVehicleType(row.getVehicleType());
                vi.setOwnerName(row.getOwnerName());
                vi.setBrand(row.getBrand());
                vi.setColor(row.getColor());
                vi.setYearMade(row.getYearMade());

                // Driver details
                vi.setDriverBadge(row.getDriverBadge());
                vi.setDriverName(row.getDriverName());
                vi.setDriverNik(row.getDriverNik());
                vi.setSimNo(row.getSimNo());
                vi.setSimClass(row.getSimClass());

                // Documents & Expiry
                vi.setStnkExpiryDate(row.getStnkExpiryDate());
                vi.setStnkFile(row.getStnkFile());
                vi.setExistingStnkFileId(row.getExistingStnkFileId());

                vi.setSimExpiryDate(row.getSimExpiryDate());
                vi.setSimFile(row.getSimFile());
                vi.setExistingSimFileId(row.getExistingSimFileId());

                vi.setKirNo(row.getKirNo());
                vi.setKirExpiryDate(row.getKirExpiryDate());
                vi.setKirFile(row.getKirFile());
                vi.setExistingKirFileId(row.getExistingKirFileId());

                vi.setLetterFile(row.getLetterFile());
                vi.setExistingLetterFileId(row.getExistingLetterFileId());
                vehicleInputs.add(vi);
            }
        }
        input.setVehicles(vehicleInputs);
        return input;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class VehicleRow {
        private Long stickerVehicleId;

        @Size(max = 30, message = "Kode jenis stiker maksimal 30 karakter")
        private String stickerTypeCode;

        @NotBlank(groups = SubmitGroup.class, message = "Nomor polisi wajib diisi")
        @Pattern(regexp = "^$|^[A-Za-z0-9\\s]+$", groups = {DraftGroup.class, SubmitGroup.class}, message = "Format nomor polisi hanya boleh huruf, angka, dan spasi")
        @Size(max = 30, message = "Nomor polisi maksimal 30 karakter")
        private String policeNo;

        @Size(max = 100, message = "Tipe kendaraan maksimal 100 karakter")
        @SafeText
        private String vehicleType;

        @Size(max = 200, message = "Nama pemilik STNK maksimal 200 karakter")
        @SafeText
        private String ownerName;

        @Size(max = 100, message = "Merk kendaraan maksimal 100 karakter")
        @SafeText
        private String brand;

        @Size(max = 50, message = "Warna kendaraan maksimal 50 karakter")
        @SafeText
        private String color;

        @Pattern(regexp = "^$|^(19|20)\\d{2}$", groups = {DraftGroup.class, SubmitGroup.class}, message = "Tahun pembuatan harus 4 digit angka (contoh: 2022)")
        private String yearMade;

        // Driver details
        @NotBlank(groups = SubmitGroup.class, message = "Nama pengemudi wajib diisi")
        @Size(max = 200, message = "Nama pengemudi maksimal 200 karakter")
        @SafeText
        private String driverName;

        @Size(max = 50, message = "Nomor badge pengemudi maksimal 50 karakter")
        private String driverBadge;

        @Size(max = 30, message = "NIK pengemudi maksimal 30 karakter")
        private String driverNik;

        @Size(max = 50, message = "Nomor SIM maksimal 50 karakter")
        private String simNo;

        @Size(max = 10, message = "Golongan SIM maksimal 10 karakter")
        private String simClass;

        // Documents & Expiry
        @DateTimeFormat(pattern = "yyyy-MM-dd")
        private Date stnkExpiryDate;

        @ValidFileExtension(message = "Berkas STNK harus berformat PDF, JPG, atau PNG")
        private MultipartFile stnkFile;

        private String existingStnkFileId;

        @DateTimeFormat(pattern = "yyyy-MM-dd")
        private Date simExpiryDate;

        @ValidFileExtension(message = "Berkas SIM harus berformat PDF, JPG, atau PNG")
        private MultipartFile simFile;

        private String existingSimFileId;

        @Size(max = 50, message = "Nomor KIR maksimal 50 karakter")
        private String kirNo;

        @DateTimeFormat(pattern = "yyyy-MM-dd")
        private Date kirExpiryDate;

        @ValidFileExtension(message = "Berkas Kartu KIR harus berformat PDF, JPG, atau PNG")
        private MultipartFile kirFile;

        private String existingKirFileId;

        // Legacy / fallback letter file
        @ValidFileExtension(message = "Berkas surat harus berformat PDF, JPG, atau PNG")
        private MultipartFile letterFile;

        private String existingLetterFileId;
    }
}
