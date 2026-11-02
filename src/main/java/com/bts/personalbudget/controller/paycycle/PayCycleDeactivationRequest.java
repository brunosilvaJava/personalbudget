package com.bts.personalbudget.controller.paycycle;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Request data to deactivate the pay cycles.")
public record PayCycleDeactivationRequest(

        @NotNull
        @Schema(description = "Version of the configuration that was read; a different current version answers 409",
                example = "1")
        @JsonProperty("expected_version")
        Long expectedVersion
) {
}
