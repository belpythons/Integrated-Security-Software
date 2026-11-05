package com.badaklng.ses.api.dto.posko;

import lombok.Data;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Data
public class PoskoCheckInRequest {
    @NotBlank(message = "Request number is required")
    private String requestNo;

    @NotBlank(message = "Vehicle plate must be selected from registered list")
    private String selectedVehiclePlate;

    @NotBlank(message = "Driver name must be selected from registered list")
    private String selectedDriverName;

    @NotBlank(message = "Visitor badge number is mandatory")
    private String visitorBadgeNo;

    @NotNull(message = "KTP held flag is mandatory")
    private Boolean ktpHeld;

    private String notes;
}

