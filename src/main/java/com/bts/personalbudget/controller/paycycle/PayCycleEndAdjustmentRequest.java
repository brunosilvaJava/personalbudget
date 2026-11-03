package com.bts.personalbudget.controller.paycycle;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

@Schema(description = "Request data to move the end of a pay cycle, and with it the start of the next one.")
public record PayCycleEndAdjustmentRequest(

        @NotNull
        @Schema(description = "The day the next cycle opens: the new exclusive end of this cycle. Must be after the "
                + "start of this cycle and before the end of the next one, and differ from the current end",
                example = "2026-12-04")
        @JsonProperty("new_end_date")
        LocalDate newEndDate,

        @NotBlank
        @Size(max = 500)
        @Schema(description = "Why the boundary is being moved; kept in the history of both cycles",
                example = "Salário caiu antes do feriado", maxLength = 500)
        String justification,

        @NotNull
        @Schema(description = "Version of this cycle the preview was made with; a different current version answers 409",
                example = "0")
        @JsonProperty("expected_version")
        Long expectedVersion,

        @NotNull
        @Schema(description = "Version of the next cycle the preview was made with; a different current version "
                + "answers 409", example = "0")
        @JsonProperty("expected_next_version")
        Long expectedNextVersion
) {
}
