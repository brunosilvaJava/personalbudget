package com.bts.personalbudget.controller.cardpurchase;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Schema(description = "Request data for registering a credit card purchase.")
public record CardPurchaseRequest(

        @NotNull
        @JsonProperty("card_code")
        @Schema(description = "Card used in the purchase. Must be ACTIVE", example = "123e4567-e89b-12d3-a456-426614174000")
        UUID cardCode,

        @NotBlank
        @Size(max = 100)
        @Schema(description = "What was bought", example = "Geladeira")
        String description,

        @NotNull
        @JsonProperty("purchase_date")
        @Schema(description = "Date of the purchase; with the closing day it decides the first invoice",
                example = "2026-11-08", type = "string", format = "date")
        LocalDate purchaseDate,

        @NotNull
        @Min(1)
        @Max(60)
        @Schema(description = "Number of installments", example = "10")
        Integer installments,

        @NotNull
        @DecimalMin(value = "0.00", inclusive = false)
        @Digits(integer = 8, fraction = 2)
        @JsonProperty("installment_amount")
        @Schema(description = "Amount of each installment; the total is installment_amount times installments",
                example = "250.00")
        BigDecimal installmentAmount,

        @JsonProperty("category_code")
        @Schema(description = "Optional DEBIT category", example = "123e4567-e89b-12d3-a456-426614174000")
        UUID categoryCode,

        @JsonProperty("card_holder_code") UUID cardHolderCode,

        @JsonProperty("card_last_digits") @jakarta.validation.constraints.Pattern(regexp = "\\d{4}") String cardLastDigits,

        @JsonProperty("third_party_code") UUID thirdPartyCode,

        @JsonProperty("own_expense") Boolean ownExpense,

        @JsonProperty("first_installment") @Min(1) @Max(60) Integer firstInstallment
) {
}
