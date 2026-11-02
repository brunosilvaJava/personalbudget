package com.bts.personalbudget.core.domain.service.fixedbill;

import com.bts.personalbudget.core.domain.entity.CalendarFixedBillEntity;
import com.bts.personalbudget.core.domain.entity.FixedBillEntity;
import com.bts.personalbudget.core.domain.enumerator.FixedBillStatus;
import com.bts.personalbudget.core.domain.enumerator.OperationType;
import com.bts.personalbudget.core.domain.enumerator.RecurrenceType;
import com.bts.personalbudget.core.domain.factory.FixedBillFactory;
import com.bts.personalbudget.core.domain.service.fixedbill.calc.MonthlyCalcFixedBillImpl;
import com.bts.personalbudget.core.domain.service.fixedbill.calc.WeeklyCalcFixedBillImpl;
import com.bts.personalbudget.core.domain.service.fixedbill.calc.YearlyCalcFixedBillImpl;
import com.bts.personalbudget.mapper.FixedBillMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import static com.bts.personalbudget.core.domain.factory.FixedBillFactory.END_DATE;
import static com.bts.personalbudget.core.domain.factory.FixedBillFactory.REFERENCE_YEAR;
import static com.bts.personalbudget.core.domain.factory.FixedBillFactory.START_DATE;
import static com.bts.personalbudget.core.domain.factory.FixedBillFactory.STATUS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class FixedBillServiceTest {

    @InjectMocks
    private FixedBillService fixedBillService;

    @Mock
    private FixedBillRepository fixedBillRepository;

    @Mock
    private CalcFixedBillFactory calcFixedBillFactory;

    @Spy
    private FixedBillMapper fixedBillMapper = FixedBillMapper.INSTANCE;

    @Test
    public void shouldSaveYearlyFixedBill() {
        when(calcFixedBillFactory.build(RecurrenceType.YEARLY)).thenReturn(new YearlyCalcFixedBillImpl());
        Set<Integer> days = Set.of(1, 365);
        FixedBill fixedBill = FixedBillFactory.buildModel(RecurrenceType.YEARLY, days);
        saveTest(fixedBill, days);
    }

    @Test
    public void shouldSaveMonthlyFixedBill() {
        when(calcFixedBillFactory.build(RecurrenceType.MONTHLY)).thenReturn(new MonthlyCalcFixedBillImpl());
        Set<Integer> days = Set.of(1, 31);
        FixedBill fixedBill = FixedBillFactory.buildModel(RecurrenceType.MONTHLY, days);
        saveTest(fixedBill, days);
    }

    @Test
    public void shouldSaveWeeklyFixedBill() {
        when(calcFixedBillFactory.build(RecurrenceType.WEEKLY)).thenReturn(new WeeklyCalcFixedBillImpl());
        Set<Integer> days = Set.of(1, 7);
        FixedBill fixedBill = FixedBillFactory.buildModel(RecurrenceType.WEEKLY, days);
        saveTest(fixedBill, days);
    }

    @Test
    public void shouldThrowsExceptionWhenDayIsInvalidForYearlyFixedBill() {
        Set<Integer> days = Set.of(366);
        FixedBill fixedBill = FixedBillFactory.buildModel(RecurrenceType.YEARLY, days);
        assertThrows(RuntimeException.class, () -> fixedBillService.save(fixedBill));
    }

    @Test
    public void shouldThrowsExceptionWhenDayIsInvalidForMonthlyFixedBill() {
        Set<Integer> days = Set.of(32);
        FixedBill fixedBill = FixedBillFactory.buildModel(RecurrenceType.MONTHLY, days);
        assertThrows(RuntimeException.class, () -> fixedBillService.save(fixedBill));
    }

    @Test
    public void shouldThrowsExceptionWhenDayIsInvalidForWeeklyFixedBill() {
        Set<Integer> days = Set.of(8);
        FixedBill fixedBill = FixedBillFactory.buildModel(RecurrenceType.WEEKLY, days);
        assertThrows(RuntimeException.class, () -> fixedBillService.save(fixedBill));
    }

    @Test
    public void shouldThrowsExceptionWhenThereIsEmptyMandatoryFields() {
        FixedBill fixedBill = FixedBillFactory.buildModel(RecurrenceType.YEARLY, Set.of());
        assertThrows(RuntimeException.class, () -> fixedBillService.save(fixedBill));
    }

    @Test
    public void shouldThrowsExceptionWhenThereIsNullFields() {
        assertThrows(RuntimeException.class, () -> fixedBillService.save(new FixedBill(null, null,
                null, null, null, null, null, null,
                null, null, null, null)));
    }

    @Test
    public void shouldThrowsExceptionWhenThereIsEmptyFields() {
        FixedBill fixedBill = FixedBillFactory.buildModelWithEmptyMandatoryFields();
        assertThrows(RuntimeException.class, () -> fixedBillService.save(fixedBill));
    }

    @Test
    void shouldReturnOptionalEmptyWhenFixedBillStatusIsInactive() {
        FixedBill fixedBill = FixedBillFactory.buildFixedBill(Map.of(
                STATUS, FixedBillStatus.INACTIVE
        ));
        Optional<LocalDate> nextDueDateOptional = fixedBillService.defineNextDueDate(fixedBill, LocalDate.now());
        assertTrue(nextDueDateOptional.isEmpty());
    }

    @Test
    void shouldReturnOptionalEmptyWhenBaseDateIsBeforeStartDate() {
        LocalDate startDate = LocalDate.now();
        FixedBill fixedBill = FixedBillFactory.buildFixedBill(Map.of(
                START_DATE, startDate,
                END_DATE, startDate.plusMonths(1)
        ));
        LocalDate baseDate = startDate.minusDays(1);
        Optional<LocalDate> nextDueDateOptional = fixedBillService.defineNextDueDate(fixedBill, baseDate);
        assertTrue(nextDueDateOptional.isEmpty());
    }

    @Test
    void shouldReturnOptionalEmptyWhenBaseDateIsAfterEndDate() {
        LocalDate endDate = LocalDate.now();
        FixedBill fixedBill = FixedBillFactory.buildFixedBill(Map.of(
                START_DATE, endDate.minusMonths(1),
                END_DATE, endDate
        ));
        Optional<LocalDate> nextDueDateOptional = fixedBillService.defineNextDueDate(fixedBill, endDate.plusDays(1));
        assertTrue(nextDueDateOptional.isEmpty());
    }

    private void saveTest(FixedBill fixedBill, Set<Integer> days) {
        fixedBillService.save(fixedBill);

        ArgumentCaptor<FixedBillEntity> argumentCaptor = ArgumentCaptor.forClass(FixedBillEntity.class);

        verify(fixedBillRepository).save(argumentCaptor.capture());

        FixedBillEntity entity = argumentCaptor.getValue();

        assertNotNull(entity.getCode());
        assertEquals(fixedBill.getRecurrenceType(), entity.getRecurrenceType());
        assertEquals(fixedBill.getOperationType(), entity.getOperationType());
        assertEquals(fixedBill.getDescription(), entity.getDescription());
        assertEquals(fixedBill.getAmount(), entity.getAmount());
        assertEquals(fixedBill.getStartDate(), entity.getStartDate());
        assertEquals(fixedBill.getEndDate(), entity.getEndDate());
        assertTrue(entity.getFlagActive());
        assertEquals(FixedBillStatus.ACTIVE, entity.getStatus());

        Set<CalendarFixedBillEntity> calendarFixedBillEntityList = entity.getCalendarFixedBillEntityList();

        assertEquals(days.size(), calendarFixedBillEntityList.size());
    }

    @Test
    void mustNotUpdateFixedBillWhenAllParametersAreNull() {
        FixedBillEntity fixedBillEntityMock = FixedBillFactory.buildFixedBillEntity(FixedBillFactory.PARAMS);

        UUID code = fixedBillEntityMock.getCode();
        OperationType operationType = null;
        String description = null;
        BigDecimal amount = null;
        RecurrenceType recurrenceType = null;
        Set<Integer> days = null;
        Integer referenceYear = null;
        FixedBillStatus status = null;
        LocalDate startDate = null;
        LocalDate endDate = null;

        when(fixedBillRepository.findByCodeAndFlagActiveTrue(code)).thenReturn(Optional.of(fixedBillEntityMock));

        FixedBill updatedFixedBill = fixedBillService.update(code, operationType, description, amount, recurrenceType,
                days, referenceYear, status, startDate, endDate);

        verify(fixedBillRepository, never()).save(any());

        assertEquals(fixedBillEntityMock.getOperationType(), updatedFixedBill.getOperationType());
        assertEquals(fixedBillEntityMock.getDescription(), updatedFixedBill.getDescription());
        assertEquals(fixedBillEntityMock.getAmount(), updatedFixedBill.getAmount());
    }

    @Test
    void mustUpdateFixedBillWhenParametersAreProvided() {
        FixedBillEntity fixedBillEntityMock = FixedBillFactory.buildFixedBillEntity(FixedBillFactory.PARAMS);

        UUID code = fixedBillEntityMock.getCode();
        OperationType operationType = OperationType.CREDIT;
        String description = "Updated Description";
        BigDecimal amount = BigDecimal.valueOf(250.00);
        RecurrenceType recurrenceType = RecurrenceType.MONTHLY;
        Set<Integer> days = Set.of(5, 15);
        Integer referenceYear = 2024;
        FixedBillStatus status = FixedBillStatus.INACTIVE;
        LocalDate startDate = LocalDate.now().minusYears(1);
        LocalDate endDate = LocalDate.now().plusYears(1);

        when(fixedBillRepository.findByCodeAndFlagActiveTrue(code)).thenReturn(Optional.of(fixedBillEntityMock));
        when(fixedBillRepository.save(any(FixedBillEntity.class))).thenReturn(fixedBillEntityMock);

        FixedBill updatedFixedBill = fixedBillService.update(code, operationType, description, amount, recurrenceType,
                days, referenceYear, status, startDate, endDate);

        verify(fixedBillRepository, times(1)).save(any(FixedBillEntity.class));

        assertEquals(operationType, updatedFixedBill.getOperationType(), "operation type is not equals");
        assertEquals(description, updatedFixedBill.getDescription(), "description is not equals");
        assertEquals(amount, updatedFixedBill.getAmount(), "amount is not equals");
        assertEquals(recurrenceType, updatedFixedBill.getRecurrenceType(), "recurrence type is not equals");
        assertEquals(status, updatedFixedBill.getStatus(), "status is not equals");
        assertEquals(startDate, updatedFixedBill.getStartDate(), "start date is not equals");
        assertEquals(endDate, updatedFixedBill.getEndDate(), "end date is not equals");
        assertEquals(referenceYear, updatedFixedBill.getReferenceYear(), "reference year is not equals");
    }

    @Test
    void mustUpdateOnlyProvidedFields() {
        FixedBillEntity fixedBillEntityMock = FixedBillFactory.buildFixedBillEntity(FixedBillFactory.PARAMS);

        String originalDescription = fixedBillEntityMock.getDescription();
        BigDecimal originalAmount = fixedBillEntityMock.getAmount();

        UUID code = fixedBillEntityMock.getCode();
        OperationType operationType = OperationType.CREDIT;
        String description = null;
        BigDecimal amount = null;
        RecurrenceType recurrenceType = null;
        Set<Integer> days = null;
        Integer referenceYear = null;
        FixedBillStatus status = null;
        LocalDate startDate = null;
        LocalDate endDate = null;

        when(fixedBillRepository.findByCodeAndFlagActiveTrue(code)).thenReturn(Optional.of(fixedBillEntityMock));
        when(fixedBillRepository.save(any(FixedBillEntity.class))).thenReturn(fixedBillEntityMock);

        FixedBill updatedFixedBill = fixedBillService.update(code, operationType, description, amount, recurrenceType,
                days, referenceYear, status, startDate, endDate);

        verify(fixedBillRepository, times(1)).save(any(FixedBillEntity.class));

        assertEquals(operationType, updatedFixedBill.getOperationType());
        assertEquals(originalDescription, updatedFixedBill.getDescription());
        assertEquals(originalAmount, updatedFixedBill.getAmount());
    }

    @Test
    void shouldChangeStatusToInactive() {
        FixedBillEntity fixedBillEntityMock = FixedBillFactory.buildFixedBillEntity(FixedBillFactory.PARAMS);
        fixedBillEntityMock.setStatus(FixedBillStatus.ACTIVE);

        UUID code = fixedBillEntityMock.getCode();

        when(fixedBillRepository.findByCodeAndFlagActiveTrue(code)).thenReturn(Optional.of(fixedBillEntityMock));
        when(fixedBillRepository.save(any(FixedBillEntity.class))).thenReturn(fixedBillEntityMock);

        FixedBill result = fixedBillService.changeStatus(code, FixedBillStatus.INACTIVE);

        verify(fixedBillRepository, times(1)).save(fixedBillEntityMock);
        assertEquals(FixedBillStatus.INACTIVE, fixedBillEntityMock.getStatus());
        assertEquals(FixedBillStatus.INACTIVE, result.getStatus());
    }

    @Test
    void shouldChangeStatusToActive() {
        when(calcFixedBillFactory.build(RecurrenceType.YEARLY)).thenReturn(new YearlyCalcFixedBillImpl());

        FixedBillEntity fixedBillEntityMock = FixedBillFactory.buildFixedBillEntity(FixedBillFactory.PARAMS);
        fixedBillEntityMock.setStatus(FixedBillStatus.INACTIVE);
        fixedBillEntityMock.setNextDueDate(null);

        UUID code = fixedBillEntityMock.getCode();

        when(fixedBillRepository.findByCodeAndFlagActiveTrue(code)).thenReturn(Optional.of(fixedBillEntityMock));
        when(fixedBillRepository.save(any(FixedBillEntity.class))).thenReturn(fixedBillEntityMock);

        FixedBill result = fixedBillService.changeStatus(code, FixedBillStatus.ACTIVE);

        verify(fixedBillRepository, times(1)).save(fixedBillEntityMock);
        assertEquals(FixedBillStatus.ACTIVE, fixedBillEntityMock.getStatus());
        assertEquals(FixedBillStatus.ACTIVE, result.getStatus());
        assertNotNull(fixedBillEntityMock.getNextDueDate());
        assertTrue(fixedBillEntityMock.getNextDueDate().isAfter(LocalDate.now()));
    }

    @Test
    void shouldNotChangeStatusWhenAlreadyInDesiredState() {
        FixedBillEntity fixedBillEntityMock = FixedBillFactory.buildFixedBillEntity(FixedBillFactory.PARAMS);
        fixedBillEntityMock.setStatus(FixedBillStatus.ACTIVE);

        UUID code = fixedBillEntityMock.getCode();

        when(fixedBillRepository.findByCodeAndFlagActiveTrue(code)).thenReturn(Optional.of(fixedBillEntityMock));

        FixedBill result = fixedBillService.changeStatus(code, FixedBillStatus.ACTIVE);

        verify(fixedBillRepository, never()).save(any());
        assertEquals(FixedBillStatus.ACTIVE, result.getStatus());
    }

    @Test
    void shouldSaveYearlyFixedBillWithReferenceYear() {
        when(calcFixedBillFactory.build(RecurrenceType.YEARLY)).thenReturn(new YearlyCalcFixedBillImpl());

        Set<Integer> days = Set.of(1, 182, 365);
        Integer referenceYear = 2024;

        FixedBill fixedBill = FixedBillFactory.buildFixedBill(Map.of(
                REFERENCE_YEAR, referenceYear
        ));
        fixedBill.setRecurrenceType(RecurrenceType.YEARLY);
        fixedBill.setDays(days);

        fixedBillService.save(fixedBill);

        ArgumentCaptor<FixedBillEntity> argumentCaptor = ArgumentCaptor.forClass(FixedBillEntity.class);
        verify(fixedBillRepository).save(argumentCaptor.capture());

        FixedBillEntity entity = argumentCaptor.getValue();

        assertEquals(RecurrenceType.YEARLY, entity.getRecurrenceType());
        assertEquals(referenceYear, entity.getReferenceYear());
        assertEquals(days.size(), entity.getCalendarFixedBillEntityList().size());
    }

    @Test
    void shouldConcludeFixedBillWhenThereIsNoNextDueDate() {
        LocalDate endDate = LocalDate.now();

        FixedBill fixedBill = FixedBillFactory.buildFixedBill(Map.of(
                START_DATE, endDate.minusMonths(1),
                END_DATE, endDate
        ));

        FixedBillEntity fixedBillEntityMock = FixedBillFactory.buildFixedBillEntity(FixedBillFactory.PARAMS);
        fixedBillEntityMock.setStatus(FixedBillStatus.ACTIVE);
        fixedBillEntityMock.setNextDueDate(endDate);

        when(fixedBillRepository.findByCodeAndFlagActiveTrue(fixedBill.getCode()))
                .thenReturn(Optional.of(fixedBillEntityMock));

        fixedBillService.updateNextDueDate(fixedBill, endDate.plusDays(1));

        assertEquals(FixedBillStatus.INACTIVE, fixedBillEntityMock.getStatus());
        assertNull(fixedBillEntityMock.getNextDueDate());
        assertEquals(FixedBillStatus.INACTIVE, fixedBill.getStatus());
    }

    @Test
    void shouldUpdateNextDueDateWhenFixedBillIsStillCurrent() {
        when(calcFixedBillFactory.build(RecurrenceType.MONTHLY)).thenReturn(new MonthlyCalcFixedBillImpl());

        LocalDate startDate = LocalDate.of(2026, 1, 1);

        FixedBill fixedBill = FixedBillFactory.buildFixedBill(Map.of(
                FixedBillFactory.RECURRENCE_TYPE, RecurrenceType.MONTHLY,
                FixedBillFactory.DAYS, Set.of(10),
                START_DATE, startDate,
                END_DATE, startDate.plusYears(5)
        ));

        FixedBillEntity fixedBillEntityMock = FixedBillFactory.buildFixedBillEntity(FixedBillFactory.PARAMS);
        fixedBillEntityMock.setStatus(FixedBillStatus.ACTIVE);

        when(fixedBillRepository.findByCodeAndFlagActiveTrue(fixedBill.getCode()))
                .thenReturn(Optional.of(fixedBillEntityMock));

        fixedBillService.updateNextDueDate(fixedBill, LocalDate.of(2026, 3, 11));

        assertEquals(LocalDate.of(2026, 4, 10), fixedBillEntityMock.getNextDueDate());
        assertEquals(FixedBillStatus.ACTIVE, fixedBillEntityMock.getStatus());
    }

    @Test
    void shouldDefineStartDateAsCurrentDateWhenStartDateIsNull() {
        when(calcFixedBillFactory.build(RecurrenceType.MONTHLY)).thenReturn(new MonthlyCalcFixedBillImpl());

        FixedBill fixedBill = FixedBillFactory.buildModelWithoutStartDate(RecurrenceType.MONTHLY, Set.of(15));

        assertEquals(LocalDate.now(), fixedBill.getStartDate());

        fixedBillService.save(fixedBill);

        ArgumentCaptor<FixedBillEntity> argumentCaptor = ArgumentCaptor.forClass(FixedBillEntity.class);
        verify(fixedBillRepository).save(argumentCaptor.capture());

        FixedBillEntity entity = argumentCaptor.getValue();

        assertEquals(LocalDate.now(), entity.getStartDate());
        assertNotNull(entity.getNextDueDate());
    }

    @Test
    void shouldConsiderFixedBillCurrentWhenStartDateIsNull() {
        FixedBill fixedBill =
                FixedBillFactory.buildModelWithNullStartDateAndCode(RecurrenceType.MONTHLY, Set.of(10));

        assertNull(fixedBill.getStartDate());
        assertTrue(fixedBill.isCurrent(LocalDate.now()));
    }

    @Test
    void shouldConcludeFixedBillWhenNextOccurrenceIsAfterEndDate() {
        when(calcFixedBillFactory.build(RecurrenceType.MONTHLY)).thenReturn(new MonthlyCalcFixedBillImpl());

        FixedBill fixedBill = FixedBillFactory.buildFixedBill(Map.of(
                FixedBillFactory.RECURRENCE_TYPE, RecurrenceType.MONTHLY,
                FixedBillFactory.DAYS, Set.of(10),
                START_DATE, LocalDate.of(2026, 1, 1),
                END_DATE, LocalDate.of(2026, 5, 15)
        ));

        FixedBillEntity fixedBillEntityMock = FixedBillFactory.buildFixedBillEntity(FixedBillFactory.PARAMS);
        fixedBillEntityMock.setStatus(FixedBillStatus.ACTIVE);
        fixedBillEntityMock.setNextDueDate(LocalDate.of(2026, 5, 10));

        when(fixedBillRepository.findByCodeAndFlagActiveTrue(fixedBill.getCode()))
                .thenReturn(Optional.of(fixedBillEntityMock));

        fixedBillService.updateNextDueDate(fixedBill, LocalDate.of(2026, 5, 11));

        assertEquals(FixedBillStatus.INACTIVE, fixedBillEntityMock.getStatus());
        assertNull(fixedBillEntityMock.getNextDueDate());
    }

    @Test
    void shouldKeepNextDueDateWhenNextOccurrenceIsOnEndDate() {
        when(calcFixedBillFactory.build(RecurrenceType.MONTHLY)).thenReturn(new MonthlyCalcFixedBillImpl());

        FixedBill fixedBill = FixedBillFactory.buildFixedBill(Map.of(
                FixedBillFactory.RECURRENCE_TYPE, RecurrenceType.MONTHLY,
                FixedBillFactory.DAYS, Set.of(10),
                START_DATE, LocalDate.of(2026, 1, 1),
                END_DATE, LocalDate.of(2026, 6, 10)
        ));

        Optional<LocalDate> nextDueDate = fixedBillService.defineNextDueDate(fixedBill, LocalDate.of(2026, 5, 11));

        assertEquals(Optional.of(LocalDate.of(2026, 6, 10)), nextDueDate);
    }

    @Test
    void shouldDefineFirstDueDateFromStartDateWhenStartDateIsInFuture() {
        when(calcFixedBillFactory.build(RecurrenceType.MONTHLY)).thenReturn(new MonthlyCalcFixedBillImpl());

        LocalDate startDate = LocalDate.now().plusMonths(2).withDayOfMonth(5);

        FixedBill fixedBill = FixedBillFactory.buildFixedBill(Map.of(
                FixedBillFactory.RECURRENCE_TYPE, RecurrenceType.MONTHLY,
                FixedBillFactory.DAYS, Set.of(10),
                START_DATE, startDate,
                END_DATE, startDate.plusYears(1)
        ));

        fixedBillService.save(fixedBill);

        ArgumentCaptor<FixedBillEntity> argumentCaptor = ArgumentCaptor.forClass(FixedBillEntity.class);
        verify(fixedBillRepository).save(argumentCaptor.capture());

        assertEquals(startDate.withDayOfMonth(10), argumentCaptor.getValue().getNextDueDate());
    }

    @Test
    void shouldRecalculateNextDueDateWhenScheduleIsUpdated() {
        when(calcFixedBillFactory.build(RecurrenceType.MONTHLY)).thenReturn(new MonthlyCalcFixedBillImpl());

        FixedBillEntity fixedBillEntityMock = FixedBillFactory.buildFixedBillEntity(FixedBillFactory.PARAMS);
        fixedBillEntityMock.setNextDueDate(LocalDate.now().plusDays(100));
        UUID code = fixedBillEntityMock.getCode();

        when(fixedBillRepository.findByCodeAndFlagActiveTrue(code)).thenReturn(Optional.of(fixedBillEntityMock));

        FixedBill updatedFixedBill = fixedBillService.update(code, null, null, null, RecurrenceType.MONTHLY,
                Set.of(10), null, null, null, null);

        LocalDate tomorrow = LocalDate.now().plusDays(1);
        LocalDate expectedNextDueDate = tomorrow.getDayOfMonth() <= 10
                ? tomorrow.withDayOfMonth(10)
                : tomorrow.plusMonths(1).withDayOfMonth(10);

        ArgumentCaptor<FixedBillEntity> argumentCaptor = ArgumentCaptor.forClass(FixedBillEntity.class);
        verify(fixedBillRepository).save(argumentCaptor.capture());

        assertEquals(expectedNextDueDate, argumentCaptor.getValue().getNextDueDate());
        assertEquals(expectedNextDueDate, updatedFixedBill.getNextDueDate());
    }

    @Test
    void shouldNotRecalculateNextDueDateWhenOnlyDescriptionIsUpdated() {
        FixedBillEntity fixedBillEntityMock = FixedBillFactory.buildFixedBillEntity(FixedBillFactory.PARAMS);
        LocalDate currentNextDueDate = LocalDate.now().plusDays(100);
        fixedBillEntityMock.setNextDueDate(currentNextDueDate);
        UUID code = fixedBillEntityMock.getCode();

        when(fixedBillRepository.findByCodeAndFlagActiveTrue(code)).thenReturn(Optional.of(fixedBillEntityMock));

        fixedBillService.update(code, null, "Updated Description", null, null,
                null, null, null, null, null);

        ArgumentCaptor<FixedBillEntity> argumentCaptor = ArgumentCaptor.forClass(FixedBillEntity.class);
        verify(fixedBillRepository).save(argumentCaptor.capture());
        verify(calcFixedBillFactory, never()).build(any());

        assertEquals(currentNextDueDate, argumentCaptor.getValue().getNextDueDate());
    }

    @Test
    void shouldDefineFirstDueDateAfterTodayWhenDueDayIsToday() {
        when(calcFixedBillFactory.build(RecurrenceType.MONTHLY)).thenReturn(new MonthlyCalcFixedBillImpl());

        LocalDate today = LocalDate.now();
        FixedBill fixedBill = FixedBillFactory.buildFixedBill(Map.of(
                FixedBillFactory.RECURRENCE_TYPE, RecurrenceType.MONTHLY,
                FixedBillFactory.DAYS, Set.of(today.getDayOfMonth()),
                START_DATE, today
        ));

        fixedBillService.save(fixedBill);

        ArgumentCaptor<FixedBillEntity> argumentCaptor = ArgumentCaptor.forClass(FixedBillEntity.class);
        verify(fixedBillRepository).save(argumentCaptor.capture());

        assertTrue(argumentCaptor.getValue().getNextDueDate().isAfter(today));
    }
}
