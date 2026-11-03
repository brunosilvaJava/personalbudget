package com.bts.personalbudget.controller.paycycle;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Schema(description = "What moving the end of a pay cycle would do, without doing anything.")
public record PayCycleEndAdjustmentPreviewResponse(

        @Schema(description = "The day the next cycle opens today", example = "2026-12-07")
        @JsonProperty("old_end_date")
        LocalDate oldEndDate,

        @Schema(description = "The day the next cycle would open", example = "2026-12-04")
        @JsonProperty("new_end_date")
        LocalDate newEndDate,

        @Schema(description = "Version of the cycle; send it back as expected_version to apply the adjustment",
                example = "0")
        @JsonProperty("expected_version")
        Long expectedVersion,

        @Schema(description = "Version of the next cycle; send it back as expected_next_version to apply the "
                + "adjustment", example = "0")
        @JsonProperty("expected_next_version")
        Long expectedNextVersion,

        @Schema(description = "The two cycles as they are: the cycle first, then the next one")
        @JsonProperty("cycles_before")
        List<PayCycleResponse> cyclesBefore,

        @Schema(description = "The two cycles as they would be, in the same order; the pendencies are what would "
                + "be pending after the change, and the version is still the current one because nothing is saved")
        @JsonProperty("cycles_after")
        List<PayCycleResponse> cyclesAfter,

        @Schema(description = "The cycle the moved days belong to today", example = "123e4567-e89b-12d3-a456-426614174001")
        @JsonProperty("leaving_cycle_code")
        UUID leavingCycleCode,

        @Schema(description = "The cycle the moved days would belong to", example = "123e4567-e89b-12d3-a456-426614174000")
        @JsonProperty("receiving_cycle_code")
        UUID receivingCycleCode,

        @Schema(description = "The active movements whose competence date is in the moved days: they change cycle. "
                + "Movements with no competence date belong to no cycle and are not listed")
        List<PayCycleMovementImpactResponse> movements,

        @Schema(description = "The occurrences of the active income schedules planned for the moved days, "
                + "materialized or not")
        @JsonProperty("income_occurrences")
        List<PayCycleIncomeImpactResponse> incomeOccurrences
) {
}
