package com.bts.personalbudget.controller.paycycle;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Request data to deactivate the pay cycles.")
public record PayCycleDeactivationRequest(

        @NotNull
        @Schema(description = "Version of the configuration that was read; a different current version answers 409",
                example = "1")
        @JsonProperty("expected_version")
        Long expectedVersion,

        @NotBlank
        @Size(max = 500)
        @Schema(description = "Why the pay cycles are being turned off; kept in the history of the cycles it touches",
                example = "Mudei de emprego e vou recalibrar a âncora", maxLength = 500)
        String justification
) {
}
