package com.bts.personalbudget.core.domain.service.forecast;

import com.bts.personalbudget.core.domain.enumerator.FinancialMovementStatus;
import com.bts.personalbudget.core.domain.enumerator.OperationType;
import com.bts.personalbudget.core.domain.enumerator.PayCycleStatus;
import com.bts.personalbudget.core.domain.factory.PayCycleFactory;
import com.bts.personalbudget.core.domain.model.FinancialMovement;
import com.bts.personalbudget.core.domain.service.FinancialMovementService;
import com.bts.personalbudget.core.domain.service.account.AccountService;
import com.bts.personalbudget.core.domain.service.account.AccountsBalance;
import com.bts.personalbudget.core.domain.service.balance.BalanceService;
import com.bts.personalbudget.core.domain.service.balance.DailyBalance;
import com.bts.personalbudget.core.domain.service.balance.ProjectedOccurrence;
import com.bts.personalbudget.core.domain.service.budgetenvelope.BudgetEnvelopeService;
import com.bts.personalbudget.core.domain.service.budgetenvelope.CycleEnvelopes;
import com.bts.personalbudget.core.domain.service.paycycle.CurrentPayCycle;
import com.bts.personalbudget.core.domain.service.paycycle.PayCycleService;
import com.bts.personalbudget.core.domain.service.paycycle.PayCycleView;
import com.bts.personalbudget.exception.InvalidFieldsException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CashForecastServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 8);
    private static final LocalDate UNTIL = LocalDate.of(2026, 11, 30);

    @InjectMocks
    private CashForecastService service;

    @Mock
    private AccountService accountService;

    @Mock
    private FinancialMovementService movementService;

    @Mock
    private BalanceService balanceService;

    @Mock
    private PayCycleService payCycleService;

    @Mock
    private BudgetEnvelopeService envelopeService;

    @Spy
    private Clock clock = Clock.fixed(Instant.parse("2026-11-08T15:00:00Z"), ZoneId.of("America/Sao_Paulo"));

    private final UUID cycleCode = UUID.randomUUID();

    private static FinancialMovement movement(final OperationType type, final String amount,
                                              final FinancialMovementStatus status, final LocalDate due,
                                              final Integer installmentNumber) {
        final BigDecimal signed = new BigDecimal(type == OperationType.DEBIT ? "-" + amount : amount);
        return new FinancialMovement(UUID.randomUUID(), type, "Teste", signed, BigDecimal.ZERO,
                due.atStartOfDay(), due.atStartOfDay(), null, status, true, null, null, null, due, false,
                null, null, installmentNumber);
    }

    private static ProjectedOccurrence occurrence(final OperationType type, final String amount) {
        return new ProjectedOccurrence(UUID.randomUUID(), LocalDate.of(2026, 11, 20), LocalDate.of(2026, 11, 20),
                new BigDecimal(type == OperationType.DEBIT ? "-" + amount : amount), type, null);
    }

    private static DailyBalance day(final LocalDate date, final String projectedClosing) {
        return new DailyBalance(date, new BigDecimal("800.00"), new BigDecimal(projectedClosing));
    }

    @BeforeEach
    void setUp() {
        when(accountService.balances(TODAY)).thenReturn(new AccountsBalance(TODAY, List.of(), new BigDecimal("1000.00")));
        when(movementService.findOpenDueThrough(UNTIL)).thenReturn(List.of(
                movement(OperationType.CREDIT, "300.00", FinancialMovementStatus.PENDING, LocalDate.of(2026, 11, 10), null),
                movement(OperationType.CREDIT, "200.00", FinancialMovementStatus.LATE, LocalDate.of(2026, 11, 5), null),
                movement(OperationType.DEBIT, "150.00", FinancialMovementStatus.PENDING, LocalDate.of(2026, 11, 12), null),
                movement(OperationType.DEBIT, "50.00", FinancialMovementStatus.LATE, LocalDate.of(2026, 11, 3), null),
                movement(OperationType.DEBIT, "100.00", FinancialMovementStatus.PENDING, LocalDate.of(2026, 11, 15), 1)));
        when(movementService.findOpenCardInstallmentsDueAfter(UNTIL)).thenReturn(List.of(
                movement(OperationType.DEBIT, "100.00", FinancialMovementStatus.PENDING, LocalDate.of(2026, 12, 15), 2),
                movement(OperationType.DEBIT, "100.00", FinancialMovementStatus.PENDING, LocalDate.of(2027, 1, 15), 3)));
        when(balanceService.findBalanceCalcDataBetween(TODAY, UNTIL)).thenReturn(List.of(
                occurrence(OperationType.DEBIT, "40.00"), occurrence(OperationType.CREDIT, "60.00")));
        // real closing today 800.00; the daily projection counts the late income as sure money
        when(balanceService.findDailyBalanceBetween(TODAY, UNTIL)).thenReturn(days("800.00", "600.00", "820.00"));
        when(payCycleService.findCurrent()).thenReturn(new CurrentPayCycle(
                new PayCycleView(PayCycleFactory.buildTransition(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 12, 1)),
                        PayCycleStatus.OPEN, List.of()), List.of()));
        when(envelopeService.find(any())).thenReturn(new CycleEnvelopes(cycleCode, List.of(),
                new BigDecimal("500.00"), new BigDecimal("380.00"), new BigDecimal("120.00"), BigDecimal.ZERO, false));
    }

    private static Set<DailyBalance> days(final String first, final String second, final String last) {
        return new LinkedHashSet<>(List.of(day(TODAY, first), day(LocalDate.of(2026, 11, 12), second),
                day(UNTIL, last)));
    }

    @Test
    void shouldKeepTheComponentsApartAndNotCountLateIncomeByDefault() {
        final CashForecast forecast = service.forecast(UNTIL, false);

        assertThat(forecast.cashBalance()).isEqualByComparingTo("1000.00");
        assertThat(forecast.expectedIncome()).isEqualByComparingTo("360.00");
        assertThat(forecast.lateIncome()).isEqualByComparingTo("200.00");
        assertThat(forecast.obligations()).isEqualByComparingTo("240.00");
        assertThat(forecast.cardInvoices()).isEqualByComparingTo("100.00");
        assertThat(forecast.projectedCash()).isEqualByComparingTo("1020.00");
        assertThat(forecast.envelopeReserve()).isEqualByComparingTo("120.00");
        assertThat(forecast.cardBeyondHorizon()).isEqualByComparingTo("200.00");
        assertThat(forecast.freeAfterReserves()).isEqualByComparingTo("700.00");
        assertThat(forecast.includeLateIncome()).isFalse();
        assertThat(forecast.negativeCashAlert()).isFalse();
        assertThat(forecast.insufficientBudgetAlert()).isFalse();
        assertThat(forecast.envelopeCycleCode()).isNotNull();
    }

    @Test
    void shouldCountLateIncomeOnlyWhenTheCallerSaysSo() {
        final CashForecast forecast = service.forecast(UNTIL, true);

        assertThat(forecast.projectedCash()).isEqualByComparingTo("1220.00");
        assertThat(forecast.freeAfterReserves()).isEqualByComparingTo("900.00");
        assertThat(forecast.includeLateIncome()).isTrue();
    }

    @Test
    void shouldFindTheLowestProjectedCashWithTheLateIncomeTakenOutAndTheAccountsAsOpening() {
        // projected closing + (accounts 1000 - real closing 800) - late income 200
        final CashForecast excluded = service.forecast(UNTIL, false);
        assertThat(excluded.lowestProjectedCash()).isEqualByComparingTo("600.00");
        assertThat(excluded.lowestProjectedCashDate()).isEqualTo(LocalDate.of(2026, 11, 12));
        assertThat(excluded.firstNegativeDate()).isNull();

        final CashForecast included = service.forecast(UNTIL, true);
        assertThat(included.lowestProjectedCash()).isEqualByComparingTo("800.00");
    }

    @Test
    void shouldRaiseTheNegativeCashAlertAndTheBudgetAlertIndependently() {
        when(balanceService.findDailyBalanceBetween(TODAY, UNTIL)).thenReturn(days("800.00", "-50.00", "820.00"));

        final CashForecast negativeCash = service.forecast(UNTIL, false);

        assertThat(negativeCash.negativeCashAlert()).isTrue();
        assertThat(negativeCash.firstNegativeDate()).isEqualTo(LocalDate.of(2026, 11, 12));
        assertThat(negativeCash.insufficientBudgetAlert()).isFalse();

        when(envelopeService.find(any())).thenReturn(new CycleEnvelopes(cycleCode, List.of(),
                new BigDecimal("2000.00"), BigDecimal.ZERO, new BigDecimal("1500.00"), BigDecimal.ZERO, true));
        when(balanceService.findDailyBalanceBetween(TODAY, UNTIL)).thenReturn(days("800.00", "600.00", "820.00"));

        final CashForecast budget = service.forecast(UNTIL, false);

        assertThat(budget.negativeCashAlert()).isFalse();
        assertThat(budget.insufficientBudgetAlert()).isTrue();
        assertThat(budget.freeAfterReserves()).isEqualByComparingTo("-680.00");
        assertThat(budget.projectionIncomplete()).isTrue();
    }

    @Test
    void shouldDefaultTheHorizonToTheLastDayOfTheCurrentCycle() {
        when(movementService.findOpenDueThrough(LocalDate.of(2026, 11, 30))).thenReturn(List.of());

        assertThat(service.forecast(null, false).until()).isEqualTo(LocalDate.of(2026, 11, 30));
    }

    @Test
    void shouldDefaultToTheEndOfTheMonthAndSkipTheReserveWhenThereIsNoCurrentCycle() {
        when(payCycleService.findCurrent()).thenReturn(new CurrentPayCycle(null, List.of()));

        final CashForecast forecast = service.forecast(null, false);

        assertThat(forecast.until()).isEqualTo(LocalDate.of(2026, 11, 30));
        assertThat(forecast.envelopeReserve()).isEqualByComparingTo("0");
        assertThat(forecast.envelopeCycleCode()).isNull();
        verify(envelopeService, never()).find(any());
    }

    @Test
    void shouldRejectAHorizonBeforeTodayOrMoreThanAYearAhead() {
        assertThrows(InvalidFieldsException.class, () -> service.forecast(TODAY.minusDays(1), false));
        assertThrows(InvalidFieldsException.class, () -> service.forecast(TODAY.plusDays(367), false));

        verify(balanceService, never()).findDailyBalanceBetween(any(), any());
    }
}
