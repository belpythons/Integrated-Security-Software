package com.badaklng.approval.model;

import com.badaklng.approval.entity.SesForm;
import com.badaklng.approval.entity.SesFormApprovalMap;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Web binding target for the form-to-chain mapping editor. formCode /
 * formName / matchKeyLabel are read-only, descriptor-synced display fields
 * carried along only so the view can render them without a second lookup —
 * they are never written back by {@code ApprovalFormService.saveMappings}.
 */
@Getter
@Setter
@NoArgsConstructor
public class SesFormMappingForm {

    /** Mapping list — the read page of the /approval/map read + /approval/map/form CRUD pair. */
    public static final String LIST_URL = "/approval/map";
    /** Virtual CRUD-grant URL — no controller mapping exists here, only the app_page row. */
    public static final String FORM_URL = "/approval/map/form";

    private String formCode;
    private String formName;
    private String matchKeyLabel;

    private Long defaultChainId;
    private List<OverrideRow> overrides = new ArrayList<>();

    /** Populates the editor from the form descriptor row and its current mapping rows. */
    public static SesFormMappingForm from(SesForm form, List<SesFormApprovalMap> mappings) {
        SesFormMappingForm f = new SesFormMappingForm();
        f.setFormCode(form.getFormCode());
        f.setFormName(form.getFormName());
        f.setMatchKeyLabel(form.getMatchKeyLabel());

        List<OverrideRow> overrideRows = new ArrayList<>();
        if (mappings != null) {
            for (SesFormApprovalMap m : mappings) {
                if (m.getMatchKey() == null) {
                    f.setDefaultChainId(m.getChainId());
                    continue;
                }
                OverrideRow row = new OverrideRow();
                row.setMapId(m.getMapId());
                row.setMatchKey(m.getMatchKey());
                row.setChainId(m.getChainId());
                row.setActiveFlag(m.getActiveFlag());
                overrideRows.add(row);
            }
        }
        f.setOverrides(overrideRows);
        return f;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class OverrideRow {
        private Long mapId;
        private String matchKey;
        private Long chainId;
        private String activeFlag = "Y";
    }
}
