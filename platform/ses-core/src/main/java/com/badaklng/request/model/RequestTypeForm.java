package com.badaklng.request.model;

import com.badaklng.platform.model.BaseForm;
import com.badaklng.request.entity.SesRequestType;
import com.badaklng.request.service.RequestTypeService;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Web binding target for /admin/request-type's create/edit form. The
 * document-requirement checkbox table is a full list of every known
 * SesDocRequirement, each row indicating whether it's selected for this
 * type and, if selected, whether it's mandatory — bound via Spring indexed
 * binding {@code docRequirements[i].*}.
 */
@Getter
@Setter
@NoArgsConstructor
public class RequestTypeForm extends BaseForm {

    public static final String LIST_URL = "/admin/request-type";
    public static final String FORM_URL = "/admin/request-type/form";

    /** Null (new code entered by hand) on create. */
    private String typeCode;
    private String typeName;
    private String needsPaymentFlag = "N";
    /**
     * Note the "Y" initialiser, unlike needsPaymentFlag's "N" right above it:
     * that is what makes the checkbox render CHECKED on a brand new type, which
     * is the requested default. blank() relies on it.
     */
    private String needsMgrApprovalFlag = "Y";
    /** Same "Y" default, same reason — the Manager Procurement step. */
    private String needsProcApprovalFlag = "Y";
    /** ALL | LOCAL | LDAP — see RequestTypeService.visibleTo. */
    private String authType = RequestTypeService.AUTH_TYPE_ALL;
    /** IDR, whole Rupiah. Null = not configured. */
    private Long paymentAmount;
    private String needExtPageUpload = "N";
    private Integer orderBy = 0;
    private String activeFlag = "Y";
    private boolean isEdit;
    private Long chainId;

    private List<DocRequirementRow> docRequirements = new ArrayList<>();

    public static RequestTypeForm from(SesRequestType type, List<RequestTypeService.DocSelection> selections) {
        return from(type, selections, null);
    }

    public static RequestTypeForm from(SesRequestType type, List<RequestTypeService.DocSelection> selections, Long chainId) {
        RequestTypeForm f = new RequestTypeForm();
        f.setChainId(chainId);
        f.setTypeCode(type.getTypeCode());
        f.setTypeName(type.getTypeName());
        f.setNeedsPaymentFlag(type.getNeedsPaymentFlag());
        f.setNeedsMgrApprovalFlag(type.getNeedsMgrApprovalFlag());
        f.setNeedsProcApprovalFlag(type.getNeedsProcApprovalFlag());
        f.setAuthType(type.getAuthType());
        f.setNeedExtPageUpload(type.getNeedExtPageUpload());
        f.setPaymentAmount(type.getPaymentAmount());
        f.setOrderBy(type.getOrderBy());
        f.setActiveFlag(type.getActiveFlag());
        f.setEdit(true);
        f.setDocRequirements(toRows(selections));
        return f;
    }

    public static RequestTypeForm blank(List<RequestTypeService.DocSelection> selections) {
        RequestTypeForm f = new RequestTypeForm();
        f.setNeedExtPageUpload("N");
        f.setEdit(false);
        f.setDocRequirements(toRows(selections));
        return f;
    }

    private static List<DocRequirementRow> toRows(List<RequestTypeService.DocSelection> selections) {
        List<DocRequirementRow> rows = new ArrayList<>();
        if (selections != null) {
            for (RequestTypeService.DocSelection sel : selections) {
                DocRequirementRow row = new DocRequirementRow();
                row.setDocCode(sel.getDocCode());
                row.setDocName(sel.getDocName());
                row.setSelected(sel.isSelected());
                row.setMandatory(sel.isMandatory());
                rows.add(row);
            }
        }
        return rows;
    }

    public SesRequestType toEntity() {
        SesRequestType type = new SesRequestType();
        type.setTypeCode(typeCode);
        type.setTypeName(typeName);
        boolean needsPayment = "Y".equalsIgnoreCase(needsPaymentFlag);
        type.setNeedsPaymentFlag(needsPayment ? "Y" : "N");
        // The amount field stays in the DOM (just hidden) when "Needs Payment" is
        // unchecked, so it still submits — drop it here rather than persist a stale
        // amount that RequestPaymentDescriptor.amountDue would otherwise still see.
        type.setPaymentAmount(needsPayment ? paymentAmount : null);
        type.setNeedsMgrApprovalFlag("Y".equalsIgnoreCase(needsMgrApprovalFlag) ? "Y" : "N");
        type.setNeedsProcApprovalFlag("Y".equalsIgnoreCase(needsProcApprovalFlag) ? "Y" : "N");
        type.setAuthType(RequestTypeService.normalizeAuthType(authType));
        type.setNeedExtPageUpload("Y".equalsIgnoreCase(needExtPageUpload) ? "Y" : "N");
        type.setOrderBy(orderBy != null ? orderBy : 0);
        type.setActiveFlag("Y".equalsIgnoreCase(activeFlag) ? "Y" : "N");
        return type;
    }

    public List<RequestTypeService.DocSelection> toDocSelections() {
        List<RequestTypeService.DocSelection> result = new ArrayList<>();
        if (docRequirements != null) {
            for (DocRequirementRow row : docRequirements) {
                if (row == null || !row.isSelected()) {
                    continue;
                }
                boolean hasCode = row.getDocCode() != null && !row.getDocCode().trim().isEmpty();
                boolean hasName = row.getDocName() != null && !row.getDocName().trim().isEmpty();
                if (!hasCode && !hasName) {
                    continue;
                }
                result.add(new RequestTypeService.DocSelection(row.getDocCode(), row.getDocName(), row.isSelected(), row.isMandatory()));
            }
        }
        return result;
    }

    /**
     * One document-requirement row. Rows for an EXISTING catalog entry (from
     * {@code allDocs}) carry docCode with docName null; rows the admin added
     * inline via "+ Add Document" carry docName with docCode null/blank —
     * {@link RequestTypeService#saveWithDocs} creates the catalog entry for
     * those on save, so the document catalog is no longer a fixed, seed-only
     * list.
     */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class DocRequirementRow {
        private String docCode;
        private String docName;
        private boolean selected;
        private boolean mandatory;
    }
}
