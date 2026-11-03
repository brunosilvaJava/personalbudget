package com.bts.personalbudget.core.domain.repository;

import com.bts.personalbudget.core.domain.entity.CategoryEntity;
import com.bts.personalbudget.core.domain.entity.CategoryGroupEntity;
import com.bts.personalbudget.core.domain.enumerator.CategoryStatus;
import com.bts.personalbudget.core.domain.enumerator.OperationType;
import com.bts.personalbudget.repository.CategoryGroupRepository;
import com.bts.personalbudget.repository.CategoryRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
@SpringBootTest
public class CategoryRepositoryTest {

    private static final List<String> SEEDED_GROUPS =
            List.of("Residência", "Pessoal", "Instrução", "Moto", "Investimento", "Outros");

    @Container
    public static MySQLContainer<MySQLContainerConfig> mySqlContainer = MySQLContainerConfig.getInstance();

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private CategoryGroupRepository categoryGroupRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldSeedCategoryGroupsWhenMigrationRuns() {
        final List<String> names = categoryGroupRepository.findAllByOrderByDisplayOrderAscNameAsc().stream()
                .map(CategoryGroupEntity::getName)
                .toList();

        assertEquals(SEEDED_GROUPS, names.subList(0, SEEDED_GROUPS.size()));
    }

    @Test
    void shouldReadSeededCodeAsSameUuid() {
        for (final CategoryGroupEntity group : categoryGroupRepository.findAllByOrderByDisplayOrderAscNameAsc()) {
            final String uuidAsMySqlWritesIt = jdbcTemplate.queryForObject(
                    "SELECT BIN_TO_UUID(code) FROM category_group WHERE id = ?", String.class, group.getId());

            assertEquals(uuidAsMySqlWritesIt, group.getCode().toString());
        }
    }

    @Test
    void shouldFindCategoryByCodeWithItsGroup() {
        final CategoryGroupEntity group = saveGroup();
        final CategoryEntity saved = saveCategory(group, OperationType.DEBIT, CategoryStatus.INACTIVE);

        final Optional<CategoryEntity> found = categoryRepository.findByCode(saved.getCode());

        assertTrue(found.isPresent());
        assertEquals(group.getName(), found.get().getCategoryGroup().getName());
        assertEquals(CategoryStatus.INACTIVE, found.get().getStatus());
    }

    @Test
    void shouldFilterCategoriesByGroupOperationTypeAndStatus() {
        final CategoryGroupEntity group = saveGroup();
        final CategoryEntity activeDebit = saveCategory(group, OperationType.DEBIT, CategoryStatus.ACTIVE);
        final CategoryEntity inactiveDebit = saveCategory(group, OperationType.DEBIT, CategoryStatus.INACTIVE);
        final CategoryEntity activeCredit = saveCategory(group, OperationType.CREDIT, CategoryStatus.ACTIVE);

        final List<UUID> activeOnly = codes(categoryRepository.findByFilters(
                null, List.of(CategoryStatus.ACTIVE), group.getCode()));
        final List<UUID> allStatuses = codes(categoryRepository.findByFilters(
                null, List.of(CategoryStatus.ACTIVE, CategoryStatus.INACTIVE), group.getCode()));
        final List<UUID> debitOnly = codes(categoryRepository.findByFilters(
                OperationType.DEBIT, List.of(CategoryStatus.ACTIVE, CategoryStatus.INACTIVE), group.getCode()));

        assertEquals(2, activeOnly.size());
        assertTrue(activeOnly.containsAll(List.of(activeDebit.getCode(), activeCredit.getCode())));
        assertEquals(3, allStatuses.size());
        assertEquals(2, debitOnly.size());
        assertTrue(debitOnly.containsAll(List.of(activeDebit.getCode(), inactiveDebit.getCode())));
    }

    @Test
    void shouldFilterWithoutOperationTypeAndGroup() {
        final CategoryGroupEntity group = saveGroup();
        final CategoryEntity saved = saveCategory(group, OperationType.DEBIT, CategoryStatus.ACTIVE);

        final List<UUID> codes = codes(categoryRepository.findByFilters(null, List.of(CategoryStatus.ACTIVE), null));

        assertTrue(codes.contains(saved.getCode()));
    }

    @Test
    void shouldFindManyCategoriesByCodeWithTheirGroupsIncludingInactive() {
        final CategoryGroupEntity group = saveGroup();
        final CategoryEntity active = saveCategory(group, OperationType.DEBIT, CategoryStatus.ACTIVE);
        final CategoryEntity inactive = saveCategory(group, OperationType.CREDIT, CategoryStatus.INACTIVE);
        saveCategory(group, OperationType.DEBIT, CategoryStatus.ACTIVE);

        final List<CategoryEntity> found = categoryRepository.findAllByCodeIn(
                List.of(active.getCode(), inactive.getCode(), UUID.randomUUID()));

        assertEquals(2, found.size());
        assertTrue(codes(found).containsAll(List.of(active.getCode(), inactive.getCode())));
        assertEquals(group.getName(), found.get(0).getCategoryGroup().getName());
    }

    @Test
    void shouldRejectDuplicateNameForSameOperationType() {
        final CategoryGroupEntity group = saveGroup();
        final CategoryEntity saved = saveCategory(group, OperationType.DEBIT, CategoryStatus.ACTIVE);

        assertTrue(categoryRepository.existsByNameAndOperationType(saved.getName(), OperationType.DEBIT));
        assertThrows(DataIntegrityViolationException.class,
                () -> categoryRepository.saveAndFlush(build(group, saved.getName(), OperationType.DEBIT)));
    }

    @Test
    void shouldAllowSameNameForDifferentOperationTypes() {
        final CategoryGroupEntity group = saveGroup();
        final CategoryEntity saved = saveCategory(group, OperationType.DEBIT, CategoryStatus.ACTIVE);

        assertFalse(categoryRepository.existsByNameAndOperationType(saved.getName(), OperationType.CREDIT));
        assertDoesNotThrow(
                () -> categoryRepository.saveAndFlush(build(group, saved.getName(), OperationType.CREDIT)));
    }

    @Test
    void shouldIgnoreOwnCodeWhenCheckingNameForRename() {
        final CategoryGroupEntity group = saveGroup();
        final CategoryEntity saved = saveCategory(group, OperationType.DEBIT, CategoryStatus.ACTIVE);

        assertFalse(categoryRepository.existsByNameAndOperationTypeAndCodeNot(
                saved.getName(), OperationType.DEBIT, saved.getCode()));
        assertTrue(categoryRepository.existsByNameAndOperationTypeAndCodeNot(
                saved.getName(), OperationType.DEBIT, UUID.randomUUID()));
    }

    private CategoryGroupEntity saveGroup() {
        return categoryGroupRepository.save(CategoryGroupEntity.builder()
                .name("G-" + UUID.randomUUID())
                .displayOrder(100)
                .build());
    }

    private CategoryEntity saveCategory(final CategoryGroupEntity group,
                                        final OperationType operationType,
                                        final CategoryStatus status) {
        final CategoryEntity category = build(group, "C-" + UUID.randomUUID(), operationType);
        category.setStatus(status);
        return categoryRepository.save(category);
    }

    private CategoryEntity build(final CategoryGroupEntity group, final String name, final OperationType operationType) {
        return CategoryEntity.builder()
                .name(name)
                .operationType(operationType)
                .status(CategoryStatus.ACTIVE)
                .categoryGroup(group)
                .build();
    }

    private List<UUID> codes(final List<CategoryEntity> categories) {
        return categories.stream().map(CategoryEntity::getCode).toList();
    }

}
