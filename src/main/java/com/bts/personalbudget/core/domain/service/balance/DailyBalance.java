package com.bts.personalbudget.core.domain.service.balance;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

@Getter
@AllArgsConstructor
public class DailyBalance {

    private final LocalDate date;
    private final BigDecimal openingBalance;
    private BigDecimal totalRevenue;
    private BigDecimal totalExpense;
    private BigDecimal closingBalance;
    private final BigDecimal projectedOpeningBalance;
    private BigDecimal pendingTotalRevenue;
    private BigDecimal pendingTotalExpense;
    private BigDecimal projectedClosingBalance;

    public DailyBalance(LocalDate date, BigDecimal openingBalance, BigDecimal projectedOpeningBalance) {
        this.date = date;
        this.openingBalance = openingBalance;
        this.projectedOpeningBalance = projectedOpeningBalance;
        verifyValues();
        defineClosingBalance();
        defineProjectedClosingBalance();
    }

    public void addRevenue(final BigDecimal value) {
        verifyValues();
        totalRevenue = totalRevenue.add(value);
        defineClosingBalance();
        defineProjectedClosingBalance();
    }

    public void addExpense(final BigDecimal value) {
        verifyValues();
        totalExpense = totalExpense.add(value);
        defineClosingBalance();
        defineProjectedClosingBalance();
    }

    private void defineClosingBalance() {
        closingBalance = openingBalance.add(totalRevenue.add(totalExpense));
    }

    public void addProjectedRevenue(final BigDecimal value) {
        verifyValues();
        pendingTotalRevenue = pendingTotalRevenue.add(value);
        defineProjectedClosingBalance();
    }

    public void addProjectedExpense(final BigDecimal value) {
        verifyValues();
        pendingTotalExpense = pendingTotalExpense.add(value);
        defineProjectedClosingBalance();
    }

    private void defineProjectedClosingBalance() {
        final BigDecimal balanceDay = totalRevenue.add(totalExpense);
        projectedClosingBalance = balanceDay.add(projectedOpeningBalance.add(pendingTotalRevenue.add(pendingTotalExpense)));
    }

    private void verifyValues() {
        if (closingBalance == null) {
            closingBalance = BigDecimal.ZERO;
        }
        if (projectedClosingBalance == null) {
            projectedClosingBalance = BigDecimal.ZERO;
        }
        if (totalRevenue == null) {
            totalRevenue = BigDecimal.ZERO;
        }
        if (totalExpense == null) {
            totalExpense = BigDecimal.ZERO;
        }
        if (pendingTotalRevenue == null) {
            pendingTotalRevenue = BigDecimal.ZERO;
        }
        if (pendingTotalExpense == null) {
            pendingTotalExpense = BigDecimal.ZERO;
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
