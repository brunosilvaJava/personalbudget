package com.bts.personalbudget.core.domain.model;

import com.bts.personalbudget.core.domain.enumerator.FinancialMovementStatus;
import com.bts.personalbudget.core.domain.enumerator.OperationType;
import com.bts.personalbudget.exception.InvalidFieldsException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FinancialMovementTest {

    @Test
    void shouldThrowsInvalidFieldsExceptionWhenCreateInvalidPaidOutFinancialMovement() {

        InvalidFieldsException exception = assertThrows(InvalidFieldsException.class, () ->
                new FinancialMovement(
                        UUID.randomUUID(),
                        OperationType.CREDIT,
                        "teste inválido",
                        BigDecimal.TEN,
                        BigDecimal.ZERO,
                        LocalDateTime.now(),
                        LocalDateTime.now(),
                        null,
                        FinancialMovementStatus.PAID_OUT,
                        Boolean.TRUE,
                        null
                ));

        Map<String, String> validations = exception.getValidations();

        assertEquals(FinancialMovement.class, exception.getAClass());
        assertTrue(validations.containsKey("amountPaid"));
        assertEquals("deve ser informado quando o status for PAID_OUT", validations.get("amountPaid"));
        assertTrue(validations.containsKey("payDate"));
        assertEquals("deve ser informado quando o status for PAID_OUT", validations.get("payDate"));
    }

    @Test
    void shouldThrowsInvalidFieldsExceptionWhenCreateInvalidLateFinancialMovement() {

        InvalidFieldsException exception = assertThrows(InvalidFieldsException.class, () ->
                new FinancialMovement(
                        UUID.randomUUID(),
                        OperationType.CREDIT,
                        "teste inválido",
                        BigDecimal.TEN,
                        BigDecimal.ZERO,
                        LocalDateTime.now(),
                        null,
                        null,
                        FinancialMovementStatus.LATE,
                        Boolean.TRUE,
                        null
                ));

        Map<String, String> validations = exception.getValidations();

        assertEquals(FinancialMovement.class, exception.getAClass());
        assertTrue(validations.containsKey("dueDate"));
        assertEquals("deve ser informado quando o status for LATE", validations.get("dueDate"));
    }

    @Test
    void shouldThrowsInvalidFieldsExceptionWhenCreateInvalidLateFinancialMovementDueDate() {

        InvalidFieldsException exception = assertThrows(InvalidFieldsException.class, () ->
                new FinancialMovement(
                        UUID.randomUUID(),
                        OperationType.CREDIT,
                        "teste inválido",
                        BigDecimal.TEN,
                        BigDecimal.ZERO,
                        LocalDateTime.now(),
                        LocalDateTime.now().plusDays(1),
                        null,
                        FinancialMovementStatus.LATE,
                        Boolean.TRUE,
                        null
                ));

        Map<String, String> validations = exception.getValidations();

        assertEquals(FinancialMovement.class, exception.getAClass());
        assertTrue(validations.containsKey("dueDate"));
        assertEquals("a data de vencimento deve ser anterior à data atual", validations.get("dueDate"));
    }
}
