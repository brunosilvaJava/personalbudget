package com.bts.personalbudget.core.domain.service.forecast;

import com.bts.personalbudget.core.domain.enumerator.OperationType;
import com.bts.personalbudget.core.domain.model.FinancialMovement;
import com.bts.personalbudget.core.domain.service.FinancialMovementService;
import com.bts.personalbudget.core.domain.service.account.AccountService;
import com.bts.personalbudget.core.domain.service.balance.BalanceCalcData;
import com.bts.personalbudget.core.domain.service.balance.BalanceService;
import com.bts.personalbudget.core.domain.service.balance.DailyBalance;
import com.bts.personalbudget.core.domain.service.balance.ProjectedOccurrence;
import com.bts.personalbudget.core.domain.service.budgetenvelope.BudgetEnvelopeService;
import com.bts.personalbudget.core.domain.service.budgetenvelope.CycleEnvelopes;
import com.bts.personalbudget.core.domain.service.paycycle.PayCycleService;
import com.bts.personalbudget.core.domain.service.paycycle.PayCycleView;
import com.bts.personalbudget.exception.InvalidFieldsException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The complete cash forecast (E11), built from the accounts (E8), the card installments (E9) and
 * the envelopes (E10), with the components kept apart so each number can be checked.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class CashForecastService {

    static final int MAX_HORIZON_DAYS = 366;

    private final AccountService accountService;
    private final FinancialMovementService movementService;
    private final BalanceService balanceService;
    private final PayCycleService payCycleService;
    private final BudgetEnvelopeService envelopeService;
    private final Clock clock;

    /**
     * @param until             last day included; when null, the last day of the current cycle, or
     *                          of the current month when there is no current cycle
     * @param includeLateIncome whether overdue income counts as money that will arrive
     * @throws InvalidFieldsException (400) when {@code until} is before today or more than 366 days ahead
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public CashForecast forecast(final LocalDate until, final boolean includeLateIncome) {
        log.info("m=forecast, until={}, includeLateIncome={}", until, includeLateIncome);
        final LocalDate today = LocalDate.now(clock);
        final PayCycleView currentCycle = payCycleService.findCurrent().cycle();
        final LocalDate horizon = until != null ? until : defaultHorizon(today, currentCycle);
        validateHorizon(today, horizon);

        final BigDecimal cashBalance = accountService.balances(today).total();
        final List<FinancialMovement> open = movementService.findOpenDueThrough(horizon);
        final List<ProjectedOccurrence> projected = balanceService.findBalanceCalcDataBetween(today, horizon).stream()
                .filter(ProjectedOccurrence.class::isInstance).map(ProjectedOccurrence.class::cast).toList();

        final Predicate<FinancialMovement> isReceivable = movement -> movement.receivableSourceCode() != null;
        final BigDecimal expectedIncome = sum(open, isCredit().and(isReceivable.negate())
                .and(movement -> !isOverdue(movement, today)))
                .add(sumProjected(projected, OperationType.CREDIT));
        final BigDecimal lateIncome = sum(open, isCredit().and(isReceivable.negate())
                .and(movement -> isOverdue(movement, today)));
        final BigDecimal receivables = sum(open, isReceivable);
        final BigDecimal obligations = sum(open, isDebit().and(movement -> !isCardInstallment(movement)))
                .add(sumProjected(projected, OperationType.DEBIT));
        final BigDecimal cardInvoices = sum(open, isDebit().and(CashForecastService::isCardInstallment));
        final BigDecimal countedLateIncome = includeLateIncome ? lateIncome : BigDecimal.ZERO;
        final BigDecimal projectedCash = cashBalance.add(expectedIncome).add(countedLateIncome).add(receivables)
                .subtract(obligations).subtract(cardInvoices);

        final CycleEnvelopes envelopes = currentCycle == null ? null
                : envelopeService.find(currentCycle.cycle().code());
        final BigDecimal envelopeReserve = envelopes == null ? BigDecimal.ZERO : envelopes.totalReserved();
        final BigDecimal cardBeyondHorizon = movementService.findOpenCardInstallmentsDueAfter(horizon).stream()
                .map(CashForecastService::residual).reduce(BigDecimal.ZERO, BigDecimal::add);
        final BigDecimal freeAfterReserves = projectedCash.subtract(envelopeReserve).subtract(cardBeyondHorizon);

        final Lowest lowest = lowest(today, horizon, cashBalance, includeLateIncome ? BigDecimal.ZERO : lateIncome);
        return new CashForecast(today, horizon, includeLateIncome, cashBalance, expectedIncome, lateIncome,
                obligations, cardInvoices, projectedCash,
                currentCycle == null ? null : currentCycle.cycle().code(), envelopeReserve, cardBeyondHorizon,
                freeAfterReserves, lowest.value(), lowest.date(), lowest.firstNegativeDate(),
                lowest.firstNegativeDate() != null, freeAfterReserves.signum() < 0,
                envelopes != null && envelopes.projectionIncomplete(), receivables);
    }

    private record Lowest(BigDecimal value, LocalDate date, LocalDate firstNegativeDate) {
    }

    /**
     * The lowest projected cash over the days up to the horizon. The daily projection counts every
     * unpaid credit (overdue ones too), so the late income is taken out unless it is included, and
     * the account balances (initial balances included) replace the settlements-only opening.
     */
    private Lowest lowest(final LocalDate today, final LocalDate horizon, final BigDecimal cashBalance,
                          final BigDecimal lateIncomeToTakeOut) {
        final List<DailyBalance> days = balanceService.findDailyBalanceBetween(today, horizon).stream().toList();
        final BigDecimal offset = cashBalance.subtract(days.get(0).getClosingBalance())
                .subtract(lateIncomeToTakeOut);
        final Map<LocalDate, BigDecimal> cashByDay = new java.util.LinkedHashMap<>();
        days.forEach(day -> cashByDay.put(day.getDate(), day.getProjectedClosingBalance().add(offset)));
        final Map.Entry<LocalDate, BigDecimal> lowest = cashByDay.entrySet().stream()
                .min(Map.Entry.<LocalDate, BigDecimal>comparingByValue().thenComparing(Map.Entry.comparingByKey()))
                .orElseThrow();
        final LocalDate firstNegative = cashByDay.entrySet().stream()
                .filter(entry -> entry.getValue().signum() < 0).map(Map.Entry::getKey)
                .min(Comparator.naturalOrder()).orElse(null);
        return new Lowest(lowest.getValue(), lowest.getKey(), firstNegative);
    }

    private static LocalDate defaultHorizon(final LocalDate today, final PayCycleView currentCycle) {
        return currentCycle != null
                ? currentCycle.cycle().endDate().minusDays(1)
                : YearMonth.from(today).atEndOfMonth();
    }

    private static void validateHorizon(final LocalDate today, final LocalDate horizon) {
        if (horizon.isBefore(today)) {
            throw new InvalidFieldsException("Campos inválidos", CashForecast.class,
                    Map.of("until", "não pode ser anterior a hoje"));
        }
        if (ChronoUnit.DAYS.between(today, horizon) > MAX_HORIZON_DAYS) {
            throw new InvalidFieldsException("Campos inválidos", CashForecast.class,
                    Map.of("until", "no máximo " + MAX_HORIZON_DAYS + " dias à frente"));
        }
    }

    private static Predicate<FinancialMovement> isCredit() {
        return movement -> movement.operationType() == OperationType.CREDIT;
    }

    private static Predicate<FinancialMovement> isDebit() {
        return movement -> movement.operationType() == OperationType.DEBIT;
    }

    private static boolean isCardInstallment(final FinancialMovement movement) {
        return movement.installmentNumber() != null;
    }

    private static boolean isOverdue(final FinancialMovement movement, final LocalDate today) {
        return movement.dueDate().toLocalDate().isBefore(today);
    }

    /** What is still owed on the movement, positive. */
    private static BigDecimal residual(final FinancialMovement movement) {
        return movement.getBalanceCalcValue().abs();
    }

    private static BigDecimal sum(final List<FinancialMovement> movements, final Predicate<FinancialMovement> filter) {
        return movements.stream().filter(filter).map(CashForecastService::residual)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal sumProjected(final List<ProjectedOccurrence> occurrences,
                                           final OperationType operationType) {
        return occurrences.stream().filter(occurrence -> occurrence.getOperationType() == operationType)
                .map(BalanceCalcData::getBalanceCalcValue).map(BigDecimal::abs)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
