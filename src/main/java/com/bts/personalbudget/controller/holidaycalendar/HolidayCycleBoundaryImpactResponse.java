package com.bts.personalbudget.controller.holidaycalendar;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.UUID;

public record HolidayCycleBoundaryImpactResponse(
        @JsonProperty("previous_cycle_code")
        @Schema(description = "Existing cycle ending on this boundary")
        UUID previousCycleCode,
        @JsonProperty("next_cycle_code")
        @Schema(description = "Existing cycle starting on this boundary; null at the end of the stored chain")
        UUID nextCycleCode,
        @JsonProperty("old_boundary")
        @Schema(description = "Boundary under the current calendar, after simulating the usual refresh")
        LocalDate oldBoundary,
        @JsonProperty("new_boundary")
        @Schema(description = "Boundary under the proposed calendar, exclusive end of the previous cycle")
        LocalDate newBoundary
) {
}
