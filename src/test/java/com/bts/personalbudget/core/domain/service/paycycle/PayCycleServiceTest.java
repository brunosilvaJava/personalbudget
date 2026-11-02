package com.bts.personalbudget.core.domain.service.paycycle;

import static com.bts.personalbudget.core.domain.factory.HolidayCalendarFactory.buildCalendar;
import static com.bts.personalbudget.core.domain.factory.HolidayCalendarFactory.buildHoliday;
import static com.bts.personalbudget.core.domain.factory.HolidayCalendarFactory.buildReviewedCalendar;
import static com.bts.personalbudget.core.domain.factory.IncomeScheduleFactory.SCHEDULE_CODE;
import static com.bts.personalbudget.core.domain.factory.IncomeScheduleFactory.buildSchedule;
import static com.bts.personalbudget.core.domain.factory.PayCycleFactory.buildCalendarMonthConfig;
import static com.bts.personalbudget.core.domain.factory.PayCycleFactory.buildPayCycleConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
import com.bts.personalbudget.core.domain.entity.PayCycleEntity;
import com.bts.personalbudget.core.domain.enumerator.PayCycleKind;
import com.bts.personalbudget.core.domain.enumerator.PayCycleLifecycle;
import com.bts.personalbudget.core.domain.enumerator.PayCycleMode;
import com.bts.personalbudget.core.domain.enumerator.PayCycleStatus;
import com.bts.personalbudget.core.domain.service.calendar.HolidayCalendar;
import com.bts.personalbudget.core.domain.service.calendar.HolidayCalendarService;
import com.bts.personalbudget.core.domain.service.income.IncomeSchedule;
import com.bts.personalbudget.core.domain.service.income.IncomeScheduleService;
import com.bts.personalbudget.exception.ConflictException;
import com.bts.personalbudget.exception.InvalidFieldsException;
import com.bts.personalbudget.mapper.PayCycleMapper;
import com.bts.personalbudget.repository.IncomeScheduleRepository;
import com.bts.personalbudget.repository.PayCycleRepository;
import jakarta.persistence.EntityNotFoundException;
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
    private PayCycleRepository cycleRepository;

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

        final PayCycleConfigView view = service.deactivate(2L);

        assertEquals(PayCycleLifecycle.ACTIVE, started.getLifecycle());
        assertEquals(PayCycleLifecycle.CANCELLED, planned.getLifecycle());
        assertEquals(PayCycleLifecycle.CANCELLED, later.getLifecycle());
        assertEquals(PayCycleMode.CALENDAR_MONTH, view.config().mode());
        assertTrue(view.pending().isEmpty());
    }

    @Test
    void shouldRejectADeactivationWhenThePayCyclesAreOff() {
        when(configService.lock()).thenReturn(buildCalendarMonthConfig());

        assertThrows(ConflictException.class, () -> service.deactivate(0L));
        verifyNoInteractions(cycleRepository);
        verify(configService, never()).switchToCalendarMonth();
    }

    @Test
    void shouldRejectADeactivationWhenTheVersionIsStale() {
        final PayCycleConfig config = buildPayCycleConfig(OCT_5, 2L);
        when(configService.lock()).thenReturn(config);
        doThrow(new ConflictException("stale")).when(configService).requireVersion(config, 1L);

        assertThrows(ConflictException.class, () -> service.deactivate(1L));
        verifyNoInteractions(cycleRepository);
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
}
