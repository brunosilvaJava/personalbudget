package com.bts.personalbudget.core.domain.service.account;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Balance of one account on a date: the initial balance (when the date is not before it) plus the
 * signed active settlements of the account from the initial balance date through the date.
 */
public record AccountBalance(
        UUID accountCode,
        LocalDate date,
        BigDecimal initialBalance,
        BigDecimal settlementsTotal,
        BigDecimal balance
) {
}
