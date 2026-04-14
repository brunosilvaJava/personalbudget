package com.bts.personalbudget.core.domain.service.dashboard;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;

@Builder
public record Dashboard(
        BigDecimal currentBalance,
        BigDecimal currentProjectedBalance,
        BigDecimal monthClosingBalance,
        BigDecimal monthProjectedClosingBalance,
        BigDecimal monthTotalRevenue,
        BigDecimal monthProjectedTotalRevenue,
        BigDecimal monthTotalExpense,
        BigDecimal monthProjectedTotalExpense,
        boolean hasNegativeBalanceAlert,
        LocalDate firstNegativeBalanceDate,
        BigDecimal lowestProjectedBalance,
        LocalDate lowestBalanceDate,
        LocalDate referenceMonth,
        LocalDate currentDate,
        Integer totalDaysInMonth,
        Integer daysElapsed,
        Integer daysRemaining,
        BigDecimal monthNetChange,
        BigDecimal projectedNetChange
) {}