package com.bts.personalbudget.controller.paycycle;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "The applied anchor transition, its history entry and affected cycles.")
public record PayCycleAnchorTransitionResponse(

        @Schema(description = "Updated pay cycle configuration")
        PayCycleConfigResponse configuration,

        @Schema(description = "History entry written in the same transaction")
        PayCycleAdjustmentResponse adjustment,

        @JsonProperty("preserved_cycles")
        List<PayCycleResponse> preservedCycles,

        @JsonProperty("cancelled_cycles")
        List<PayCycleResponse> cancelledCycles,

        @JsonProperty("new_cycles")
        List<PayCycleResponse> newCycles
) {
}
