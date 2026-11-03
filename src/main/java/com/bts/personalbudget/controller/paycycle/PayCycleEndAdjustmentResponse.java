package com.bts.personalbudget.controller.paycycle;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A pay cycle boundary that was moved: the history entry and the two cycles as they are now.")
public record PayCycleEndAdjustmentResponse(

        @Schema(description = "The entry recorded in the history, in the same transaction as the change")
        PayCycleAdjustmentResponse adjustment,

        @Schema(description = "The cycle whose end was moved, with its new version")
        PayCycleResponse cycle,

        @Schema(description = "The next cycle, whose start moved with it, with its new version")
        @JsonProperty("next_cycle")
        PayCycleResponse nextCycle
) {
}
