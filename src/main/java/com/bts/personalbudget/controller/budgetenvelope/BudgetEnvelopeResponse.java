package com.bts.personalbudget.controller.budgetenvelope;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.UUID;

@Schema(description = "An envelope: the amount planned for a category in a cycle and what the cycle did with it.")
public record BudgetEnvelopeResponse(
        @Schema(description = "Public envelope identifier")
        UUID code,

        @JsonProperty("cycle_code")
        @Schema(description = "Code of the cycle")
        UUID cycleCode,

        @JsonProperty("category_code")
        @Schema(description = "Code of the category")
        UUID categoryCode,

        @JsonProperty("category_name")
        @Schema(description = "Name of the category")
        String categoryName,

        @JsonProperty("planned_amount")
        @Schema(description = "Amount planned for the category in the cycle")
        BigDecimal plannedAmount,

        @JsonProperty("recognized_amount")
        @Schema(description = "Consumption already paid, by the cycle's competence")
        BigDecimal recognizedAmount,

        @JsonProperty("committed_amount")
        @Schema(description = "Consumption of the cycle still to be paid (pending, late, card installments)")
        BigDecimal committedAmount,

        @JsonProperty("consumed_amount")
        @Schema(description = "recognized + committed")
        BigDecimal consumedAmount,

        @JsonProperty("remaining_amount")
        @Schema(description = "planned - consumed; negative when the envelope is overrun")
        BigDecimal remainingAmount,

        @JsonProperty("reserved_amount")
        @Schema(description = "What is still free in the envelope (never negative). It is set aside, not a "
                + "dated bank outflow")
        BigDecimal reservedAmount,

        @JsonProperty("overrun_amount")
        @Schema(description = "Consumption beyond the plan (never negative)")
        BigDecimal overrunAmount,

        @Schema(description = "Optimistic-lock version")
        Long version
) {
}
