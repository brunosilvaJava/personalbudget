package com.bts.personalbudget.controller.cardpurchase;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Schema(description = "A card purchase with its installments.")
public record CardPurchaseResponse(

        @Schema(description = "Unique identifier for the purchase")
        UUID code,

        @JsonProperty("card_code")
        @Schema(description = "Card of the purchase")
        UUID cardCode,

        @Schema(description = "What was bought", example = "Geladeira")
        String description,

        @JsonProperty("purchase_date")
        @Schema(description = "Date of the purchase", type = "string", format = "date")
        LocalDate purchaseDate,

        @Schema(description = "Number of installments", example = "10")
        Integer installments,

        @JsonProperty("installment_amount")
        @Schema(description = "Amount of each installment", example = "250.00")
        BigDecimal installmentAmount,

        @JsonProperty("total_amount")
        @Schema(description = "installment_amount times installments", example = "2500.00")
        BigDecimal totalAmount,

        @JsonProperty("category_code")
        @Schema(description = "DEBIT category, if any")
        UUID categoryCode,

        @JsonProperty("installment_list")
        @Schema(description = "The installments in order")
        List<CardInstallmentResponse> installmentList
) {
}
