package com.bts.personalbudget.core.domain.service.balance;

import com.bts.personalbudget.core.domain.enumerator.OperationType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One future occurrence of a FixedBill or InstallmentBill that has not been posted as a
 * FinancialMovement yet. Exists only for the balance projection: it lets a bill contribute every
 * occurrence in the period, instead of the bill itself contributing just its next date.
 */
public record ProjectedOccurrence(
        UUID recurrenceBillCode,
        LocalDate occurrenceDate,
        BigDecimal balanceCalcValue,
        OperationType operationType,
        UUID categoryCode
) implements BalanceCalcData {

    /**
     * @param recurrenceBill the FixedBill/InstallmentBill this is an occurrence of; its sign
     *                       convention (debits negative) and its category are kept
     */
    public static ProjectedOccurrence of(final UUID recurrenceBillCode,
                                         final BalanceCalcData recurrenceBill,
                                         final LocalDate occurrenceDate) {
        return new ProjectedOccurrence(recurrenceBillCode, occurrenceDate,
                recurrenceBill.getBalanceCalcValue(), recurrenceBill.getOperationType(),
                recurrenceBill.getCategoryCode());
    }

    @Override
    public LocalDate findBalanceCalcDate() {
        return occurrenceDate;
    }

    @Override
    public BigDecimal getBalanceCalcValue() {
        return balanceCalcValue;
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
    public PaymentStatus findStatus() {
        return PaymentStatus.PENDING;
    }
}
