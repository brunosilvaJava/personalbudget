package com.bts.personalbudget.core.domain.factory;

import com.bts.personalbudget.core.domain.service.balance.DailyBalance;
import java.math.BigDecimal;
import java.time.LocalDate;

public class DailyBalanceFactory {
    public static DailyBalance buildDailyBalance(LocalDate date,
                                                 String openingBalance,
                                                 String projectedOpeningBalance) {
        return new DailyBalance(date,
                new BigDecimal(openingBalance),
                new BigDecimal(projectedOpeningBalance)
        );
    }

    public static DailyBalance buildDailyBalance(LocalDate date,
                                                 String openingBalance,
                                                 String totalRevenue,
                                                 String totalExpense,
                                                 String closingBalance,
                                                 String projectedOpeningBalance,
                                                 String pendingTotalRevenue,
                                                 String pendingTotalExpense,
                                                 String projectedClosingBalance
    ) {
        return new DailyBalance(date,
                new BigDecimal(openingBalance),
                new BigDecimal(totalRevenue),
                new BigDecimal(totalExpense),
                new BigDecimal(closingBalance),
                new BigDecimal(projectedOpeningBalance),
                new BigDecimal(pendingTotalRevenue),
                new BigDecimal(pendingTotalExpense),
                new BigDecimal(projectedClosingBalance)
        );
    }

    public static DailyBalance buildDailyBalance(LocalDate date,
                                                 String openingBalance, String totalRevenue, String totalExpense,
                                                 String projectedOpeningBalance, String projectedTotalRevenue, String projectedTotalExpense
    ) {
        DailyBalance dailyBalance = new DailyBalance(date,
                new BigDecimal(openingBalance),
                new BigDecimal(projectedOpeningBalance)
        );
        dailyBalance.addRevenue(new BigDecimal(totalRevenue));
        dailyBalance.addExpense(new BigDecimal(totalExpense));
        return dailyBalance;
    }
}
