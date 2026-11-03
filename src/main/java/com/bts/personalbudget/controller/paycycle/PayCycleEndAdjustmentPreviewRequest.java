package com.bts.personalbudget.controller.paycycle;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

@Schema(description = "Request data to preview moving the end of a pay cycle.")
public record PayCycleEndAdjustmentPreviewRequest(

        @NotNull
        @Schema(description = "The day the next cycle would open: the new exclusive end of this cycle. Must be "
                + "after the start of this cycle and before the end of the next one, and differ from the current end",
                example = "2026-12-04")
        @JsonProperty("new_end_date")
        LocalDate newEndDate
) {
}
