package com.bts.personalbudget.core.domain.service.dashboard;

import com.bts.personalbudget.core.domain.service.balance.BalanceService;
import com.bts.personalbudget.core.domain.service.balance.DailyBalance;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.Optional;
import java.util.Set;

@Slf4j
@RequiredArgsConstructor
@Service
public class DashboardService {

    private final BalanceService balanceService;

    /**
     * Busca dados do dashboard para o mês atual
     */
    public Dashboard getDashboard() {
        log.info("m=getDashboard");

        final LocalDate today = LocalDate.now();
        final YearMonth currentMonth = YearMonth.from(today);

        return getDashboardForMonth(currentMonth);
    }

    /**
     * Busca dados do dashboard para um mês específico
     */
    public Dashboard getDashboardForMonth(final YearMonth yearMonth) {
        log.info("m=getDashboardForMonth, yearMonth={}", yearMonth);

        final LocalDate firstDayOfMonth = yearMonth.atDay(1);
        final LocalDate lastDayOfMonth = yearMonth.atEndOfMonth();
        final LocalDate today = LocalDate.now();

        // Buscar balanço diário do mês inteiro
        final Set<DailyBalance> dailyBalances = balanceService.findDailyBalanceBetween(
                firstDayOfMonth,
                lastDayOfMonth
        );

        // Buscar saldo atual (hoje) se não estiver no período
        DailyBalance currentDayBalance = null;
        if (today.isBefore(firstDayOfMonth) || today.isAfter(lastDayOfMonth)) {
            Set<DailyBalance> todayBalance = balanceService.findDailyBalanceBetween(today, today);
            currentDayBalance = todayBalance.isEmpty() ? null : todayBalance.iterator().next();
        } else {
            currentDayBalance = dailyBalances.stream()
                    .filter(db -> db.getDate().equals(today))
                    .findFirst()
                    .orElse(null);
        }

        return buildDashboard(dailyBalances, currentDayBalance, yearMonth, today);
    }

    /**
     * Constrói o objeto Dashboard com os dados calculados
     */
    private Dashboard buildDashboard(
            final Set<DailyBalance> dailyBalances,
            final DailyBalance currentDayBalance,
            final YearMonth yearMonth,
            final LocalDate today
    ) {
        // Saldos atuais (hoje)
        final BigDecimal currentBalance = currentDayBalance != null
                ? currentDayBalance.getClosingBalance()
                : BigDecimal.ZERO;

        final BigDecimal currentProjectedBalance = currentDayBalance != null
                ? currentDayBalance.getProjectedClosingBalance()
                : BigDecimal.ZERO;

        // Último dia do mês
        final Optional<DailyBalance> lastDayOptional = dailyBalances.stream()
                .max(Comparator.comparing(DailyBalance::getDate));

        final BigDecimal monthClosingBalance = lastDayOptional
                .map(DailyBalance::getClosingBalance)
                .orElse(BigDecimal.ZERO);

        final BigDecimal monthProjectedClosingBalance = lastDayOptional
                .map(DailyBalance::getProjectedClosingBalance)
                .orElse(BigDecimal.ZERO);

        // Somar receitas e despesas do mês
        final BigDecimal monthTotalRevenue = dailyBalances.stream()
                .map(db -> db.getTotalRevenue())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        final BigDecimal monthTotalExpense = dailyBalances.stream()
                .map(db -> db.getTotalExpense())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Receitas e despesas projetadas (incluindo pendentes)
        final BigDecimal monthProjectedTotalRevenue = dailyBalances.stream()
                .map(db -> db.getTotalRevenue().add(db.getPendingTotalRevenue()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        final BigDecimal monthProjectedTotalExpense = dailyBalances.stream()
                .map(db -> db.getTotalExpense().add(db.getPendingTotalExpense()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Verificar se haverá saldo negativo no mês
        final Optional<DailyBalance> firstNegativeBalance = dailyBalances.stream()
                .filter(db -> db.getProjectedClosingBalance().compareTo(BigDecimal.ZERO) < 0)
                .min(Comparator.comparing(DailyBalance::getDate));

        final boolean hasNegativeBalanceAlert = firstNegativeBalance.isPresent();
        final LocalDate firstNegativeBalanceDate = firstNegativeBalance
                .map(DailyBalance::getDate)
                .orElse(null);

        // Encontrar menor saldo projetado do mês
        final Optional<DailyBalance> lowestBalanceOptional = dailyBalances.stream()
                .min(Comparator.comparing(DailyBalance::getProjectedClosingBalance));

        final BigDecimal lowestProjectedBalance = lowestBalanceOptional
                .map(DailyBalance::getProjectedClosingBalance)
                .orElse(BigDecimal.ZERO);

        final LocalDate lowestBalanceDate = lowestBalanceOptional
                .map(DailyBalance::getDate)
                .orElse(null);

        final LocalDate firstDayOfMonth = yearMonth.atDay(1);
        final LocalDate lastDayOfMonth = yearMonth.atEndOfMonth();
        final int totalDaysInMonth = yearMonth.lengthOfMonth();

        final int daysElapsed = today.isBefore(firstDayOfMonth) ? 0
                : today.isAfter(lastDayOfMonth) ? totalDaysInMonth
                : today.getDayOfMonth();

        final int daysRemaining = totalDaysInMonth - daysElapsed;

        final BigDecimal monthNetChange = monthTotalRevenue.add(monthTotalExpense);
        final BigDecimal projectedNetChange = monthProjectedTotalRevenue.add(monthProjectedTotalExpense);

        return Dashboard.builder()
                .currentBalance(currentBalance)
                .currentProjectedBalance(currentProjectedBalance)
                .monthClosingBalance(monthClosingBalance)
                .monthProjectedClosingBalance(monthProjectedClosingBalance)
                .monthTotalRevenue(monthTotalRevenue)
                .monthProjectedTotalRevenue(monthProjectedTotalRevenue)
                .monthTotalExpense(monthTotalExpense)
                .monthProjectedTotalExpense(monthProjectedTotalExpense)
                .hasNegativeBalanceAlert(hasNegativeBalanceAlert)
                .firstNegativeBalanceDate(firstNegativeBalanceDate)
                .lowestProjectedBalance(lowestProjectedBalance)
                .lowestBalanceDate(lowestBalanceDate)
                .referenceMonth(firstDayOfMonth)
                .currentDate(today)
                .totalDaysInMonth(totalDaysInMonth)
                .daysElapsed(daysElapsed)
                .daysRemaining(daysRemaining)
                .monthNetChange(monthNetChange)
                .projectedNetChange(projectedNetChange)
                .build();
    }
}