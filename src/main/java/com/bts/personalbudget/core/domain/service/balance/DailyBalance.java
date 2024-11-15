package com.bts.personalbudget.core.domain.service.balance;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

@Getter
@RequiredArgsConstructor
@AllArgsConstructor
public class DailyBalance {

    private final LocalDate date;
    private final BigDecimal openingBalance;
    private BigDecimal totalRevenue;
    private BigDecimal totalExpense;
    private final BigDecimal projectedOpeningBalance;
    private BigDecimal projectedTotalRevenue;
    private BigDecimal projectedTotalExpense;

    {
        verifyValues();
    }

    public void addRevenue(final BigDecimal value) {
        verifyValues();
        totalRevenue = totalRevenue.add(value);
    }

    public void addExpense(final BigDecimal value) {
        verifyValues();
        totalExpense = totalExpense.add(value);
    }

    public BigDecimal getClosingBalance() {
        verifyValues();
        return openingBalance.add(totalRevenue.subtract(totalExpense));
    }

    public BigDecimal getProjectedBalance() {
        verifyValues();
        final BigDecimal finalBalance = totalRevenue.subtract(totalExpense);
        final BigDecimal projectedBalance = projectedTotalRevenue.subtract(projectedTotalExpense);
        return projectedOpeningBalance.add(finalBalance.add(projectedBalance));
    }

    private void verifyValues() {
        if (totalRevenue == null) {
            totalRevenue = BigDecimal.ZERO;
        }
        if (totalExpense == null) {
            totalExpense = BigDecimal.ZERO;
        }
        if (projectedTotalRevenue == null) {
            projectedTotalRevenue = BigDecimal.ZERO;
        }
        if (projectedTotalExpense == null) {
            projectedTotalExpense = BigDecimal.ZERO;
        }
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DailyBalance that = (DailyBalance) o;
        return Objects.equals(date, that.date);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(date);
    }
}
