package com.bts.personalbudget.core.domain.model;

import com.bts.personalbudget.core.domain.enumerator.FinancialMovementStatus;
import com.bts.personalbudget.core.domain.enumerator.OperationType;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public class FinancialMovementEntityFactory {

    public static FinancialMovementEntity build() {
        final LocalDateTime now = LocalDateTime.now();
        FinancialMovementEntity financialMovementEntity = FinancialMovementEntity.builder()
                .operationType(OperationType.DEBIT)
                .description("Testando")
                .amount(BigDecimal.ONE)
                .amountPaid(BigDecimal.ONE)
                .movementDate(now)
                .dueDate(now)
                .payDate(now)
                .status(FinancialMovementStatus.PAID_OUT)
                .build();
        financialMovementEntity.prePersist();
        return financialMovementEntity;
    }

}
