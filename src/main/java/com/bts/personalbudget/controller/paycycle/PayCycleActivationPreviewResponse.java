package com.bts.personalbudget.controller.paycycle;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "What an activation would create, without creating anything.")
public record PayCycleActivationPreviewResponse(

        @Schema(description = "Version of the configuration; send it back as expected_version to activate",
                example = "0")
        @JsonProperty("expected_version")
        long expectedVersion,

        @Schema(description = "The first cycles, with their dates, labels and references. Empty when the first one "
                + "cannot be calculated: the pendencies say why")
        List<PayCycleResponse> cycles,

        @Schema(description = "What keeps the cycles from being complete")
        List<PayCyclePendingResponse> pending
) {
}
