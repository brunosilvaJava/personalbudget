package com.bts.personalbudget.core.domain.service.account;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Balance of every account on a date and their sum. Transfers cancel out in the total, so it
 * only moves with settlements and initial balances. INACTIVE accounts are included because the
 * money in them still exists.
 */
public record AccountsBalance(
        LocalDate date,
        List<AccountBalance> accounts,
        BigDecimal total
) {
}
