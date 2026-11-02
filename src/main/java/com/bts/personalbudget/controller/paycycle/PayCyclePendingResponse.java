package com.bts.personalbudget.controller.paycycle;

import com.bts.personalbudget.core.domain.service.paycycle.PayCyclePending.Type;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

@Schema(description = "Something that keeps the pay cycles from being complete or exact. Calculated when read; "
        + "it goes away by itself when the calendar is reviewed or the rule is fixed.")
public record PayCyclePendingResponse(

        @Schema(description = "CALENDAR_NOT_REVIEWED, BUSINESS_DAY_NOT_FOUND, ANCHOR_SCHEDULE_ENDS, "
                + "BOUNDARY_DIVERGES_FROM_ANCHOR or NO_CURRENT_CYCLE", example = "CALENDAR_NOT_REVIEWED")
        Type type,

        @Schema(description = "Month of the anchor occurrence the pendency is about", example = "2027-01", type = "string")
        @JsonProperty("nominal_month")
        YearMonth nominalMonth,

        @Schema(description = "Calendar year to review; only for CALENDAR_NOT_REVIEWED", example = "2027")
        Integer year,

        @Schema(description = "Cycle that starts on the boundary; only for BOUNDARY_DIVERGES_FROM_ANCHOR",
                example = "123e4567-e89b-12d3-a456-426614174000")
        @JsonProperty("cycle_code")
        UUID cycleCode,

        @Schema(description = "Where the boundary is; only for BOUNDARY_DIVERGES_FROM_ANCHOR", example = "2026-12-07")
        @JsonProperty("boundary_date")
        LocalDate boundaryDate,

        @Schema(description = "Where the anchor occurrence is now; only for BOUNDARY_DIVERGES_FROM_ANCHOR",
                example = "2026-12-08")
        @JsonProperty("anchor_date")
        LocalDate anchorDate
) {
}
