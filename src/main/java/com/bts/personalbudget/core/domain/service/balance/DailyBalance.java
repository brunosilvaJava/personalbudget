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
    private BigDecimal passThroughIn;
    private BigDecimal passThroughOut;
    private BigDecimal pendingPassThroughIn;
    private BigDecimal pendingPassThroughOut;

    /** Compatibility constructor used by the existing factories and serialized-contract tests. */
    public DailyBalance(final LocalDate date, final BigDecimal openingBalance, final BigDecimal totalRevenue,
                        final BigDecimal totalExpense, final BigDecimal closingBalance,
                        final BigDecimal projectedOpeningBalance, final BigDecimal pendingTotalRevenue,
                        final BigDecimal pendingTotalExpense, final BigDecimal projectedClosingBalance) {
        this.date = date;
        this.openingBalance = openingBalance;
        this.totalRevenue = totalRevenue;
        this.totalExpense = totalExpense;
        this.closingBalance = closingBalance;
        this.projectedOpeningBalance = projectedOpeningBalance;
        this.pendingTotalRevenue = pendingTotalRevenue;
        this.pendingTotalExpense = pendingTotalExpense;
        this.projectedClosingBalance = projectedClosingBalance;
        verifyValues();
    }

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

    public void addPassThroughIn(final BigDecimal value) {
        verifyValues();
        passThroughIn = passThroughIn.add(value);
        defineClosingBalance();
        defineProjectedClosingBalance();
    }

    public void addPassThroughOut(final BigDecimal value) {
        verifyValues();
        passThroughOut = passThroughOut.add(value);
        defineClosingBalance();
        defineProjectedClosingBalance();
    }

    private void defineClosingBalance() {
        closingBalance = openingBalance.add(totalRevenue.add(totalExpense).add(passThroughIn).add(passThroughOut));
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

    public void addProjectedPassThroughIn(final BigDecimal value) {
        verifyValues();
        pendingPassThroughIn = pendingPassThroughIn.add(value);
        defineProjectedClosingBalance();
    }

    public void addProjectedPassThroughOut(final BigDecimal value) {
        verifyValues();
        pendingPassThroughOut = pendingPassThroughOut.add(value);
        defineProjectedClosingBalance();
    }

    private void defineProjectedClosingBalance() {
        final BigDecimal balanceDay = totalRevenue.add(totalExpense).add(passThroughIn).add(passThroughOut);
        projectedClosingBalance = balanceDay.add(projectedOpeningBalance.add(
                pendingTotalRevenue.add(pendingTotalExpense).add(pendingPassThroughIn).add(pendingPassThroughOut)));
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
        if (passThroughIn == null) passThroughIn = BigDecimal.ZERO;
        if (passThroughOut == null) passThroughOut = BigDecimal.ZERO;
        if (pendingPassThroughIn == null) pendingPassThroughIn = BigDecimal.ZERO;
        if (pendingPassThroughOut == null) pendingPassThroughOut = BigDecimal.ZERO;
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
