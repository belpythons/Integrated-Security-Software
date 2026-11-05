package com.badaklng.ses.api.dto.posko;

import lombok.Data;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Data
public class PoskoCheckOutRequest {
    @NotBlank(message = "Request number is required")
    private String requestNo;

    @NotBlank(message = "Vehicle plate must be selected")
    private String selectedVehiclePlate;

    @NotNull(message = "Visitor badge return flag is mandatory")
    private Boolean visitorBadgeReturned;

    @NotNull(message = "KTP return flag is mandatory")
    private Boolean ktpReturned;

    private String notes;
}

