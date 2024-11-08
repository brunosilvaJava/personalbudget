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
    private BigDecimal totalRevenue;
    private BigDecimal totalExpense;
    private BigDecimal projectedBalance;

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

    public BigDecimal calcFinalBalance() {
        verifyValues();
        return totalRevenue.subtract(totalExpense);
    }

    private void verifyValues() {
        if (totalRevenue == null) {
            totalRevenue = BigDecimal.ZERO;
        }
        if (totalExpense == null) {
            totalExpense = BigDecimal.ZERO;
        }
    }

    @Override
    public boolean equals(Object o) {
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
