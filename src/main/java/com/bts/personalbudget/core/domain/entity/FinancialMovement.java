package com.bts.personalbudget.core.domain.entity;

import com.bts.personalbudget.core.domain.enumerator.FinancialMovementStatus;
import com.bts.personalbudget.core.domain.enumerator.OperationType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record FinancialMovement(
        UUID code,
        OperationType operationType,
        String description,
        BigDecimal amount,
        BigDecimal amountPaid,
        LocalDateTime movementDate,
        LocalDateTime dueDate,
        LocalDateTime payDate,
        FinancialMovementStatus status,
        Boolean flagActive
) {
}
