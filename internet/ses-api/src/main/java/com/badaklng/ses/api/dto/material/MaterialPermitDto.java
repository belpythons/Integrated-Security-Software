package com.badaklng.ses.api.dto.material;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MaterialPermitDto {
    private String requestNo;
    private String vendorCode;
    private String vendorName;
    private String contractNo;
    private String poNumber;
    private String doNumber;
    private String destinationDepartment;
    private String driverName;
    private String driverNik;
    private String vehiclePlate;
    private String vehicleType;
    private String validFrom;
    private String validTo;
    private String status;
    private List<Long> attachmentFileIds;
}

