package com.bts.personalbudget.controller.financialmovement;

import com.bts.personalbudget.core.domain.enumerator.OperationType;
import com.bts.personalbudget.core.domain.enumerator.FinancialMovementStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Schema(description = "Data for creating a new unpaid financial movement. Payments are recorded as settlements.")
public record FinancialMovementRequest(

        @NotNull
        @Schema(description = "Type of operation (DEBIT or CREDIT)", example = "DEBIT")
        @JsonProperty("operation_type")
        OperationType operationType,

        @Schema(description = "Unique identifier for the financial movement", example = "123e4567-e89b-12d3-a456-426614174000")
        UUID code,

        @Size(min = 4, max = 50)
        @NotBlank
        @Schema(description = "Description of the financial movement", example = "Monthly subscription payment")
        String description,

        @NotNull
        @DecimalMin(value = "0.01", inclusive = false)
        @Digits(integer = 8, fraction = 2)
        @Schema(description = "Total amount of the financial movement", example = "150.75")
        BigDecimal amount,

        @NotNull
        @JsonProperty("movement_date")
        @Schema(description = "Date and time when the financial movement was created", example = "2024-11-07T15:30:00", type = "string", format = "date-time")
        LocalDateTime movementDate,

        @NotNull
        @JsonProperty("due_date")
        @Schema(description = "Due date for the financial movement", example = "2024-11-30T23:59:59", type = "string", format = "date-time")
        LocalDateTime dueDate,

        @Null(message = "Use the settlements endpoints to record payments; payment fields and status are not accepted here")
        @JsonProperty("amount_paid")
        @Schema(hidden = true)
        BigDecimal legacyAmountPaid,

        @Null(message = "Use the settlements endpoints to record payments; payment fields and status are not accepted here")
        @JsonProperty("pay_date")
        @Schema(hidden = true)
        LocalDateTime legacyPayDate,

        @Null(message = "Use the settlements endpoints to record payments; payment fields and status are not accepted here")
        @JsonProperty("status")
        @Schema(hidden = true)
        FinancialMovementStatus legacyStatus,

        @Schema(description = "Code of the category. Must be an ACTIVE category of the same operation type", example = "123e4567-e89b-12d3-a456-426614174000")
        @JsonProperty("category_code")
        UUID categoryCode,

        @Schema(description = "Budget competence date. Optional: when informed it is manual and never recalculated; when omitted it is automatic (the date of movement_date)", example = "2024-11-01", type = "string", format = "date")
        @JsonProperty("competence_date")
        LocalDate competenceDate
) {
}
