package com.bts.personalbudget.core.domain.service.paycycle;

import static com.bts.personalbudget.core.domain.enumerator.CategoryStatus.INACTIVE;
import static com.bts.personalbudget.core.domain.enumerator.FinancialMovementStatus.LATE;
import static com.bts.personalbudget.core.domain.enumerator.FinancialMovementStatus.PAID_OUT;
import static com.bts.personalbudget.core.domain.enumerator.FinancialMovementStatus.PENDING;
import static com.bts.personalbudget.core.domain.enumerator.OperationType.CREDIT;
import static com.bts.personalbudget.core.domain.enumerator.OperationType.DEBIT;
import static com.bts.personalbudget.core.domain.enumerator.PayCycleKind.TRANSITION;
import static com.bts.personalbudget.core.domain.enumerator.PayCycleLifecycle.ACTIVE;
import static com.bts.personalbudget.core.domain.enumerator.PayCycleStatus.OPEN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bts.personalbudget.core.domain.model.Category;
import com.bts.personalbudget.core.domain.model.FinancialMovement;
import com.bts.personalbudget.core.domain.service.FinancialMovementService;
import com.bts.personalbudget.core.domain.service.balance.BalanceService;
import com.bts.personalbudget.core.domain.service.balance.ProjectedOccurrence;
import com.bts.personalbudget.core.domain.service.category.CategoryService;
import com.bts.personalbudget.core.domain.service.income.IncomeProjection;
import com.bts.personalbudget.core.domain.service.income.IncomeProjectionPending;
import com.bts.personalbudget.core.domain.service.income.IncomeProjectionWindow;
import com.bts.personalbudget.core.domain.service.income.IncomeScheduleService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PayCycleSummaryServiceTest {

    private static final UUID CYCLE_CODE = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID RENT_CODE = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID SALARY_CODE = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final UUID SCHEDULE_CODE = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
    private static final LocalDate START = LocalDate.of(2026, 11, 1);
    private static final LocalDate END_EXCLUSIVE = LocalDate.of(2026, 12, 1);

    @InjectMocks
    private PayCycleSummaryService service;

    @Mock
    private PayCycleService payCycleService;

    @Mock
    private FinancialMovementService financialMovementService;

    @Mock
    private BalanceService balanceService;

    @Mock
    private IncomeScheduleService incomeScheduleService;

    @Mock
    private CategoryService categoryService;

    @Test
    void shouldSummarizeRecognizedAndProjectedAmountsByCompetence() {
        final PayCyclePending cyclePending = PayCyclePending.boundaryDiverges(
                CYCLE_CODE, YearMonth.of(2026, 11), START, LocalDate.of(2026, 11, 2));
        when(payCycleService.findByCode(CYCLE_CODE)).thenReturn(new PayCycleView(
                new PayCycle(CYCLE_CODE, START, END_EXCLUSIVE, TRANSITION, null, null, ACTIVE,
                        false, false, 1L),
                OPEN,
                List.of(cyclePending)));
        when(financialMovementService.findByCompetenceInterval(START, END_EXCLUSIVE)).thenReturn(List.of(
                movement(CREDIT, "300", "300", PAID_OUT, SALARY_CODE, LocalDate.of(2026, 11, 3)),
                movement(DEBIT, "-150", "-120", PAID_OUT, RENT_CODE, LocalDate.of(2026, 11, 4)),
                movement(CREDIT, "50", null, PENDING, SALARY_CODE, LocalDate.of(2026, 11, 5)),
                movement(DEBIT, "-30", null, LATE, null, LocalDate.of(2026, 11, 6))));
        when(balanceService.findProjectedBillOccurrencesBetween(START, END_EXCLUSIVE)).thenReturn(List.of(
                new ProjectedOccurrence(UUID.randomUUID(), LocalDate.of(2026, 11, 8),
                        LocalDate.of(2026, 11, 8), new BigDecimal("-20"), DEBIT, RENT_CODE)));
        when(incomeScheduleService.findProjectionBetween(START, END_EXCLUSIVE)).thenReturn(
                new IncomeProjectionWindow(
                        List.of(new IncomeProjection(SCHEDULE_CODE, YearMonth.of(2026, 11),
                                LocalDate.of(2026, 11, 10), new BigDecimal("25"), SALARY_CODE)),
                        List.of(new IncomeProjectionPending(
                                IncomeProjectionPending.Type.CALENDAR_NOT_REVIEWED,
                                SCHEDULE_CODE, YearMonth.of(2026, 11), 2026))));
        when(categoryService.findAllByCodes(Set.of(SALARY_CODE, RENT_CODE))).thenReturn(List.of(
                new Category(RENT_CODE, "Aluguel", DEBIT, INACTIVE, null, null),
                new Category(SALARY_CODE, "Salário", CREDIT, INACTIVE, null, null)));

        final PayCycleSummary summary = service.summarize(CYCLE_CODE);

        assertThat(summary.recognizedRevenue()).isEqualByComparingTo("300");
        assertThat(summary.recognizedExpense()).isEqualByComparingTo("120");
        assertThat(summary.projectedRevenue()).isEqualByComparingTo("375");
        assertThat(summary.projectedExpense()).isEqualByComparingTo("170");
        assertThat(summary.projectionIncomplete()).isTrue();
        assertThat(summary.categories()).extracting(PayCycleSummary.CategoryDetail::categoryName)
                .containsExactly("Aluguel", "Sem categoria", "Salário");
        assertThat(summary.categories().get(0).recognizedAmount()).isEqualByComparingTo("120");
        assertThat(summary.categories().get(0).projectedAmount()).isEqualByComparingTo("140");
        assertThat(summary.categories().get(1).categoryCode()).isNull();
        assertThat(summary.categories().get(1).projectedAmount()).isEqualByComparingTo("30");
        assertThat(summary.categories().get(2).recognizedAmount()).isEqualByComparingTo("300");
        assertThat(summary.categories().get(2).projectedAmount()).isEqualByComparingTo("375");
        assertThat(summary.pending()).hasSize(2);
        assertThat(summary.pending())
                .extracting(PayCycleSummary.Pending::source)
                .containsExactly(
                        PayCycleSummary.Pending.Source.PAY_CYCLE,
                        PayCycleSummary.Pending.Source.INCOME_SCHEDULE);

        verify(financialMovementService).findByCompetenceInterval(START, END_EXCLUSIVE);
        verify(balanceService).findProjectedBillOccurrencesBetween(START, END_EXCLUSIVE);
        verify(incomeScheduleService).findProjectionBetween(START, END_EXCLUSIVE);
    }

    @Test
    void shouldReturnCompleteEmptySummaryWhenThereAreNoMovementsOrPendings() {
        when(payCycleService.findByCode(CYCLE_CODE)).thenReturn(new PayCycleView(
                new PayCycle(CYCLE_CODE, START, END_EXCLUSIVE, TRANSITION, null, null, ACTIVE,
                        false, false, 1L),
                OPEN,
                List.of()));
        when(financialMovementService.findByCompetenceInterval(START, END_EXCLUSIVE)).thenReturn(List.of());
        when(balanceService.findProjectedBillOccurrencesBetween(START, END_EXCLUSIVE)).thenReturn(List.of());
        when(incomeScheduleService.findProjectionBetween(START, END_EXCLUSIVE))
                .thenReturn(new IncomeProjectionWindow(List.of(), List.of()));
        when(categoryService.findAllByCodes(Set.of())).thenReturn(List.of());

        final PayCycleSummary summary = service.summarize(CYCLE_CODE);

        assertThat(summary.recognizedRevenue()).isEqualByComparingTo("0");
        assertThat(summary.recognizedExpense()).isEqualByComparingTo("0");
        assertThat(summary.projectedRevenue()).isEqualByComparingTo("0");
        assertThat(summary.projectedExpense()).isEqualByComparingTo("0");
        assertThat(summary.categories()).isEmpty();
        assertThat(summary.pending()).isEmpty();
        assertThat(summary.projectionIncomplete()).isFalse();
    }

    private static FinancialMovement movement(final com.bts.personalbudget.core.domain.enumerator.OperationType operationType,
                                              final String amount,
                                              final String amountPaid,
                                              final com.bts.personalbudget.core.domain.enumerator.FinancialMovementStatus status,
                                              final UUID categoryCode,
                                              final LocalDate competenceDate) {
        final LocalDateTime date = competenceDate.atStartOfDay();
        return new FinancialMovement(
                UUID.randomUUID(),
                operationType,
                "Movimento de teste",
                new BigDecimal(amount),
                amountPaid == null ? null : new BigDecimal(amountPaid),
                date,
                date,
                status == PAID_OUT ? date : null,
                status,
                true,
                null,
                null,
                categoryCode,
                competenceDate,
                false);
    }
}
