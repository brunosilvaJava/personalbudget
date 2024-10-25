package com.bts.personalbudget.core.domain.service;

import com.bts.personalbudget.controller.fixedbill.FixedBillRepository;
import com.bts.personalbudget.core.domain.entity.CalendarFixedBillEntity;
import com.bts.personalbudget.core.domain.entity.FixedBillEntity;
import com.bts.personalbudget.core.domain.entity.FixedBillFactory;
import com.bts.personalbudget.core.domain.enumerator.FixedBillStatus;
import com.bts.personalbudget.core.domain.enumerator.RecurrenceType;
import com.bts.personalbudget.core.domain.model.FixedBill;
import com.bts.personalbudget.core.domain.service.fixedbill.WeeklyFixedBillServiceImpl;
import com.bts.personalbudget.mapper.FixedBillMapper;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class WeeklyFixedBillServiceTest {

    @InjectMocks
    private WeeklyFixedBillServiceImpl weeklyFixedBillService;

    @Mock
    private FixedBillRepository repository;

    @Spy
    private FixedBillMapper fixedBillMapper = FixedBillMapper.INSTANCE;

    @Test
    public void shouldSaveWeeklyFixedBill() {
        List<Integer> days = List.of(7, 1);
        FixedBill fixedBill = FixedBillFactory.buildModel(RecurrenceType.WEEKLY, days);
        saveTest(fixedBill, days);
    }

    @Test
    public void shouldThrowsExceptionWhenDayIsInvalidForWeeklyFixedBill() {
        List<Integer> days = List.of(8);
        FixedBill fixedBill = FixedBillFactory.buildModel(RecurrenceType.WEEKLY, days);
        assertThrows(RuntimeException.class, () -> weeklyFixedBillService.save(fixedBill));
    }

    @Test
    void shouldReturnWeeklyFixedBillNextDueDateWhenDueDateNextWeekly() {
        FixedBill fixedBill = FixedBillFactory.buildModel(RecurrenceType.WEEKLY, List.of(2, 4));
        LocalDate paramDate = LocalDate.of(2024, 10, 10);
        LocalDate nextDueDate = weeklyFixedBillService.calcNextDueDate(fixedBill, paramDate);
        assertEquals(LocalDate.of(2024, 10, 14), nextDueDate);
    }

    @Test
    void shouldReturnWeeklyFixedBillNextDueDateWhenDueDateSameWeekly() {
        FixedBill fixedBill = FixedBillFactory.buildModel(RecurrenceType.WEEKLY, List.of(2, 7));
        LocalDate paramDate = LocalDate.of(2024, 10, 10);
        LocalDate nextDueDate = weeklyFixedBillService.calcNextDueDate(fixedBill, paramDate);
        assertEquals(LocalDate.of(2024, 10, 12), nextDueDate);
    }

    @Test
    void shouldReturnWeeklyFixedBillNextDueDateWhenDueDateSameDay() {
        FixedBill fixedBill = FixedBillFactory.buildModel(RecurrenceType.WEEKLY, List.of(2, 5));
        LocalDate paramDate = LocalDate.of(2024, 10, 10);
        LocalDate nextDueDate = weeklyFixedBillService.calcNextDueDate(fixedBill, paramDate);
        assertEquals(LocalDate.of(2024, 10, 10), nextDueDate);
    }

    @Test
    void shouldReturnWeeklyFixedBillNextDueDate() {
        FixedBill fixedBill = FixedBillFactory.buildModel(RecurrenceType.WEEKLY, List.of(2, 6));
        LocalDate paramDate = LocalDate.of(2024, 10, 10);
        LocalDate nextDueDate = weeklyFixedBillService.calcNextDueDate(fixedBill, paramDate);
        assertEquals(LocalDate.of(2024, 10, 11), nextDueDate);
    }

    private void saveTest(FixedBill fixedBill, List<Integer> days) {
        weeklyFixedBillService.save(fixedBill);

        ArgumentCaptor<FixedBillEntity> argumentCaptor = ArgumentCaptor.forClass(FixedBillEntity.class);

        verify(repository).save(argumentCaptor.capture());

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
