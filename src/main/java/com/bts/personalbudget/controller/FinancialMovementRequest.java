package com.bts.personalbudget.controller;

import com.bts.personalbudget.core.domain.enumerator.FinancialMovementStatus;
import com.bts.personalbudget.core.domain.enumerator.OperationType;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record FinancialMovementRequest(
        @NotNull
        @JsonProperty("operation_type")
        OperationType operationType,
        UUID code,
        @Size(min = 4, max = 50)
        @NotBlank
        String description,
        @NotNull
        @DecimalMin(value = "0.0", inclusive = false)
        @Digits(integer = 8, fraction = 2)
        BigDecimal amount,
        @DecimalMin(value = "0.0", inclusive = false)
        @Digits(integer = 8, fraction = 2)
        @JsonProperty("amount_paid")
        BigDecimal amountPaid,
        @NotNull
        @JsonProperty("movement_date")
        LocalDateTime movementDate,
        @NotNull
        @JsonProperty("due_date")
        LocalDateTime dueDate,
        @JsonProperty("pay_date")
        LocalDateTime payDate,
        @NotNull
        FinancialMovementStatus status
) {
}
