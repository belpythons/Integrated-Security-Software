package com.badaklng.approval.model;

import com.badaklng.platform.model.BaseForm;
import com.badaklng.approval.entity.SesApprovalChain;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Chain list row + header display DTO for the chain admin screens.
 */
@Getter
@Setter
@NoArgsConstructor
public class ApprovalChainForm extends BaseForm {

    /** Chain list — the read page of the /approval/chain read + /approval/chain/form CRUD pair. */
    public static final String LIST_URL = "/approval/chain";
    /** Virtual CRUD-grant URL — no controller mapping exists here, only the app_page row. */
    public static final String FORM_URL = "/approval/chain/form";

    private Long chainId;
    private String chainKey;
    private String chainName;
    private String description;
    private String activeFlag;
    private long stepCount;
    private boolean inUse;

    public static ApprovalChainForm from(SesApprovalChain chain, long stepCount, boolean inUse) {
        ApprovalChainForm f = new ApprovalChainForm();
        f.setChainId(chain.getChainId());
        f.setChainKey(chain.getChainKey());
        f.setChainName(chain.getChainName());
        f.setDescription(chain.getDescription());
        f.setActiveFlag(chain.getActiveFlag());
        f.setStepCount(stepCount);
        f.setInUse(inUse);
        return f;
    }
}
