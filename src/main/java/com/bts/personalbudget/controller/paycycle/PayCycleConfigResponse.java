package com.bts.personalbudget.controller.paycycle;

import com.bts.personalbudget.core.domain.enumerator.PayCycleMode;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Schema(description = "The financial cycle configuration.")
public record PayCycleConfigResponse(

        @Schema(description = "CALENDAR_MONTH (reports follow the calendar month) or PAY_CYCLE (the anchor income "
                + "schedule opens the cycles)", example = "PAY_CYCLE")
        PayCycleMode mode,

        @Schema(description = "Anchor income schedule in use; null in CALENDAR_MONTH",
                example = "123e4567-e89b-12d3-a456-426614174000")
        @JsonProperty("anchor_schedule_code")
        UUID anchorScheduleCode,

        @Schema(description = "Start of the first cycle of the current activation; null in CALENDAR_MONTH",
                example = "2026-11-05")
        @JsonProperty("activation_date")
        LocalDate activationDate,

        @Schema(description = "Version of the configuration; send it back as expected_version to change it",
                example = "3")
        long version,

        @Schema(description = "What keeps the cycles from being complete or exact right now")
        List<PayCyclePendingResponse> pending
) {
}
