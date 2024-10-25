package com.bts.personalbudget.core.domain.service;

import com.bts.personalbudget.core.domain.entity.FixedBillFactory;
import com.bts.personalbudget.core.domain.enumerator.FixedBillStatus;
import com.bts.personalbudget.core.domain.model.FixedBill;
import com.bts.personalbudget.core.domain.service.fixedbill.WeeklyFixedBillServiceImpl;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import static com.bts.personalbudget.core.domain.entity.FixedBillFactory.END_DATE;
import static com.bts.personalbudget.core.domain.entity.FixedBillFactory.START_DATE;
import static com.bts.personalbudget.core.domain.entity.FixedBillFactory.STATUS;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
public class FixedBillServiceTest {

    @InjectMocks
    private WeeklyFixedBillServiceImpl fixedBillService;

    @Test
    public void shouldThrowsExceptionWhenThereIsNullFields() {
        assertThrows(RuntimeException.class, () -> fixedBillService.save(new FixedBill()));
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

}
