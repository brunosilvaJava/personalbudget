package com.bts.personalbudget.core.domain.repository;

import com.bts.personalbudget.core.domain.entity.CategoryEntity;
import com.bts.personalbudget.core.domain.entity.CategoryGroupEntity;
import com.bts.personalbudget.core.domain.entity.FinancialMovementEntity;
import com.bts.personalbudget.core.domain.enumerator.CategoryStatus;
import com.bts.personalbudget.core.domain.enumerator.FinancialMovementStatus;
import com.bts.personalbudget.core.domain.enumerator.OperationType;
import com.bts.personalbudget.core.domain.factory.FinancialMovementFactory;
import com.bts.personalbudget.repository.CategoryGroupRepository;
import com.bts.personalbudget.repository.CategoryRepository;
import com.bts.personalbudget.repository.FinancialMovementRepository;
import com.bts.personalbudget.repository.FinancialMovementSpecifications;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
@SpringBootTest
public class FinancialMovementRepositoryTest {

    @Container
    public static MySQLContainer<MySQLContainerConfig> mySqlContainer = MySQLContainerConfig.getInstance();

    @Autowired
    private FinancialMovementRepository financialMovementRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private CategoryGroupRepository categoryGroupRepository;

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

    @Test
    void shouldFindRecurrenceOccurrencesInPeriodIncludingSoftDeleted() {
        final UUID recurrenceBillCode = UUID.randomUUID();
        final UUID otherRecurrenceBillCode = UUID.randomUUID();
        final LocalDate july10 = LocalDate.of(2026, 7, 10);
        final LocalDate august10 = LocalDate.of(2026, 8, 10);

        financialMovementRepository.save(buildEntityWithOccurrence(recurrenceBillCode, july10));
        final FinancialMovementEntity softDeleted = buildEntityWithOccurrence(recurrenceBillCode, august10);
        softDeleted.delete();
        financialMovementRepository.save(softDeleted);
        // Outside the period, and a bill that was not asked for.
        financialMovementRepository.save(buildEntityWithOccurrence(recurrenceBillCode, LocalDate.of(2026, 10, 10)));
        financialMovementRepository.save(buildEntityWithOccurrence(otherRecurrenceBillCode, july10));

        final List<LocalDate> occurrenceDates = financialMovementRepository
                .findAllByRecurrenceBillCodeInAndOccurrenceDateBetween(
                        List.of(recurrenceBillCode), july10, LocalDate.of(2026, 9, 30))
                .stream()
                .map(FinancialMovementEntity::getOccurrenceDate)
                .sorted()
                .toList();

        assertEquals(List.of(july10, august10), occurrenceDates);
    }

    @Test
    void shouldFilterMovementsByCategory() {
        final CategoryEntity category = saveCategory();
        final String marker = marker();
        final FinancialMovementEntity categorized = saveMovement(marker, category.getCode());
        saveMovement(marker, null);
        saveMovement(marker, saveCategory().getCode());

        final List<UUID> found = findByMarker(marker, List.of(category.getCode()), false);

        assertEquals(List.of(categorized.getCode()), found);
    }

    @Test
    void shouldReturnUncategorizedMovementsWhenWithoutCategoryIsTrue() {
        final CategoryEntity category = saveCategory();
        final String marker = marker();
        saveMovement(marker, category.getCode());
        final FinancialMovementEntity uncategorized = saveMovement(marker, null);

        final List<UUID> found = findByMarker(marker, null, true);

        assertEquals(List.of(uncategorized.getCode()), found);
    }

    @Test
    void shouldReturnCategorizedAndUncategorizedMovementsWhenBothFiltersAreSent() {
        final CategoryEntity category = saveCategory();
        final String marker = marker();
        final FinancialMovementEntity categorized = saveMovement(marker, category.getCode());
        final FinancialMovementEntity uncategorized = saveMovement(marker, null);
        saveMovement(marker, saveCategory().getCode());

        final List<UUID> found = findByMarker(marker, List.of(category.getCode()), true);

        assertEquals(2, found.size());
        assertTrue(found.containsAll(List.of(categorized.getCode(), uncategorized.getCode())));
    }

    @Test
    void shouldNotFilterByCategoryWhenNoCategoryFilterIsSent() {
        final CategoryEntity category = saveCategory();
        final String marker = marker();
        saveMovement(marker, category.getCode());
        saveMovement(marker, null);

        assertEquals(2, findByMarker(marker, null, false).size());
    }

    @Test
    void shouldRejectMovementWithUnknownCategoryCode() {
        final FinancialMovementEntity entity = FinancialMovementFactory.buildEntity();
        entity.setCategoryCode(UUID.randomUUID());

        assertThrows(DataIntegrityViolationException.class, () -> financialMovementRepository.saveAndFlush(entity));
    }

    private List<UUID> findByMarker(final String marker, final List<UUID> categoryCodes, final boolean withoutCategory) {
        return financialMovementRepository.findAll(
                        FinancialMovementSpecifications.filter(
                                marker,
                                Arrays.asList(OperationType.values()),
                                Arrays.asList(FinancialMovementStatus.values()),
                                LocalDateTime.now().minusDays(1),
                                LocalDateTime.now().plusDays(1),
                                categoryCodes,
                                withoutCategory),
                        Pageable.unpaged())
                .stream()
                .map(FinancialMovementEntity::getCode)
                .toList();
    }

    private String marker() {
        return "CAT-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private FinancialMovementEntity saveMovement(final String description, final UUID categoryCode) {
        final FinancialMovementEntity entity = FinancialMovementFactory.buildEntity();
        entity.setCode(UUID.randomUUID());
        entity.setDescription(description);
        entity.setCategoryCode(categoryCode);
        return financialMovementRepository.save(entity);
    }

    private CategoryEntity saveCategory() {
        final CategoryGroupEntity group = categoryGroupRepository.save(CategoryGroupEntity.builder()
                .name("G-" + UUID.randomUUID())
                .displayOrder(100)
                .build());
        return categoryRepository.save(CategoryEntity.builder()
                .name("C-" + UUID.randomUUID())
                .operationType(OperationType.DEBIT)
                .status(CategoryStatus.ACTIVE)
                .categoryGroup(group)
                .build());
    }

    private FinancialMovementEntity buildEntityWithOccurrence(final UUID recurrenceBillCode, final LocalDate occurrenceDate) {
        final FinancialMovementEntity entity = FinancialMovementFactory.buildEntity();
        entity.setRecurrenceBillCode(recurrenceBillCode);
        entity.setOccurrenceDate(occurrenceDate);
        return entity;
    }

}
