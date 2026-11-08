package com.bts.personalbudget.core.domain.service.paycycle;

import static com.bts.personalbudget.core.domain.factory.HolidayCalendarFactory.buildReviewedCalendar;
import static com.bts.personalbudget.core.domain.factory.IncomeScheduleFactory.SCHEDULE_CODE;
import static com.bts.personalbudget.core.domain.factory.IncomeScheduleFactory.buildSchedule;
import static com.bts.personalbudget.core.domain.factory.PayCycleFactory.buildPayCycleConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.bts.personalbudget.core.domain.entity.IncomeScheduleEntity;
import com.bts.personalbudget.core.domain.entity.PayCycleAdjustmentEntity;
import com.bts.personalbudget.core.domain.entity.PayCycleEntity;
import com.bts.personalbudget.core.domain.enumerator.BusinessWeek;
import com.bts.personalbudget.core.domain.enumerator.DateRuleType;
import com.bts.personalbudget.core.domain.enumerator.IncomeScheduleStatus;
import com.bts.personalbudget.core.domain.enumerator.NonBusinessDayPolicy;
import com.bts.personalbudget.core.domain.enumerator.PayCycleAdjustmentType;
import com.bts.personalbudget.core.domain.enumerator.PayCycleKind;
import com.bts.personalbudget.core.domain.enumerator.PayCycleLifecycle;
import com.bts.personalbudget.core.domain.enumerator.PayCycleTransitionShape;
import com.bts.personalbudget.core.domain.service.FinancialMovementService;
import com.bts.personalbudget.core.domain.service.calendar.HolidayCalendar;
import com.bts.personalbudget.core.domain.service.calendar.HolidayCalendarService;
import com.bts.personalbudget.core.domain.service.calendar.IncomeDateRule;
import com.bts.personalbudget.core.domain.service.income.IncomeSchedule;
import com.bts.personalbudget.core.domain.service.income.IncomeScheduleService;
import com.bts.personalbudget.exception.ConflictException;
import com.bts.personalbudget.exception.InvalidFieldsException;
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
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PayCycleAnchorTransitionServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 2);
    private static final LocalDate OCT_5 = LocalDate.of(2026, 10, 5);
    private static final LocalDate NOV_5 = LocalDate.of(2026, 11, 5);
    private static final LocalDate DEC_7 = LocalDate.of(2026, 12, 7);
    private static final LocalDate JAN_5 = LocalDate.of(2027, 1, 5);
    private static final UUID NEXT_SCHEDULE_CODE = UUID.fromString("bbbbbbbb-1111-1111-1111-111111111111");
    private static final String JUSTIFICATION = "Mudança de agenda financeira";
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
    private Clock clock = Clock.fixed(Instant.parse("2026-11-02T13:00:00Z"), ZoneId.of("America/Sao_Paulo"));

    private final IncomeSchedule previous = anchorSchedule(buildSchedule(YearMonth.of(2026, 10)));
    private final IncomeSchedule next = schedule(
            NEXT_SCHEDULE_CODE,
            IncomeDateRule.fixedDay(5, NonBusinessDayPolicy.KEEP, BusinessWeek.MONDAY_TO_FRIDAY),
            YearMonth.of(2026, 11));
    private final IncomeScheduleEntity previousEntity = entity(SCHEDULE_CODE, 1L, true);
    private final IncomeScheduleEntity nextEntity = entity(NEXT_SCHEDULE_CODE, 2L, false);
    private List<PayCycleEntity> storedChain;

    @BeforeEach
    void setUp() {
        storedChain = List.of(
                cycle(OCT_5, NOV_5, YearMonth.of(2026, 10)),
                cycle(NOV_5, DEC_7, YearMonth.of(2026, 11)),
                cycle(DEC_7, JAN_5, YearMonth.of(2026, 12)));
        lenient().doAnswer(invocation -> {
            final var config = invocation.<com.bts.personalbudget.core.domain.service.paycycle.PayCycleConfig>getArgument(0);
            final Long expectedVersion = invocation.getArgument(1);
            if (expectedVersion == null || config.version() != expectedVersion) {
                throw new ConflictException("Version conflict");
            }
            return null;
        }).when(configService).requireVersion(any(), any());
    }

    @Test
    void shouldApplyARegularContinuationWhenTheNewAnchorStartsOnTheFrozenBoundary() {
        final IncomeSchedule startsAtBoundary = schedule(
                NEXT_SCHEDULE_CODE,
                IncomeDateRule.fixedDay(5, NonBusinessDayPolicy.KEEP, BusinessWeek.MONDAY_TO_FRIDAY),
                YearMonth.of(2026, 10));
        stubSwitch(startsAtBoundary, 2L);

        final PayCycleAnchorTransitionResult result = service.transitionAnchor(change(
                NEXT_SCHEDULE_CODE, null, null, JUSTIFICATION, 2L));

        assertEquals(1, result.preservedCycles().size());
        assertEquals(2, result.cancelledCycles().size());
        assertEquals(PayCycleKind.REGULAR, result.newCycles().get(0).cycle().kind());
        assertEquals(NOV_5, result.newCycles().get(0).cycle().startDate());
        final var history = savedHistory();
        assertEquals(PayCycleAdjustmentType.ANCHOR_TRANSITION, history.getType());
        assertEquals(JUSTIFICATION, history.getJustification());
        assertEquals(2L, history.getExpectedConfigVersion());
        verify(cycleRepository, org.mockito.Mockito.times(2)).flush();
    }

    @Test
    void shouldRejectAStaleConfigurationVersionWithoutWritingAnything() {
        when(configService.lock()).thenReturn(buildPayCycleConfig(OCT_5, 3L));

        assertThrows(ConflictException.class, () -> service.transitionAnchor(
                change(NEXT_SCHEDULE_CODE, null, null, JUSTIFICATION, 2L)));

        verifyNoInteractions(incomeScheduleService, cycleRepository, adjustmentRepository);
    }

    @Test
    void shouldRejectAMissingJustificationBeforeLocking() {
        assertThrows(InvalidFieldsException.class, () -> service.transitionAnchor(
                change(NEXT_SCHEDULE_CODE, null, null, " ", 2L)));

        verifyNoInteractions(configService, incomeScheduleService, cycleRepository, adjustmentRepository);
    }

    @Test
    void shouldRejectAScheduleSwitchPreviewWithoutItsRequiredShape() {
        when(configService.find()).thenReturn(buildPayCycleConfig(OCT_5, 2L));
        when(incomeScheduleService.findByCode(SCHEDULE_CODE)).thenReturn(previous);
        when(incomeScheduleService.findByCode(NEXT_SCHEDULE_CODE)).thenReturn(schedule(
                NEXT_SCHEDULE_CODE,
                IncomeDateRule.fixedDay(10, NonBusinessDayPolicy.KEEP, BusinessWeek.MONDAY_TO_FRIDAY),
                YearMonth.of(2026, 10)));
        when(cycleRepository.findByLifecycleIn(List.of(PayCycleLifecycle.ACTIVE))).thenReturn(storedChain);
        when(incomeScheduleService.findMaterializedOccurrences(NEXT_SCHEDULE_CODE)).thenReturn(List.of());
        when(holidayCalendarService.findCalendar()).thenReturn(CALENDAR);

        assertThrows(InvalidFieldsException.class, () -> service.previewAnchorTransition(
                change(NEXT_SCHEDULE_CODE, null, null, null, null)));

        verify(cycleRepository, never()).save(any());
        verify(cycleRepository, never()).flush();
        verifyNoInteractions(adjustmentRepository);
    }

    private void stubSwitch(final IncomeSchedule target, final long version) {
        when(configService.lock()).thenReturn(buildPayCycleConfig(OCT_5, version));
        when(incomeScheduleService.findByCodeForUpdate(SCHEDULE_CODE)).thenReturn(previous);
        when(incomeScheduleService.findByCode(NEXT_SCHEDULE_CODE)).thenReturn(target);
        when(incomeScheduleService.findByCodeForUpdate(NEXT_SCHEDULE_CODE)).thenReturn(target);
        when(incomeScheduleService.findMaterializedOccurrences(NEXT_SCHEDULE_CODE)).thenReturn(List.of());
        when(incomeScheduleService.switchAnchorForTransition(SCHEDULE_CODE, NEXT_SCHEDULE_CODE)).thenReturn(target);
        when(cycleRepository.findForUpdateByLifecycle(PayCycleLifecycle.ACTIVE)).thenReturn(storedChain);
        when(cycleRepository.findByLifecycleIn(List.of(PayCycleLifecycle.ACTIVE))).thenReturn(List.of());
        when(cycleRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        when(holidayCalendarService.findCalendar()).thenReturn(CALENDAR);
        when(scheduleRepository.findByCode(NEXT_SCHEDULE_CODE)).thenReturn(Optional.of(nextEntity));
        when(adjustmentRepository.saveAndFlush(any(PayCycleAdjustmentEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(configService.switchAnchor(NEXT_SCHEDULE_CODE))
                .thenReturn(new com.bts.personalbudget.core.domain.service.paycycle.PayCycleConfig(
                        com.bts.personalbudget.core.domain.factory.PayCycleFactory.CONFIG_CODE,
                        com.bts.personalbudget.core.domain.enumerator.PayCycleMode.PAY_CYCLE,
                        NEXT_SCHEDULE_CODE, OCT_5, version + 1));
    }

    private PayCycleAnchorChange change(
            final UUID targetCode,
            final Integer ruleValue,
            final PayCycleTransitionShape shape,
            final String justification,
            final Long version) {
        final var update = new com.bts.personalbudget.core.domain.service.income.IncomeScheduleUpdate(
                null, null, null,
                ruleValue == null ? null : DateRuleType.FIXED_DAY,
                ruleValue,
                null,
                null,
                null,
                false,
                null);
        return new PayCycleAnchorChange(targetCode, update, shape, justification, version);
    }

    private PayCycleAdjustmentEntity savedHistory() {
        final var captor = org.mockito.ArgumentCaptor.forClass(PayCycleAdjustmentEntity.class);
        verify(adjustmentRepository).saveAndFlush(captor.capture());
        return captor.getValue();
    }

    private PayCycleEntity cycle(final LocalDate start, final LocalDate end, final YearMonth month) {
        return PayCycleEntity.builder()
                .id(start.toEpochDay())
                .code(UUID.randomUUID())
                .startDate(start)
                .endDate(end)
                .kind(PayCycleKind.REGULAR)
                .anchorSchedule(previousEntity)
                .anchorNominalMonth(month.atDay(1))
                .lifecycle(PayCycleLifecycle.ACTIVE)
                .startAdjusted(false)
                .endAdjusted(false)
                .version(0L)
                .build();
    }

    private IncomeScheduleEntity entity(final UUID code, final long id, final boolean anchor) {
        return IncomeScheduleEntity.builder()
                .id(id)
                .code(code)
                .description("Salário")
                .amount(java.math.BigDecimal.valueOf(5000))
                .ruleType(DateRuleType.FIXED_DAY)
                .ruleValue(5)
                .businessWeek(BusinessWeek.MONDAY_TO_FRIDAY)
                .nonBusinessDayPolicy(NonBusinessDayPolicy.POSTPONE)
                .startMonth(YearMonth.of(2026, 10).atDay(1))
                .anchor(anchor)
                .status(IncomeScheduleStatus.ACTIVE)
                .build();
    }

    private IncomeSchedule anchorSchedule(final IncomeSchedule schedule) {
        return new IncomeSchedule(schedule.code(), schedule.description(), schedule.amount(), schedule.categoryCode(),
                schedule.rule(), schedule.startMonth(), schedule.endMonth(), true, schedule.status(),
                schedule.replacesFixedBillCode());
    }

    private IncomeSchedule schedule(final UUID code, final IncomeDateRule rule, final YearMonth startMonth) {
        return new IncomeSchedule(code, "Outra receita", java.math.BigDecimal.valueOf(5000), null, rule,
                startMonth, null, false, IncomeScheduleStatus.ACTIVE, null);
    }
}
