package com.bts.personalbudget.core.domain.service.paycycle;

import static com.bts.personalbudget.core.domain.enumerator.PayCycleKind.REGULAR;
import static com.bts.personalbudget.core.domain.enumerator.PayCycleLifecycle.ACTIVE;
import static com.bts.personalbudget.core.domain.enumerator.PayCycleMode.PAY_CYCLE;
import static com.bts.personalbudget.core.domain.enumerator.PayCycleStatus.PENDING_CLOSE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.bts.personalbudget.core.domain.enumerator.FinancialMovementStatus;
import com.bts.personalbudget.core.domain.enumerator.OperationType;
import com.bts.personalbudget.core.domain.model.FinancialMovement;
import com.bts.personalbudget.core.domain.service.FinancialMovementService;
import com.bts.personalbudget.core.domain.service.fixedbill.FixedBillService;
import com.bts.personalbudget.core.domain.service.income.IncomeScheduleService;
import com.bts.personalbudget.core.domain.service.installmentbill.InstallmentBillService;
import com.bts.personalbudget.mapper.PayCycleMapper;
import com.bts.personalbudget.repository.IncomeScheduleRepository;
import com.bts.personalbudget.repository.PayCycleRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

/** What a third party's card purchase does, and does not, do to the closure of a cycle. */
@ExtendWith(MockitoExtension.class)
class PayCycleClosureReceivableTest {
    private static final UUID CODE = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000411");
    private static final UUID ANCHOR_CODE = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000411");
    private static final UUID THIRD_PARTY = UUID.fromString("cccccccc-0000-0000-0000-000000000411");
    private static final LocalDate START = LocalDate.of(2026, 10, 1);
    private static final LocalDate END = LocalDate.of(2026, 11, 1);

    @InjectMocks
    private PayCycleClosureService service;

    @Mock private PayCycleConfigService configService;
    @Mock private IncomeScheduleRepository scheduleRepository;
    @Mock private PayCycleRepository cycleRepository;
    @Mock private PayCycleService payCycleService;
    @Mock private PayCycleSummaryService summaryService;
    @Mock private FinancialMovementService movementService;
    @Mock private FixedBillService fixedBillService;
    @Mock private InstallmentBillService installmentBillService;
    @Mock private IncomeScheduleService incomeScheduleService;
    @Mock private PayCycleSnapshotService snapshotService;
    @Spy private PayCycleMapper mapper = PayCycleMapper.INSTANCE;
    @Spy private Clock clock = Clock.fixed(Instant.parse("2026-11-08T12:00:00Z"),
            ZoneId.of("America/Sao_Paulo"));

    @Test
    void shouldNotBlockClosureWhenReceivableIsLate() {
        stubPreview(List.of(lateReceivable()));

        final PayCycleClosurePreview preview = service.preview(CODE);

        assertThat(preview.canClose()).isTrue();
        assertThat(preview.blockers()).isEmpty();
        assertThat(preview.lateMovements()).isEmpty();
    }

    @Test
    void shouldBlockClosureWhenThirdPartyInstallmentIsLate() {
        final FinancialMovement installment = lateThirdPartyInstallment();
        stubPreview(List.of(installment));

        final PayCycleClosurePreview preview = service.preview(CODE);

        assertThat(preview.canClose()).isFalse();
        assertThat(preview.blockers()).containsExactly("LATE_MOVEMENTS");
        assertThat(preview.lateMovements()).containsExactly(installment);
    }

    @Test
    void shouldIgnoreTheReceivableButKeepTheInstallmentWhenBothAreLate() {
        final FinancialMovement installment = lateThirdPartyInstallment();
        stubPreview(List.of(installment, lateReceivable()));

        final PayCycleClosurePreview preview = service.preview(CODE);

        assertThat(preview.blockers()).containsExactly("LATE_MOVEMENTS");
        assertThat(preview.lateMovements()).containsExactly(installment);
    }

    private void stubPreview(final List<FinancialMovement> movements) {
        when(configService.find()).thenReturn(new PayCycleConfig(UUID.randomUUID(), PAY_CYCLE, ANCHOR_CODE, START, 1L));
        when(summaryService.summarize(CODE)).thenReturn(summary());
        when(movementService.findByCompetenceInterval(START, END)).thenReturn(movements);
    }

    private static PayCycleSummary summary() {
        final PayCycle cycle = new PayCycle(CODE, START, END, REGULAR, ANCHOR_CODE, YearMonth.from(START),
                ACTIVE, false, false, 4L);
        return new PayCycleSummary(new PayCycleView(cycle, PENDING_CLOSE, List.of()),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, List.of(), List.of());
    }

    /** The amount a third party still owes: a CREDIT linked to its source installment, no card link. */
    private static FinancialMovement lateReceivable() {
        return new FinancialMovement(UUID.randomUUID(), OperationType.CREDIT, "A receber: Compra",
                new BigDecimal("100.00"), null, START.atStartOfDay(), START.plusDays(5).atStartOfDay(), null,
                FinancialMovementStatus.LATE, true, null, null, null, START, true,
                null, null, null, THIRD_PARTY, UUID.randomUUID());
    }

    /** The bank's side of the same purchase: a DEBIT installment on the user's card, marked as the third party's. */
    private static FinancialMovement lateThirdPartyInstallment() {
        return new FinancialMovement(UUID.randomUUID(), OperationType.DEBIT, "Compra",
                new BigDecimal("-100.00"), null, START.atStartOfDay(), START.plusDays(5).atStartOfDay(), null,
                FinancialMovementStatus.LATE, true, null, null, null, START, true,
                UUID.randomUUID(), UUID.randomUUID(), 1, THIRD_PARTY, null);
    }
}
