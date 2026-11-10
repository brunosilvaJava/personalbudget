package com.bts.personalbudget.core.domain.service.forecast;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * The cash forecast from {@code today} through {@code until}. Every component is positive and
 * the sets are disjoint: a card installment is only in {@code cardInvoices} (or in
 * {@code cardBeyondHorizon} when due after the horizon), never in {@code obligations}; an income
 * posted as a movement is never also in {@code projectedIncome}; the envelope reserve is what is
 * still free in the envelopes, so what the cycle already owes is not in it.
 *
 * <pre>
 * projectedCash       = cashBalance + expectedIncome (+ lateIncome when included)
 *                       - obligations - cardInvoices
 * freeAfterReserves   = projectedCash - envelopeReserve - cardBeyondHorizon
 * </pre>
 *
 * <p>Late income is not counted as sure money unless the caller says so: {@code includeLateIncome}
 * states the assumption used. Overdue obligations and invoices stay in the debt.
 *
 * <p>Two alerts, kept apart: {@code negativeCashAlert} (the projected cash goes below zero on some
 * day up to the horizon) and {@code insufficientBudgetAlert} (the cash is not enough to cover the
 * reserves and the card still committed).
 */
public record CashForecast(
        LocalDate today,
        LocalDate until,
        boolean includeLateIncome,
        BigDecimal cashBalance,
        BigDecimal expectedIncome,
        BigDecimal lateIncome,
        BigDecimal obligations,
        BigDecimal cardInvoices,
        BigDecimal projectedCash,
        UUID envelopeCycleCode,
        BigDecimal envelopeReserve,
        BigDecimal cardBeyondHorizon,
        BigDecimal freeAfterReserves,
        BigDecimal lowestProjectedCash,
        LocalDate lowestProjectedCashDate,
        LocalDate firstNegativeDate,
        boolean negativeCashAlert,
        boolean insufficientBudgetAlert,
        boolean projectionIncomplete,
        BigDecimal receivables
) {
    public CashForecast(final LocalDate today, final LocalDate until, final boolean includeLateIncome,
                        final BigDecimal cashBalance, final BigDecimal expectedIncome, final BigDecimal lateIncome,
                        final BigDecimal obligations, final BigDecimal cardInvoices, final BigDecimal projectedCash,
                        final UUID envelopeCycleCode, final BigDecimal envelopeReserve, final BigDecimal cardBeyondHorizon,
                        final BigDecimal freeAfterReserves, final BigDecimal lowestProjectedCash,
                        final LocalDate lowestProjectedCashDate, final LocalDate firstNegativeDate,
                        final boolean negativeCashAlert, final boolean insufficientBudgetAlert,
                        final boolean projectionIncomplete) {
        this(today, until, includeLateIncome, cashBalance, expectedIncome, lateIncome, obligations, cardInvoices,
                projectedCash, envelopeCycleCode, envelopeReserve, cardBeyondHorizon, freeAfterReserves,
                lowestProjectedCash, lowestProjectedCashDate, firstNegativeDate, negativeCashAlert,
                insufficientBudgetAlert, projectionIncomplete, BigDecimal.ZERO);
    }
}
