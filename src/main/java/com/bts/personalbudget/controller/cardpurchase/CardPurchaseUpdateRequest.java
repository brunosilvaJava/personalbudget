package com.bts.personalbudget.controller.cardpurchase;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Schema(description = "Request data for updating a card purchase. Fields left out are kept as they are. "
        + "The card cannot be changed. Date, installments and amount are only accepted while no installment "
        + "has a payment.")
public record CardPurchaseUpdateRequest(

        @Size(max = 100)
        @Schema(description = "New description", example = "Geladeira inverter")
        String description,

        @JsonProperty("purchase_date")
        @Schema(description = "New purchase date", example = "2026-11-09", type = "string", format = "date")
        LocalDate purchaseDate,

        @Min(1)
        @Max(60)
        @Schema(description = "New number of installments", example = "12")
        Integer installments,

        @DecimalMin(value = "0.00", inclusive = false)
        @Digits(integer = 8, fraction = 2)
        @JsonProperty("installment_amount")
        @Schema(description = "New amount of each installment", example = "230.00")
        BigDecimal installmentAmount,

        @JsonProperty("category_code")
        @Schema(description = "New DEBIT category", example = "123e4567-e89b-12d3-a456-426614174000")
        UUID categoryCode
) {
}
