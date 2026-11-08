package com.bts.personalbudget.core.domain.service.paycycle;

import static com.bts.personalbudget.core.domain.enumerator.PayCycleKind.REGULAR;
import static com.bts.personalbudget.core.domain.enumerator.PayCycleLifecycle.ACTIVE;
import static com.bts.personalbudget.core.domain.enumerator.PayCycleMode.PAY_CYCLE;
import static com.bts.personalbudget.core.domain.enumerator.PayCycleStatus.PENDING_CLOSE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bts.personalbudget.core.domain.entity.IncomeScheduleEntity;
import com.bts.personalbudget.core.domain.entity.PayCycleEntity;
import com.bts.personalbudget.core.domain.enumerator.FinancialMovementStatus;
import com.bts.personalbudget.core.domain.enumerator.PayCycleLifecycle;
import com.bts.personalbudget.core.domain.enumerator.PayCycleStatus;
import com.bts.personalbudget.core.domain.model.FinancialMovement;
import com.bts.personalbudget.core.domain.service.FinancialMovementService;
import com.bts.personalbudget.core.domain.service.fixedbill.FixedBillService;
import com.bts.personalbudget.core.domain.service.income.IncomeScheduleService;
import com.bts.personalbudget.core.domain.service.installmentbill.InstallmentBillService;
import com.bts.personalbudget.exception.ConflictException;
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
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PayCycleClosureServiceTest {
    private static final UUID CODE = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000311");
    private static final UUID ANCHOR_CODE = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000311");
    private static final LocalDate START = LocalDate.of(2026, 10, 1);
    private static final LocalDate END = LocalDate.of(2026, 11, 1);
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 8);

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
    void shouldCloseAndSnapshotAfterTakingLocksInOrder() {
        final PayCycleEntity entity = activeCycle();
        final PayCycleSummary summary = summary(PayCycleStatus.CLOSED, List.of());
        final PayCycleSnapshot snapshot = snapshot();
        stubClosure(entity, summary);
        when(snapshotService.recordClosure(summary)).thenReturn(snapshot);

        final PayCycleClosureResult result = service.close(CODE, 4L);

        assertThat(result.cycle().cycle().lifecycle()).isEqualTo(PayCycleLifecycle.CLOSED);
        assertThat(result.snapshot()).isEqualTo(snapshot);
        final InOrder order = inOrder(configService, scheduleRepository, cycleRepository,
                fixedBillService, installmentBillService, incomeScheduleService,
                summaryService, movementService, snapshotService);
        order.verify(configService).lock();
        order.verify(scheduleRepository).findForUpdateByCode(ANCHOR_CODE);
        order.verify(cycleRepository).findByCode(CODE);
        order.verify(fixedBillService).findCodesByNextDueDateLessThanEqual(END.minusDays(1));
        order.verify(installmentBillService).findCodesByNextInstallmentDateLessThanEqual(END.minusDays(1));
        order.verify(incomeScheduleService).findCodesToPost(END.minusDays(1));
        order.verify(cycleRepository).findForUpdateByCode(CODE);
        order.verify(summaryService).summarize(CODE);
        order.verify(movementService).findByCompetenceInterval(START, END);
        order.verify(cycleRepository).flush();
        order.verify(summaryService).summarize(CODE);
        order.verify(movementService).findByCompetenceInterval(START, END);
        order.verify(snapshotService).recordClosure(summary);
    }

    @Test
    void shouldRecoverDueOccurrencesBeforeLockingCycle() {
        final PayCycleEntity entity = activeCycle();
        final PayCycleSummary summary = summary(PayCycleStatus.CLOSED, List.of());
        stubClosure(entity, summary);
        final UUID fixedBillCode = UUID.randomUUID();
        final UUID installmentBillCode = UUID.randomUUID();
        final UUID incomeScheduleCode = UUID.randomUUID();
        when(fixedBillService.findCodesByNextDueDateLessThanEqual(END.minusDays(1)))
                .thenReturn(List.of(fixedBillCode));
        when(installmentBillService.findCodesByNextInstallmentDateLessThanEqual(END.minusDays(1)))
                .thenReturn(List.of(installmentBillCode));
        when(incomeScheduleService.findCodesToPost(END.minusDays(1))).thenReturn(List.of(incomeScheduleCode));

        service.close(CODE, 4L);

        final InOrder order = inOrder(fixedBillService, installmentBillService,
                incomeScheduleService, cycleRepository);
        order.verify(fixedBillService).postDueOccurrences(fixedBillCode, END.minusDays(1));
        order.verify(installmentBillService).postDueInstallments(installmentBillCode, END.minusDays(1));
        order.verify(incomeScheduleService).postDueOccurrences(incomeScheduleCode, END.minusDays(1));
        order.verify(cycleRepository).findForUpdateByCode(CODE);
    }

    @Test
    void shouldRejectAStaleVersionWithoutClosingOrRecordingASnapshot() {
        final PayCycleEntity entity = activeCycle();
        entity.setVersion(5L);
        when(configService.lock()).thenReturn(config());
        when(scheduleRepository.findForUpdateByCode(ANCHOR_CODE))
                .thenReturn(Optional.of(IncomeScheduleEntity.builder().code(ANCHOR_CODE).build()));
        when(cycleRepository.findByCode(CODE)).thenReturn(Optional.of(entity));

        assertThrows(ConflictException.class, () -> service.close(CODE, 4L));

        assertThat(entity.getLifecycle()).isEqualTo(ACTIVE);
        verify(cycleRepository, never()).flush();
        verify(snapshotService, never()).recordClosure(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldRefuseIncompleteProjectionAndNotRecordASnapshot() {
        final PayCycleEntity entity = activeCycle();
        stubClosure(entity, summary(PayCycleStatus.CLOSED, List.of(new PayCycleSummary.Pending(
                PayCycleSummary.Pending.Source.PAY_CYCLE,
                PayCycleSummary.Pending.Type.NO_CURRENT_CYCLE, CODE, null, null, null, null))));

        assertThrows(ConflictException.class, () -> service.close(CODE, 4L));

        verify(snapshotService, never()).recordClosure(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldRefuseLateMovementsInPreview() {
        final PayCycleSummary summary = summary(PENDING_CLOSE, List.of());
        final FinancialMovement late = new FinancialMovement(
                UUID.randomUUID(), com.bts.personalbudget.core.domain.enumerator.OperationType.DEBIT, "Atrasada",
                new BigDecimal("-10.00"), null, START.atStartOfDay(), START.atStartOfDay(), null,
                FinancialMovementStatus.LATE, true, null, null, null, START, false);
        when(summaryService.summarize(CODE)).thenReturn(summary);
        when(configService.find()).thenReturn(config());
        when(movementService.findByCompetenceInterval(START, END)).thenReturn(List.of(late));

        final PayCycleClosurePreview preview = service.preview(CODE);

        assertThat(preview.canClose()).isFalse();
        assertThat(preview.blockers()).containsExactly("LATE_MOVEMENTS");
        assertThat(preview.lateMovements()).containsExactly(late);
    }

    @Test
    void shouldRefuseOverduePendingMovementsInPreviewButAllowNotYetDueOnes() {
        final PayCycleSummary summary = summary(PENDING_CLOSE, List.of());
        when(summaryService.summarize(CODE)).thenReturn(summary);
        when(configService.find()).thenReturn(config());
        when(movementService.findByCompetenceInterval(START, END)).thenReturn(List.of(
                movement(FinancialMovementStatus.PENDING, TODAY.minusDays(1)),
                movement(FinancialMovementStatus.PENDING, TODAY)));

        final PayCycleClosurePreview preview = service.preview(CODE);

        assertThat(preview.canClose()).isFalse();
        assertThat(preview.blockers()).containsExactly("OVERDUE_PENDING_MOVEMENTS");
        assertThat(preview.lateMovements()).isEmpty();
    }

    @Test
    void shouldAllowPendingMovementsThatAreNotOverdueInPreview() {
        when(summaryService.summarize(CODE)).thenReturn(summary(PENDING_CLOSE, List.of()));
        when(configService.find()).thenReturn(config());
        when(movementService.findByCompetenceInterval(START, END))
                .thenReturn(List.of(movement(FinancialMovementStatus.PENDING, TODAY)));

        final PayCycleClosurePreview preview = service.preview(CODE);

        assertThat(preview.canClose()).isTrue();
        assertThat(preview.blockers()).isEmpty();
    }

    @Test
    void shouldRefuseToCloseWhenALateMovementExists() {
        final PayCycleEntity entity = activeCycle();
        final PayCycleSummary summary = summary(PayCycleStatus.CLOSED, List.of());
        stubClosure(entity, summary);
        final FinancialMovement late = new FinancialMovement(
                UUID.randomUUID(), com.bts.personalbudget.core.domain.enumerator.OperationType.DEBIT, "Atrasada",
                new BigDecimal("-10.00"), null, START.atStartOfDay(), START.atStartOfDay(), null,
                FinancialMovementStatus.LATE, true, null, null, null, START, false);
        when(movementService.findByCompetenceInterval(START, END)).thenReturn(List.of(late));

        assertThrows(ConflictException.class, () -> service.close(CODE, 4L));

        verify(snapshotService, never()).recordClosure(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldRefuseToCloseWhenAPendingMovementIsOverdue() {
        final PayCycleEntity entity = activeCycle();
        stubClosure(entity, summary(PayCycleStatus.CLOSED, List.of()));
        when(movementService.findByCompetenceInterval(START, END))
                .thenReturn(List.of(movement(FinancialMovementStatus.PENDING, TODAY.minusDays(1))));

        assertThrows(ConflictException.class, () -> service.close(CODE, 4L));

        assertThat(entity.getLifecycle()).isEqualTo(ACTIVE);
        verify(cycleRepository, never()).flush();
        verify(snapshotService, never()).recordClosure(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldReopenTheCycleAndPreserveItsSnapshotHistory() {
        final PayCycleEntity entity = activeCycle();
        entity.setLifecycle(PayCycleLifecycle.CLOSED);
        entity.setVersion(5L);
        final PayCycleView reopened = new PayCycleView(
                new PayCycle(CODE, START, END, REGULAR, ANCHOR_CODE, YearMonth.from(START),
                        ACTIVE, false, false, 6L),
                PENDING_CLOSE, List.of());
        when(configService.lock()).thenReturn(config());
        when(scheduleRepository.findForUpdateByCode(ANCHOR_CODE))
                .thenReturn(Optional.of(IncomeScheduleEntity.builder().code(ANCHOR_CODE).build()));
        when(cycleRepository.findForUpdateByCode(CODE)).thenReturn(Optional.of(entity));
        when(payCycleService.findByCode(CODE)).thenReturn(reopened);

        assertThat(service.reopen(CODE, 5L)).isEqualTo(reopened);

        assertThat(entity.getLifecycle()).isEqualTo(ACTIVE);
        verify(payCycleService).findByCode(CODE);
    }

    private void stubClosure(final PayCycleEntity entity, final PayCycleSummary summary) {
        when(configService.lock()).thenReturn(config());
        when(scheduleRepository.findForUpdateByCode(ANCHOR_CODE))
                .thenReturn(Optional.of(IncomeScheduleEntity.builder().code(ANCHOR_CODE).build()));
        when(cycleRepository.findByCode(CODE)).thenReturn(Optional.of(entity));
        when(cycleRepository.findForUpdateByCode(CODE)).thenReturn(Optional.of(entity));
        when(fixedBillService.findCodesByNextDueDateLessThanEqual(END.minusDays(1))).thenReturn(List.of());
        when(installmentBillService.findCodesByNextInstallmentDateLessThanEqual(END.minusDays(1)))
                .thenReturn(List.of());
        when(incomeScheduleService.findCodesToPost(END.minusDays(1))).thenReturn(List.of());
        when(summaryService.summarize(CODE)).thenReturn(summary);
        when(movementService.findByCompetenceInterval(START, END)).thenReturn(List.of());
    }

    private static PayCycleConfig config() {
        return new PayCycleConfig(UUID.randomUUID(), PAY_CYCLE, ANCHOR_CODE, START, 1L);
    }

    private static PayCycleEntity activeCycle() {
        return PayCycleEntity.builder().code(CODE).startDate(START).endDate(END).kind(REGULAR)
                .lifecycle(ACTIVE).startAdjusted(false).endAdjusted(false).version(4L).build();
    }

    private static PayCycleSummary summary(final com.bts.personalbudget.core.domain.enumerator.PayCycleStatus status,
                                          final List<PayCycleSummary.Pending> pending) {
        final PayCycle cycle = new PayCycle(CODE, START, END, REGULAR, ANCHOR_CODE, YearMonth.from(START),
                status == PayCycleStatus.CLOSED ? PayCycleLifecycle.CLOSED : ACTIVE, false, false, 4L);
        return new PayCycleSummary(new PayCycleView(cycle, status, List.of()),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, List.of(), pending);
    }

    private static PayCycleSnapshot snapshot() {
        return new PayCycleSnapshot(UUID.randomUUID(), CODE, 1, 4L, Instant.parse("2026-11-08T12:00:00Z"),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, List.of());
    }

    private static FinancialMovement movement(final FinancialMovementStatus status, final LocalDate dueDate) {
        return new FinancialMovement(
                UUID.randomUUID(), com.bts.personalbudget.core.domain.enumerator.OperationType.DEBIT, "Conta",
                new BigDecimal("-10.00"), null, dueDate.atStartOfDay(), dueDate.atStartOfDay(), null,
                status, true, null, null, null, START, false);
    }
}
