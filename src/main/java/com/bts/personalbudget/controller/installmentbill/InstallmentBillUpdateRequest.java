package com.bts.personalbudget.controller.installmentbill;

import com.bts.personalbudget.core.domain.enumerator.OperationType;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Schema(description = "Request data for updating an existing installment bill.")
public record InstallmentBillUpdateRequest(

        @Schema(description = "Unique identifier for the installment bill to be updated", example = "123e4567-e89b-12d3-a456-426614174000")
        UUID code,

        @Schema(description = "Type of operation for the installment bill (DEBIT or CREDIT)", example = "DEBIT")
        @JsonProperty("operation_type")
        OperationType operationType,

        @NotBlank
        @Schema(description = "Updated description of the installment bill", example = "Updated laptop purchase")
        String description,

        @DecimalMin(value = "0.01", inclusive = false)
        @Digits(integer = 8, fraction = 2)
        @Schema(description = "Updated total amount of the installment bill", example = "1550.00")
        BigDecimal amount,

        @JsonProperty("purchase_date")
        @Schema(description = "Updated date of purchase", example = "2024-01-15", type = "string", format = "date")
        LocalDate purchaseDate,

        @Min(1)
        @JsonProperty("installment_total")
        @Schema(description = "Updated total number of installments for the bill", example = "12")
        Integer installmentTotal
) {
}
