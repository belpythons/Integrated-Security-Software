package com.badaklng.approval.model;

import com.badaklng.platform.model.BaseForm;
import com.badaklng.approval.entity.ApprovalStatus;
import com.badaklng.approval.entity.SesApprovalInstance;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Date;

/**
 * Worklist row: an instance the current user can act on.
 */
@Getter
@Setter
@NoArgsConstructor
public class ApprovalTaskForm extends BaseForm {

    public static final String LIST_URL = "/approval";
    public static final String DETAIL_URL = "/approval";
    public static final String FORM_URL = "/approval/form";

    private Long instanceId;
    private String chainName;
    private String businessRefType;
    private Long businessRefId;
    private String requestorUserId;
    /** Requestor's real name, resolved by {@code ApprovalService.toTaskForm} — never the raw username. */
    private String requestorName;
    private String currentStepName;
    private Integer currentStepOrder;
    private String status;
    private String formCode;
    private String matchKey;
    private String resolutionTier;

    /** Human-readable form/module name (e.g. "New Request") — where the request comes from. */
    private String module;
    /** Business transaction number (e.g. request no), falls back to businessRefType+id when the module has none. */
    private String transactionId;
    /** One-line summary of what the record is about (e.g. request type name). */
    private String description;
    private Date requestDate;

    /*
     * There is deliberately NO requestorId field carrying APP_USER.USER_ID.
     *
     * One existed, was rendered verbatim in a "Requestor ID" column on
     * approval/my.html, and showed the reader
     * "00000000-0000-0000-0000-000000000001" next to the resolved name it
     * added nothing to. Dropping only the <td> would have left the field
     * sitting here with a javadoc inviting the next person to render it again,
     * so the carrier went with it. Two identifiers are enough:
     * requestorUserId (the username, i.e. the badge id) and requestorName.
     */

    public static ApprovalTaskForm from(SesApprovalInstance instance, String currentStepName,
                                        String module, String transactionId, String description) {
        ApprovalTaskForm f = new ApprovalTaskForm();
        f.setInstanceId(instance.getInstanceId());
        f.setChainName(instance.getChainName());
        f.setBusinessRefType(instance.getBusinessRefType());
        f.setBusinessRefId(instance.getBusinessRefId());
        f.setRequestorUserId(instance.getRequestorUserId());
        // requestorName is NOT set from requestorUserId here: that column holds a
        // username, and "jdoe" under a column headed "Requestor Name" is what this
        // whole pass exists to remove. ApprovalService.toTaskForm fills it in.
        f.setCurrentStepName(currentStepName);
        f.setStatus(instance.getStatus().name());
        f.setFormCode(instance.getFormCode());
        f.setMatchKey(instance.getMatchKey());
        f.setResolutionTier(instance.getResolutionTier() != null ? instance.getResolutionTier().name() : null);
        f.setModule(module);
        f.setTransactionId(transactionId != null ? transactionId : instance.getBusinessRefType() + " #" + instance.getBusinessRefId());
        f.setDescription(description);
        f.setRequestDate(instance.getCreatedDate());
        return f;
    }
}
