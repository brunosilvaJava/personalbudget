package com.bts.personalbudget.controller.financialmovement;

import com.bts.personalbudget.core.domain.enumerator.FinancialMovementStatus;
import com.bts.personalbudget.core.domain.enumerator.OperationType;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "Request data for updating an existing financial movement.")
public record FinancialMovementUpdateRequest(

        @Schema(description = "Type of operation (DEBIT or CREDIT)", example = "DEBIT")
        @JsonProperty("operation_type")
        OperationType operationType,

        @Size(min = 4, max = 50)
        @Schema(description = "Description of the financial movement", example = "Updated monthly subscription payment")
        String description,

        @DecimalMin(value = "0.01", inclusive = false)
        @Digits(integer = 8, fraction = 2)
        @Schema(description = "Total amount of the financial movement", example = "200.00")
        BigDecimal amount,

        @DecimalMin(value = "0.01", inclusive = false)
        @Digits(integer = 8, fraction = 2)
        @JsonProperty("amount_paid")
        @Schema(description = "Amount that has already been paid", example = "150.50")
        BigDecimal amountPaid,

        @JsonProperty("movement_date")
        @Schema(description = "Date and time when the financial movement was created or last updated", example = "2024-11-07T15:30:00", type = "string", format = "date-time")
        LocalDateTime movementDate,

        @JsonProperty("due_date")
        @Schema(description = "Due date for the financial movement", example = "2024-11-30T23:59:59", type = "string", format = "date-time")
        LocalDateTime dueDate,

        @JsonProperty("pay_date")
        @Schema(description = "Date when the payment was made", example = "2024-11-15T10:00:00", type = "string", format = "date-time")
        LocalDateTime payDate,

        @Schema(description = "Status of the financial movement (e.g., PENDING, COMPLETED, CANCELLED)", example = "COMPLETED")
        FinancialMovementStatus status
) {
}
