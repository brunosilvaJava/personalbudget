package com.bts.personalbudget.core.domain.service.fixedbill.calc;

import com.bts.personalbudget.core.domain.factory.FixedBillFactory;
import com.bts.personalbudget.core.domain.enumerator.RecurrenceType;
import com.bts.personalbudget.core.domain.service.fixedbill.FixedBill;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
public class MonthlyFixedBillServiceTest {

    @InjectMocks
    private MonthlyCalcFixedBillImpl monthlyFixedBillService;

    @Test
    void shouldReturnMonthlyFixedBillNextDueDateWhenDueDateNextMonth() {
        FixedBill fixedBill = FixedBillFactory.buildModel(RecurrenceType.MONTHLY, List.of(2, 9));
        LocalDate paramDate = LocalDate.of(2024, 10, 10);
        LocalDate nextDueDate = monthlyFixedBillService.calcNextDueDate(fixedBill, paramDate);
        assertEquals(LocalDate.of(2024, 11, 2), nextDueDate);
    }

    @Test
    void shouldReturnMonthlyFixedBillNextDueDateWhenDueDateSameMonth() {
        FixedBill fixedBill = FixedBillFactory.buildModel(RecurrenceType.MONTHLY, List.of(2, 11, 20));
        LocalDate paramDate = LocalDate.of(2024, 10, 10);
        LocalDate nextDueDate = monthlyFixedBillService.calcNextDueDate(fixedBill, paramDate);
        assertEquals(LocalDate.of(2024, 10, 11), nextDueDate);
    }

    @Test
    void shouldReturnMonthlyFixedBillNextDueDateWhenDueDateSameDay() {
        FixedBill fixedBill = FixedBillFactory.buildModel(RecurrenceType.MONTHLY, List.of(2, 10, 20));
        LocalDate paramDate = LocalDate.of(2024, 10, 10);
        LocalDate nextDueDate = monthlyFixedBillService.calcNextDueDate(fixedBill, paramDate);
        assertEquals(LocalDate.of(2024, 10, 10), nextDueDate);
    }

    @Test
    void shouldReturnMonthlyFixedBillNextDueDateWhenDayOfMonthlyDoesNotExist() {
        FixedBill fixedBill = FixedBillFactory.buildModel(RecurrenceType.MONTHLY, List.of(31));
        LocalDate paramDate = LocalDate.of(2024, 9, 10);
        LocalDate nextDueDate = monthlyFixedBillService.calcNextDueDate(fixedBill, paramDate);
        assertEquals(LocalDate.of(2024, 9, 30), nextDueDate);
    }

//    private void saveTest(FixedBill fixedBill, List<Integer> days) {
//        monthlyFixedBillService.save(fixedBill);
//
//        ArgumentCaptor<FixedBillEntity> argumentCaptor = ArgumentCaptor.forClass(FixedBillEntity.class);
//
//        verify(repository).save(argumentCaptor.capture());
//
//        FixedBillEntity entity = argumentCaptor.getValue();
//
//        assertNotNull(entity.getCode());
//        assertEquals(fixedBill.getRecurrenceType(), entity.getRecurrenceType());
//        assertEquals(fixedBill.getOperationType(), entity.getOperationType());
//        assertEquals(fixedBill.getDescription(), entity.getDescription());
//        assertEquals(fixedBill.getAmount(), entity.getAmount());
//        assertEquals(fixedBill.getStartDate(), entity.getStartDate());
//        assertEquals(fixedBill.getEndDate(), entity.getEndDate());
//        assertTrue(entity.getFlagActive());
//        assertEquals(FixedBillStatus.ACTIVE, entity.getStatus());
//
//        List<CalendarFixedBillEntity> calendarFixedBillEntityList = entity.getCalendarFixedBillEntityList();
//
//        assertEquals(days.size(), calendarFixedBillEntityList.size());
//        for (int i = 0; i < days.size(); i++) {
//            assertEquals(days.get(i), calendarFixedBillEntityList.get(i).getDayLaunch());
//            assertTrue(calendarFixedBillEntityList.get(i).getFlgActive());
//            assertFalse(calendarFixedBillEntityList.get(i).getFlgLeapYear());
//        }
//    }

}
