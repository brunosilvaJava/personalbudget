package com.bts.personalbudget.controller.budgetenvelope;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

@Schema(description = "Data for planning an amount for a category in a cycle.")
public record BudgetEnvelopeRequest(
        @NotNull
        @JsonProperty("category_code")
        @Schema(description = "Code of an ACTIVE DEBIT category", example = "123e4567-e89b-12d3-a456-426614174000")
        UUID categoryCode,

        @NotNull
        @DecimalMin(value = "0.00", inclusive = false)
        @Digits(integer = 10, fraction = 2)
        @JsonProperty("planned_amount")
        @Schema(description = "Amount planned for the category in the cycle", example = "800.00")
        BigDecimal plannedAmount
) {
}
