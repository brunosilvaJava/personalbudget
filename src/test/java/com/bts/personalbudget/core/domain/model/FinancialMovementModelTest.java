package com.bts.personalbudget.core.domain.model;

import java.util.Objects;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FinancialMovementEntityTest {

    @Test
    void shouldContainCodeAndFlagActiveTrue() {
        final FinancialMovementEntity financialMovementEntity = FinancialMovementEntityFactory.build();
        financialMovementEntity.prePersist();
        assertNotNull(financialMovementEntity.getCode());
        assertNotNull(financialMovementEntity.getFlagActive());
        assertTrue(financialMovementEntity.getFlagActive());
    }

    @Test
    void shouldBeEqual() {
        final long id = 1L;
        final UUID code = UUID.randomUUID();
        final FinancialMovementEntity entityOne = FinancialMovementEntity.builder().id(id).code(code).build();
        final FinancialMovementEntity entityTwo = FinancialMovementEntity.builder().id(id).code(code).build();
        assertEquals(entityOne, entityTwo);
    }

    @Test
    void shouldHashCodeBeEquals() {
        final FinancialMovementEntity entity = FinancialMovementEntity.builder().build();
        assertEquals(
                Objects.hash(entity.getId(), entity.getCode()),
                entity.hashCode());
    }

}
