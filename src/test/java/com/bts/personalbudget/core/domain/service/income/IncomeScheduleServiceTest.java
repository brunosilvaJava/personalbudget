package com.bts.personalbudget.core.domain.service.income;

import static com.bts.personalbudget.core.domain.factory.HolidayCalendarFactory.buildHoliday;
import static com.bts.personalbudget.core.domain.factory.HolidayCalendarFactory.buildReviewedCalendar;
import static com.bts.personalbudget.core.domain.factory.IncomeScheduleFactory.AMOUNT;
import static com.bts.personalbudget.core.domain.factory.IncomeScheduleFactory.CATEGORY_CODE;
import static com.bts.personalbudget.core.domain.factory.IncomeScheduleFactory.FIXED_BILL_CODE;
import static com.bts.personalbudget.core.domain.factory.IncomeScheduleFactory.MOVEMENT_CODE;
import static com.bts.personalbudget.core.domain.factory.IncomeScheduleFactory.SCHEDULE_CODE;
import static com.bts.personalbudget.core.domain.factory.IncomeScheduleFactory.buildInactive;
import static com.bts.personalbudget.core.domain.factory.IncomeScheduleFactory.buildNewSchedule;
import static com.bts.personalbudget.core.domain.factory.IncomeScheduleFactory.buildSchedule;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bts.personalbudget.core.domain.entity.IncomeOccurrenceEntity;
import com.bts.personalbudget.core.domain.entity.IncomeScheduleEntity;
import com.bts.personalbudget.core.domain.enumerator.BusinessWeek;
import com.bts.personalbudget.core.domain.enumerator.DateRuleType;
import com.bts.personalbudget.core.domain.enumerator.FinancialMovementStatus;
import com.bts.personalbudget.core.domain.enumerator.FixedBillStatus;
import com.bts.personalbudget.core.domain.enumerator.IncomeScheduleStatus;
import com.bts.personalbudget.core.domain.enumerator.NonBusinessDayPolicy;
import com.bts.personalbudget.core.domain.enumerator.OperationType;
import com.bts.personalbudget.core.domain.enumerator.RecurrenceType;
import com.bts.personalbudget.core.domain.model.FinancialMovement;
import com.bts.personalbudget.core.domain.service.FinancialMovementService;
import com.bts.personalbudget.core.domain.service.calendar.HolidayCalendarService;
import com.bts.personalbudget.core.domain.service.calendar.IncomeDateRule;
import com.bts.personalbudget.core.domain.service.category.CategoryService;
import com.bts.personalbudget.core.domain.service.fixedbill.FixedBill;
import com.bts.personalbudget.core.domain.service.fixedbill.FixedBillService;
import com.bts.personalbudget.core.domain.service.fixedbill.FixedBillUpdate;
import com.bts.personalbudget.exception.InvalidFieldsException;
import com.bts.personalbudget.mapper.IncomeScheduleMapper;
import com.bts.personalbudget.repository.IncomeOccurrenceRepository;
import com.bts.personalbudget.repository.IncomeScheduleRepository;
import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
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
 * Today is Monday 2026-06-15. With the factory's rule (day 5, postponed, Monday to Friday) the
 * occurrences fall on Fri 2026-06-05, Mon 2026-07-06 (Sunday the 5th is postponed), Wed 2026-08-05
 * and Mon 2026-09-07 (Saturday the 5th is postponed).
 */
@ExtendWith(MockitoExtension.class)
class IncomeScheduleServiceTest {

    private static final ZoneId ZONE_ID = ZoneId.of("America/Sao_Paulo");
    private static final Instant FIXED_INSTANT = Instant.parse("2026-06-15T10:00:00Z");
    private static final LocalDate TODAY = LocalDate.ofInstant(FIXED_INSTANT, ZONE_ID);
    private static final YearMonth JUNE = YearMonth.of(2026, 6);
    private static final YearMonth JULY = YearMonth.of(2026, 7);
    private static final YearMonth AUGUST = YearMonth.of(2026, 8);
    private static final LocalDate JUNE_5 = LocalDate.of(2026, 6, 5);
    private static final LocalDate JULY_6 = LocalDate.of(2026, 7, 6);
    private static final LocalDate AUGUST_5 = LocalDate.of(2026, 8, 5);

    @InjectMocks
    private IncomeScheduleService service;

    @Mock
    private IncomeScheduleRepository scheduleRepository;

    @Mock
    private IncomeOccurrenceRepository occurrenceRepository;

    @Spy
    private IncomeScheduleMapper mapper = IncomeScheduleMapper.INSTANCE;

    @Mock
    private HolidayCalendarService holidayCalendarService;

    @Mock
    private FinancialMovementService financialMovementService;

    @Mock
    private FixedBillService fixedBillService;

    @Mock
    private CategoryService categoryService;

    @Spy
    private Clock clock = Clock.fixed(FIXED_INSTANT, ZONE_ID);

    // ---- create

    @Test
    void shouldCreateActiveScheduleWithTrimmedDescription() {
        stubSave();

        final IncomeSchedule created = service.create(buildNewSchedule(JULY, false, null));

        assertEquals("Salário", created.description());
        assertEquals(IncomeScheduleStatus.ACTIVE, created.status());
        assertEquals(JULY, created.startMonth());
        verify(categoryService).validateForUse(CATEGORY_CODE, OperationType.CREDIT);
    }

    @Test
    void shouldRejectStartMonthBeforeTheCurrentMonth() {
        final InvalidFieldsException exception = assertThrows(InvalidFieldsException.class,
                () -> service.create(buildNewSchedule(YearMonth.of(2026, 5), false, null)));

        assertTrue(exception.getValidations().containsKey("startMonth"));
        verify(scheduleRepository, never()).save(any());
    }

    @Test
    void shouldAcceptStartInTheCurrentMonth() {
        stubSave();

        assertEquals(JUNE, service.create(buildNewSchedule(JUNE, false, null)).startMonth());
    }

    @Test
    void shouldRejectEndMonthBeforeStartMonth() {
        final IncomeSchedule schedule = new IncomeSchedule(null, "Salário", AMOUNT, null,
                IncomeDateRule.fixedDay(5, NonBusinessDayPolicy.POSTPONE, BusinessWeek.MONDAY_TO_FRIDAY),
                JULY, JUNE, false, null, null);

        final InvalidFieldsException exception =
                assertThrows(InvalidFieldsException.class, () -> service.create(schedule));

        assertTrue(exception.getValidations().containsKey("endMonth"));
    }

    @Test
    void shouldRejectShortDescriptionAfterTrimming() {
        final IncomeSchedule schedule = new IncomeSchedule(null, "ab ", AMOUNT, null,
                IncomeDateRule.fixedDay(5, NonBusinessDayPolicy.POSTPONE, BusinessWeek.MONDAY_TO_FRIDAY),
                JULY, null, false, null, null);

        final InvalidFieldsException exception =
                assertThrows(InvalidFieldsException.class, () -> service.create(schedule));

        assertTrue(exception.getValidations().containsKey("description"));
    }

    @Test
    void shouldRejectCategoryThatIsNotUsableAsCredit() {
        doThrow(new InvalidFieldsException("Campos inválidos", CategoryService.class,
                Map.of("categoryCode", "categoria é do tipo DEBIT e o lançamento é CREDIT")))
                .when(categoryService).validateForUse(CATEGORY_CODE, OperationType.CREDIT);

        assertThrows(InvalidFieldsException.class, () -> service.create(buildNewSchedule(JULY, false, null)));

        verify(scheduleRepository, never()).save(any());
    }

    @Test
    void shouldRejectSecondActiveAnchorOnCreate() {
        when(scheduleRepository.existsByAnchorTrueAndStatus(IncomeScheduleStatus.ACTIVE)).thenReturn(true);

        final InvalidFieldsException exception = assertThrows(InvalidFieldsException.class,
                () -> service.create(buildNewSchedule(JULY, true, null)));

        assertTrue(exception.getValidations().containsKey("anchor"));
        verify(scheduleRepository, never()).save(any());
    }

    @Test
    void shouldCreateAnchorWhenThereIsNoActiveAnchor() {
        when(scheduleRepository.existsByAnchorTrueAndStatus(IncomeScheduleStatus.ACTIVE)).thenReturn(false);
        stubSave();

        assertTrue(service.create(buildNewSchedule(JULY, true, null)).anchor());
    }

    // ---- replacing a fixed bill

    @Test
    void shouldEndTheReplacedFixedBillTheDayBeforeTheFirstMonthServed() {
        stubReplaceableFixedBill(buildFixedBill(OperationType.CREDIT, null));
        stubSave();

        final IncomeSchedule created = service.create(buildNewSchedule(JULY, false, FIXED_BILL_CODE));

        assertEquals(FIXED_BILL_CODE, created.replacesFixedBillCode());
        final ArgumentCaptor<FixedBillUpdate> captor = ArgumentCaptor.forClass(FixedBillUpdate.class);
        verify(fixedBillService).update(eq(FIXED_BILL_CODE), captor.capture());
        assertEquals(LocalDate.of(2026, 6, 30), captor.getValue().endDate());
        assertNull(captor.getValue().amount());
        assertNull(captor.getValue().status());
        assertNull(captor.getValue().startDate());

        final InOrder order = inOrder(fixedBillService, scheduleRepository, financialMovementService);
        order.verify(fixedBillService).findByCodeForUpdate(FIXED_BILL_CODE);
        order.verify(scheduleRepository).findActiveReplacementsForUpdate(
                eq(FIXED_BILL_CODE), eq(IncomeScheduleStatus.ACTIVE), any(UUID.class));
        order.verify(financialMovementService).findRecurrenceOccurrencesBetweenForUpdate(
                List.of(FIXED_BILL_CODE), LocalDate.of(2026, 7, 1), LocalDate.of(9999, 12, 31));
    }

    @Test
    void shouldNotExtendAFixedBillThatAlreadyEndsEarlier() {
        stubReplaceableFixedBill(buildFixedBill(OperationType.CREDIT, LocalDate.of(2026, 5, 31)));
        stubSave();

        service.create(buildNewSchedule(JULY, false, FIXED_BILL_CODE));

        verify(fixedBillService, never()).update(any(), any());
    }

    @Test
    void shouldRejectReplacingAFixedBillThatIsNotACredit() {
        when(fixedBillService.findByCodeForUpdate(FIXED_BILL_CODE))
                .thenReturn(buildFixedBill(OperationType.DEBIT, null));

        final InvalidFieldsException exception = assertThrows(InvalidFieldsException.class,
                () -> service.create(buildNewSchedule(JULY, false, FIXED_BILL_CODE)));

        assertTrue(exception.getValidations().containsKey("replacesFixedBillCode"));
        verify(fixedBillService, never()).update(any(), any());
        verify(scheduleRepository, never()).save(any());
    }

    @Test
    void shouldRejectReplacingAFixedBillThatDoesNotExist() {
        when(fixedBillService.findByCodeForUpdate(FIXED_BILL_CODE))
                .thenThrow(new EntityNotFoundException("not found"));

        final InvalidFieldsException exception = assertThrows(InvalidFieldsException.class,
                () -> service.create(buildNewSchedule(JULY, false, FIXED_BILL_CODE)));

        assertTrue(exception.getValidations().containsKey("replacesFixedBillCode"));
    }

    @Test
    void shouldRejectReplacingAFixedBillAlreadyReplacedByAnActiveSchedule() {
        when(fixedBillService.findByCodeForUpdate(FIXED_BILL_CODE))
                .thenReturn(buildFixedBill(OperationType.CREDIT, null));
        when(scheduleRepository.findActiveReplacementsForUpdate(
                eq(FIXED_BILL_CODE), eq(IncomeScheduleStatus.ACTIVE), any(UUID.class)))
                .thenReturn(List.of(new IncomeScheduleEntity()));

        final InvalidFieldsException exception = assertThrows(InvalidFieldsException.class,
                () -> service.create(buildNewSchedule(JULY, false, FIXED_BILL_CODE)));

        assertTrue(exception.getValidations().containsKey("replacesFixedBillCode"));
    }

    @Test
    void shouldRejectReplacingWhenTheFixedBillAlreadyPostedFromTheFirstMonthOn() {
        when(fixedBillService.findByCodeForUpdate(FIXED_BILL_CODE))
                .thenReturn(buildFixedBill(OperationType.CREDIT, null));
        when(financialMovementService.findRecurrenceOccurrencesBetweenForUpdate(
                List.of(FIXED_BILL_CODE), LocalDate.of(2026, 7, 1), LocalDate.of(9999, 12, 31)))
                .thenReturn(List.of(buildPostedMovement(true)));

        final InvalidFieldsException exception = assertThrows(InvalidFieldsException.class,
                () -> service.create(buildNewSchedule(JULY, false, FIXED_BILL_CODE)));

        assertTrue(exception.getValidations().containsKey("startMonth"));
        verify(fixedBillService, never()).update(any(), any());
    }

    @Test
    void shouldIgnoreADeletedMovementOfTheFixedBillWhenReplacing() {
        when(fixedBillService.findByCodeForUpdate(FIXED_BILL_CODE))
                .thenReturn(buildFixedBill(OperationType.CREDIT, null));
        when(financialMovementService.findRecurrenceOccurrencesBetweenForUpdate(
                List.of(FIXED_BILL_CODE), LocalDate.of(2026, 7, 1), LocalDate.of(9999, 12, 31)))
                .thenReturn(List.of(buildPostedMovement(false)));
        stubSave();

        service.create(buildNewSchedule(JULY, false, FIXED_BILL_CODE));

        verify(fixedBillService).update(eq(FIXED_BILL_CODE), any(FixedBillUpdate.class));
    }

    // ---- materialize

    @Test
    void shouldMaterializeAsPendingCreditMovementOnThePlannedDate() {
        final IncomeSchedule schedule = buildSchedule(JUNE);
        stubLocked(schedule);
        when(occurrenceRepository.findByScheduleCodeAndNominalMonth(SCHEDULE_CODE, LocalDate.of(2026, 6, 1)))
                .thenReturn(Optional.empty());
        when(holidayCalendarService.findCalendar()).thenReturn(buildReviewedCalendar(2026));
        stubMovementAndOccurrenceSave();

        final MaterializationResult result = service.materialize(SCHEDULE_CODE, JUNE);

        assertTrue(result.created());
        assertEquals(JUNE, result.occurrence().nominalMonth());
        assertEquals(JUNE_5, result.occurrence().plannedDate());

        final ArgumentCaptor<FinancialMovement> movement = ArgumentCaptor.forClass(FinancialMovement.class);
        verify(financialMovementService).createWithAutomaticCompetence(movement.capture());
        assertEquals(OperationType.CREDIT, movement.getValue().operationType());
        assertEquals(FinancialMovementStatus.PENDING, movement.getValue().status());
        assertEquals("Salário", movement.getValue().description());
        assertEquals(AMOUNT, movement.getValue().amount());
        assertEquals(JUNE_5.atStartOfDay(), movement.getValue().dueDate());
        assertEquals(SCHEDULE_CODE, movement.getValue().recurrenceBillCode());
        assertEquals(JUNE.atDay(1), movement.getValue().occurrenceDate());
        assertEquals(JUNE_5, movement.getValue().competenceDate());
        assertFalse(movement.getValue().competenceOverridden());
        assertEquals(CATEGORY_CODE, movement.getValue().categoryCode());

        final ArgumentCaptor<IncomeOccurrenceEntity> occurrence = ArgumentCaptor.forClass(IncomeOccurrenceEntity.class);
        verify(occurrenceRepository).save(occurrence.capture());
        assertEquals(LocalDate.of(2026, 6, 1), occurrence.getValue().getNominalMonth());
        assertEquals(JUNE_5, occurrence.getValue().getPlannedDate());
        assertEquals(AMOUNT, occurrence.getValue().getPlannedAmount());
        assertEquals(result.occurrence().movementCode(), occurrence.getValue().getMovementCode());
    }

    @Test
    void shouldMaterializeNextMonthAheadOfTheDateForAnEarlyReceipt() {
        stubLocked(buildSchedule(JUNE));
        when(occurrenceRepository.findByScheduleCodeAndNominalMonth(SCHEDULE_CODE, LocalDate.of(2026, 7, 1)))
                .thenReturn(Optional.empty());
        when(holidayCalendarService.findCalendar()).thenReturn(buildReviewedCalendar(2026));
        stubMovementAndOccurrenceSave();

        final MaterializationResult result = service.materialize(SCHEDULE_CODE, JULY);

        assertTrue(result.created());
        assertEquals(JULY_6, result.occurrence().plannedDate());
    }

    @Test
    void shouldReturnTheExistingOccurrenceWithoutCreatingAnotherMovement() {
        // The service never looks at the movement, only at the occurrence row: a receipt the user
        // deleted (soft delete, the row keeps its movement_code) is not generated again either.
        stubLocked(buildSchedule(JUNE));
        when(occurrenceRepository.findByScheduleCodeAndNominalMonth(SCHEDULE_CODE, LocalDate.of(2026, 6, 1)))
                .thenReturn(Optional.of(buildOccurrenceEntity(buildSchedule(JUNE), JUNE, JUNE_5)));

        final MaterializationResult result = service.materialize(SCHEDULE_CODE, JUNE);

        assertFalse(result.created());
        assertEquals(MOVEMENT_CODE, result.occurrence().movementCode());
        verify(financialMovementService, never()).createWithAutomaticCompetence(any(FinancialMovement.class));
        verify(occurrenceRepository, never()).save(any());
    }

    @Test
    void shouldRejectMaterializingAnInactiveSchedule() {
        stubLocked(buildInactive(buildSchedule(JUNE)));
        when(occurrenceRepository.findByScheduleCodeAndNominalMonth(SCHEDULE_CODE, LocalDate.of(2026, 6, 1)))
                .thenReturn(Optional.empty());

        final InvalidFieldsException exception = assertThrows(InvalidFieldsException.class,
                () -> service.materialize(SCHEDULE_CODE, JUNE));

        assertTrue(exception.getValidations().containsKey("code"));
        verify(financialMovementService, never()).createWithAutomaticCompetence(any(FinancialMovement.class));
    }

    @Test
    void shouldRejectMaterializingAMonthOutsideTheValidity() {
        stubLocked(buildSchedule(JULY));
        when(occurrenceRepository.findByScheduleCodeAndNominalMonth(SCHEDULE_CODE, LocalDate.of(2026, 6, 1)))
                .thenReturn(Optional.empty());

        final InvalidFieldsException exception = assertThrows(InvalidFieldsException.class,
                () -> service.materialize(SCHEDULE_CODE, JUNE));

        assertTrue(exception.getValidations().containsKey("nominalMonth"));
    }

    @Test
    void shouldRejectMaterializingMoreThanOneMonthAhead() {
        stubLocked(buildSchedule(JUNE));
        when(occurrenceRepository.findByScheduleCodeAndNominalMonth(SCHEDULE_CODE, LocalDate.of(2026, 8, 1)))
                .thenReturn(Optional.empty());

        final InvalidFieldsException exception = assertThrows(InvalidFieldsException.class,
                () -> service.materialize(SCHEDULE_CODE, AUGUST));

        assertTrue(exception.getValidations().containsKey("nominalMonth"));
    }

    @Test
    void shouldRejectMaterializingWhenTheCalendarYearWasNotReviewed() {
        stubLocked(buildSchedule(JUNE));
        when(occurrenceRepository.findByScheduleCodeAndNominalMonth(SCHEDULE_CODE, LocalDate.of(2026, 6, 1)))
                .thenReturn(Optional.empty());
        when(holidayCalendarService.findCalendar()).thenReturn(buildReviewedCalendar(2025));

        final InvalidFieldsException exception = assertThrows(InvalidFieldsException.class,
                () -> service.materialize(SCHEDULE_CODE, JUNE));

        assertTrue(exception.getValidations().containsKey("nominalMonth"));
        verify(financialMovementService, never()).createWithAutomaticCompetence(any(FinancialMovement.class));
    }

    @Test
    void shouldAnswerNotFoundWhenMaterializingAnUnknownSchedule() {
        when(scheduleRepository.findForUpdateByCode(SCHEDULE_CODE)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> service.materialize(SCHEDULE_CODE, JUNE));
    }

    // ---- job

    @Test
    void shouldPostOnlyTheOccurrencesDueUntilToday() {
        stubLocked(buildSchedule(JUNE));
        when(occurrenceRepository.findByScheduleCode(SCHEDULE_CODE)).thenReturn(List.of());
        when(holidayCalendarService.findCalendar()).thenReturn(buildReviewedCalendar(2026));
        stubMovementAndOccurrenceSave();

        final int posted = service.postDueOccurrences(SCHEDULE_CODE, TODAY);

        // June 5 is due; July 6 (next month, anticipated into the window) is still in the future.
        assertEquals(1, posted);
        final ArgumentCaptor<FinancialMovement> movement = ArgumentCaptor.forClass(FinancialMovement.class);
        verify(financialMovementService).createWithAutomaticCompetence(movement.capture());
        assertEquals(JUNE.atDay(1), movement.getValue().occurrenceDate());
    }

    @Test
    void shouldNotPostAnOccurrenceTheEarlyReceiptAlreadyMaterialized() {
        final IncomeSchedule schedule = buildSchedule(JUNE);
        stubLocked(schedule);
        when(occurrenceRepository.findByScheduleCode(SCHEDULE_CODE))
                .thenReturn(List.of(buildOccurrenceEntity(schedule, JUNE, JUNE_5)));
        when(holidayCalendarService.findCalendar()).thenReturn(buildReviewedCalendar(2026));

        final int posted = service.postDueOccurrences(SCHEDULE_CODE, TODAY);

        assertEquals(0, posted);
        verify(financialMovementService, never()).createWithAutomaticCompetence(any(FinancialMovement.class));
    }

    @Test
    void shouldCatchUpEveryMonthMissedSinceTheStartMonth() {
        final LocalDate today = LocalDate.of(2026, 8, 20);
        stubLocked(buildSchedule(JUNE));
        when(occurrenceRepository.findByScheduleCode(SCHEDULE_CODE)).thenReturn(List.of());
        when(holidayCalendarService.findCalendar()).thenReturn(buildReviewedCalendar(2026));
        stubMovementAndOccurrenceSave();

        final int posted = service.postDueOccurrences(SCHEDULE_CODE, today);

        assertEquals(3, posted);
        final ArgumentCaptor<FinancialMovement> movement = ArgumentCaptor.forClass(FinancialMovement.class);
        verify(financialMovementService, times(3)).createWithAutomaticCompetence(movement.capture());
        assertEquals(List.of(JUNE.atDay(1), JULY.atDay(1), AUGUST.atDay(1)),
                movement.getAllValues().stream().map(FinancialMovement::occurrenceDate).toList());
    }

    @Test
    void shouldSkipTheMonthWhoseCalendarYearWasNotReviewed() {
        stubLocked(buildSchedule(JUNE));
        when(occurrenceRepository.findByScheduleCode(SCHEDULE_CODE)).thenReturn(List.of());
        when(holidayCalendarService.findCalendar()).thenReturn(buildReviewedCalendar(2025));

        final int posted = service.postDueOccurrences(SCHEDULE_CODE, TODAY);

        assertEquals(0, posted);
        verify(financialMovementService, never()).createWithAutomaticCompetence(any(FinancialMovement.class));
    }

    @Test
    void shouldPostNothingForAnInactiveSchedule() {
        stubLocked(buildInactive(buildSchedule(JUNE)));

        assertEquals(0, service.postDueOccurrences(SCHEDULE_CODE, TODAY));

        verify(financialMovementService, never()).createWithAutomaticCompetence(any(FinancialMovement.class));
    }

    @Test
    void shouldPostNothingWhenTheScheduleNoLongerExists() {
        when(scheduleRepository.findForUpdateByCode(SCHEDULE_CODE)).thenReturn(Optional.empty());

        assertEquals(0, service.postDueOccurrences(SCHEDULE_CODE, TODAY));
    }

    @Test
    void shouldListTheCodesOfTheActiveSchedulesAlreadyStartedOrStartingNextMonth() {
        when(scheduleRepository.findAllByStatusAndStartMonthLessThanEqual(
                IncomeScheduleStatus.ACTIVE, LocalDate.of(2026, 7, 1)))
                .thenReturn(List.of(buildEntity(buildSchedule(JUNE))));

        assertEquals(List.of(SCHEDULE_CODE), service.findCodesToPost(TODAY));
    }

    // ---- projection

    @Test
    void shouldProjectOnlyTheOccurrencesNotMaterializedUntilTheEndDate() {
        final IncomeSchedule schedule = buildSchedule(JUNE);
        when(scheduleRepository.findAllByStatus(IncomeScheduleStatus.ACTIVE)).thenReturn(List.of(buildEntity(schedule)));
        when(holidayCalendarService.findCalendar()).thenReturn(buildReviewedCalendar(2026));
        when(occurrenceRepository.findByScheduleCodeIn(List.of(SCHEDULE_CODE)))
                .thenReturn(List.of(buildOccurrenceEntity(schedule, JUNE, JUNE_5)));

        final List<IncomeProjection> projections = service.findProjectedOccurrencesUntil(LocalDate.of(2026, 8, 31));

        // June is materialized (its movement counts); September 7 is after the end date.
        assertEquals(List.of(JULY_6, AUGUST_5), projections.stream().map(IncomeProjection::date).toList());
        assertThat(projections).allMatch(projection -> SCHEDULE_CODE.equals(projection.scheduleCode()));
        assertThat(projections).allMatch(projection -> AMOUNT.equals(projection.amount()));
        assertThat(projections).allMatch(projection -> CATEGORY_CODE.equals(projection.categoryCode()));
    }

    @Test
    void shouldKeepAProjectedMonthWhosePlannedDateMatchesThePreviousMonthsMaterializedDate() {
        final YearMonth february = YearMonth.of(2026, 2);
        final YearMonth march = YearMonth.of(2026, 3);
        final IncomeSchedule schedule = buildSchedule(
                IncomeDateRule.fixedDay(2, NonBusinessDayPolicy.KEEP, BusinessWeek.MONDAY_TO_FRIDAY),
                february, null);
        when(scheduleRepository.findAllByStatus(IncomeScheduleStatus.ACTIVE))
                .thenReturn(List.of(buildEntity(schedule)));
        when(holidayCalendarService.findCalendar()).thenReturn(buildReviewedCalendar(2026));
        when(occurrenceRepository.findByScheduleCodeIn(List.of(SCHEDULE_CODE)))
                .thenReturn(List.of(buildOccurrenceEntity(schedule, february, LocalDate.of(2026, 3, 2))));

        final List<IncomeProjection> projections =
                service.findProjectedOccurrencesUntil(LocalDate.of(2026, 3, 31));

        assertThat(projections).containsExactly(new IncomeProjection(
                SCHEDULE_CODE, march, LocalDate.of(2026, 3, 2), AMOUNT, CATEGORY_CODE));
    }

    @Test
    void shouldNotProjectAnOccurrenceWhoseMovementWasDeleted() {
        // Same rule as the job: the row exists, so the month is neither posted nor projected again.
        final IncomeSchedule schedule = buildSchedule(JUNE);
        when(scheduleRepository.findAllByStatus(IncomeScheduleStatus.ACTIVE)).thenReturn(List.of(buildEntity(schedule)));
        when(holidayCalendarService.findCalendar()).thenReturn(buildReviewedCalendar(2026));
        when(occurrenceRepository.findByScheduleCodeIn(List.of(SCHEDULE_CODE)))
                .thenReturn(List.of(buildOccurrenceEntity(schedule, JUNE, JUNE_5)));

        final List<IncomeProjection> projections = service.findProjectedOccurrencesUntil(LocalDate.of(2026, 6, 30));

        assertThat(projections).isEmpty();
    }

    @Test
    void shouldLeaveOutMonthsWhoseCalendarYearWasNotReviewed() {
        final IncomeSchedule schedule = buildSchedule(JUNE);
        when(scheduleRepository.findAllByStatus(IncomeScheduleStatus.ACTIVE)).thenReturn(List.of(buildEntity(schedule)));
        when(holidayCalendarService.findCalendar()).thenReturn(buildReviewedCalendar(2025));
        when(occurrenceRepository.findByScheduleCodeIn(List.of(SCHEDULE_CODE))).thenReturn(List.of());

        assertThat(service.findProjectedOccurrencesUntil(LocalDate.of(2026, 8, 31))).isEmpty();
    }

    @Test
    void shouldProjectNothingWhenThereIsNoActiveSchedule() {
        when(scheduleRepository.findAllByStatus(IncomeScheduleStatus.ACTIVE)).thenReturn(List.of());

        assertThat(service.findProjectedOccurrencesUntil(LocalDate.of(2026, 8, 31))).isEmpty();
        verify(holidayCalendarService, never()).findCalendar();
    }

    // ---- listing

    @Test
    void shouldListMaterializedAndProjectedMonths() {
        final IncomeSchedule schedule = buildSchedule(JUNE);
        when(scheduleRepository.findByCode(SCHEDULE_CODE)).thenReturn(Optional.of(buildEntity(schedule)));
        when(occurrenceRepository.findByScheduleCode(SCHEDULE_CODE))
                .thenReturn(List.of(buildOccurrenceEntity(schedule, JUNE, JUNE_5)));
        when(holidayCalendarService.findCalendar()).thenReturn(buildReviewedCalendar(2026));

        final List<IncomeOccurrenceView> views = service.findOccurrences(SCHEDULE_CODE, JUNE, JULY);

        assertEquals(2, views.size());
        assertEquals(IncomeOccurrenceView.State.MATERIALIZED, views.get(0).state());
        assertEquals(IncomeOccurrenceView.State.PROJECTED, views.get(1).state());
        assertEquals(JULY_6, views.get(1).plannedDate());
    }

    @Test
    void shouldDefaultTheListingToTheCurrentMonthAndTheElevenAfterIt() {
        when(scheduleRepository.findByCode(SCHEDULE_CODE)).thenReturn(Optional.of(buildEntity(buildSchedule(JUNE))));
        when(occurrenceRepository.findByScheduleCode(SCHEDULE_CODE)).thenReturn(List.of());
        when(holidayCalendarService.findCalendar()).thenReturn(buildReviewedCalendar(2026, 2027));

        final List<IncomeOccurrenceView> views = service.findOccurrences(SCHEDULE_CODE, null, null);

        assertEquals(12, views.size());
        assertEquals(JUNE, views.get(0).nominalMonth());
        assertEquals(YearMonth.of(2027, 5), views.get(11).nominalMonth());
    }

    @Test
    void shouldRejectListingMoreThanThirtySixMonths() {
        final InvalidFieldsException exception = assertThrows(InvalidFieldsException.class,
                () -> service.findOccurrences(SCHEDULE_CODE, JUNE, JUNE.plusMonths(36)));

        assertTrue(exception.getValidations().containsKey("to"));
    }

    @Test
    void shouldRejectListingWithEndBeforeStart() {
        final InvalidFieldsException exception = assertThrows(InvalidFieldsException.class,
                () -> service.findOccurrences(SCHEDULE_CODE, JULY, JUNE));

        assertTrue(exception.getValidations().containsKey("to"));
    }

    // ---- update

    @Test
    void shouldChangeOnlyTheFieldsSent() {
        stubLocked(buildSchedule(JUNE));
        stubSave();

        final IncomeSchedule updated = service.update(SCHEDULE_CODE, update(
                null, new BigDecimal("5200.00"), null, null, null, null, null, null, false, null));

        assertEquals(new BigDecimal("5200.00"), updated.amount());
        assertEquals("Salário", updated.description());
        assertEquals(IncomeDateRule.fixedDay(5, NonBusinessDayPolicy.POSTPONE, BusinessWeek.MONDAY_TO_FRIDAY),
                updated.rule());
        assertEquals(JUNE, updated.startMonth());
    }

    @Test
    void shouldMergeTheRuleComponentsSentWithTheStoredRule() {
        stubLocked(buildSchedule(JUNE));
        stubSave();

        final IncomeSchedule updated = service.update(SCHEDULE_CODE, update(
                null, null, null, null, 7, BusinessWeek.MONDAY_TO_SATURDAY, null, null, false, null));

        assertEquals(IncomeDateRule.fixedDay(7, NonBusinessDayPolicy.POSTPONE, BusinessWeek.MONDAY_TO_SATURDAY),
                updated.rule());
    }

    @Test
    void shouldRejectARuleThatIsInvalidAsAWhole() {
        final IncomeSchedule nthBusinessDay = buildSchedule(
                IncomeDateRule.nthBusinessDay(5, BusinessWeek.MONDAY_TO_FRIDAY), JUNE, null);
        stubLocked(nthBusinessDay);

        // FIXED_DAY needs a policy and the stored NTH_BUSINESS_DAY rule has none to merge.
        final InvalidFieldsException exception = assertThrows(InvalidFieldsException.class,
                () -> service.update(SCHEDULE_CODE, update(
                        null, null, null, DateRuleType.FIXED_DAY, null, null, null, null, false, null)));

        assertTrue(exception.getValidations().containsKey("nonBusinessDayPolicy"));
        verify(scheduleRepository, never()).save(any());
    }

    @Test
    void shouldSetTheEndMonth() {
        stubLocked(buildSchedule(JUNE));
        stubSave();

        assertEquals(YearMonth.of(2027, 5), service.update(SCHEDULE_CODE, update(
                null, null, null, null, null, null, null, YearMonth.of(2027, 5), false, null)).endMonth());
    }

    @Test
    void shouldRemoveTheEndMonthWhenAskedTo() {
        stubLocked(buildSchedule(IncomeDateRule.fixedDay(5, NonBusinessDayPolicy.POSTPONE, BusinessWeek.MONDAY_TO_FRIDAY),
                JUNE, YearMonth.of(2027, 5)));
        stubSave();

        assertNull(service.update(SCHEDULE_CODE, update(
                null, null, null, null, null, null, null, null, true, null)).endMonth());
    }

    @Test
    void shouldRejectEndMonthSentTogetherWithRemoveEndMonth() {
        stubLocked(buildSchedule(JUNE));

        final InvalidFieldsException exception = assertThrows(InvalidFieldsException.class,
                () -> service.update(SCHEDULE_CODE, update(
                        null, null, null, null, null, null, null, YearMonth.of(2027, 5), true, null)));

        assertTrue(exception.getValidations().containsKey("endMonth"));
    }

    @Test
    void shouldRejectEndMonthBeforeTheStartMonthOnUpdate() {
        stubLocked(buildSchedule(JULY));

        final InvalidFieldsException exception = assertThrows(InvalidFieldsException.class,
                () -> service.update(SCHEDULE_CODE, update(
                        null, null, null, null, null, null, null, JUNE, false, null)));

        assertTrue(exception.getValidations().containsKey("endMonth"));
    }

    @Test
    void shouldRejectMakingASecondActiveScheduleTheAnchor() {
        stubLocked(buildSchedule(JUNE));
        when(scheduleRepository.existsByAnchorTrueAndStatusAndCodeNot(IncomeScheduleStatus.ACTIVE, SCHEDULE_CODE))
                .thenReturn(true);

        final InvalidFieldsException exception = assertThrows(InvalidFieldsException.class,
                () -> service.update(SCHEDULE_CODE, update(
                        null, null, null, null, null, null, null, null, false, true)));

        assertTrue(exception.getValidations().containsKey("anchor"));
        verify(scheduleRepository, never()).save(any());
    }

    @Test
    void shouldValidateTheNewCategoryAsCredit() {
        final UUID newCategory = UUID.randomUUID();
        stubLocked(buildSchedule(JUNE));
        stubSave();

        service.update(SCHEDULE_CODE, update(null, null, newCategory, null, null, null, null, null, false, null));

        verify(categoryService).validateForUse(newCategory, OperationType.CREDIT);
    }

    @Test
    void shouldNotSaveWhenTheUpdateIsEmpty() {
        stubLocked(buildSchedule(JUNE));

        service.update(SCHEDULE_CODE, update(null, null, null, null, null, null, null, null, false, null));

        verify(scheduleRepository, never()).save(any());
    }

    // ---- status

    @Test
    void shouldInactivateTheSchedule() {
        stubLocked(buildSchedule(JUNE));
        stubSave();

        assertEquals(IncomeScheduleStatus.INACTIVE, service.inactivate(SCHEDULE_CODE).status());
    }

    @Test
    void shouldMoveTheStartMonthToTheCurrentMonthWhenReactivating() {
        // Inactive since long ago: reactivating must not make the job post every month in between.
        stubLocked(buildInactive(buildSchedule(YearMonth.of(2026, 1))));
        stubSave();

        final IncomeSchedule activated = service.activate(SCHEDULE_CODE);

        assertEquals(IncomeScheduleStatus.ACTIVE, activated.status());
        assertEquals(JUNE, activated.startMonth());
    }

    @Test
    void shouldKeepAFutureStartMonthWhenReactivating() {
        stubLocked(buildInactive(buildSchedule(AUGUST)));
        stubSave();

        assertEquals(AUGUST, service.activate(SCHEDULE_CODE).startMonth());
    }

    @Test
    void shouldRejectReactivatingWhenTheEndMonthHasAlreadyPassed() {
        stubLocked(buildInactive(buildSchedule(
                IncomeDateRule.fixedDay(5, NonBusinessDayPolicy.POSTPONE, BusinessWeek.MONDAY_TO_FRIDAY),
                YearMonth.of(2026, 1), YearMonth.of(2026, 5))));

        final InvalidFieldsException exception =
                assertThrows(InvalidFieldsException.class, () -> service.activate(SCHEDULE_CODE));

        assertTrue(exception.getValidations().containsKey("endMonth"));
        verify(scheduleRepository, never()).save(any());
    }

    @Test
    void shouldRejectReactivatingASecondFixedBillReplacement() {
        final IncomeSchedule replacement = new IncomeSchedule(SCHEDULE_CODE, "Salário", AMOUNT, null,
                IncomeDateRule.fixedDay(5, NonBusinessDayPolicy.POSTPONE, BusinessWeek.MONDAY_TO_FRIDAY),
                JUNE, null, false, IncomeScheduleStatus.INACTIVE, FIXED_BILL_CODE);
        stubLocked(replacement);
        when(fixedBillService.findByCodeForUpdate(FIXED_BILL_CODE))
                .thenReturn(buildFixedBill(OperationType.CREDIT, null));
        when(scheduleRepository.findActiveReplacementsForUpdate(
                FIXED_BILL_CODE, IncomeScheduleStatus.ACTIVE, SCHEDULE_CODE))
                .thenReturn(List.of(new IncomeScheduleEntity()));

        final InvalidFieldsException exception =
                assertThrows(InvalidFieldsException.class, () -> service.activate(SCHEDULE_CODE));

        assertTrue(exception.getValidations().containsKey("replacesFixedBillCode"));
        verify(scheduleRepository, never()).save(any());
    }

    @Test
    void shouldRejectReactivatingASecondAnchor() {
        final IncomeSchedule anchor = new IncomeSchedule(SCHEDULE_CODE, "Salário", AMOUNT, null,
                IncomeDateRule.fixedDay(5, NonBusinessDayPolicy.POSTPONE, BusinessWeek.MONDAY_TO_FRIDAY),
                JUNE, null, true, IncomeScheduleStatus.INACTIVE, null);
        stubLocked(anchor);
        when(scheduleRepository.existsByAnchorTrueAndStatusAndCodeNot(IncomeScheduleStatus.ACTIVE, SCHEDULE_CODE))
                .thenReturn(true);

        final InvalidFieldsException exception =
                assertThrows(InvalidFieldsException.class, () -> service.activate(SCHEDULE_CODE));

        assertTrue(exception.getValidations().containsKey("anchor"));
    }

    @Test
    void shouldNotChangeAnythingWhenTheStatusIsAlreadySet() {
        stubLocked(buildSchedule(JUNE));

        assertEquals(IncomeScheduleStatus.ACTIVE, service.activate(SCHEDULE_CODE).status());

        verify(scheduleRepository, never()).save(any());
    }

    // ---- calendar preview

    @Test
    void shouldPreviewTheOccurrencesWhoseDateMovesWithTheProposedHolidays() {
        final IncomeSchedule schedule = buildSchedule(JUNE);
        when(holidayCalendarService.findCalendar()).thenReturn(buildReviewedCalendar(2026));
        when(scheduleRepository.findAllByStatus(IncomeScheduleStatus.ACTIVE)).thenReturn(List.of(buildEntity(schedule)));
        when(occurrenceRepository.findByScheduleCodeIn(List.of(SCHEDULE_CODE))).thenReturn(List.of());

        // August 5 becomes a holiday: that occurrence moves to Thursday August 6.
        final List<IncomeCalendarImpact> impacts = service.previewCalendarChange(
                2026, List.of(buildHoliday(2026, 8, 5)));

        assertEquals(1, impacts.size());
        assertEquals(SCHEDULE_CODE, impacts.get(0).scheduleCode());
        assertEquals(AUGUST_5, impacts.get(0).change().before().plannedDate());
        assertEquals(LocalDate.of(2026, 8, 6), impacts.get(0).change().after().plannedDate());
    }

    @Test
    void shouldPreviewOverdueUnmaterializedMonthsWhenReviewingTheCalendar() {
        final YearMonth january = YearMonth.of(2026, 1);
        final IncomeSchedule schedule = buildSchedule(january);
        when(holidayCalendarService.findCalendar()).thenReturn(buildReviewedCalendar(2026));
        when(scheduleRepository.findAllByStatus(IncomeScheduleStatus.ACTIVE))
                .thenReturn(List.of(buildEntity(schedule)));
        when(occurrenceRepository.findByScheduleCodeIn(List.of(SCHEDULE_CODE))).thenReturn(List.of());

        final List<IncomeCalendarImpact> impacts = service.previewCalendarChange(
                2026, List.of(buildHoliday(2026, 1, 5)));

        assertThat(impacts).anySatisfy(impact -> {
            assertEquals(january, impact.change().before().nominalMonth());
            assertEquals(LocalDate.of(2026, 1, 5), impact.change().before().plannedDate());
            assertEquals(LocalDate.of(2026, 1, 6), impact.change().after().plannedDate());
        });
    }

    @Test
    void shouldPreviewNothingWhenThereIsNoActiveSchedule() {
        when(holidayCalendarService.findCalendar()).thenReturn(buildReviewedCalendar(2026));
        when(scheduleRepository.findAllByStatus(IncomeScheduleStatus.ACTIVE)).thenReturn(List.of());

        assertThat(service.previewCalendarChange(2026, List.of(buildHoliday(2026, 8, 5)))).isEmpty();
    }

    // ---- helpers

    private IncomeScheduleEntity buildEntity(final IncomeSchedule schedule) {
        final IncomeScheduleEntity entity = IncomeScheduleMapper.INSTANCE.toEntity(schedule);
        entity.setId(1L);
        return entity;
    }

    private IncomeOccurrenceEntity buildOccurrenceEntity(final IncomeSchedule schedule,
                                                         final YearMonth nominalMonth,
                                                         final LocalDate plannedDate) {
        return IncomeOccurrenceEntity.builder()
                .id(10L)
                .code(UUID.randomUUID())
                .incomeSchedule(buildEntity(schedule))
                .nominalMonth(nominalMonth.atDay(1))
                .plannedDate(plannedDate)
                .plannedAmount(AMOUNT)
                .movementCode(MOVEMENT_CODE)
                .build();
    }

    private void stubLocked(final IncomeSchedule schedule) {
        when(scheduleRepository.findForUpdateByCode(schedule.code())).thenReturn(Optional.of(buildEntity(schedule)));
    }

    private void stubSave() {
        when(scheduleRepository.save(any(IncomeScheduleEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private void stubMovementAndOccurrenceSave() {
        when(financialMovementService.createWithAutomaticCompetence(any(FinancialMovement.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(occurrenceRepository.save(any(IncomeOccurrenceEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private void stubReplaceableFixedBill(final FixedBill fixedBill) {
        when(fixedBillService.findByCodeForUpdate(FIXED_BILL_CODE)).thenReturn(fixedBill);
        when(scheduleRepository.findActiveReplacementsForUpdate(
                eq(FIXED_BILL_CODE), eq(IncomeScheduleStatus.ACTIVE), any(UUID.class)))
                .thenReturn(List.of());
        when(financialMovementService.findRecurrenceOccurrencesBetweenForUpdate(
                List.of(FIXED_BILL_CODE), LocalDate.of(2026, 7, 1), LocalDate.of(9999, 12, 31)))
                .thenReturn(List.of());
    }

    private FixedBill buildFixedBill(final OperationType operationType, final LocalDate endDate) {
        return new FixedBill(null, FIXED_BILL_CODE, operationType, "Salário", new BigDecimal("4000.00"),
                RecurrenceType.MONTHLY, Set.of(5), null, FixedBillStatus.ACTIVE, LocalDate.of(2026, 1, 1),
                endDate, LocalDate.of(2026, 7, 5));
    }

    private FinancialMovement buildPostedMovement(final boolean active) {
        return new FinancialMovement(null, OperationType.CREDIT, "Salário", new BigDecimal("4000.00"), null,
                LocalDate.of(2026, 7, 5).atStartOfDay(), LocalDate.of(2026, 7, 5).atStartOfDay(), null,
                FinancialMovementStatus.PENDING, active, FIXED_BILL_CODE, LocalDate.of(2026, 7, 5), null);
    }

    private IncomeScheduleUpdate update(final String description,
                                        final BigDecimal amount,
                                        final UUID categoryCode,
                                        final DateRuleType ruleType,
                                        final Integer ruleValue,
                                        final BusinessWeek businessWeek,
                                        final NonBusinessDayPolicy policy,
                                        final YearMonth endMonth,
                                        final boolean removeEndMonth,
                                        final Boolean anchor) {
        return new IncomeScheduleUpdate(description, amount, categoryCode, ruleType, ruleValue, businessWeek,
                policy, endMonth, removeEndMonth, anchor);
    }
}
