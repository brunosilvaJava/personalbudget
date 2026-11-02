package com.bts.personalbudget.core.domain.repository;

import com.bts.personalbudget.core.domain.entity.FinancialMovementEntity;
import com.bts.personalbudget.core.domain.factory.FinancialMovementFactory;
import com.bts.personalbudget.repository.FinancialMovementRepository;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
@SpringBootTest
public class FinancialMovementRepositoryTest {

    @Container
    public static MySQLContainer<MySQLContainerConfig> mySqlContainer = MySQLContainerConfig.getInstance();

    @Autowired
    private FinancialMovementRepository financialMovementRepository;

    @Test
    void shouldSaveFinancialMovement() {
        final FinancialMovementEntity financialMovementEntityMock = FinancialMovementFactory.buildEntity();
        financialMovementRepository.save(financialMovementEntityMock);
        final Optional<FinancialMovementEntity> financialMovementModel =
                financialMovementRepository.findByCode(financialMovementEntityMock.getCode());
        assertTrue(financialMovementModel.isPresent());
    }

    @Test
    void shouldRejectDuplicateMovementWhenUniqueConstraintViolated() {
        final UUID recurrenceBillCode = UUID.randomUUID();
        final LocalDate occurrenceDate = LocalDate.of(2026, 11, 10);

        financialMovementRepository.save(buildEntityWithOccurrence(recurrenceBillCode, occurrenceDate));

        assertThrows(DataIntegrityViolationException.class, () ->
                financialMovementRepository.save(buildEntityWithOccurrence(recurrenceBillCode, occurrenceDate)));
    }

    @Test
    void shouldAllowMultipleMovementsWithoutRecurrenceBillCode() {
        final LocalDate occurrenceDate = LocalDate.of(2026, 11, 10);

        assertDoesNotThrow(() -> {
            financialMovementRepository.save(buildEntityWithOccurrence(null, occurrenceDate));
            financialMovementRepository.save(buildEntityWithOccurrence(null, occurrenceDate));
        });
    }

    private FinancialMovementEntity buildEntityWithOccurrence(final UUID recurrenceBillCode, final LocalDate occurrenceDate) {
        final FinancialMovementEntity entity = FinancialMovementFactory.buildEntity();
        entity.setRecurrenceBillCode(recurrenceBillCode);
        entity.setOccurrenceDate(occurrenceDate);
        return entity;
    }

}
