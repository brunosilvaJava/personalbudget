package com.bts.personalbudget.controller.holidaycalendar;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "One year of the holiday calendar.")
public record HolidayYearResponse(

        @Schema(description = "Calendar year", example = "2026")
        int year,

        @Schema(description = "False means the year is not prepared yet, which is different from a reviewed "
                + "year with no holidays. Income dates in an unreviewed year are not calculated", example = "true")
        boolean reviewed,

        @Schema(description = "Holidays of the year ordered by date")
        List<HolidayResponse> holidays
) {
}
