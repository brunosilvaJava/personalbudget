package com.bts.personalbudget.controller.holidaycalendar;

import com.bts.personalbudget.controller.incomeschedule.IncomeOccurrenceResponse;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.YearMonth;
import java.util.UUID;

@Schema(description = "What reviewing a calendar year would change in one income occurrence that is not "
        + "generated yet.")
public record HolidayChangeImpactResponse(

        @Schema(description = "Income schedule", example = "123e4567-e89b-12d3-a456-426614174000")
        @JsonProperty("schedule_code")
        UUID scheduleCode,

        @Schema(description = "Income schedule description", example = "Salário")
        @JsonProperty("schedule_description")
        String scheduleDescription,

        @Schema(description = "Month the occurrence belongs to", example = "2026-12", type = "string")
        @JsonProperty("nominal_month")
        YearMonth nominalMonth,

        @Schema(description = "The occurrence with the calendar as it is now")
        IncomeOccurrenceResponse before,

        @Schema(description = "The occurrence with the calendar after the review")
        IncomeOccurrenceResponse after
) {
}
