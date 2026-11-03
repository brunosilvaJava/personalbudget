package com.bts.personalbudget.controller.paycycle;

import com.bts.personalbudget.core.domain.service.income.IncomeOccurrenceView.State;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

@Schema(description = "An income occurrence planned for days that would change from one pay cycle to the other.")
public record PayCycleIncomeImpactResponse(

        @Schema(description = "Income schedule the occurrence belongs to", example = "123e4567-e89b-12d3-a456-426614174000")
        @JsonProperty("schedule_code")
        UUID scheduleCode,

        @Schema(description = "Description of the income schedule", example = "Salário")
        @JsonProperty("schedule_description")
        String scheduleDescription,

        @Schema(description = "Month the occurrence belongs to; with the schedule, its identity", example = "2026-12",
                type = "string")
        @JsonProperty("nominal_month")
        YearMonth nominalMonth,

        @Schema(description = "MATERIALIZED (a movement exists, also listed with the movements; date and amount are "
                + "frozen) or PROJECTED (not generated yet, calculated from the rule and the calendar)",
                example = "PROJECTED")
        State state,

        @Schema(description = "Planned date of the occurrence: the one that decides its cycle", example = "2026-12-07")
        @JsonProperty("planned_date")
        LocalDate plannedDate,

        @Schema(description = "Materialized amount, or the schedule's current amount for a projection",
                example = "5000.00")
        BigDecimal amount,

        @Schema(description = "Generated movement; only for MATERIALIZED", example = "123e4567-e89b-12d3-a456-426614174000")
        @JsonProperty("movement_code")
        UUID movementCode
) {
}
