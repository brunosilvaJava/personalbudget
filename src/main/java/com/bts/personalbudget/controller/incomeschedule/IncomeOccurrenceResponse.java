package com.bts.personalbudget.controller.incomeschedule;

import com.bts.personalbudget.core.domain.service.income.IncomeOccurrenceView.State;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

@Schema(description = "One nominal month of an income schedule.")
public record IncomeOccurrenceResponse(

        @Schema(description = "Month the occurrence belongs to; with the schedule, its identity", example = "2026-12",
                type = "string")
        @JsonProperty("nominal_month")
        YearMonth nominalMonth,

        @Schema(description = "MATERIALIZED (a movement exists; date and amount are frozen), PROJECTED (not "
                + "generated yet, calculated from the rule and the calendar), CALENDAR_NOT_REVIEWED (depends on a "
                + "calendar year that was not reviewed; no date is assumed) or BUSINESS_DAY_NOT_FOUND",
                example = "PROJECTED")
        State state,

        @Schema(description = "Expected date; null when it cannot be calculated", example = "2026-12-07")
        @JsonProperty("planned_date")
        LocalDate plannedDate,

        @Schema(description = "Materialized amount, or the schedule's current amount for a projection; null when "
                + "the date cannot be calculated", example = "5000.00")
        BigDecimal amount,

        @Schema(description = "Generated movement; only for MATERIALIZED", example = "123e4567-e89b-12d3-a456-426614174000")
        @JsonProperty("movement_code")
        UUID movementCode,

        @Schema(description = "Calendar year to review; only for CALENDAR_NOT_REVIEWED", example = "2027")
        @JsonProperty("pending_year")
        Integer pendingYear
) {
}
