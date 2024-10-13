package com.bts.personalbudget.core.domain.service;

import com.bts.personalbudget.core.domain.entity.FixedBillEntity;
import com.bts.personalbudget.controller.fixedbill.FixedBillRepository;
import com.bts.personalbudget.core.domain.entity.CalendarFixedBillEntity;
import com.bts.personalbudget.core.domain.entity.FixedBillFactory;
import com.bts.personalbudget.core.domain.enumerator.FixedBillStatus;
import com.bts.personalbudget.core.domain.enumerator.RecurrenceType;
import com.bts.personalbudget.core.domain.model.FixedBill;
import com.bts.personalbudget.mapper.FixedBillMapper;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
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
public class FixedBillServiceTest {

    @InjectMocks
    private FixedBillService fixedBillService;

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
    public void shouldSaveMonthlyFixedBill() {
        List<Integer> days = List.of(1, 31);
        FixedBill fixedBill = FixedBillFactory.buildModel(RecurrenceType.MONTHLY, days);
        saveTest(fixedBill, days);
    }

    @Test
    public void shouldSaveYearlyFixedBill() {
        List<Integer> days = List.of(1, 365);
        FixedBill fixedBill = FixedBillFactory.buildModel(RecurrenceType.YEARLY, days);
        saveTest(fixedBill, days);
    }

    @Test
    public void shouldThrowsExceptionWhenDayIsInvalidForWeeklyFixedBill() {
        List<Integer> days = List.of(8);
        FixedBill fixedBill = FixedBillFactory.buildModel(RecurrenceType.WEEKLY, days);
        assertThrows(RuntimeException.class, () -> fixedBillService.save(fixedBill));
    }

    @Test
    public void shouldThrowsExceptionWhenDayIsInvalidForMonthlyFixedBill() {
        List<Integer> days = List.of(32);
        FixedBill fixedBill = FixedBillFactory.buildModel(RecurrenceType.MONTHLY, days);
        assertThrows(RuntimeException.class, () -> fixedBillService.save(fixedBill));
    }

    @Test
    public void shouldThrowsExceptionWhenDayIsInvalidForYearlyFixedBill() {
        List<Integer> days = List.of(366);
        FixedBill fixedBill = FixedBillFactory.buildModel(RecurrenceType.YEARLY, days);
        assertThrows(RuntimeException.class, () -> fixedBillService.save(fixedBill));
    }

    @Test
    public void shouldThrowsExceptionWhenThereIsEmptyMandatoryFields() {
        FixedBill fixedBill = FixedBillFactory.buildModel(RecurrenceType.YEARLY, null);
        assertThrows(RuntimeException.class, () -> fixedBillService.save(fixedBill));
    }

    @Test
    public void shouldThrowsExceptionWhenThereIsNullFields() {
        assertThrows(RuntimeException.class, () -> fixedBillService.save(new FixedBill()));
    }

    @Test
    public void shouldThrowsExceptionWhenThereIsEmptyFields() {
        FixedBill fixedBill = FixedBillFactory.buildModelWithEmptyMandatoryFields();
        assertThrows(RuntimeException.class, () -> fixedBillService.save(fixedBill));
    }

    private void saveTest(FixedBill fixedBill, List<Integer> days) {
        fixedBillService.save(fixedBill);

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

    @Test
    void shouldReturnWeeklyFixedBillNextDueDateWhenDueDateNextWeekly() {
        FixedBill fixedBill = FixedBillFactory.buildModel(RecurrenceType.WEEKLY, List.of(2, 4));
        LocalDate paramDate = LocalDate.of(2024, 10, 10);
        Optional<LocalDate> nextDueDateOptional = fixedBillService.defineNextDueDate(fixedBill, paramDate);
        assertTrue(nextDueDateOptional.isPresent());
        assertEquals(LocalDate.of(2024, 10, 14), nextDueDateOptional.get());
    }

    @Test
    void shouldReturnWeeklyFixedBillNextDueDateWhenDueDateSameWeekly() {
        FixedBill fixedBill = FixedBillFactory.buildModel(RecurrenceType.WEEKLY, List.of(2, 7));
        LocalDate paramDate = LocalDate.of(2024, 10, 10);
        Optional<LocalDate> nextDueDateOptional = fixedBillService.defineNextDueDate(fixedBill, paramDate);
        assertTrue(nextDueDateOptional.isPresent());
        assertEquals(LocalDate.of(2024, 10, 12), nextDueDateOptional.get());
    }

    @Test
    void shouldReturnWeeklyFixedBillNextDueDateWhenDueDateSameDay() {
        FixedBill fixedBill = FixedBillFactory.buildModel(RecurrenceType.WEEKLY, List.of(2, 5));
        LocalDate paramDate = LocalDate.of(2024, 10, 10);
        Optional<LocalDate> nextDueDateOptional = fixedBillService.defineNextDueDate(fixedBill, paramDate);
        assertTrue(nextDueDateOptional.isPresent());
        assertEquals(LocalDate.of(2024, 10, 10), nextDueDateOptional.get());
    }

    @Test
    void shouldReturnWeeklyFixedBillNextDueDate() {
        FixedBill fixedBill = FixedBillFactory.buildModel(RecurrenceType.WEEKLY, List.of(2, 9));
        LocalDate paramDate = LocalDate.of(2024, 10, 10);
        Optional<LocalDate> nextDueDateOptional = fixedBillService.defineNextDueDate(fixedBill, paramDate);
        assertTrue(nextDueDateOptional.isPresent());
        assertEquals(LocalDate.of(2024, 10, 12), nextDueDateOptional.get());
    }

    @Test
    void shouldReturnMonthlyFixedBillNextDueDateWhenDueDateNextMonth() {
        FixedBill fixedBill = FixedBillFactory.buildModel(RecurrenceType.MONTHLY, List.of(2, 9));
        LocalDate paramDate = LocalDate.of(2024, 10, 10);
        Optional<LocalDate> nextDueDateOptional = fixedBillService.defineNextDueDate(fixedBill, paramDate);
        assertTrue(nextDueDateOptional.isPresent());
        assertEquals(LocalDate.of(2024, 11, 2), nextDueDateOptional.get());
    }

    @Test
    void shouldReturnMonthlyFixedBillNextDueDateWhenDueDateSameMonth() {
        FixedBill fixedBill = FixedBillFactory.buildModel(RecurrenceType.MONTHLY, List.of(2, 11, 20));
        LocalDate paramDate = LocalDate.of(2024, 10, 10);
        Optional<LocalDate> nextDueDateOptional = fixedBillService.defineNextDueDate(fixedBill, paramDate);
        assertTrue(nextDueDateOptional.isPresent());
        assertEquals(LocalDate.of(2024, 10, 11), nextDueDateOptional.get());
    }

    @Test
    void shouldReturnMonthlyFixedBillNextDueDateWhenDueDateSameDay() {
        FixedBill fixedBill = FixedBillFactory.buildModel(RecurrenceType.MONTHLY, List.of(2, 10, 20));
        LocalDate paramDate = LocalDate.of(2024, 10, 10);
        Optional<LocalDate> nextDueDateOptional = fixedBillService.defineNextDueDate(fixedBill, paramDate);
        assertTrue(nextDueDateOptional.isPresent());
        assertEquals(LocalDate.of(2024, 10, 10), nextDueDateOptional.get());
    }

    @Test
    @DisplayName("Próximo data de vencimento - Tipo Anual / Dia 60-bissexto (29/02) / data base de ano bissexto e anterior do vencimento")
    void test1aa() {
        testDefineNextDueDate(
                RecurrenceType.YEARLY,
                List.of(60),
                Boolean.TRUE,
                LocalDate.of(2024, 2, 28),
                LocalDate.of(2024, 2, 29));
    }

    @Test
    @DisplayName("Próximo data de vencimento - Tipo Anual / Dia 60-bissexto (29/02) / data base de ano bissexto e igual do vencimento")
    void test1ab() {
        testDefineNextDueDate(
                RecurrenceType.YEARLY,
                List.of(60),
                Boolean.TRUE,
                LocalDate.of(2024, 2, 29),
                LocalDate.of(2024, 2, 29));
    }

    @Test
    @DisplayName("Próximo data de vencimento - Tipo Anual / Dia 60-bissexto (29/02) / data base de ano bissexto e maior que do vencimento")
    void test1ac() {
        testDefineNextDueDate(
                RecurrenceType.YEARLY,
                List.of(60),
                Boolean.TRUE,
                LocalDate.of(2024, 3, 1),
                LocalDate.of(2025, 2, 28));
    }

    @Test
    @DisplayName("Próximo data de vencimento - Tipo Anual / Dia 60-bissexto (29/02) / data base de ano comum e igual do vencimento")
    void test1ba() {
        testDefineNextDueDate(
                RecurrenceType.YEARLY,
                List.of(60),
                Boolean.TRUE,
                LocalDate.of(2023, 2, 28),
                LocalDate.of(2023, 2, 28));
    }

    @Test
    @DisplayName("Próximo data de vencimento - Tipo Anual / Dia 60-bissexto (29/02) / data base de ano comum e maior que do vencimento")
    void test1bb() {
        testDefineNextDueDate(
                RecurrenceType.YEARLY,
                List.of(60),
                Boolean.TRUE,
                LocalDate.of(2023, 3,1),
                LocalDate.of(2024, 2, 29));
    }

    @Test
    @DisplayName("Próximo data de vencimento - Tipo Anual / Dia 60-bissexto (29/02) / data base de ano comum e proximo ano comum e igual do vencimento")
    void test1ca() {
        testDefineNextDueDate(
                RecurrenceType.YEARLY,
                List.of(60),
                Boolean.TRUE,
                LocalDate.of(2022, 2, 28),
                LocalDate.of(2022, 2, 28));
    }

    @Test
    @DisplayName("Próximo data de vencimento - Tipo Anual / Dia 60-bissexto (29/02) / data base de ano comum e proximo ano comum e maior que do vencimento")
    void test1cb() {
        testDefineNextDueDate(
                RecurrenceType.YEARLY,
                List.of(60),
                Boolean.TRUE,
                LocalDate.of(2022, 3, 1),
                LocalDate.of(2023, 2, 28));
    }

    @Test
    @DisplayName("Próximo data de vencimento - Tipo Anual / Dia 60-comum (01/03) / data base de ano bissexto e anterior do vencimento")
    void test2aa() {
        testDefineNextDueDate(
                RecurrenceType.YEARLY,
                List.of(60),
                Boolean.TRUE,
                LocalDate.of(2024, 2, 29),
                LocalDate.of(2024, 3, 1));
    }

    @Test
    @DisplayName("Próximo data de vencimento - Tipo Anual / Dia 60-comum (01/03) / data base de ano bissexto e igual do vencimento")
    void test2ab() {
        testDefineNextDueDate(
                RecurrenceType.YEARLY,
                List.of(60),
                Boolean.TRUE,
                LocalDate.of(2024, 3, 1),
                LocalDate.of(2024, 3, 1));
    }

    @Test
    @DisplayName("Próximo data de vencimento - Tipo Anual / Dia 60-comum (01/03) / data base de ano bissexto e maior que do vencimento")
    void test2ac() {
        testDefineNextDueDate(
                RecurrenceType.YEARLY,
                List.of(60),
                Boolean.FALSE,
                LocalDate.of(2024, 3, 2),
                LocalDate.of(2025, 3, 1));
    }

    @Test
    @DisplayName("Próximo data de vencimento - Tipo Anual / Dia 60-comum (01/03) / data base de ano comum e proximo ano bissexto e anterior do vencimento")
    void test2ba() {
        testDefineNextDueDate(
                RecurrenceType.YEARLY,
                List.of(60),
                Boolean.FALSE,
                LocalDate.of(2023, 2, 28),
                LocalDate.of(2023, 3, 1));
    }

    @Test
    @DisplayName("Próximo data de vencimento - Tipo Anual / Dia 60-comum (01/03) / data base de ano comum e proximo ano bissexto e igual do vencimento")
    void test2bb() {
        testDefineNextDueDate(
                RecurrenceType.YEARLY,
                List.of(60),
                Boolean.FALSE,
                LocalDate.of(2023, 3, 1),
                LocalDate.of(2023, 3, 1));
    }

    @Test
    @DisplayName("Próximo data de vencimento - Tipo Anual / Dia 60-comum (01/03) / data base de ano comum e proximo ano bissexto e maior que do vencimento")
    void test2bc() {
        testDefineNextDueDate(
                RecurrenceType.YEARLY,
                List.of(60),
                Boolean.FALSE,
                LocalDate.of(2023, 3, 2),
                LocalDate.of(2024, 3, 1));
    }

    @Test
    @DisplayName("Próximo data de vencimento - Tipo Anual / Dia 60-comum (01/03) / data base de ano comum e próximo ano comum e anterior do vencimento")
    void test2ca() {
        testDefineNextDueDate(
                RecurrenceType.YEARLY,
                List.of(60),
                Boolean.FALSE,
                LocalDate.of(2022, 2, 28),
                LocalDate.of(2022, 3, 1));
    }

    @Test
    @DisplayName("Próximo data de vencimento - Tipo Anual / Dia 60-bissexto (29/02) / data base de ano comum e próximo ano comum e igual do vencimento")
    void test2cb() {
        testDefineNextDueDate(
                RecurrenceType.YEARLY,
                List.of(60),
                Boolean.FALSE,
                LocalDate.of(2022, 3, 1),
                LocalDate.of(2022, 3, 1));
    }

    @Test
    @DisplayName("Próximo data de vencimento - Tipo Anual / Dia 60-comum (01/03) / data base de ano comum e próximo ano comum e maior que do vencimento")
    void test2cc() {
        testDefineNextDueDate(
                RecurrenceType.YEARLY,
                List.of(60),
                Boolean.FALSE,
                LocalDate.of(2022, 3, 2),
                LocalDate.of(2023, 3, 1));
    }

    private void testDefineNextDueDate(RecurrenceType recurrenceType, List<Integer> days, Boolean leapYear, LocalDate baseData, LocalDate expectedDueDate) {
        FixedBill fixedBill = FixedBillFactory.buildModel(recurrenceType, days, leapYear);
        Optional<LocalDate> nextDueDateOptional = fixedBillService.defineNextDueDate(fixedBill, baseData);
        assertTrue(nextDueDateOptional.isPresent());
        assertEquals(expectedDueDate, nextDueDateOptional.get());
    }

}
