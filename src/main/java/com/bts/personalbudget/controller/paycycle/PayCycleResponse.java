package com.bts.personalbudget.controller.paycycle;

import com.bts.personalbudget.core.domain.enumerator.PayCycleKind;
import com.bts.personalbudget.core.domain.enumerator.PayCycleStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

@Schema(description = "A financial cycle: the interval [start_date, end_date_exclusive).")
public record PayCycleResponse(

        @Schema(description = "Unique identifier of the cycle; it never changes, even when its dates are adjusted",
                example = "123e4567-e89b-12d3-a456-426614174000")
        UUID code,

        @Schema(description = "First day of the cycle", example = "2026-11-05")
        @JsonProperty("start_date")
        LocalDate startDate,

        @Schema(description = "Day the next cycle opens; it belongs to the next cycle, not to this one",
                example = "2026-12-07")
        @JsonProperty("end_date_exclusive")
        LocalDate endDateExclusive,

        @Schema(description = "Last day of the cycle, inclusive", example = "2026-12-06")
        @JsonProperty("last_day")
        LocalDate lastDay,

        @Schema(description = "Number of days of the cycle", example = "32")
        @JsonProperty("total_days")
        int totalDays,

        @Schema(description = "Name to show, calculated from the dates; never an identifier", example = "Nov/Dez")
        String label,

        @Schema(description = "Nominal month of the anchor occurrence that opens the cycle; stable when the dates "
                + "are adjusted; null for a TRANSITION no anchor occurrence opens", example = "2026-11", type = "string")
        @JsonProperty("reference_month")
        YearMonth referenceMonth,

        @Schema(description = "REGULAR or TRANSITION", example = "REGULAR")
        PayCycleKind kind,

        @Schema(description = "PLANNED (starts after today), OPEN (today is inside), PENDING_CLOSE (already ended) "
                + "or CANCELLED. Derived from the dates, never stored", example = "OPEN")
        PayCycleStatus status,

        @Schema(description = "Schedule of the anchor occurrence that opens the cycle",
                example = "123e4567-e89b-12d3-a456-426614174000")
        @JsonProperty("anchor_schedule_code")
        UUID anchorScheduleCode,

        @Schema(description = "The start was fixed by a manual adjustment and is not recalculated", example = "false")
        @JsonProperty("start_adjusted")
        boolean startAdjusted,

        @Schema(description = "The end was fixed by a manual adjustment and is not recalculated", example = "false")
        @JsonProperty("end_adjusted")
        boolean endAdjusted,

        @Schema(description = "Version of the cycle; null in a preview, where nothing is saved", example = "0")
        Long version,

        @Schema(description = "Pendencies about this cycle")
        List<PayCyclePendingResponse> pending
) {
}
