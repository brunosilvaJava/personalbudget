package com.bts.personalbudget.core.domain.enumerator;

import java.time.LocalDate;

/**
 * Derived, never stored: OPEN before the closing date, CLOSED from the closing date on, PAID when
 * the invoice has installments and every one of them is paid.
 */
public enum CardInvoiceState {
    OPEN,
    CLOSED,
    PAID;

    public static CardInvoiceState of(final LocalDate closingDate, final LocalDate today, final boolean paid) {
        if (paid) {
            return PAID;
        }
        return today.isBefore(closingDate) ? OPEN : CLOSED;
    }
}
