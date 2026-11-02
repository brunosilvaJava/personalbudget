package com.bts.personalbudget.controller.holidaycalendar;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

@Schema(description = "A holiday of the local calendar.")
public record HolidayResponse(

        @Schema(description = "Holiday date", example = "2026-09-07")
        LocalDate date,

        @Schema(description = "Holiday name", example = "Independência do Brasil")
        String name
) {
}
