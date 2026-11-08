package com.bts.personalbudget.controller.holidaycalendar;

import com.bts.personalbudget.controller.paycycle.PayCyclePendingResponse;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record HolidayCalendarPreviewResponse(
        @JsonProperty("income_occurrences")
        @Schema(description = "Unmaterialized income occurrences affected by the proposed review")
        List<HolidayChangeImpactResponse> incomeOccurrences,
        @JsonProperty("cycle_boundaries")
        @Schema(description = "Existing cycle boundaries that the review would move; each shared boundary appears once")
        List<HolidayCycleBoundaryImpactResponse> cycleBoundaries,
        @JsonProperty("cycle_divergences")
        @Schema(description = "New or changed BOUNDARY_DIVERGES_FROM_ANCHOR pendencies; unchanged divergences are omitted")
        List<PayCyclePendingResponse> cycleDivergences
) {
}
