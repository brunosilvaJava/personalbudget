package com.bts.personalbudget.core.domain.model;

import com.bts.personalbudget.core.domain.enumerator.FinancialMovementStatus;
import com.bts.personalbudget.core.domain.enumerator.OperationType;
import com.bts.personalbudget.exception.InvalidFieldsException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
                        null,
                        null,
                        null
                ));

        Map<String, String> validations = exception.getValidations();

        assertEquals(FinancialMovement.class, exception.getAClass());
        assertTrue(validations.containsKey("amountPaid"));
        assertEquals("deve ser informado um valor válido quando o status for PAID_OUT", validations.get("amountPaid"));
        assertTrue(validations.containsKey("payDate"));
        assertEquals("deve ser informado um valor válido quando o status for PAID_OUT", validations.get("payDate"));
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
                        null,
                        null,
                        null
                ));

        Map<String, String> validations = exception.getValidations();

        assertEquals(FinancialMovement.class, exception.getAClass());
        assertTrue(validations.containsKey("dueDate"));
        assertEquals("deve ser informado um valor válido quando o status for LATE", validations.get("dueDate"));
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
                        null,
                        null,
                        null
                ));

        Map<String, String> validations = exception.getValidations();

        assertEquals(FinancialMovement.class, exception.getAClass());
        assertTrue(validations.containsKey("dueDate"));
        assertEquals("a data de vencimento deve ser anterior à data atual", validations.get("dueDate"));
    }

    @Test
    void shouldAcceptPositiveAmountPaidWhenDebitIsPaidOut() {
        // The API sends amount_paid positive for a debit too; the sign is applied when it is
        // stored. Guards the quick "already paid" expense of the front-end.
        assertDoesNotThrow(() -> paidOut(OperationType.DEBIT, new BigDecimal("10.00"), new BigDecimal("10.00")));
    }

    @Test
    void shouldUseAmountPaidInBalanceWhenDebitIsPartiallyPaid() {
        final FinancialMovement financialMovement =
                paidOut(OperationType.DEBIT, new BigDecimal("100.00"), new BigDecimal("60.00"));

        assertEquals(0, new BigDecimal("-60.00").compareTo(financialMovement.getBalanceCalcValue()));
    }

    @Test
    void shouldKeepStoredNegativeAmountPaidInBalanceWhenDebitIsPaidOut() {
        final FinancialMovement financialMovement =
                paidOut(OperationType.DEBIT, new BigDecimal("-100.00"), new BigDecimal("-60.00"));

        assertEquals(0, new BigDecimal("-60.00").compareTo(financialMovement.getBalanceCalcValue()));
    }

    @Test
    void shouldUseAmountPaidInBalanceWhenCreditIsPaidOut() {
        final FinancialMovement financialMovement =
                paidOut(OperationType.CREDIT, new BigDecimal("100.00"), new BigDecimal("60.00"));

        assertEquals(0, new BigDecimal("60.00").compareTo(financialMovement.getBalanceCalcValue()));
    }

    @Test
    void shouldExposeCategoryCodeToBalanceCalculation() {
        final UUID categoryCode = UUID.randomUUID();
        final LocalDateTime now = LocalDateTime.now();

        final FinancialMovement financialMovement = new FinancialMovement(
                UUID.randomUUID(), OperationType.DEBIT, "teste", new BigDecimal("10.00"), null,
                now, now.plusDays(1), null, FinancialMovementStatus.PENDING, Boolean.TRUE,
                null, null, categoryCode);

        assertEquals(categoryCode, financialMovement.getCategoryCode());
    }

    @Test
    void shouldExposeNullCategoryCodeWhenMovementHasNoCategory() {
        assertNull(paidOut(OperationType.DEBIT, new BigDecimal("10.00"), new BigDecimal("10.00"))
                .getCategoryCode());
    }

    private static FinancialMovement paidOut(final OperationType operationType,
                                             final BigDecimal amount,
                                             final BigDecimal amountPaid) {
        final LocalDateTime now = LocalDateTime.now();
        return new FinancialMovement(
                UUID.randomUUID(),
                operationType,
                "teste pago",
                amount,
                amountPaid,
                now,
                now,
                now,
                FinancialMovementStatus.PAID_OUT,
                Boolean.TRUE,
                null,
                null, null);
    }
}
