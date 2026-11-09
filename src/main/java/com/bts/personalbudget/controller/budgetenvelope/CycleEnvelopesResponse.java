package com.bts.personalbudget.controller.budgetenvelope;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Schema(description = "The envelopes of a cycle and their totals.")
public record CycleEnvelopesResponse(
        @JsonProperty("cycle_code")
        UUID cycleCode,

        List<BudgetEnvelopeResponse> envelopes,

        @JsonProperty("total_planned")
        BigDecimal totalPlanned,

        @JsonProperty("total_consumed")
        BigDecimal totalConsumed,

        @JsonProperty("total_reserved")
        @Schema(description = "Sum of the reserved amounts: what the envelopes still hold free")
        BigDecimal totalReserved,

        @JsonProperty("total_overrun")
        BigDecimal totalOverrun,

        @JsonProperty("projection_incomplete")
        @Schema(description = "True when the cycle summary has pendencies: the consumed amounts may be short")
        boolean projectionIncomplete
) {
}
