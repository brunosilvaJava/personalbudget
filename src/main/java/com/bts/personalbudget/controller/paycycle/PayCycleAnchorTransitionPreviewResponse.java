package com.bts.personalbudget.controller.paycycle;

import com.bts.personalbudget.core.domain.enumerator.PayCycleTransitionShape;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Schema(description = "The cycles affected by a proposed anchor transition; nothing is persisted.")
public record PayCycleAnchorTransitionPreviewResponse(

        @JsonProperty("expected_version")
        long expectedVersion,

        @JsonProperty("previous_anchor_schedule_code")
        UUID previousAnchorScheduleCode,

        @JsonProperty("anchor_schedule_code")
        UUID anchorScheduleCode,

        @Schema(description = "Last frozen boundary from which the future chain is rebuilt")
        @JsonProperty("frozen_boundary")
        LocalDate frozenBoundary,

        @JsonProperty("old_boundary")
        LocalDate oldBoundary,

        @JsonProperty("new_boundary")
        LocalDate newBoundary,

        @JsonProperty("transition_shape")
        PayCycleTransitionShape transitionShape,

        @Schema(description = "Started or frozen cycles that remain unchanged")
        @JsonProperty("preserved_cycles")
        List<PayCycleResponse> preservedCycles,

        @Schema(description = "Future cycles that would be cancelled")
        @JsonProperty("cancelled_cycles")
        List<PayCycleResponse> cancelledCycles,

        @Schema(description = "Cycles that would be created after the frozen boundary")
        @JsonProperty("new_cycles")
        List<PayCycleResponse> newCycles,

        @Schema(description = "Date-calculation blockers or frozen boundaries that diverge")
        @JsonProperty("pending")
        List<PayCyclePendingResponse> pending
) {
}
