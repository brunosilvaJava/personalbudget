package com.bts.personalbudget.core.domain.model;

import java.util.Objects;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FinancialMovementModelTest {

    @Test
    void shouldContainCodeAndFlagActiveTrue() {
        final FinancialMovementModel financialMovementModel = FinancialMovementFactory.buildModel();
        financialMovementModel.prePersist();
        assertNotNull(financialMovementModel.getCode());
        assertNotNull(financialMovementModel.getFlagActive());
        assertTrue(financialMovementModel.getFlagActive());
    }

    @Test
    void shouldBeEqual() {
        final long id = 1L;
        final UUID code = UUID.randomUUID();
        final FinancialMovementModel entityOne = FinancialMovementModel.builder().id(id).code(code).build();
        final FinancialMovementModel entityTwo = FinancialMovementModel.builder().id(id).code(code).build();
        assertEquals(entityOne, entityTwo);
    }

    @Test
    void shouldHashCodeBeEquals() {
        final FinancialMovementModel entity = FinancialMovementModel.builder().build();
        assertEquals(
                Objects.hash(entity.getId(), entity.getCode()),
                entity.hashCode());
    }

}
