package com.bts.personalbudget.core.domain.model;

import com.bts.personalbudget.exception.InvalidFieldsException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FinancialMovementUpdateTest {

    @Test
    void shouldRejectManualCompetenceTogetherWithAutomaticRestore() {
        final InvalidFieldsException exception = assertThrows(InvalidFieldsException.class, () ->
                new FinancialMovementUpdate(UUID.randomUUID(), null, null, null, null, null, null, null,
                        null, null, LocalDate.of(2026, 10, 1), true));

        assertTrue(exception.getValidations().containsKey("competenceDate"));
    }

    @Test
    void shouldAcceptEachCompetenceCommandAlone() {
        assertDoesNotThrow(() -> new FinancialMovementUpdate(UUID.randomUUID(), null, null, null, null, null,
                null, null, null, null, LocalDate.of(2026, 10, 1), false));
        assertDoesNotThrow(() -> new FinancialMovementUpdate(UUID.randomUUID(), null, null, null, null, null,
                null, null, null, null, null, true));
    }
}
