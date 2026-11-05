package com.badaklng.ses.api.dto.posko;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PoskoScanResponse {
    private String requestNo;
    private String vendorName;
    private String department;
    private String poNumber;
    private String poDescription;
    
    /**
     * Price masking for security posko field officers.
     * Price is strictly masked to maintain procurement confidentiality.
     */
    private String unitPriceMasked;
    
    private String validFrom;
    private String validTo;
    
    /**
     * Non-free text selection: Officers must strictly choose from these registered items.
     */
    private List<String> registeredVehicles;
    private List<String> registeredDrivers;
    
    private String screeningStatus;
    private boolean hasKkbSticker;
    private String currentStatus;
}

