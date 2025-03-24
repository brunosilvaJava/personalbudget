package com.bts.personalbudget.core.domain.service.fixedbill;

import com.bts.personalbudget.controller.fixedbill.FixedBillRepository;
import com.bts.personalbudget.core.domain.entity.CalendarFixedBillEntity;
import com.bts.personalbudget.core.domain.entity.FixedBillEntity;
import com.bts.personalbudget.core.domain.factory.FixedBillFactory;
import com.bts.personalbudget.core.domain.enumerator.FixedBillStatus;
import com.bts.personalbudget.core.domain.enumerator.RecurrenceType;
import com.bts.personalbudget.core.domain.model.FixedBill;
import com.bts.personalbudget.core.domain.service.fixedbill.calc.MonthlyCalcFixedBillImpl;
import com.bts.personalbudget.core.domain.service.fixedbill.calc.WeeklyCalcFixedBillImpl;
import com.bts.personalbudget.core.domain.service.fixedbill.calc.YearlyCalcFixedBillImpl;
import com.bts.personalbudget.mapper.FixedBillMapper;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import static com.bts.personalbudget.core.domain.factory.FixedBillFactory.END_DATE;
import static com.bts.personalbudget.core.domain.factory.FixedBillFactory.START_DATE;
import static com.bts.personalbudget.core.domain.factory.FixedBillFactory.STATUS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class FixedBillServiceTest {

    @InjectMocks
    private FixedBillServiceImpl fixedBillService;

    @Mock
    private FixedBillRepository fixedBillRepository;

    @Mock
    private CalcFixedBillFactory calcFixedBillFactory;

    @Spy
    private FixedBillMapper fixedBillMapper = FixedBillMapper.INSTANCE;

    @Test
    public void shouldSaveYearlyFixedBill() {
        when(calcFixedBillFactory.build(RecurrenceType.YEARLY)).thenReturn(new YearlyCalcFixedBillImpl());
        List<Integer> days = List.of(1, 365);
        FixedBill fixedBill = FixedBillFactory.buildModel(RecurrenceType.YEARLY, days);
        saveTest(fixedBill, days);
    }

    @Test
    public void shouldSaveMonthlyFixedBill() {
        when(calcFixedBillFactory.build(RecurrenceType.MONTHLY)).thenReturn(new MonthlyCalcFixedBillImpl());
        List<Integer> days = List.of(1, 31);
        FixedBill fixedBill = FixedBillFactory.buildModel(RecurrenceType.MONTHLY, days);
        saveTest(fixedBill, days);
    }

    @Test
    public void shouldSaveWeeklyFixedBill() {
        when(calcFixedBillFactory.build(RecurrenceType.WEEKLY)).thenReturn(new WeeklyCalcFixedBillImpl());
        List<Integer> days = List.of(7, 1);
        FixedBill fixedBill = FixedBillFactory.buildModel(RecurrenceType.WEEKLY, days);
        saveTest(fixedBill, days);
    }

    @Test
    public void shouldThrowsExceptionWhenDayIsInvalidForYearlyFixedBill() {
        List<Integer> days = List.of(366);
        FixedBill fixedBill = FixedBillFactory.buildModel(RecurrenceType.YEARLY, days);
        assertThrows(RuntimeException.class, () -> fixedBillService.save(fixedBill));
    }

    @Test
    public void shouldThrowsExceptionWhenDayIsInvalidForMonthlyFixedBill() {
        List<Integer> days = List.of(32);
        FixedBill fixedBill = FixedBillFactory.buildModel(RecurrenceType.MONTHLY, days);
        assertThrows(RuntimeException.class, () -> fixedBillService.save(fixedBill));
    }

    @Test
    public void shouldThrowsExceptionWhenDayIsInvalidForWeeklyFixedBill() {
        List<Integer> days = List.of(8);
        FixedBill fixedBill = FixedBillFactory.buildModel(RecurrenceType.WEEKLY, days);
        assertThrows(RuntimeException.class, () -> fixedBillService.save(fixedBill));
    }

    @Test
    public void shouldThrowsExceptionWhenThereIsEmptyMandatoryFields() {
        FixedBill fixedBill = FixedBillFactory.buildModel(RecurrenceType.YEARLY, List.of());
        assertThrows(RuntimeException.class, () -> fixedBillService.save(fixedBill));
    }

    @Test
    public void shouldThrowsExceptionWhenThereIsNullFields() {
        assertThrows(RuntimeException.class, () -> fixedBillService.save(new FixedBill(null, null,
                null, null, null, null, null, null,
                null, null, null)));
    }

    @Test
    public void shouldThrowsExceptionWhenThereIsEmptyFields() {
        FixedBill fixedBill = FixedBillFactory.buildModelWithEmptyMandatoryFields();
        assertThrows(RuntimeException.class, () -> fixedBillService.save(fixedBill));
    }

    @Test
    void shouldReturnOptionalEmptyWhenFixedBillStatusIsInactive(){
        FixedBill fixedBill = FixedBillFactory.buildFixedBill(Map.of(
                STATUS, FixedBillStatus.INACTIVE
        ));
        Optional<LocalDate> nextDueDateOptional = fixedBillService.defineNextDueDate(fixedBill, LocalDate.now());
        assertTrue(nextDueDateOptional.isEmpty());
    }

    @Test
    void shouldReturnOptionalEmptyWhenBaseDateIsBeforeStartDate(){
        LocalDate startDate = LocalDate.now();
        FixedBill fixedBill = FixedBillFactory.buildFixedBill(Map.of(
                START_DATE, startDate,
                END_DATE, startDate.plusMonths(1)
        ));
        Optional<LocalDate> nextDueDateOptional = fixedBillService.defineNextDueDate(fixedBill, startDate.minusDays(1));
        assertTrue(nextDueDateOptional.isEmpty());
    }

    @Test
    void shouldReturnOptionalEmptyWhenBaseDateIsAfterEndDate(){
        LocalDate endDate = LocalDate.now();
        FixedBill fixedBill = FixedBillFactory.buildFixedBill(Map.of(
                START_DATE, endDate.minusMonths(1),
                END_DATE, endDate
        ));
        Optional<LocalDate> nextDueDateOptional = fixedBillService.defineNextDueDate(fixedBill, endDate.plusDays(1));
        assertTrue(nextDueDateOptional.isEmpty());
    }

    private void saveTest(FixedBill fixedBill, List<Integer> days) {
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

        List<CalendarFixedBillEntity> calendarFixedBillEntityList = entity.getCalendarFixedBillEntityList();

        assertEquals(days.size(), calendarFixedBillEntityList.size());
        for (int i = 0; i < days.size(); i++) {
            assertEquals(days.get(i), calendarFixedBillEntityList.get(i).getDayLaunch());
            assertTrue(calendarFixedBillEntityList.get(i).getFlgActive());
            assertFalse(calendarFixedBillEntityList.get(i).getFlgLeapYear());
        }
    }

}
