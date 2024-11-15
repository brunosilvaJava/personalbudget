package com.bts.personalbudget.controller.financialmovement;


import com.bts.personalbudget.core.domain.enumerator.FinancialMovementStatus;
import com.bts.personalbudget.core.domain.enumerator.OperationType;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import static java.math.BigDecimal.ZERO;

public record FinancialMovementResponse(
        UUID code,
        @JsonProperty("operation_type")
        OperationType operationType,
        String description,
        BigDecimal amount,
        @JsonProperty("amount_paid")
        BigDecimal amountPaid,
        @JsonProperty("movement_date")
        LocalDateTime movementDate,
        @JsonProperty("due_date")
        LocalDateTime dueDate,
        @JsonProperty("pay_date")
        LocalDateTime payDate,
        FinancialMovementStatus status,
        @JsonProperty("recurrence_bill_code")
        UUID recurrenceBillCode
) {
    public FinancialMovementResponse {
        if (operationType == OperationType.DEBIT) {
            if (amount != null && amount.compareTo(ZERO) < 0) {
                amount = amount.negate();
            }
            if (amountPaid != null && amountPaid.compareTo(ZERO) < 0) {
                amountPaid = amountPaid.negate();
            }
        }
    }
}

