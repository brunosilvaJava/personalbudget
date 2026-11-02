package com.bts.personalbudget.controller.holidaycalendar;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

@Schema(description = "The complete list of holidays of a year. Sending it marks the year as reviewed, "
        + "even when the list is empty.")
public record HolidayYearRequest(

        @NotNull
        @Schema(description = "Every holiday of the year; the ones left out are removed")
        List<@NotNull @Valid HolidayRequest> holidays
) {
}
