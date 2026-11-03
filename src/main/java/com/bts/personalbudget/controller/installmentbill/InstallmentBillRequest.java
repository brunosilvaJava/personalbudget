package com.bts.personalbudget.controller.installmentbill;

import com.bts.personalbudget.core.domain.enumerator.OperationType;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Schema(description = "Request data for creating a new installment bill.")
public record InstallmentBillRequest(

        @Schema(description = "Type of operation for the installment bill (DEBIT or CREDIT)", example = "DEBIT")
        @JsonProperty("operation_type")
        OperationType operationType,

        @Size(min = 4, max = 50)
        @NotBlank
        @Schema(description = "Description of the installment bill", example = "Laptop purchase")
        String description,

        @NotNull
        @DecimalMin(value = "0.01", inclusive = false)
        @Digits(integer = 8, fraction = 2)
        @Schema(description = "Amount of each installment, not the purchase total", example = "125.00")
        BigDecimal amount,

        @JsonProperty("purchase_date")
        @Schema(description = "Date of purchase", example = "2024-01-15", type = "string", format = "date")
        LocalDate purchaseDate,

        @NotNull
        @Min(1)
        @JsonProperty("installment_total")
        @Schema(description = "Total number of installments for the bill", example = "12")
        Integer installmentTotal,

        @Schema(description = "Code of the category. Must be an ACTIVE category of the same operation type. The installments posted from this bill take it", example = "123e4567-e89b-12d3-a456-426614174000")
        @JsonProperty("category_code")
        UUID categoryCode
) {
}
