package com.bts.personalbudget.core.domain.service.paycycle;

import static com.bts.personalbudget.core.domain.enumerator.OperationType.DEBIT;
import static com.bts.personalbudget.core.domain.enumerator.PayCycleKind.REGULAR;
import static com.bts.personalbudget.core.domain.enumerator.PayCycleLifecycle.ACTIVE;
import static com.bts.personalbudget.core.domain.enumerator.PayCycleStatus.OPEN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.bts.personalbudget.core.domain.enumerator.CategoryStatus;
import com.bts.personalbudget.core.domain.model.Category;
import com.bts.personalbudget.core.domain.service.FinancialMovementService;
import com.bts.personalbudget.core.domain.service.balance.BalanceService;
import com.bts.personalbudget.core.domain.service.category.CategoryService;
import com.bts.personalbudget.core.domain.service.income.IncomeProjectionWindow;
import com.bts.personalbudget.core.domain.service.income.IncomeScheduleService;
import com.bts.personalbudget.core.domain.service.movementsettlement.MovementSettlementService;
import com.bts.personalbudget.core.domain.service.thirdparty.ReceivableWriteOff;
import com.bts.personalbudget.core.domain.service.thirdparty.ReceivableWriteOffCategory;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** A write-off is an own expense of the competence of its date, with no cash and no revenue. */
@ExtendWith(MockitoExtension.class)
class PayCycleSummaryWriteOffTest {
    private static final UUID CYCLE_CODE = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000601");
    private static final LocalDate START = LocalDate.of(2026, 11, 1);
    private static final LocalDate END_EXCLUSIVE = LocalDate.of(2026, 12, 1);

    @InjectMocks
    private PayCycleSummaryService service;

    @Mock private PayCycleService payCycleService;
    @Mock private FinancialMovementService financialMovementService;
    @Mock private BalanceService balanceService;
    @Mock private IncomeScheduleService incomeScheduleService;
    @Mock private CategoryService categoryService;
    @Mock private MovementSettlementService movementSettlementService;

    @Test
    void shouldCountWriteOffsAsRecognizedOwnExpenseInTheFixedCategory() {
        when(payCycleService.findByCode(CYCLE_CODE)).thenReturn(new PayCycleView(
                new PayCycle(CYCLE_CODE, START, END_EXCLUSIVE, REGULAR, null, null, ACTIVE, false, false, 1L),
                OPEN, List.of()));
        when(incomeScheduleService.findProjectionBetween(START, END_EXCLUSIVE))
                .thenReturn(new IncomeProjectionWindow(List.of(), List.of()));
        when(financialMovementService.findReceivableWriteOffsByEffectiveInterval(START, END_EXCLUSIVE))
                .thenReturn(List.of(
                        new ReceivableWriteOff(UUID.randomUUID(), ReceivableWriteOffCategory.CODE,
                                new BigDecimal("70.00"), LocalDate.of(2026, 11, 10)),
                        new ReceivableWriteOff(UUID.randomUUID(), ReceivableWriteOffCategory.CODE,
                                new BigDecimal("30.00"), LocalDate.of(2026, 11, 20))));
        when(categoryService.findAllByCodes(Set.of(ReceivableWriteOffCategory.CODE))).thenReturn(List.of(
                new Category(ReceivableWriteOffCategory.CODE, "Perdas com terceiros", DEBIT,
                        CategoryStatus.ACTIVE, null, null)));

        final PayCycleSummary summary = service.summarize(CYCLE_CODE);

        assertThat(summary.recognizedExpense()).isEqualByComparingTo("100.00");
        assertThat(summary.projectedExpense()).isEqualByComparingTo("100.00");
        assertThat(summary.recognizedRevenue()).isEqualByComparingTo("0");
        assertThat(summary.projectedRevenue()).isEqualByComparingTo("0");
        assertThat(summary.categories()).hasSize(1);
        assertThat(summary.categories().get(0).categoryName()).isEqualTo("Perdas com terceiros");
        assertThat(summary.categories().get(0).categoryCode()).isEqualTo(ReceivableWriteOffCategory.CODE);
        assertThat(summary.categories().get(0).recognizedAmount()).isEqualByComparingTo("100.00");
        assertThat(summary.categories().get(0).projectedAmount()).isEqualByComparingTo("100.00");
    }
}
