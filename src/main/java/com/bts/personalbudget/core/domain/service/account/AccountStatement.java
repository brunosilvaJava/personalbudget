package com.bts.personalbudget.core.domain.service.account;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * What moved an account in a period, line by line, with the running balance. Only active
 * settlements and transfers count, as in {@link AccountBalance}: reversed ones are left out.
 *
 * @param openingBalance the balance at the end of the day before startDate (zero before the initial
 *                       balance date)
 * @param closingBalance the balance on endDate; equals openingBalance plus the sum of the entries
 */
public record AccountStatement(
        UUID accountCode,
        LocalDate startDate,
        LocalDate endDate,
        BigDecimal openingBalance,
        BigDecimal closingBalance,
        List<AccountStatementEntry> entries
) {
}
