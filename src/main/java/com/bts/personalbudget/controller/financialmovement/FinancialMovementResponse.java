package com.bts.personalbudget.controller.financialmovement;

import com.bts.personalbudget.core.domain.enumerator.FinancialMovementStatus;
import com.bts.personalbudget.core.domain.enumerator.OperationType;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record FinancialMovementResponse (

        @Schema(description = "Unique identifier for the financial movement", example = "123e4567-e89b-12d3-a456-426614174000")
        UUID code,

        @Schema(description = "Type of operation (DEBIT or CREDIT)", example = "DEBIT")
        @JsonProperty("operation_type")
        OperationType operationType,

        @Schema(description = "Description of the financial movement", example = "Monthly subscription payment")
        String description,

        @Schema(description = "Total amount of the financial movement", example = "150.75")
        BigDecimal amount,

        @Schema(description = "Amount that has already been paid", example = "100.50")
        @JsonProperty("amount_paid")
        BigDecimal amountPaid,

        @Schema(description = "Date and time when the financial movement was created", example = "2024-11-07T15:30:00", type = "string", format = "date-time")
        @JsonProperty("movement_date")
        LocalDateTime movementDate,

        @Schema(description = "Due date for the financial movement", example = "2024-11-30T23:59:59", type = "string", format = "date-time")
        @JsonProperty("due_date")
        LocalDateTime dueDate,

        @Schema(description = "Date when the payment was made", example = "2024-11-15T10:00:00", type = "string", format = "date-time")
        @JsonProperty("pay_date")
        LocalDateTime payDate,

        @Schema(description = "Status of the financial movement (e.g., PENDING, COMPLETED, CANCELLED)", example = "PENDING")
        FinancialMovementStatus status,

        @Schema(description = "Code of the recurrence bill associated with this movement, if any", example = "123e4567-e89b-12d3-a456-426614174000")
        @JsonProperty("recurrence_bill_code")
        UUID recurrenceBillCode
) {
}
