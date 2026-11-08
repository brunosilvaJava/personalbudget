package com.bts.personalbudget.controller.movementsettlement;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(description = "Data for recording a payment settlement.")
public record MovementSettlementRequest(
        @NotNull
        @DecimalMin(value = "0.00", inclusive = false)
        @Digits(integer = 8, fraction = 2)
        @Schema(description = "Positive payment amount, regardless of movement operation type", example = "60.00")
        BigDecimal amount,

        @NotNull
        @JsonProperty("effective_date")
        @Schema(description = "Date the payment took effect", example = "2026-11-15", type = "string", format = "date")
        LocalDate effectiveDate
) {
}
