package com.bts.personalbudget.controller.holidaycalendar;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

@Schema(description = "A holiday of the local calendar.")
public record HolidayRequest(

        @NotNull
        @Schema(description = "Holiday date; must belong to the year in the path", example = "2026-09-07")
        LocalDate date,

        @NotBlank
        @Size(max = 100)
        @Schema(description = "Holiday name", example = "Independência do Brasil")
        String name
) {
}
