package com.bts.personalbudget.controller.balance;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DailyBalanceResponse(
        LocalDate date,
        Balance balance,
        Projected projected
) {
    public record Balance(
            BigDecimal opening,
            BigDecimal totalRevenue,
            BigDecimal totalExpense,
            BigDecimal closing) {
    }

    public record Projected(
            BigDecimal opening,
            BigDecimal pendingTotalRevenue,
            BigDecimal pendingTotalExpense,
            BigDecimal closing) {
    }
}
