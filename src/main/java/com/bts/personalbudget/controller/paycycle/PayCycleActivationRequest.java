package com.bts.personalbudget.controller.paycycle;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

@Schema(description = "Request data to activate the pay cycles.")
public record PayCycleActivationRequest(

        @NotNull
        @Schema(description = "Start of the first cycle. Between 31 days before and 31 days after today, and not "
                + "before the end of a previous cycle", example = "2026-11-05")
        @JsonProperty("initial_date")
        LocalDate initialDate,

        @NotNull
        @Schema(description = "Version of the configuration the preview was made with; a different current "
                + "version answers 409", example = "0")
        @JsonProperty("expected_version")
        Long expectedVersion
) {
}
