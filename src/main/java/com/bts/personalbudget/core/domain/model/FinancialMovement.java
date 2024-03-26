package com.bts.personalbudget.core.domain.model;

import com.bts.personalbudget.core.domain.enumerator.FinancialMovementStatus;
import com.bts.personalbudget.core.domain.enumerator.OperationType;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@RequiredArgsConstructor
public class FinancialMovement {

    private final Integer id;
    private final String operationType;
    private final OperationType description;
    private final BigDecimal amount;
    private final BigDecimal amountPaid;
    private final LocalDateTime movementDate;
    private final LocalDateTime dueDate;
    private final LocalDateTime payDate;
    private final FinancialMovementStatus status;
    private final Boolean flagActive;
    

}
