package com.bts.personalbudget.core.domain.service.paycycle;

import static com.bts.personalbudget.core.domain.factory.HolidayCalendarFactory.buildReviewedCalendar;
import static com.bts.personalbudget.core.domain.factory.IncomeScheduleFactory.SCHEDULE_CODE;
import static com.bts.personalbudget.core.domain.factory.IncomeScheduleFactory.buildOccurrence;
import static com.bts.personalbudget.core.domain.factory.IncomeScheduleFactory.buildSchedule;
import static com.bts.personalbudget.core.domain.factory.PayCycleFactory.buildCalendarMonthConfig;
import static com.bts.personalbudget.core.domain.factory.PayCycleFactory.buildCycle;
import static com.bts.personalbudget.core.domain.factory.PayCycleFactory.buildPayCycleConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.bts.personalbudget.core.domain.entity.IncomeScheduleEntity;
import com.bts.personalbudget.core.domain.entity.PayCycleEntity;
import com.bts.personalbudget.core.domain.enumerator.PayCycleLifecycle;
import com.bts.personalbudget.core.domain.service.FinancialMovementService;
import com.bts.personalbudget.core.domain.service.calendar.Holiday;
import com.bts.personalbudget.core.domain.service.calendar.HolidayCalendar;
import com.bts.personalbudget.core.domain.service.calendar.HolidayCalendarService;
import com.bts.personalbudget.core.domain.service.income.IncomeOccurrence;
import com.bts.personalbudget.core.domain.service.income.IncomeScheduleService;
import com.bts.personalbudget.mapper.PayCycleMapper;
import com.bts.personalbudget.repository.IncomeScheduleRepository;
import com.bts.personalbudget.repository.PayCycleAdjustmentRepository;
import com.bts.personalbudget.repository.PayCycleRepository;
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

@ExtendWith(MockitoExtension.class)
class PayCycleCalendarPreviewTest {

    private static final LocalDate OCT_5 = LocalDate.of(2026, 10, 5);
    private static final LocalDate NOV_5 = LocalDate.of(2026, 11, 5);
    private static final LocalDate DEC_7 = LocalDate.of(2026, 12, 7);
    private static final LocalDate JAN_5 = LocalDate.of(2027, 1, 5);
    private static final HolidayCalendar CALENDAR = buildReviewedCalendar(2026, 2027);

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
    private Clock clock = Clock.fixed(Instant.parse("2026-11-02T13:00:00Z"), ZoneId.of("America/Sao_Paulo"));

    @Test
    void shouldPreviewFreeSharedBoundaryOnceWithoutWriting() {
        final List<PayCycleEntity> chain = givenChain(CALENDAR, List.of());

        final PayCycleCalendarImpact impact = service.previewCalendarChange(2026,
                List.of(new Holiday(DEC_7, "Holiday")));

        assertThat(impact.boundaries()).containsExactly(new PayCycleBoundaryImpact(
                chain.get(1).getCode(), chain.get(2).getCode(), DEC_7, DEC_7.plusDays(1)));
        assertThat(impact.divergences()).isEmpty();
        assertThat(chain.get(1).getEndDate()).isEqualTo(DEC_7);
        assertThat(chain.get(2).getStartDate()).isEqualTo(DEC_7);
        verifyNoWrites();
    }

    @Test
    void shouldReportNewDivergenceInsteadOfMovingStartedCycleBoundary() {
        final List<PayCycleEntity> chain = givenChain(CALENDAR, List.of());

        final PayCycleCalendarImpact impact = service.previewCalendarChange(2026,
                List.of(new Holiday(NOV_5, "Holiday")));

        assertThat(impact.boundaries()).isEmpty();
        assertThat(impact.divergences()).containsExactly(PayCyclePending.boundaryDiverges(
                chain.get(1).getCode(), YearMonth.of(2026, 11), NOV_5, NOV_5.plusDays(1)));
        verifyNoWrites();
    }

    @Test
    void shouldPreserveManuallyAdjustedFutureBoundaryAndReportDivergence() {
        final List<PayCycleEntity> chain = givenChain(CALENDAR, List.of());
        chain.get(1).setEndAdjusted(true);
        chain.get(2).setStartAdjusted(true);

        final PayCycleCalendarImpact impact = service.previewCalendarChange(2026,
                List.of(new Holiday(DEC_7, "Holiday")));

        assertThat(impact.boundaries()).isEmpty();
        assertThat(impact.divergences()).containsExactly(PayCyclePending.boundaryDiverges(
                chain.get(2).getCode(), YearMonth.of(2026, 12), DEC_7, DEC_7.plusDays(1)));
        verifyNoWrites();
    }

    @Test
    void shouldKeepMaterializedAnchorDateDespiteProposedHoliday() {
        givenChain(CALENDAR, List.of(buildOccurrence(YearMonth.of(2026, 12), DEC_7)));

        final PayCycleCalendarImpact impact = service.previewCalendarChange(2026,
                List.of(new Holiday(DEC_7, "Holiday")));

        assertThat(impact.boundaries()).isEmpty();
        assertThat(impact.divergences()).isEmpty();
        verifyNoWrites();
    }

    @Test
    void shouldIncludeStoredChainFinalBoundaryWhenReviewingNextYear() {
        final List<PayCycleEntity> chain = givenChain(CALENDAR, List.of());

        final PayCycleCalendarImpact impact = service.previewCalendarChange(2027,
                List.of(new Holiday(JAN_5, "Holiday")));

        assertThat(impact.boundaries()).containsExactly(new PayCycleBoundaryImpact(
                chain.get(2).getCode(), null, JAN_5, JAN_5.plusDays(1)));
        verifyNoWrites();
    }

    @Test
    void shouldOmitUnchangedDivergenceAndUnchangedBoundaries() {
        givenChain(CALENDAR.replacingYear(2026, List.of(new Holiday(NOV_5, "Holiday"))), List.of());

        final PayCycleCalendarImpact impact = service.previewCalendarChange(2026,
                List.of(new Holiday(NOV_5, "Renamed holiday")));

        assertThat(impact.boundaries()).isEmpty();
        assertThat(impact.divergences()).isEmpty();
        verifyNoWrites();
    }

    @Test
    void shouldReportChangedExistingDivergence() {
        final List<PayCycleEntity> chain = givenChain(
                CALENDAR.replacingYear(2026, List.of(new Holiday(NOV_5, "Holiday"))), List.of());

        final PayCycleCalendarImpact impact = service.previewCalendarChange(2026,
                List.of(new Holiday(NOV_5, "Holiday"), new Holiday(NOV_5.plusDays(1), "Second holiday")));

        assertThat(impact.divergences()).containsExactly(PayCyclePending.boundaryDiverges(
                chain.get(1).getCode(), YearMonth.of(2026, 11), NOV_5, NOV_5.plusDays(4)));
        verifyNoWrites();
    }

    @Test
    void shouldOmitUnchangedDivergenceOfSimulatedHorizonCycleWithoutExposingTemporaryCode() {
        final List<PayCycleEntity> chain = givenChain(
                CALENDAR.replacingYear(2026, List.of(new Holiday(NOV_5, "Holiday"))), List.of());
        when(cycleRepository.findByLifecycleIn(List.of(PayCycleLifecycle.ACTIVE)))
                .thenReturn(List.of(chain.get(0)));

        final PayCycleCalendarImpact impact = service.previewCalendarChange(2026,
                List.of(new Holiday(NOV_5, "Holiday")));

        assertThat(impact.divergences()).isEmpty();
        verifyNoWrites();
    }

    @Test
    void shouldUseNullCodeForNewDivergenceAtStoredChainEnd() {
        final List<PayCycleEntity> chain = givenChain(CALENDAR, List.of());
        when(cycleRepository.findByLifecycleIn(List.of(PayCycleLifecycle.ACTIVE)))
                .thenReturn(List.of(chain.get(0)));

        final PayCycleCalendarImpact impact = service.previewCalendarChange(2026,
                List.of(new Holiday(NOV_5, "Holiday")));

        assertThat(impact.divergences()).containsExactly(PayCyclePending.boundaryDiverges(
                null, YearMonth.of(2026, 11), NOV_5, NOV_5.plusDays(1)));
        verifyNoWrites();
    }

    @Test
    void shouldReturnEmptyCycleImpactWhenCalendarMonthModeIsActive() {
        when(holidayCalendarService.findCalendar()).thenReturn(CALENDAR);
        when(configService.find()).thenReturn(buildCalendarMonthConfig());

        final PayCycleCalendarImpact impact = service.previewCalendarChange(2026, List.of());

        assertThat(impact.boundaries()).isEmpty();
        assertThat(impact.divergences()).isEmpty();
        verifyNoInteractions(incomeScheduleService, cycleRepository);
    }

    @Test
    void shouldValidateHolidaysEvenWhenCyclesAreOff() {
        when(holidayCalendarService.findCalendar()).thenReturn(CALENDAR);

        assertThatThrownBy(() -> service.previewCalendarChange(2026,
                List.of(new Holiday(JAN_5, "Wrong year"))))
                .isInstanceOf(com.bts.personalbudget.exception.InvalidFieldsException.class);

        verifyNoInteractions(configService, incomeScheduleService, cycleRepository);
    }

    private List<PayCycleEntity> givenChain(final HolidayCalendar calendar,
                                          final List<IncomeOccurrence> materialized) {
        when(configService.find()).thenReturn(buildPayCycleConfig(OCT_5, 1L));
        when(holidayCalendarService.findCalendar()).thenReturn(calendar);
        when(incomeScheduleService.findByCode(SCHEDULE_CODE))
                .thenReturn(buildSchedule(YearMonth.of(2026, 10)));
        when(incomeScheduleService.findMaterializedOccurrences(SCHEDULE_CODE)).thenReturn(materialized);
        final IncomeScheduleEntity anchor = IncomeScheduleEntity.builder().code(SCHEDULE_CODE).build();
        final List<PayCycleEntity> chain = List.of(
                entity(OCT_5, NOV_5, YearMonth.of(2026, 10), anchor),
                entity(NOV_5, DEC_7, YearMonth.of(2026, 11), anchor),
                entity(DEC_7, JAN_5, YearMonth.of(2026, 12), anchor));
        when(cycleRepository.findByLifecycleIn(List.of(PayCycleLifecycle.ACTIVE))).thenReturn(chain);
        return chain;
    }

    private PayCycleEntity entity(final LocalDate start, final LocalDate end,
                                 final YearMonth month, final IncomeScheduleEntity anchor) {
        final PayCycleEntity entity = mapper.toEntity(buildCycle(start, end, month), anchor);
        entity.setCode(UUID.randomUUID());
        entity.setVersion(0L);
        return entity;
    }

    private void verifyNoWrites() {
        verify(configService, never()).lock();
        verify(incomeScheduleService, never()).findByCodeForUpdate(any());
        verify(cycleRepository, never()).findForUpdateByLifecycle(any());
        verify(cycleRepository, never()).save(any());
        verify(cycleRepository, never()).saveAll(any());
        verify(cycleRepository, never()).flush();
        verifyNoInteractions(adjustmentRepository, scheduleRepository, financialMovementService);
    }
}
