package com.badaklng.request.model;

import com.badaklng.platform.model.BaseForm;
import com.badaklng.request.entity.SesRequest;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Date;

/**
 * List row DTO for "My Requests" (/request). Edit/Cancel are only offered
 * (server-side too, via RequestService) while status is PENDING.
 */
@Getter
@Setter
@NoArgsConstructor
public class RequestListForm extends BaseForm {

    public static final String LIST_URL = "/request";
    public static final String FORM_URL = "/request/form";

    private Long requestId;
    private String requestNo;
    private String typeCode;
    private String typeName;
    private String status;
    private Date createdDate;
    private Long approvalInstanceId;
    /** Status is PENDING/DRAFT AND (no instance yet, or its step 1 hasn't been acted on) — set by the controller, not {@link #from}. */
    private boolean editable;

    public static RequestListForm from(SesRequest request, String typeName) {
        RequestListForm f = new RequestListForm();
        f.setRequestId(request.getRequestId());
        f.setRequestNo(request.getRequestNo());
        f.setTypeCode(request.getTypeCode());
        f.setTypeName(typeName);
        f.setStatus(request.getStatus());
        f.setCreatedDate(request.getCreatedDate());
        f.setApprovalInstanceId(request.getApprovalInstanceId());
        return f;
    }
}
