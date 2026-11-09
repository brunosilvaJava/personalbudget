package com.bts.personalbudget.controller.budgetenvelope;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

@Schema(description = "Data for changing the planned amount of an envelope.")
public record BudgetEnvelopeUpdateRequest(
        @NotNull
        @DecimalMin(value = "0.00", inclusive = false)
        @Digits(integer = 10, fraction = 2)
        @JsonProperty("planned_amount")
        @Schema(description = "New planned amount", example = "900.00")
        BigDecimal plannedAmount
) {
}
