package com.bts.personalbudget.core.domain.service;

import com.bts.personalbudget.controller.fixedbill.FixedBillRepository;
import com.bts.personalbudget.core.domain.entity.CalendarFixedBillEntity;
import com.bts.personalbudget.core.domain.entity.FixedBillEntity;
import com.bts.personalbudget.core.domain.entity.FixedBillFactory;
import com.bts.personalbudget.core.domain.enumerator.FixedBillStatus;
import com.bts.personalbudget.core.domain.enumerator.RecurrenceType;
import com.bts.personalbudget.core.domain.model.FixedBill;
import com.bts.personalbudget.core.domain.service.fixedbill.MonthlyFixedBillServiceImpl;
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
public class MonthlyFixedBillServiceTest {

    @InjectMocks
    private MonthlyFixedBillServiceImpl monthlyFixedBillService;

    @Mock
    private FixedBillRepository repository;

    @Spy
    private FixedBillMapper fixedBillMapper = FixedBillMapper.INSTANCE;

    @Test
    public void shouldSaveMonthlyFixedBill() {
        List<Integer> days = List.of(1, 31);
        FixedBill fixedBill = FixedBillFactory.buildModel(RecurrenceType.MONTHLY, days);
        saveTest(fixedBill, days);
    }

    @Test
    public void shouldThrowsExceptionWhenDayIsInvalidForMonthlyFixedBill() {
        List<Integer> days = List.of(32);
        FixedBill fixedBill = FixedBillFactory.buildModel(RecurrenceType.MONTHLY, days);
        assertThrows(RuntimeException.class, () -> monthlyFixedBillService.save(fixedBill));
    }

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

    private void saveTest(FixedBill fixedBill, List<Integer> days) {
        monthlyFixedBillService.save(fixedBill);

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
