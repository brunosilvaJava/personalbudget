package com.bts.personalbudget.controller.installmentbill;

import com.bts.personalbudget.core.domain.enumerator.InstallmentBillStatus;
import com.bts.personalbudget.core.domain.enumerator.OperationType;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Schema(description = "Response data for an installment bill.")
public record InstallmentBillResponse(

        @Schema(description = "Unique identifier for the installment bill", example = "123e4567-e89b-12d3-a456-426614174000")
        UUID code,

        @Schema(description = "Type of operation for the installment bill (DEBIT or CREDIT)", example = "DEBIT")
        @JsonProperty("operation_type")
        OperationType operationType,

        @Schema(description = "Description of the installment bill", example = "Laptop purchase")
        String description,

        @Schema(description = "Total amount of the installment bill", example = "1500.00")
        BigDecimal amount,

        @Schema(description = "Current status of the installment bill (e.g., ACTIVE, COMPLETED, CANCELED)", example = "ACTIVE")
        InstallmentBillStatus status,

        @JsonProperty("purchase_date")
        @Schema(description = "Date of purchase for the installment bill", example = "2024-01-15", type = "string", format = "date")
        LocalDate purchaseDate,

        @JsonProperty("installment_total")
        @Schema(description = "Total number of installments for the bill", example = "12")
        Integer installmentTotal
) {
}
