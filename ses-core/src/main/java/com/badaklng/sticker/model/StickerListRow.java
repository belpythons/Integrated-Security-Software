package com.badaklng.sticker.model;

import com.badaklng.platform.model.BaseForm;
import com.badaklng.sticker.entity.SesStickerRequest;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Date;

/** List row DTO for "Sticker Kendaraan" (/sticker). Edit/Cancel only offered (server-side too) while status is PENDING/DRAFT. */
@Getter
@Setter
@NoArgsConstructor
public class StickerListRow extends BaseForm {

    public static final String LIST_URL = "/sticker";
    public static final String FORM_URL = "/sticker/form";

    private Long stickerRequestId;
    private String requestNo;
    private String requestorName;
    /** Summary of vehicles on the request, e.g. "2 kendaraan (B 1234 XY, ...)". */
    private String vehicleSummary;
    private String status;
    private Date createdDate;
    private Long approvalInstanceId;
    /** Status is PENDING/DRAFT AND (no instance yet, or its step 1 hasn't been acted on) — set by the controller, not {@link #from}. */
    private boolean editable;

    public static StickerListRow from(SesStickerRequest request, String vehicleSummary) {
        StickerListRow f = new StickerListRow();
        f.setStickerRequestId(request.getStickerRequestId());
        f.setRequestNo(request.getRequestNo());
        f.setRequestorName(request.getRequestorName());
        f.setVehicleSummary(vehicleSummary);
        f.setStatus(request.getStatus());
        f.setCreatedDate(request.getCreatedDate());
        f.setApprovalInstanceId(request.getApprovalInstanceId());
        return f;
    }
}
