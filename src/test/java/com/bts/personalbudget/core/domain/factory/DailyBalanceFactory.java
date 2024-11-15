package com.bts.personalbudget.core.domain.factory;

import com.bts.personalbudget.core.domain.service.balance.DailyBalance;
import java.math.BigDecimal;
import java.time.LocalDate;

public class DailyBalanceFactory {
    public static DailyBalance buildDailyBalance(LocalDate date,
                                                 String openingBalance, String totalRevenue, String totalExpense,
                                                 String projectedOpeningBalance, String projectedTotalRevenue, String projectedTotalExpense
    ) {
        return new DailyBalance(date,
                new BigDecimal(openingBalance),
                new BigDecimal(projectedOpeningBalance)
        );
    }
}
