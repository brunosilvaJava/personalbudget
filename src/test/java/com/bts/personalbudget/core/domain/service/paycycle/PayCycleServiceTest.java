package com.bts.personalbudget.core.domain.service.paycycle;

import static com.bts.personalbudget.core.domain.factory.HolidayCalendarFactory.buildCalendar;
import static com.bts.personalbudget.core.domain.factory.HolidayCalendarFactory.buildHoliday;
import static com.bts.personalbudget.core.domain.factory.HolidayCalendarFactory.buildReviewedCalendar;
import static com.bts.personalbudget.core.domain.factory.IncomeScheduleFactory.SCHEDULE_CODE;
import static com.bts.personalbudget.core.domain.factory.IncomeScheduleFactory.buildSchedule;
import static com.bts.personalbudget.core.domain.factory.PayCycleFactory.buildCalendarMonthConfig;
import static com.bts.personalbudget.core.domain.factory.PayCycleFactory.buildPayCycleConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.bts.personalbudget.core.domain.entity.IncomeScheduleEntity;
import com.bts.personalbudget.core.domain.entity.PayCycleAdjustmentEntity;
import com.bts.personalbudget.core.domain.entity.PayCycleEntity;
import com.bts.personalbudget.core.domain.enumerator.FinancialMovementStatus;
import com.bts.personalbudget.core.domain.enumerator.OperationType;
import com.bts.personalbudget.core.domain.enumerator.PayCycleAdjustmentType;
import com.bts.personalbudget.core.domain.enumerator.PayCycleKind;
import com.bts.personalbudget.core.domain.enumerator.PayCycleLifecycle;
import com.bts.personalbudget.core.domain.enumerator.PayCycleMode;
import com.bts.personalbudget.core.domain.enumerator.PayCycleStatus;
import com.bts.personalbudget.core.domain.factory.FinancialMovementFactory;
import com.bts.personalbudget.core.domain.model.FinancialMovement;
import com.bts.personalbudget.core.domain.service.FinancialMovementService;
import com.bts.personalbudget.core.domain.service.calendar.HolidayCalendar;
import com.bts.personalbudget.core.domain.service.calendar.HolidayCalendarService;
import com.bts.personalbudget.core.domain.service.income.IncomeOccurrenceImpact;
import com.bts.personalbudget.core.domain.service.income.IncomeOccurrenceView;
import com.bts.personalbudget.core.domain.service.income.IncomeSchedule;
import com.bts.personalbudget.core.domain.service.income.IncomeScheduleService;
import com.bts.personalbudget.exception.ConflictException;
import com.bts.personalbudget.exception.InvalidFieldsException;
import com.bts.personalbudget.mapper.PayCycleMapper;
import com.bts.personalbudget.repository.IncomeScheduleRepository;
import com.bts.personalbudget.repository.PayCycleAdjustmentRepository;
import com.bts.personalbudget.repository.PayCycleRepository;
import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Today is Monday 2026-11-02. The anchor schedule is "day 5, postponed to the next business day"
 * (Monday to Friday): Oct 5, Nov 5, Dec 7 (the 5th is a Saturday), Jan 5 2027, Feb 5. The planning
 * rules themselves are covered by PayCyclePlannerTest; here the service loads, saves and locks.
 */
@ExtendWith(MockitoExtension.class)
class PayCycleServiceTest {

    private static final ZoneId ZONE_ID = ZoneId.of("America/Sao_Paulo");
    private static final Instant FIXED_INSTANT = Instant.parse("2026-11-02T13:00:00Z");

    private static final YearMonth OCT = YearMonth.of(2026, 10);
    private static final YearMonth NOV = YearMonth.of(2026, 11);
    private static final YearMonth DEC = YearMonth.of(2026, 12);
    private static final YearMonth JAN = YearMonth.of(2027, 1);

    private static final LocalDate OCT_5 = LocalDate.of(2026, 10, 5);
    private static final LocalDate NOV_5 = LocalDate.of(2026, 11, 5);
    private static final LocalDate DEC_7 = LocalDate.of(2026, 12, 7);
    private static final LocalDate JAN_5 = LocalDate.of(2027, 1, 5);
    private static final LocalDate NOV_10 = LocalDate.of(2026, 11, 10);

    private static final String JUSTIFICATION = "O salário caiu mais tarde neste mês";

    private static final HolidayCalendar CALENDAR = buildReviewedCalendar(2026, 2027, 2028);

    @InjectMocks
    private PayCycleService service;

    @Mock
    private PayCycleConfigService configService;

    @Mock
    private IncomeScheduleService incomeScheduleService;

    @Mock
    private IncomeScheduleRepository scheduleRepository;

    @Mock
    private HolidayCalendarService holidayCalendarService;

    @Mock
    private FinancialMovementService financialMovementService;

    @Mock
    private PayCycleRepository cycleRepository;

    @Mock
    private PayCycleAdjustmentRepository adjustmentRepository;

    @Spy
    private PayCycleMapper mapper = PayCycleMapper.INSTANCE;

    @Spy
    private Clock clock = Clock.fixed(FIXED_INSTANT, ZONE_ID);

    private final IncomeSchedule novemberSchedule = buildSchedule(NOV);
    private final IncomeSchedule octoberSchedule = buildSchedule(OCT);
    private final IncomeScheduleEntity scheduleEntity = IncomeScheduleEntity.builder().id(1L).code(SCHEDULE_CODE).build();

    // ---- activation preview

    @Test
    void shouldPreviewTheFirstCyclesWithoutSavingAnything() {
        stubPreview(CALENDAR);

        final PayCycleActivationPreview preview = service.previewActivation(NOV_5);

        assertEquals(0L, preview.version());
        assertEquals(3, preview.cycles().size());
        final PayCycle first = preview.cycles().get(0).cycle();
        assertEquals(NOV_5, first.startDate());
        assertEquals(DEC_7, first.endDate());
        assertEquals(PayCycleKind.REGULAR, first.kind());
        assertEquals(PayCycleStatus.PLANNED, preview.cycles().get(0).status());
        assertTrue(preview.pending().isEmpty());
        verify(cycleRepository, never()).save(any());
        verify(configService, never()).switchToPayCycle(any(), any());
    }

    @Test
    void shouldPreviewNoCyclesAndTheReasonWhenTheFirstOneCannotBeCalculated() {
        stubPreview(HolidayCalendar.empty());

        final PayCycleActivationPreview preview = service.previewActivation(NOV_5);

        assertTrue(preview.cycles().isEmpty());
        assertEquals(List.of(PayCyclePending.calendarNotReviewed(NOV, 2026)), preview.pending());
    }

    @Test
    void shouldRejectAPreviewWhenThereIsNoActiveAnchor() {
        when(configService.find()).thenReturn(buildCalendarMonthConfig());
        when(incomeScheduleService.findActiveAnchor()).thenReturn(Optional.empty());

        final InvalidFieldsException exception =
                assertThrows(InvalidFieldsException.class, () -> service.previewActivation(NOV_5));

        assertTrue(exception.getValidations().containsKey("anchor"));
    }

    @Test
    void shouldRejectAPreviewWhenThePayCyclesAreAlreadyOn() {
        when(configService.find()).thenReturn(buildPayCycleConfig(OCT_5, 1L));

        assertThrows(ConflictException.class, () -> service.previewActivation(NOV_5));
        verifyNoInteractions(cycleRepository);
    }

    @Test
    void shouldRejectAnInitialDateMoreThan31DaysBeforeToday() {
        stubPreviewUntilTheDate();

        final InvalidFieldsException exception = assertThrows(InvalidFieldsException.class,
                () -> service.previewActivation(LocalDate.of(2026, 10, 1)));

        assertTrue(exception.getValidations().containsKey("initialDate"));
    }

    @Test
    void shouldRejectAnInitialDateMoreThan31DaysAfterToday() {
        stubPreviewUntilTheDate();

        final InvalidFieldsException exception = assertThrows(InvalidFieldsException.class,
                () -> service.previewActivation(LocalDate.of(2026, 12, 4)));

        assertTrue(exception.getValidations().containsKey("initialDate"));
    }

    @Test
    void shouldAcceptTheLimitsOfTheInitialDateWindow() {
        stubPreview(CALENDAR);

        assertEquals(PayCycleKind.TRANSITION, service.previewActivation(LocalDate.of(2026, 10, 2)).cycles().get(0).cycle().kind());
        assertEquals(PayCycleKind.TRANSITION, service.previewActivation(LocalDate.of(2026, 12, 3)).cycles().get(0).cycle().kind());
    }

    @Test
    void shouldRejectAnInitialDateBeforeTheEndOfThePreviousCycle() {
        when(configService.find()).thenReturn(buildCalendarMonthConfig());
        when(incomeScheduleService.findActiveAnchor()).thenReturn(Optional.of(novemberSchedule));
        when(cycleRepository.findByLifecycleIn(List.of(PayCycleLifecycle.ACTIVE)))
                .thenReturn(List.of(entity(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 11, 20), OCT)));

        final InvalidFieldsException exception = assertThrows(InvalidFieldsException.class,
                () -> service.previewActivation(LocalDate.of(2026, 11, 10)));

        assertTrue(exception.getValidations().get("initialDate").contains("2026-11-20"));
    }

    // ---- activation

    @Test
    void shouldActivateCreatingTheFirstCyclesUnderTheLocksInOrder() {
        stubActivation(CALENDAR);

        final PayCycleConfigView view = service.activate(NOV_5, 0L);

        final ArgumentCaptor<PayCycleEntity> saved = ArgumentCaptor.forClass(PayCycleEntity.class);
        verify(cycleRepository, times(3)).save(saved.capture());
        final List<PayCycleEntity> entities = saved.getAllValues();
        assertEquals(NOV_5, entities.get(0).getStartDate());
        assertEquals(DEC_7, entities.get(0).getEndDate());
        assertEquals(DEC_7, entities.get(1).getStartDate());
        assertEquals(JAN_5, entities.get(1).getEndDate());
        assertEquals(JAN_5, entities.get(2).getStartDate());
        assertEquals(NOV.atDay(1), entities.get(0).getAnchorNominalMonth());
        assertEquals(DEC.atDay(1), entities.get(1).getAnchorNominalMonth());
        assertEquals(scheduleEntity, entities.get(0).getAnchorSchedule());
        assertEquals(PayCycleLifecycle.ACTIVE, entities.get(0).getLifecycle());

        final InOrder order = inOrder(configService, incomeScheduleService, cycleRepository);
        order.verify(configService).lock();
        order.verify(incomeScheduleService).findActiveAnchorForUpdate();
        order.verify(cycleRepository).findForUpdateByLifecycle(PayCycleLifecycle.ACTIVE);
        order.verify(cycleRepository, times(3)).save(any(PayCycleEntity.class));
        order.verify(configService).switchToPayCycle(SCHEDULE_CODE, NOV_5);

        assertEquals(PayCycleMode.PAY_CYCLE, view.config().mode());
        assertTrue(view.pending().isEmpty());
    }

    @Test
    void shouldStartATransitionWithNoAnchorWhenTheInitialDateIsNotAnAnchorDate() {
        stubActivation(CALENDAR, LocalDate.of(2026, 11, 20));

        service.activate(LocalDate.of(2026, 11, 20), 0L);

        final ArgumentCaptor<PayCycleEntity> saved = ArgumentCaptor.forClass(PayCycleEntity.class);
        verify(cycleRepository, times(3)).save(saved.capture());
        final PayCycleEntity first = saved.getAllValues().get(0);
        assertEquals(PayCycleKind.TRANSITION, first.getKind());
        assertNull(first.getAnchorSchedule());
        assertNull(first.getAnchorNominalMonth());
        assertEquals(DEC_7, first.getEndDate());
    }

    @Test
    void shouldRejectAnActivationWhenTheVersionIsStale() {
        final PayCycleConfig config = buildCalendarMonthConfig();
        when(configService.lock()).thenReturn(config);
        doThrow(new ConflictException("stale")).when(configService).requireVersion(config, 5L);

        assertThrows(ConflictException.class, () -> service.activate(NOV_5, 5L));
        verifyNoInteractions(incomeScheduleService, cycleRepository);
        verify(configService, never()).switchToPayCycle(any(), any());
    }

    @Test
    void shouldRejectAnActivationWhenThePayCyclesAreAlreadyOn() {
        when(configService.lock()).thenReturn(buildPayCycleConfig(OCT_5, 1L));

        assertThrows(ConflictException.class, () -> service.activate(NOV_5, 1L));
        verifyNoInteractions(incomeScheduleService, cycleRepository);
    }

    @Test
    void shouldRejectAnActivationWithoutAnActiveAnchor() {
        when(configService.lock()).thenReturn(buildCalendarMonthConfig());
        when(incomeScheduleService.findActiveAnchorForUpdate()).thenReturn(Optional.empty());

        final InvalidFieldsException exception =
                assertThrows(InvalidFieldsException.class, () -> service.activate(NOV_5, 0L));

        assertTrue(exception.getValidations().containsKey("anchor"));
        verify(cycleRepository, never()).save(any());
    }

    @Test
    void shouldRejectAnActivationWhenTheFirstCycleCannotBeCalculated() {
        when(configService.lock()).thenReturn(buildCalendarMonthConfig());
        when(incomeScheduleService.findActiveAnchorForUpdate()).thenReturn(Optional.of(novemberSchedule));
        when(holidayCalendarService.findCalendar()).thenReturn(HolidayCalendar.empty());

        final InvalidFieldsException exception =
                assertThrows(InvalidFieldsException.class, () -> service.activate(NOV_5, 0L));

        assertTrue(exception.getValidations().get("initialDate").contains("2026"));
        verify(cycleRepository, never()).save(any());
        verify(configService, never()).switchToPayCycle(any(), any());
    }

    @Test
    void shouldRejectAnActivationWithAnInitialDateOutsideTheWindow() {
        when(configService.lock()).thenReturn(buildCalendarMonthConfig());
        when(incomeScheduleService.findActiveAnchorForUpdate()).thenReturn(Optional.of(novemberSchedule));

        assertThrows(InvalidFieldsException.class, () -> service.activate(LocalDate.of(2027, 3, 1), 0L));
        verify(cycleRepository, never()).save(any());
    }

    // ---- deactivation

    @Test
    void shouldCancelTheCyclesThatHaveNotStartedAndKeepTheOnesThatDid() {
        final PayCycleEntity started = entity(OCT_5, NOV_5, OCT);
        final PayCycleEntity planned = entity(NOV_5, DEC_7, NOV);
        final PayCycleEntity later = entity(DEC_7, JAN_5, DEC);
        when(configService.lock()).thenReturn(buildPayCycleConfig(OCT_5, 2L));
        when(cycleRepository.findForUpdateByLifecycle(PayCycleLifecycle.ACTIVE)).thenReturn(List.of(started, planned, later));
        when(configService.switchToCalendarMonth()).thenReturn(buildCalendarMonthConfig());

        final PayCycleConfigView view = service.deactivate(2L, JUSTIFICATION);

        assertEquals(PayCycleLifecycle.ACTIVE, started.getLifecycle());
        assertEquals(PayCycleLifecycle.CANCELLED, planned.getLifecycle());
        assertEquals(PayCycleLifecycle.CANCELLED, later.getLifecycle());
        assertEquals(PayCycleMode.CALENDAR_MONTH, view.config().mode());
        assertTrue(view.pending().isEmpty());
    }

    @Test
    void shouldRecordTheDeactivationInTheHistoryOfTheLastStartedAndTheFirstCancelledCycle() {
        final PayCycleEntity started = entity(OCT_5, NOV_5, OCT);
        final PayCycleEntity planned = entity(NOV_5, DEC_7, NOV);
        final PayCycleEntity later = entity(DEC_7, JAN_5, DEC);
        stubDeactivation(2L, started, planned, later);

        service.deactivate(2L, "  " + JUSTIFICATION + "  ");

        final PayCycleAdjustmentEntity recorded = savedAdjustment();
        assertEquals(PayCycleAdjustmentType.DEACTIVATION, recorded.getType());
        assertEquals(started, recorded.getPreviousCycle());
        assertEquals(planned, recorded.getNextCycle());
        assertEquals(NOV_5, recorded.getOldBoundary());
        assertNull(recorded.getNewBoundary());
        assertEquals(JUSTIFICATION, recorded.getJustification());
        assertEquals(2L, recorded.getExpectedConfigVersion());
        assertNull(recorded.getExpectedPreviousVersion());
        assertNull(recorded.getExpectedNextVersion());
    }

    @Test
    void shouldRecordTheDeactivationBeforeSwitchingTheModeAndUnderTheLocks() {
        stubDeactivation(2L, entity(OCT_5, NOV_5, OCT), entity(NOV_5, DEC_7, NOV));

        service.deactivate(2L, JUSTIFICATION);

        final InOrder order = inOrder(configService, cycleRepository, adjustmentRepository);
        order.verify(configService).lock();
        order.verify(cycleRepository).findForUpdateByLifecycle(PayCycleLifecycle.ACTIVE);
        order.verify(adjustmentRepository).save(any(PayCycleAdjustmentEntity.class));
        order.verify(configService).switchToCalendarMonth();
    }

    @Test
    void shouldRecordTheDeactivationWithNoPreviousCycleWhenNoneHadStarted() {
        final PayCycleEntity upcoming = entity(NOV_5, DEC_7, NOV);
        when(configService.lock()).thenReturn(buildPayCycleConfig(NOV_5, 2L));
        when(cycleRepository.findForUpdateByLifecycle(PayCycleLifecycle.ACTIVE)).thenReturn(List.of(upcoming));
        when(configService.switchToCalendarMonth()).thenReturn(buildCalendarMonthConfig());

        service.deactivate(2L, JUSTIFICATION);

        final PayCycleAdjustmentEntity recorded = savedAdjustment();
        assertNull(recorded.getPreviousCycle());
        assertEquals(upcoming, recorded.getNextCycle());
        assertEquals(NOV_5, recorded.getOldBoundary());
    }

    @Test
    void shouldRecordTheDeactivationWithNoNextCycleWhenNothingWasCancelled() {
        final PayCycleEntity started = entity(OCT_5, NOV_5, OCT);
        stubDeactivation(2L, started);

        service.deactivate(2L, JUSTIFICATION);

        final PayCycleAdjustmentEntity recorded = savedAdjustment();
        assertEquals(started, recorded.getPreviousCycle());
        assertNull(recorded.getNextCycle());
        assertNull(recorded.getOldBoundary());
    }

    @Test
    void shouldLinkTheDeactivationOnlyToTheCyclesOfTheCurrentActivation() {
        final PayCycleEntity history = entity(LocalDate.of(2026, 8, 5), LocalDate.of(2026, 9, 7), YearMonth.of(2026, 8));
        final PayCycleEntity started = entity(OCT_5, NOV_5, OCT);
        stubDeactivation(2L, history, started);

        service.deactivate(2L, JUSTIFICATION);

        assertEquals(started, savedAdjustment().getPreviousCycle());
        assertEquals(PayCycleLifecycle.ACTIVE, history.getLifecycle());
    }

    @Test
    void shouldRejectADeactivationWithoutAJustification() {
        final InvalidFieldsException blank =
                assertThrows(InvalidFieldsException.class, () -> service.deactivate(2L, "   "));
        final InvalidFieldsException missing =
                assertThrows(InvalidFieldsException.class, () -> service.deactivate(2L, null));

        assertTrue(blank.getValidations().containsKey("justification"));
        assertTrue(missing.getValidations().containsKey("justification"));
        verifyNoInteractions(configService, cycleRepository, adjustmentRepository);
    }

    @Test
    void shouldRejectADeactivationWithATooLongJustification() {
        final InvalidFieldsException exception = assertThrows(InvalidFieldsException.class,
                () -> service.deactivate(2L, "x".repeat(PayCycleService.JUSTIFICATION_MAX_LENGTH + 1)));

        assertTrue(exception.getValidations().get("justification").contains("500"));
        verifyNoInteractions(configService, cycleRepository, adjustmentRepository);
    }

    @Test
    void shouldAcceptAJustificationWithTheMaximumLength() {
        stubDeactivation(2L, entity(OCT_5, NOV_5, OCT));

        service.deactivate(2L, "x".repeat(PayCycleService.JUSTIFICATION_MAX_LENGTH));

        assertEquals(PayCycleService.JUSTIFICATION_MAX_LENGTH, savedAdjustment().getJustification().length());
    }

    @Test
    void shouldRejectADeactivationWhenThePayCyclesAreOff() {
        when(configService.lock()).thenReturn(buildCalendarMonthConfig());

        assertThrows(ConflictException.class, () -> service.deactivate(0L, JUSTIFICATION));
        verifyNoInteractions(cycleRepository, adjustmentRepository);
        verify(configService, never()).switchToCalendarMonth();
    }

    @Test
    void shouldRejectADeactivationWhenTheVersionIsStale() {
        final PayCycleConfig config = buildPayCycleConfig(OCT_5, 2L);
        when(configService.lock()).thenReturn(config);
        doThrow(new ConflictException("stale")).when(configService).requireVersion(config, 1L);

        assertThrows(ConflictException.class, () -> service.deactivate(1L, JUSTIFICATION));
        verifyNoInteractions(cycleRepository, adjustmentRepository);
    }

    // ---- end adjustment: preview

    @Test
    void shouldPreviewMovingTheEndLaterWithTheMovementsAndIncomesThatChangeCycle() {
        final PayCycleEntity first = entity(OCT_5, NOV_5, OCT);
        final PayCycleEntity second = entity(NOV_5, DEC_7, NOV);
        final PayCycleEntity third = entity(DEC_7, JAN_5, DEC);
        stubReadChain(first, second, third);
        final FinancialMovement movement = FinancialMovementFactory.buildModel(
                LocalDate.of(2026, 11, 7), OperationType.DEBIT, "120.00", FinancialMovementStatus.PENDING);
        final IncomeOccurrenceImpact income = incomeImpact(LocalDate.of(2026, 11, 7));
        when(financialMovementService.findByCompetenceInterval(NOV_5, NOV_10)).thenReturn(List.of(movement));
        when(incomeScheduleService.findOccurrencesPlannedBetween(NOV_5, NOV_10)).thenReturn(List.of(income));

        final PayCycleEndAdjustmentPreview preview = service.previewEndAdjustment(first.getCode(), NOV_10);

        assertEquals(NOV_5, preview.oldEndDate());
        assertEquals(NOV_10, preview.newEndDate());
        assertEquals(List.of(movement), preview.movements());
        assertEquals(List.of(income), preview.incomeOccurrences());
        assertEquals(second.getCode(), preview.leavingCycleCode());
        assertEquals(first.getCode(), preview.receivingCycleCode());
    }

    @Test
    void shouldPreviewTheTwoCyclesBeforeAndAfter() {
        final PayCycleEntity first = entity(OCT_5, NOV_5, OCT);
        final PayCycleEntity second = entity(NOV_5, DEC_7, NOV);
        stubReadChain(first, second, entity(DEC_7, JAN_5, DEC));

        final PayCycleEndAdjustmentPreview preview = service.previewEndAdjustment(first.getCode(), NOV_10);

        assertEquals(2, preview.before().size());
        assertEquals(NOV_5, preview.before().get(0).cycle().endDate());
        assertEquals(NOV_5, preview.before().get(1).cycle().startDate());
        assertFalse(preview.before().get(0).cycle().endAdjusted());
        assertEquals(2, preview.after().size());
        assertEquals(first.getCode(), preview.after().get(0).cycle().code());
        assertEquals(NOV_10, preview.after().get(0).cycle().endDate());
        assertTrue(preview.after().get(0).cycle().endAdjusted());
        assertEquals(second.getCode(), preview.after().get(1).cycle().code());
        assertEquals(NOV_10, preview.after().get(1).cycle().startDate());
        assertEquals(DEC_7, preview.after().get(1).cycle().endDate());
        assertTrue(preview.after().get(1).cycle().startAdjusted());
    }

    @Test
    void shouldPreviewTheDivergenceFromTheAnchorTheAdjustmentLeaves() {
        final PayCycleEntity first = entity(OCT_5, NOV_5, OCT);
        final PayCycleEntity second = entity(NOV_5, DEC_7, NOV);
        stubReadChain(first, second, entity(DEC_7, JAN_5, DEC));

        final PayCycleEndAdjustmentPreview preview = service.previewEndAdjustment(first.getCode(), NOV_10);

        assertTrue(preview.before().stream().allMatch(view -> view.pending().isEmpty()));
        assertEquals(List.of(PayCyclePending.boundaryDiverges(second.getCode(), NOV, NOV_10, NOV_5)),
                preview.after().get(1).pending());
    }

    @Test
    void shouldPreviewMovingTheEndEarlierWithTheDaysThatLeaveTheFirstCycle() {
        final PayCycleEntity first = entity(OCT_5, NOV_5, OCT);
        final PayCycleEntity second = entity(NOV_5, DEC_7, NOV);
        stubReadChain(first, second, entity(DEC_7, JAN_5, DEC));
        final LocalDate nov1 = LocalDate.of(2026, 11, 1);

        final PayCycleEndAdjustmentPreview preview = service.previewEndAdjustment(first.getCode(), nov1);

        verify(financialMovementService).findByCompetenceInterval(nov1, NOV_5);
        verify(incomeScheduleService).findOccurrencesPlannedBetween(nov1, NOV_5);
        assertEquals(first.getCode(), preview.leavingCycleCode());
        assertEquals(second.getCode(), preview.receivingCycleCode());
    }

    @Test
    void shouldChangeNothingOnPreview() {
        final PayCycleEntity first = entity(OCT_5, NOV_5, OCT);
        final PayCycleEntity second = entity(NOV_5, DEC_7, NOV);
        stubReadChain(first, second, entity(DEC_7, JAN_5, DEC));

        service.previewEndAdjustment(first.getCode(), NOV_10);

        assertEquals(NOV_5, first.getEndDate());
        assertFalse(first.getEndAdjusted());
        assertEquals(NOV_5, second.getStartDate());
        assertFalse(second.getStartAdjusted());
        assertEquals(0L, first.getVersion());
        verify(cycleRepository, never()).save(any());
        verify(cycleRepository, never()).flush();
        verify(configService, never()).lock();
        verifyNoInteractions(adjustmentRepository);
    }

    @Test
    void shouldRejectAPreviewWithTheDateOfTheBoundaryThatAlreadyExists() {
        final PayCycleEntity first = entity(OCT_5, NOV_5, OCT);
        stubPairLookup(first, entity(NOV_5, DEC_7, NOV));

        final InvalidFieldsException exception = assertThrows(InvalidFieldsException.class,
                () -> service.previewEndAdjustment(first.getCode(), NOV_5));

        assertTrue(exception.getValidations().get("newEndDate").contains("2026-11-05"));
        verifyNoInteractions(financialMovementService);
    }

    @Test
    void shouldRejectAPreviewThatWouldLeaveACycleEmpty() {
        final PayCycleEntity first = entity(OCT_5, NOV_5, OCT);
        stubPairLookup(first, entity(NOV_5, DEC_7, NOV));

        assertTrue(assertThrows(InvalidFieldsException.class,
                () -> service.previewEndAdjustment(first.getCode(), OCT_5)).getValidations().containsKey("newEndDate"));
        assertTrue(assertThrows(InvalidFieldsException.class,
                () -> service.previewEndAdjustment(first.getCode(), DEC_7)).getValidations().containsKey("newEndDate"));
        verifyNoInteractions(financialMovementService);
    }

    @Test
    void shouldRejectAPreviewWithoutADate() {
        final PayCycleEntity first = entity(OCT_5, NOV_5, OCT);
        stubPairLookup(first, entity(NOV_5, DEC_7, NOV));

        final InvalidFieldsException exception = assertThrows(InvalidFieldsException.class,
                () -> service.previewEndAdjustment(first.getCode(), null));

        assertTrue(exception.getValidations().containsKey("newEndDate"));
    }

    @Test
    void shouldRejectAPreviewWhenThePayCyclesAreOff() {
        when(configService.find()).thenReturn(buildCalendarMonthConfig());

        assertThrows(ConflictException.class, () -> service.previewEndAdjustment(UUID.randomUUID(), NOV_10));
        verifyNoInteractions(cycleRepository, financialMovementService);
    }

    @Test
    void shouldRejectAPreviewOfTheLastCycleOfTheChain() {
        final PayCycleEntity last = entity(DEC_7, JAN_5, DEC);
        stubPairLookup(entity(OCT_5, NOV_5, OCT), entity(NOV_5, DEC_7, NOV), last);

        final ConflictException exception = assertThrows(ConflictException.class,
                () -> service.previewEndAdjustment(last.getCode(), LocalDate.of(2026, 12, 20)));

        assertTrue(exception.getMessage().contains("não tem ciclo seguinte"));
    }

    @Test
    void shouldRejectAPreviewOfACycleOfAnEarlierActivation() {
        final PayCycleEntity history = entity(LocalDate.of(2026, 8, 5), LocalDate.of(2026, 9, 7), YearMonth.of(2026, 8));
        stubPairLookup(history, entity(OCT_5, NOV_5, OCT), entity(NOV_5, DEC_7, NOV));
        when(cycleRepository.findByCode(history.getCode())).thenReturn(Optional.of(history));

        final ConflictException exception = assertThrows(ConflictException.class,
                () -> service.previewEndAdjustment(history.getCode(), LocalDate.of(2026, 8, 20)));

        assertTrue(exception.getMessage().contains("ativação anterior"));
    }

    @Test
    void shouldRejectAPreviewOfACancelledCycle() {
        final PayCycleEntity cancelled = entity(DEC_7, JAN_5, DEC);
        cancelled.setLifecycle(PayCycleLifecycle.CANCELLED);
        stubPairLookup(entity(OCT_5, NOV_5, OCT), entity(NOV_5, DEC_7, NOV));
        when(cycleRepository.findByCode(cancelled.getCode())).thenReturn(Optional.of(cancelled));

        final ConflictException exception = assertThrows(ConflictException.class,
                () -> service.previewEndAdjustment(cancelled.getCode(), LocalDate.of(2026, 12, 20)));

        assertTrue(exception.getMessage().contains("cancelado"));
    }

    @Test
    void shouldFailToPreviewACycleThatDoesNotExist() {
        final UUID code = UUID.randomUUID();
        stubPairLookup(entity(OCT_5, NOV_5, OCT), entity(NOV_5, DEC_7, NOV));
        when(cycleRepository.findByCode(code)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> service.previewEndAdjustment(code, NOV_10));
    }

    // ---- end adjustment: apply

    @Test
    void shouldMoveTheBoundaryOfBothCyclesAndMarkThemAsAdjusted() {
        final PayCycleEntity first = entity(OCT_5, NOV_5, OCT);
        final PayCycleEntity second = entity(NOV_5, DEC_7, NOV);
        final PayCycleEntity third = entity(DEC_7, JAN_5, DEC);
        stubAdjustEnd(first, second, third);

        final PayCycleEndAdjustmentResult result = service.adjustEnd(first.getCode(), NOV_10, JUSTIFICATION, 0L, 0L);

        assertEquals(OCT_5, first.getStartDate());
        assertEquals(NOV_10, first.getEndDate());
        assertTrue(first.getEndAdjusted());
        assertFalse(first.getStartAdjusted());
        assertEquals(NOV_10, second.getStartDate());
        assertEquals(DEC_7, second.getEndDate());
        assertTrue(second.getStartAdjusted());
        assertFalse(second.getEndAdjusted());
        assertEquals(DEC_7, third.getStartDate());
        assertFalse(third.getStartAdjusted());
        assertEquals(first.getCode(), result.previous().cycle().code());
        assertEquals(NOV_10, result.previous().cycle().endDate());
        assertEquals(second.getCode(), result.next().cycle().code());
        assertEquals(NOV_10, result.next().cycle().startDate());
    }

    @Test
    void shouldLeaveNoGapAndNoOverlapAfterTheAdjustment() {
        final PayCycleEntity first = entity(OCT_5, NOV_5, OCT);
        final PayCycleEntity second = entity(NOV_5, DEC_7, NOV);
        final PayCycleEntity third = entity(DEC_7, JAN_5, DEC);
        stubAdjustEnd(first, second, third);

        service.adjustEnd(first.getCode(), LocalDate.of(2026, 11, 1), JUSTIFICATION, 0L, 0L);

        assertEquals(first.getEndDate(), second.getStartDate());
        assertEquals(second.getEndDate(), third.getStartDate());
        assertTrue(first.getStartDate().isBefore(first.getEndDate()));
        assertTrue(second.getStartDate().isBefore(second.getEndDate()));
    }

    @Test
    void shouldWriteTheHistoryInTheSameTransactionAsTheChange() {
        final PayCycleEntity first = entity(OCT_5, NOV_5, OCT);
        final PayCycleEntity second = entity(NOV_5, DEC_7, NOV);
        stubAdjustEnd(first, second, entity(DEC_7, JAN_5, DEC));

        final PayCycleEndAdjustmentResult result =
                service.adjustEnd(first.getCode(), NOV_10, "  " + JUSTIFICATION + "  ", 0L, 0L);

        final PayCycleAdjustmentEntity recorded = savedAdjustment();
        assertEquals(PayCycleAdjustmentType.MANUAL_BOUNDARY, recorded.getType());
        assertEquals(first, recorded.getPreviousCycle());
        assertEquals(second, recorded.getNextCycle());
        assertEquals(NOV_5, recorded.getOldBoundary());
        assertEquals(NOV_10, recorded.getNewBoundary());
        assertEquals(JUSTIFICATION, recorded.getJustification());
        assertEquals(0L, recorded.getExpectedPreviousVersion());
        assertEquals(0L, recorded.getExpectedNextVersion());
        assertNull(recorded.getExpectedConfigVersion());
        assertEquals(recorded.getCode(), result.adjustment().code());
        assertEquals(PayCycleAdjustmentType.MANUAL_BOUNDARY, result.adjustment().type());
        assertEquals(first.getCode(), result.adjustment().previousCycleCode());
        assertEquals(second.getCode(), result.adjustment().nextCycleCode());
    }

    @Test
    void shouldLockInOrderAndWriteTheCyclesBeforeTheHistory() {
        final PayCycleEntity first = entity(OCT_5, NOV_5, OCT);
        stubAdjustEnd(first, entity(NOV_5, DEC_7, NOV), entity(DEC_7, JAN_5, DEC));

        service.adjustEnd(first.getCode(), NOV_10, JUSTIFICATION, 0L, 0L);

        final InOrder order = inOrder(configService, cycleRepository, adjustmentRepository);
        order.verify(configService).lock();
        order.verify(cycleRepository).findForUpdateByLifecycle(PayCycleLifecycle.ACTIVE);
        order.verify(cycleRepository).flush();
        order.verify(adjustmentRepository).save(any(PayCycleAdjustmentEntity.class));
    }

    @Test
    void shouldKeepThePendingDivergenceFromTheAnchorAfterTheAdjustment() {
        final PayCycleEntity first = entity(OCT_5, NOV_5, OCT);
        final PayCycleEntity second = entity(NOV_5, DEC_7, NOV);
        stubAdjustEnd(first, second, entity(DEC_7, JAN_5, DEC));

        final PayCycleEndAdjustmentResult result = service.adjustEnd(first.getCode(), NOV_10, JUSTIFICATION, 0L, 0L);

        assertEquals(List.of(PayCyclePending.boundaryDiverges(second.getCode(), NOV, NOV_10, NOV_5)),
                result.next().pending());
    }

    @Test
    void shouldAdjustTheEndOfACycleThatHasNotStarted() {
        final PayCycleEntity second = entity(NOV_5, DEC_7, NOV);
        final PayCycleEntity third = entity(DEC_7, JAN_5, DEC);
        stubAdjustEnd(entity(OCT_5, NOV_5, OCT), second, third);

        service.adjustEnd(second.getCode(), LocalDate.of(2026, 12, 10), JUSTIFICATION, 0L, 0L);

        assertEquals(LocalDate.of(2026, 12, 10), second.getEndDate());
        assertEquals(LocalDate.of(2026, 12, 10), third.getStartDate());
    }

    @Test
    void shouldAdjustAPastBoundary() {
        final PayCycleEntity first = entity(OCT_5, NOV_5, OCT);
        final PayCycleEntity second = entity(NOV_5, DEC_7, NOV);
        stubAdjustEnd(first, second, entity(DEC_7, JAN_5, DEC));

        service.adjustEnd(first.getCode(), LocalDate.of(2026, 10, 20), JUSTIFICATION, 0L, 0L);

        assertEquals(LocalDate.of(2026, 10, 20), first.getEndDate());
        assertEquals(LocalDate.of(2026, 10, 20), second.getStartDate());
    }

    @Test
    void shouldRejectAnAdjustmentWhenTheVersionOfTheCycleIsStale() {
        final PayCycleEntity first = entity(OCT_5, NOV_5, OCT);
        final PayCycleEntity second = entity(NOV_5, DEC_7, NOV);
        stubAdjustmentLocks(first, second, entity(DEC_7, JAN_5, DEC));

        final ConflictException exception = assertThrows(ConflictException.class,
                () -> service.adjustEnd(first.getCode(), NOV_10, JUSTIFICATION, 3L, 0L));

        assertTrue(exception.getMessage().contains("recarregue"));
        assertNothingWasWritten(first, second);
    }

    @Test
    void shouldRejectAnAdjustmentWhenTheVersionOfTheNextCycleIsStale() {
        final PayCycleEntity first = entity(OCT_5, NOV_5, OCT);
        final PayCycleEntity second = entity(NOV_5, DEC_7, NOV);
        stubAdjustmentLocks(first, second, entity(DEC_7, JAN_5, DEC));

        assertThrows(ConflictException.class,
                () -> service.adjustEnd(first.getCode(), NOV_10, JUSTIFICATION, 0L, 3L));

        assertNothingWasWritten(first, second);
    }

    @Test
    void shouldRejectAnAdjustmentWithoutTheExpectedVersions() {
        final PayCycleEntity first = entity(OCT_5, NOV_5, OCT);
        final PayCycleEntity second = entity(NOV_5, DEC_7, NOV);
        stubAdjustmentLocks(first, second, entity(DEC_7, JAN_5, DEC));

        assertThrows(ConflictException.class, () -> service.adjustEnd(first.getCode(), NOV_10, JUSTIFICATION, null, 0L));
        assertThrows(ConflictException.class, () -> service.adjustEnd(first.getCode(), NOV_10, JUSTIFICATION, 0L, null));

        assertNothingWasWritten(first, second);
    }

    @Test
    void shouldRejectAnAdjustmentWithoutAJustification() {
        final InvalidFieldsException blank = assertThrows(InvalidFieldsException.class,
                () -> service.adjustEnd(UUID.randomUUID(), NOV_10, "  ", 0L, 0L));
        final InvalidFieldsException missing = assertThrows(InvalidFieldsException.class,
                () -> service.adjustEnd(UUID.randomUUID(), NOV_10, null, 0L, 0L));

        assertTrue(blank.getValidations().containsKey("justification"));
        assertTrue(missing.getValidations().containsKey("justification"));
        verifyNoInteractions(configService, cycleRepository, adjustmentRepository);
    }

    @Test
    void shouldRejectAnAdjustmentWithATooLongJustification() {
        final InvalidFieldsException exception = assertThrows(InvalidFieldsException.class,
                () -> service.adjustEnd(UUID.randomUUID(), NOV_10,
                        "x".repeat(PayCycleService.JUSTIFICATION_MAX_LENGTH + 1), 0L, 0L));

        assertTrue(exception.getValidations().get("justification").contains("500"));
        verifyNoInteractions(configService, cycleRepository, adjustmentRepository);
    }

    @Test
    void shouldRejectAnAdjustmentToTheSameDate() {
        final PayCycleEntity first = entity(OCT_5, NOV_5, OCT);
        final PayCycleEntity second = entity(NOV_5, DEC_7, NOV);
        stubAdjustmentLocks(first, second, entity(DEC_7, JAN_5, DEC));

        final InvalidFieldsException exception = assertThrows(InvalidFieldsException.class,
                () -> service.adjustEnd(first.getCode(), NOV_5, JUSTIFICATION, 0L, 0L));

        assertTrue(exception.getValidations().containsKey("newEndDate"));
        assertNothingWasWritten(first, second);
    }

    @Test
    void shouldRejectAnAdjustmentThatWouldLeaveACycleEmpty() {
        final PayCycleEntity first = entity(OCT_5, NOV_5, OCT);
        final PayCycleEntity second = entity(NOV_5, DEC_7, NOV);
        stubAdjustmentLocks(first, second, entity(DEC_7, JAN_5, DEC));

        assertThrows(InvalidFieldsException.class, () -> service.adjustEnd(first.getCode(), OCT_5, JUSTIFICATION, 0L, 0L));
        assertThrows(InvalidFieldsException.class, () -> service.adjustEnd(first.getCode(), DEC_7, JUSTIFICATION, 0L, 0L));

        assertNothingWasWritten(first, second);
    }

    @Test
    void shouldRejectAnAdjustmentWithoutADate() {
        final PayCycleEntity first = entity(OCT_5, NOV_5, OCT);
        final PayCycleEntity second = entity(NOV_5, DEC_7, NOV);
        stubAdjustmentLocks(first, second, entity(DEC_7, JAN_5, DEC));

        final InvalidFieldsException exception = assertThrows(InvalidFieldsException.class,
                () -> service.adjustEnd(first.getCode(), null, JUSTIFICATION, 0L, 0L));

        assertTrue(exception.getValidations().containsKey("newEndDate"));
        assertNothingWasWritten(first, second);
    }

    @Test
    void shouldRejectAnAdjustmentWhenThePayCyclesAreOff() {
        when(configService.lock()).thenReturn(buildCalendarMonthConfig());

        assertThrows(ConflictException.class, () -> service.adjustEnd(UUID.randomUUID(), NOV_10, JUSTIFICATION, 0L, 0L));
        verifyNoInteractions(cycleRepository, adjustmentRepository);
    }

    @Test
    void shouldRejectAnAdjustmentOfTheLastCycleOfTheChain() {
        final PayCycleEntity last = entity(DEC_7, JAN_5, DEC);
        stubAdjustmentLocks(entity(OCT_5, NOV_5, OCT), entity(NOV_5, DEC_7, NOV), last);

        assertThrows(ConflictException.class,
                () -> service.adjustEnd(last.getCode(), LocalDate.of(2026, 12, 20), JUSTIFICATION, 0L, 0L));

        assertEquals(JAN_5, last.getEndDate());
        verify(cycleRepository, never()).flush();
        verifyNoInteractions(adjustmentRepository);
    }

    @Test
    void shouldRejectAnAdjustmentOfACycleOfAnEarlierActivation() {
        final PayCycleEntity history = entity(LocalDate.of(2026, 8, 5), LocalDate.of(2026, 9, 7), YearMonth.of(2026, 8));
        final PayCycleEntity first = entity(OCT_5, NOV_5, OCT);
        stubAdjustmentLocks(history, first, entity(NOV_5, DEC_7, NOV));
        when(cycleRepository.findByCode(history.getCode())).thenReturn(Optional.of(history));

        assertThrows(ConflictException.class,
                () -> service.adjustEnd(history.getCode(), LocalDate.of(2026, 8, 20), JUSTIFICATION, 0L, 0L));

        assertEquals(LocalDate.of(2026, 9, 7), history.getEndDate());
        verifyNoInteractions(adjustmentRepository);
    }

    @Test
    void shouldRejectAnAdjustmentOfACancelledCycle() {
        final PayCycleEntity cancelled = entity(DEC_7, JAN_5, DEC);
        cancelled.setLifecycle(PayCycleLifecycle.CANCELLED);
        stubAdjustmentLocks(entity(OCT_5, NOV_5, OCT), entity(NOV_5, DEC_7, NOV));
        when(cycleRepository.findByCode(cancelled.getCode())).thenReturn(Optional.of(cancelled));

        assertThrows(ConflictException.class,
                () -> service.adjustEnd(cancelled.getCode(), LocalDate.of(2026, 12, 20), JUSTIFICATION, 0L, 0L));

        verifyNoInteractions(adjustmentRepository);
    }

    @Test
    void shouldFailToAdjustACycleThatDoesNotExist() {
        final UUID code = UUID.randomUUID();
        stubAdjustmentLocks(entity(OCT_5, NOV_5, OCT), entity(NOV_5, DEC_7, NOV));
        when(cycleRepository.findByCode(code)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> service.adjustEnd(code, NOV_10, JUSTIFICATION, 0L, 0L));
        verifyNoInteractions(adjustmentRepository);
    }

    // ---- end adjustment: history

    @Test
    void shouldFindTheHistoryOfACycle() {
        final PayCycleEntity first = entity(OCT_5, NOV_5, OCT);
        final PayCycleEntity second = entity(NOV_5, DEC_7, NOV);
        final PayCycleAdjustmentEntity newer = PayCycleAdjustmentEntity.builder()
                .code(UUID.randomUUID())
                .type(PayCycleAdjustmentType.MANUAL_BOUNDARY)
                .previousCycle(first)
                .nextCycle(second)
                .oldBoundary(NOV_5)
                .newBoundary(NOV_10)
                .justification(JUSTIFICATION)
                .expectedPreviousVersion(0L)
                .expectedNextVersion(0L)
                .createdDate(Instant.parse("2026-11-02T13:00:00Z"))
                .build();
        when(cycleRepository.findByCode(second.getCode())).thenReturn(Optional.of(second));
        when(adjustmentRepository.findByCycleId(second.getId())).thenReturn(List.of(newer));

        final List<PayCycleAdjustment> history = service.findAdjustments(second.getCode());

        assertEquals(1, history.size());
        assertEquals(newer.getCode(), history.get(0).code());
        assertEquals(PayCycleAdjustmentType.MANUAL_BOUNDARY, history.get(0).type());
        assertEquals(first.getCode(), history.get(0).previousCycleCode());
        assertEquals(second.getCode(), history.get(0).nextCycleCode());
        assertEquals(NOV_5, history.get(0).oldBoundary());
        assertEquals(NOV_10, history.get(0).newBoundary());
        assertEquals(JUSTIFICATION, history.get(0).justification());
        assertEquals(Instant.parse("2026-11-02T13:00:00Z"), history.get(0).createdDate());
    }

    @Test
    void shouldFindAnEmptyHistoryForACycleNothingTouched() {
        final PayCycleEntity first = entity(OCT_5, NOV_5, OCT);
        when(cycleRepository.findByCode(first.getCode())).thenReturn(Optional.of(first));
        when(adjustmentRepository.findByCycleId(first.getId())).thenReturn(List.of());

        assertTrue(service.findAdjustments(first.getCode()).isEmpty());
    }

    @Test
    void shouldFindTheHistoryOfACancelledCycle() {
        final PayCycleEntity cancelled = entity(DEC_7, JAN_5, DEC);
        cancelled.setLifecycle(PayCycleLifecycle.CANCELLED);
        when(cycleRepository.findByCode(cancelled.getCode())).thenReturn(Optional.of(cancelled));
        when(adjustmentRepository.findByCycleId(cancelled.getId())).thenReturn(List.of());

        service.findAdjustments(cancelled.getCode());

        verify(adjustmentRepository).findByCycleId(cancelled.getId());
    }

    @Test
    void shouldFailToFindTheHistoryOfACycleThatDoesNotExist() {
        final UUID code = UUID.randomUUID();
        when(cycleRepository.findByCode(code)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> service.findAdjustments(code));
        verifyNoInteractions(adjustmentRepository);
    }

    // ---- refresh

    @Test
    void shouldDoNothingOnRefreshWhenThePayCyclesAreOff() {
        when(configService.lock()).thenReturn(buildCalendarMonthConfig());

        service.refresh();

        verifyNoInteractions(incomeScheduleService, cycleRepository, holidayCalendarService);
    }

    @Test
    void shouldCompleteTheHorizonOnRefresh() {
        final PayCycleEntity first = entity(OCT_5, NOV_5, OCT);
        stubRefresh(CALENDAR, first);

        service.refresh();

        final ArgumentCaptor<PayCycleEntity> saved = ArgumentCaptor.forClass(PayCycleEntity.class);
        verify(cycleRepository, times(2)).save(saved.capture());
        assertEquals(NOV_5, saved.getAllValues().get(0).getStartDate());
        assertEquals(DEC_7, saved.getAllValues().get(0).getEndDate());
        assertEquals(NOV.atDay(1), saved.getAllValues().get(0).getAnchorNominalMonth());
        assertEquals(DEC_7, saved.getAllValues().get(1).getStartDate());
        assertEquals(JAN_5, saved.getAllValues().get(1).getEndDate());
    }

    @Test
    void shouldWriteNothingOnRefreshWhenTheChainIsAlreadyComplete() {
        final PayCycleEntity first = entity(OCT_5, NOV_5, OCT);
        final PayCycleEntity second = entity(NOV_5, DEC_7, NOV);
        final PayCycleEntity third = entity(DEC_7, JAN_5, DEC);
        stubRefresh(CALENDAR, first, second, third);

        service.refresh();

        verify(cycleRepository, never()).save(any());
        assertEquals(NOV_5, first.getEndDate());
        assertEquals(DEC_7, second.getEndDate());
        assertEquals(JAN_5, third.getEndDate());
    }

    @Test
    void shouldNotMoveTheEndOfTheCycleThatAlreadyStartedWhenTheCalendarChanges() {
        final PayCycleEntity first = entity(OCT_5, NOV_5, OCT);
        final PayCycleEntity second = entity(NOV_5, DEC_7, NOV);
        final PayCycleEntity third = entity(DEC_7, JAN_5, DEC);
        // A holiday on Nov 5 moves the November occurrence to Nov 6, but the October cycle is open.
        stubRefresh(buildCalendar(Set.of(2026, 2027, 2028), buildHoliday(2026, 11, 5)), first, second, third);

        service.refresh();

        assertEquals(NOV_5, first.getEndDate());
        assertEquals(NOV_5, second.getStartDate());
        verify(cycleRepository, never()).save(any());
    }

    @Test
    void shouldMoveTheEndOfACycleThatHasNotStartedWhenTheCalendarChanges() {
        final PayCycleEntity first = entity(OCT_5, NOV_5, OCT);
        final PayCycleEntity second = entity(NOV_5, DEC_7, NOV);
        final PayCycleEntity third = entity(DEC_7, JAN_5, DEC);
        // A holiday on Jan 5 moves the January occurrence to Jan 6: the December cycle has not started.
        stubRefresh(buildCalendar(Set.of(2026, 2027, 2028), buildHoliday(2027, 1, 5)), first, second, third);

        service.refresh();

        assertEquals(LocalDate.of(2027, 1, 6), third.getEndDate());
        assertEquals(DEC_7, third.getStartDate());
        verify(cycleRepository, never()).save(any());
    }

    @Test
    void shouldIgnoreTheCyclesOfAnEarlierActivation() {
        final PayCycleEntity history = entity(LocalDate.of(2026, 8, 5), LocalDate.of(2026, 9, 7), YearMonth.of(2026, 8));
        final PayCycleEntity first = entity(OCT_5, NOV_5, OCT);
        stubRefresh(CALENDAR, history, first);

        service.refresh();

        assertEquals(LocalDate.of(2026, 9, 7), history.getEndDate());
        verify(cycleRepository, times(2)).save(any(PayCycleEntity.class));
    }

    // ---- reading

    @Test
    void shouldReadTheConfigurationWithNoPendingWhenThePayCyclesAreOff() {
        when(configService.find()).thenReturn(buildCalendarMonthConfig());

        final PayCycleConfigView view = service.findConfig();

        assertEquals(PayCycleMode.CALENDAR_MONTH, view.config().mode());
        assertTrue(view.pending().isEmpty());
        verifyNoInteractions(cycleRepository);
    }

    @Test
    void shouldReadWhatIsPendingWithoutSavingAnything() {
        when(configService.find()).thenReturn(buildPayCycleConfig(OCT_5, 2L));
        when(incomeScheduleService.findByCode(SCHEDULE_CODE)).thenReturn(octoberSchedule);
        when(cycleRepository.findByLifecycleIn(List.of(PayCycleLifecycle.ACTIVE)))
                .thenReturn(List.of(entity(OCT_5, NOV_5, OCT)));
        when(holidayCalendarService.findCalendar()).thenReturn(buildReviewedCalendar(2026));

        final PayCycleConfigView view = service.findConfig();

        assertTrue(view.pending().contains(PayCyclePending.calendarNotReviewed(JAN, 2027)));
        verify(cycleRepository, never()).save(any());
    }

    @Test
    void shouldFindTheCurrentCycle() {
        final PayCycleEntity first = entity(OCT_5, NOV_5, OCT);
        stubReadChain(first, entity(NOV_5, DEC_7, NOV), entity(DEC_7, JAN_5, DEC));

        final CurrentPayCycle current = service.findCurrent();

        assertEquals(first.getCode(), current.cycle().cycle().code());
        assertEquals(PayCycleStatus.OPEN, current.cycle().status());
    }

    @Test
    void shouldIgnoreAnOpenCycleFromAnEarlierActivation() {
        when(configService.find()).thenReturn(buildPayCycleConfig(NOV_5, 2L));
        when(incomeScheduleService.findByCode(SCHEDULE_CODE)).thenReturn(octoberSchedule);
        when(cycleRepository.findByLifecycleIn(List.of(PayCycleLifecycle.ACTIVE)))
                .thenReturn(List.of(entity(OCT_5, NOV_5, OCT)));
        when(holidayCalendarService.findCalendar()).thenReturn(CALENDAR);

        final CurrentPayCycle current = service.findCurrent();

        assertNull(current.cycle());
    }

    @Test
    void shouldKeepNoCurrentCycleUntilThePlannedExtensionIsPersisted() {
        when(configService.find()).thenReturn(buildPayCycleConfig(OCT_5, 2L));
        when(incomeScheduleService.findByCode(SCHEDULE_CODE)).thenReturn(octoberSchedule);
        when(cycleRepository.findByLifecycleIn(List.of(PayCycleLifecycle.ACTIVE)))
                .thenReturn(List.of(entity(OCT_5, LocalDate.of(2026, 11, 1), OCT)));
        when(holidayCalendarService.findCalendar()).thenReturn(CALENDAR);

        final PayCycleConfigView view = service.findConfig();

        assertTrue(view.pending().contains(PayCyclePending.noCurrentCycle()));
    }

    @Test
    void shouldFindNoCurrentCycleWhenThePayCyclesAreOff() {
        when(configService.find()).thenReturn(buildCalendarMonthConfig());

        final CurrentPayCycle current = service.findCurrent();

        assertNull(current.cycle());
        assertTrue(current.pending().isEmpty());
        verifyNoInteractions(cycleRepository);
    }

    @Test
    void shouldFindOnlyTheCycleThatContainsTheDate() {
        stubCyclesInCalendarMonthMode(entity(OCT_5, NOV_5, OCT), entity(NOV_5, DEC_7, NOV), entity(DEC_7, JAN_5, DEC));

        final List<PayCycleView> views = service.findCycles(DEC_7, null, null, false);

        assertEquals(1, views.size());
        assertEquals(DEC_7, views.get(0).cycle().startDate());
    }

    @Test
    void shouldGiveTheBoundaryDayToTheCycleThatOpensOnIt() {
        stubCyclesInCalendarMonthMode(entity(OCT_5, NOV_5, OCT), entity(NOV_5, DEC_7, NOV));

        final List<PayCycleView> views = service.findCycles(NOV_5, null, null, false);

        assertEquals(1, views.size());
        assertEquals(NOV_5, views.get(0).cycle().startDate());
    }

    @Test
    void shouldFindTheCyclesThatOverlapARange() {
        stubCyclesInCalendarMonthMode(entity(OCT_5, NOV_5, OCT), entity(NOV_5, DEC_7, NOV), entity(DEC_7, JAN_5, DEC));

        final List<PayCycleView> views = service.findCycles(null, LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 10), false);

        assertEquals(2, views.size());
        assertEquals(NOV_5, views.get(0).cycle().startDate());
        assertEquals(DEC_7, views.get(1).cycle().startDate());
    }

    @Test
    void shouldNotOverlapARangeThatStartsOnTheEndDay() {
        stubCyclesInCalendarMonthMode(entity(OCT_5, NOV_5, OCT), entity(NOV_5, DEC_7, NOV));

        final List<PayCycleView> views = service.findCycles(null, NOV_5, null, false);

        assertEquals(1, views.size());
        assertEquals(NOV_5, views.get(0).cycle().startDate());
    }

    @Test
    void shouldAskForCancelledCyclesOnlyWhenRequested() {
        when(configService.find()).thenReturn(buildCalendarMonthConfig());

        service.findCycles(null, null, null, true);

        verify(cycleRepository).findByLifecycleIn(List.of(PayCycleLifecycle.ACTIVE, PayCycleLifecycle.CANCELLED));
    }

    @Test
    void shouldAskForActiveCyclesByDefault() {
        when(configService.find()).thenReturn(buildCalendarMonthConfig());

        service.findCycles(null, null, null, false);

        verify(cycleRepository).findByLifecycleIn(List.of(PayCycleLifecycle.ACTIVE));
    }

    @Test
    void shouldRejectADateCombinedWithARange() {
        final InvalidFieldsException exception = assertThrows(InvalidFieldsException.class,
                () -> service.findCycles(NOV_5, NOV_5, null, false));

        assertTrue(exception.getValidations().containsKey("date"));
        verifyNoInteractions(cycleRepository);
    }

    @Test
    void shouldRejectAnInvertedRange() {
        final InvalidFieldsException exception = assertThrows(InvalidFieldsException.class,
                () -> service.findCycles(null, DEC_7, NOV_5, false));

        assertTrue(exception.getValidations().containsKey("to"));
    }

    @Test
    void shouldFindACycleByCode() {
        final PayCycleEntity entity = entity(OCT_5, NOV_5, OCT);
        when(cycleRepository.findByCode(entity.getCode())).thenReturn(Optional.of(entity));
        when(configService.find()).thenReturn(buildCalendarMonthConfig());

        final PayCycleView view = service.findByCode(entity.getCode());

        assertEquals(entity.getCode(), view.cycle().code());
        assertEquals(PayCycleStatus.OPEN, view.status());
    }

    @Test
    void shouldFailToFindACycleThatDoesNotExist() {
        final UUID code = UUID.randomUUID();
        when(cycleRepository.findByCode(code)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> service.findByCode(code));
    }

    // ---- refresh keeps what was adjusted by hand

    @Test
    void shouldKeepAnAdjustedBoundaryOfCyclesThatHaveNotStartedOnRefresh() {
        final PayCycleEntity first = entity(OCT_5, NOV_5, OCT);
        final PayCycleEntity second = entity(NOV_5, LocalDate.of(2026, 12, 10), NOV);
        final PayCycleEntity third = entity(LocalDate.of(2026, 12, 10), JAN_5, DEC);
        second.setEndAdjusted(true);
        third.setStartAdjusted(true);
        stubRefresh(CALENDAR, first, second, third);

        service.refresh();

        assertEquals(LocalDate.of(2026, 12, 10), second.getEndDate());
        assertEquals(LocalDate.of(2026, 12, 10), third.getStartDate());
        verify(cycleRepository, never()).save(any());
    }

    @Test
    void shouldMoveTheSameBoundaryBackOnRefreshWhenItWasNotAdjusted() {
        final PayCycleEntity first = entity(OCT_5, NOV_5, OCT);
        final PayCycleEntity second = entity(NOV_5, LocalDate.of(2026, 12, 10), NOV);
        final PayCycleEntity third = entity(LocalDate.of(2026, 12, 10), JAN_5, DEC);
        stubRefresh(CALENDAR, first, second, third);

        service.refresh();

        assertEquals(DEC_7, second.getEndDate());
        assertEquals(DEC_7, third.getStartDate());
    }

    @Test
    void shouldKeepTheAdjustedBoundaryOfTheCycleThatAlreadyStartedOnRefresh() {
        final PayCycleEntity first = entity(OCT_5, NOV_10, OCT);
        final PayCycleEntity second = entity(NOV_10, DEC_7, NOV);
        final PayCycleEntity third = entity(DEC_7, JAN_5, DEC);
        first.setEndAdjusted(true);
        second.setStartAdjusted(true);
        stubRefresh(CALENDAR, first, second, third);

        service.refresh();

        assertEquals(NOV_10, first.getEndDate());
        assertEquals(NOV_10, second.getStartDate());
        assertEquals(DEC_7, second.getEndDate());
        verify(cycleRepository, never()).save(any());
        verifyNoInteractions(adjustmentRepository);
    }

    // ---- helpers

    private PayCycleEntity entity(final LocalDate start, final LocalDate end, final YearMonth anchorMonth) {
        return PayCycleEntity.builder()
                .id((long) (start.hashCode() & 0xffff))
                .code(UUID.randomUUID())
                .startDate(start)
                .endDate(end)
                .kind(PayCycleKind.REGULAR)
                .anchorSchedule(scheduleEntity)
                .anchorNominalMonth(anchorMonth.atDay(1))
                .lifecycle(PayCycleLifecycle.ACTIVE)
                .startAdjusted(false)
                .endAdjusted(false)
                .version(0L)
                .build();
    }

    private void stubPreview(final HolidayCalendar calendar) {
        stubPreviewUntilTheDate();
        when(holidayCalendarService.findCalendar()).thenReturn(calendar);
    }

    private void stubPreviewUntilTheDate() {
        when(configService.find()).thenReturn(buildCalendarMonthConfig());
        when(incomeScheduleService.findActiveAnchor()).thenReturn(Optional.of(novemberSchedule));
    }

    private void stubActivation(final HolidayCalendar calendar) {
        stubActivation(calendar, NOV_5);
    }

    private void stubActivation(final HolidayCalendar calendar, final LocalDate activationDate) {
        when(configService.lock()).thenReturn(buildCalendarMonthConfig());
        when(incomeScheduleService.findActiveAnchorForUpdate()).thenReturn(Optional.of(novemberSchedule));
        when(holidayCalendarService.findCalendar()).thenReturn(calendar);
        when(scheduleRepository.findByCode(SCHEDULE_CODE)).thenReturn(Optional.of(scheduleEntity));
        when(configService.switchToPayCycle(SCHEDULE_CODE, activationDate)).thenReturn(buildPayCycleConfig(activationDate, 1L));
    }

    private void stubRefresh(final HolidayCalendar calendar, final PayCycleEntity... chain) {
        when(configService.lock()).thenReturn(buildPayCycleConfig(OCT_5, 2L));
        when(incomeScheduleService.findByCodeForUpdate(SCHEDULE_CODE)).thenReturn(octoberSchedule);
        when(cycleRepository.findForUpdateByLifecycle(PayCycleLifecycle.ACTIVE)).thenReturn(List.of(chain));
        when(holidayCalendarService.findCalendar()).thenReturn(calendar);
        when(scheduleRepository.findByCode(SCHEDULE_CODE)).thenReturn(Optional.of(scheduleEntity));
    }

    private void stubReadChain(final PayCycleEntity... chain) {
        when(configService.find()).thenReturn(buildPayCycleConfig(OCT_5, 2L));
        when(incomeScheduleService.findByCode(SCHEDULE_CODE)).thenReturn(octoberSchedule);
        when(cycleRepository.findByLifecycleIn(List.of(PayCycleLifecycle.ACTIVE))).thenReturn(List.of(chain));
        when(holidayCalendarService.findCalendar()).thenReturn(CALENDAR);
    }

    private void stubCyclesInCalendarMonthMode(final PayCycleEntity... cycles) {
        when(configService.find()).thenReturn(buildCalendarMonthConfig());
        when(cycleRepository.findByLifecycleIn(List.of(PayCycleLifecycle.ACTIVE))).thenReturn(List.of(cycles));
    }

    private void stubDeactivation(final long version, final PayCycleEntity... chain) {
        when(configService.lock()).thenReturn(buildPayCycleConfig(OCT_5, version));
        when(cycleRepository.findForUpdateByLifecycle(PayCycleLifecycle.ACTIVE)).thenReturn(List.of(chain));
        when(configService.switchToCalendarMonth()).thenReturn(buildCalendarMonthConfig());
    }

    /**
     * Only what the preview needs to find the pair: the configuration and the active cycles.
     */
    private void stubPairLookup(final PayCycleEntity... chain) {
        when(configService.find()).thenReturn(buildPayCycleConfig(OCT_5, 2L));
        when(cycleRepository.findByLifecycleIn(List.of(PayCycleLifecycle.ACTIVE))).thenReturn(List.of(chain));
    }

    /**
     * The locks an adjustment takes before it looks at versions and dates.
     */
    private void stubAdjustmentLocks(final PayCycleEntity... chain) {
        when(configService.lock()).thenReturn(buildPayCycleConfig(OCT_5, 2L));
        when(cycleRepository.findForUpdateByLifecycle(PayCycleLifecycle.ACTIVE)).thenReturn(List.of(chain));
    }

    /**
     * Everything a successful adjustment touches: the locks, the pendencies read after the change and
     * the save of the history.
     */
    private void stubAdjustEnd(final PayCycleEntity... chain) {
        stubAdjustmentLocks(chain);
        when(cycleRepository.findByLifecycleIn(List.of(PayCycleLifecycle.ACTIVE))).thenReturn(List.of(chain));
        when(incomeScheduleService.findByCode(SCHEDULE_CODE)).thenReturn(octoberSchedule);
        when(holidayCalendarService.findCalendar()).thenReturn(CALENDAR);
        when(adjustmentRepository.save(any(PayCycleAdjustmentEntity.class))).thenAnswer(call -> call.getArgument(0));
    }

    private PayCycleAdjustmentEntity savedAdjustment() {
        final ArgumentCaptor<PayCycleAdjustmentEntity> saved = ArgumentCaptor.forClass(PayCycleAdjustmentEntity.class);
        verify(adjustmentRepository).save(saved.capture());
        return saved.getValue();
    }

    /**
     * A refused adjustment leaves the pair as it was and writes neither the cycles nor the history.
     */
    private void assertNothingWasWritten(final PayCycleEntity first, final PayCycleEntity second) {
        assertEquals(NOV_5, first.getEndDate());
        assertFalse(first.getEndAdjusted());
        assertEquals(NOV_5, second.getStartDate());
        assertFalse(second.getStartAdjusted());
        verify(cycleRepository, never()).flush();
        verify(cycleRepository, never()).save(any());
        verifyNoInteractions(adjustmentRepository);
    }

    private IncomeOccurrenceImpact incomeImpact(final LocalDate plannedDate) {
        return new IncomeOccurrenceImpact(SCHEDULE_CODE, "Salário", new IncomeOccurrenceView(
                NOV, IncomeOccurrenceView.State.PROJECTED, plannedDate, new BigDecimal("1000.00"), null, null));
    }
}
