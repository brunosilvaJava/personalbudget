package com.bts.personalbudget.core.domain.factory;

import com.bts.personalbudget.core.domain.service.balance.DailyBalance;
import java.math.BigDecimal;
import java.time.LocalDate;

public class DailyBalanceFactory {
    public static DailyBalance buildDailyBalance(LocalDate yesterday, String previousBalance, String totalRevenue, String totalExpense) {
        return new DailyBalance(yesterday,
                new BigDecimal(previousBalance),
                new BigDecimal(totalRevenue),
                new BigDecimal(totalExpense));
    }
}
