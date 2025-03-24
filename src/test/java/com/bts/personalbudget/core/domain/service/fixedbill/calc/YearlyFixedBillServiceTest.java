package com.bts.personalbudget.core.domain.service.fixedbill.calc;

import com.bts.personalbudget.core.domain.enumerator.RecurrenceType;
import com.bts.personalbudget.core.domain.factory.FixedBillFactory;
import com.bts.personalbudget.core.domain.service.fixedbill.FixedBill;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
public class YearlyFixedBillServiceTest {

    @InjectMocks
    private YearlyCalcFixedBillImpl yearlyFixedBillService;

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
                LocalDate.of(2024, 3, 1), // 61
                LocalDate.of(2025, 2, 28)); // 59
    }

    @Test
    @DisplayName("Próximo data de vencimento - Tipo Anual / Dia 60-bissexto (29/02) / data base de ano comum e igual do vencimento")
    void test1ba() {
        testDefineNextDueDate(
                RecurrenceType.YEARLY,
                List.of(60),
                Boolean.TRUE,
                LocalDate.of(2023, 2, 28), // 59
                LocalDate.of(2023, 2, 28)); // 59
    }

    @Test
    @DisplayName("Próximo data de vencimento - Tipo Anual / Dia 60-bissexto (29/02) / data base de ano comum e maior que do vencimento")
    void test1bb() {
        testDefineNextDueDate(
                RecurrenceType.YEARLY,
                List.of(60),
                Boolean.TRUE,
                LocalDate.of(2023, 3,1), // 60
                LocalDate.of(2024, 2, 29)); // 60
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
                Boolean.FALSE,
                LocalDate.of(2024, 2, 29), // 60
                LocalDate.of(2024, 3, 1)); // 60
    }

    @Test
    @DisplayName("Próximo data de vencimento - Tipo Anual / Dia 60-comum (01/03) / data base de ano bissexto e igual do vencimento")
    void test2ab() {
        testDefineNextDueDate(
                RecurrenceType.YEARLY,
                List.of(60),
                Boolean.FALSE,
                LocalDate.of(2024, 3, 1), // 61
                LocalDate.of(2024, 3, 1)); // 60
    }

    @Test
    @DisplayName("Próximo data de vencimento - Tipo Anual / Dia 60-comum (01/03) / data base de ano bissexto e maior que do vencimento")
    void test2ac() {
        testDefineNextDueDate(
                RecurrenceType.YEARLY,
                List.of(60),
                Boolean.FALSE,
                LocalDate.of(2024, 3, 2), // 62
                LocalDate.of(2025, 3, 1)); // 60
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
        LocalDate nextDueDateOptional = yearlyFixedBillService.calcNextDueDate(fixedBill, baseData);
        assertEquals(expectedDueDate, nextDueDateOptional);
    }

}
