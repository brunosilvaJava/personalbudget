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
        LocalDate competenceDate,
        UUID accountCode,
        boolean passThrough
) implements BalanceCalcData {

    /**
     * Without an account: the service resolves the default one when recording, and a settlement
     * built outside the persistence mapping has none to show.
     */
    public MovementSettlement(final UUID code, final UUID movementCode, final BigDecimal amount,
                              final LocalDate effectiveDate, final LocalDateTime reversedAt, final Long version,
                              final OperationType operationType, final UUID categoryCode,
                              final LocalDate competenceDate) {
        this(code, movementCode, amount, effectiveDate, reversedAt, version, operationType, categoryCode,
                competenceDate, null, false);
    }

    public MovementSettlement(final UUID code, final UUID movementCode, final BigDecimal amount,
                              final LocalDate effectiveDate, final LocalDateTime reversedAt, final Long version,
                              final OperationType operationType, final UUID categoryCode,
                              final LocalDate competenceDate, final UUID accountCode) {
        this(code, movementCode, amount, effectiveDate, reversedAt, version, operationType, categoryCode,
                competenceDate, accountCode, false);
    }

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

    @Override
    public boolean isPassThrough() {
        return passThrough;
    }
}
