package com.bts.personalbudget.core.domain.service.movementsettlement;

import com.bts.personalbudget.core.domain.enumerator.OperationType;
import com.bts.personalbudget.core.domain.service.balance.BalanceCalcData;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record MovementSettlement(
        UUID code,
        UUID movementCode,
        BigDecimal amount,
        LocalDate effectiveDate,
        LocalDateTime reversedAt,
        Long version,
        OperationType operationType,
        UUID categoryCode,
        LocalDate competenceDate
) implements BalanceCalcData {

    @Override
    public LocalDate findBalanceCalcDate() {
        return effectiveDate;
    }

    @Override
    public BigDecimal getBalanceCalcValue() {
        return operationType == OperationType.DEBIT ? amount.negate() : amount;
    }

    @Override
    public PaymentStatus findStatus() {
        return PaymentStatus.DONE;
    }

    @Override
    public OperationType getOperationType() {
        return operationType;
    }

    @Override
    public UUID getCategoryCode() {
        return categoryCode;
    }
}
