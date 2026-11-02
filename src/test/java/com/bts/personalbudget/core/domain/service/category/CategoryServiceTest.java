package com.bts.personalbudget.core.domain.service.category;

import com.bts.personalbudget.core.domain.entity.CategoryEntity;
import com.bts.personalbudget.core.domain.entity.CategoryGroupEntity;
import com.bts.personalbudget.core.domain.enumerator.CategoryStatus;
import com.bts.personalbudget.core.domain.enumerator.OperationType;
import com.bts.personalbudget.core.domain.model.Category;
import com.bts.personalbudget.core.domain.model.CategoryGroup;
import com.bts.personalbudget.exception.InvalidFieldsException;
import com.bts.personalbudget.mapper.CategoryMapper;
import com.bts.personalbudget.repository.CategoryGroupRepository;
import com.bts.personalbudget.repository.CategoryRepository;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import static com.bts.personalbudget.core.domain.factory.CategoryFactory.CATEGORY_CODE;
import static com.bts.personalbudget.core.domain.factory.CategoryFactory.CATEGORY_NAME;
import static com.bts.personalbudget.core.domain.factory.CategoryFactory.GROUP_CODE;
import static com.bts.personalbudget.core.domain.factory.CategoryFactory.GROUP_NAME;
import static com.bts.personalbudget.core.domain.factory.CategoryFactory.OTHER_GROUP_CODE;
import static com.bts.personalbudget.core.domain.factory.CategoryFactory.buildEntity;
import static com.bts.personalbudget.core.domain.factory.CategoryFactory.buildGroupEntity;
import static com.bts.personalbudget.core.domain.factory.CategoryFactory.buildNewModel;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @InjectMocks
    private CategoryService categoryService;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private CategoryGroupRepository categoryGroupRepository;

    @Spy
    private CategoryMapper categoryMapper = CategoryMapper.INSTANCE;

    @Test
    void shouldCreateCategoryWhenGroupExists() {
        when(categoryGroupRepository.findByCode(GROUP_CODE)).thenReturn(Optional.of(buildGroupEntity()));
        when(categoryRepository.existsByNameAndOperationType(CATEGORY_NAME, OperationType.DEBIT)).thenReturn(false);
        when(categoryRepository.save(any(CategoryEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        final Category created = categoryService.create(buildNewModel("  " + CATEGORY_NAME + " ", OperationType.DEBIT));

        final ArgumentCaptor<CategoryEntity> captor = ArgumentCaptor.forClass(CategoryEntity.class);
        verify(categoryRepository).save(captor.capture());
        assertEquals(CATEGORY_NAME, captor.getValue().getName());
        assertEquals(CategoryStatus.ACTIVE, captor.getValue().getStatus());
        assertEquals(GROUP_CODE, captor.getValue().getCategoryGroup().getCode());
        assertEquals(CATEGORY_NAME, created.name());
        assertEquals(OperationType.DEBIT, created.operationType());
        assertEquals(CategoryStatus.ACTIVE, created.status());
        assertEquals(GROUP_CODE, created.groupCode());
        assertEquals(GROUP_NAME, created.groupName());
    }

    @Test
    void shouldRejectCategoryWhenNameAlreadyExistsForOperationType() {
        when(categoryGroupRepository.findByCode(GROUP_CODE)).thenReturn(Optional.of(buildGroupEntity()));
        when(categoryRepository.existsByNameAndOperationType(CATEGORY_NAME, OperationType.DEBIT)).thenReturn(true);

        final InvalidFieldsException exception = assertThrows(InvalidFieldsException.class,
                () -> categoryService.create(buildNewModel(CATEGORY_NAME, OperationType.DEBIT)));

        assertTrue(exception.getValidations().containsKey("name"));
        verify(categoryRepository, never()).save(any());
    }

    @Test
    void shouldAllowSameNameForDifferentOperationTypes() {
        lenient().when(categoryRepository.existsByNameAndOperationType(CATEGORY_NAME, OperationType.DEBIT))
                .thenReturn(true);
        when(categoryRepository.existsByNameAndOperationType(CATEGORY_NAME, OperationType.CREDIT))
                .thenReturn(false);
        when(categoryGroupRepository.findByCode(GROUP_CODE)).thenReturn(Optional.of(buildGroupEntity()));
        when(categoryRepository.save(any(CategoryEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        final Category created = categoryService.create(buildNewModel(CATEGORY_NAME, OperationType.CREDIT));

        assertEquals(OperationType.CREDIT, created.operationType());
        verify(categoryRepository).save(any(CategoryEntity.class));
    }

    @Test
    void shouldRejectCategoryWhenGroupDoesNotExist() {
        when(categoryGroupRepository.findByCode(GROUP_CODE)).thenReturn(Optional.empty());

        final InvalidFieldsException exception = assertThrows(InvalidFieldsException.class,
                () -> categoryService.create(buildNewModel(CATEGORY_NAME, OperationType.DEBIT)));

        assertTrue(exception.getValidations().containsKey("groupCode"));
        verify(categoryRepository, never()).save(any());
    }

    @Test
    void shouldRejectCategoryWhenNameIsTooShortAfterTrim() {
        final InvalidFieldsException exception = assertThrows(InvalidFieldsException.class,
                () -> categoryService.create(buildNewModel("ab ", OperationType.DEBIT)));

        assertTrue(exception.getValidations().containsKey("name"));
        verify(categoryRepository, never()).save(any());
    }

    @Test
    void shouldInactivateCategory() {
        when(categoryRepository.findByCode(CATEGORY_CODE))
                .thenReturn(Optional.of(buildEntity(OperationType.DEBIT, CategoryStatus.ACTIVE)));
        when(categoryRepository.save(any(CategoryEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        final Category inactivated = categoryService.inactivate(CATEGORY_CODE);

        assertEquals(CategoryStatus.INACTIVE, inactivated.status());
        verify(categoryRepository).save(any(CategoryEntity.class));
    }

    @Test
    void shouldNotSaveWhenCategoryIsAlreadyInactive() {
        when(categoryRepository.findByCode(CATEGORY_CODE))
                .thenReturn(Optional.of(buildEntity(OperationType.DEBIT, CategoryStatus.INACTIVE)));

        final Category inactivated = categoryService.inactivate(CATEGORY_CODE);

        assertEquals(CategoryStatus.INACTIVE, inactivated.status());
        verify(categoryRepository, never()).save(any());
    }

    @Test
    void shouldActivateCategory() {
        when(categoryRepository.findByCode(CATEGORY_CODE))
                .thenReturn(Optional.of(buildEntity(OperationType.DEBIT, CategoryStatus.INACTIVE)));
        when(categoryRepository.save(any(CategoryEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        final Category activated = categoryService.activate(CATEGORY_CODE);

        assertEquals(CategoryStatus.ACTIVE, activated.status());
    }

    @Test
    void shouldListOnlyActiveCategoriesByDefault() {
        final List<CategoryEntity> entities = List.of(buildEntity(OperationType.DEBIT, CategoryStatus.ACTIVE));
        when(categoryRepository.findByFilters(null, List.of(CategoryStatus.ACTIVE), null)).thenReturn(entities);

        assertEquals(1, categoryService.find(null, null, null).size());
        assertEquals(1, categoryService.find(null, List.of(), null).size());
    }

    @Test
    void shouldKeepRequestedStatusesWhenListing() {
        final List<CategoryStatus> statuses = List.of(CategoryStatus.ACTIVE, CategoryStatus.INACTIVE);
        when(categoryRepository.findByFilters(OperationType.CREDIT, statuses, GROUP_CODE)).thenReturn(List.of());

        assertTrue(categoryService.find(OperationType.CREDIT, statuses, GROUP_CODE).isEmpty());
    }

    @Test
    void shouldReturnInactiveCategoryWhenFoundByCode() {
        when(categoryRepository.findByCode(CATEGORY_CODE))
                .thenReturn(Optional.of(buildEntity(OperationType.DEBIT, CategoryStatus.INACTIVE)));

        final Category found = categoryService.findByCode(CATEGORY_CODE);

        assertEquals(CategoryStatus.INACTIVE, found.status());
        assertEquals(CATEGORY_NAME, found.name());
    }

    @Test
    void shouldThrowEntityNotFoundWhenCategoryCodeDoesNotExist() {
        final UUID unknown = UUID.randomUUID();
        when(categoryRepository.findByCode(unknown)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> categoryService.findByCode(unknown));
        assertThrows(EntityNotFoundException.class, () -> categoryService.update(unknown, "Novo nome", null));
        assertThrows(EntityNotFoundException.class, () -> categoryService.inactivate(unknown));
        assertThrows(EntityNotFoundException.class, () -> categoryService.activate(unknown));
    }

    @Test
    void shouldRenameCategory() {
        when(categoryRepository.findByCode(CATEGORY_CODE))
                .thenReturn(Optional.of(buildEntity(OperationType.DEBIT, CategoryStatus.ACTIVE)));
        when(categoryRepository.existsByNameAndOperationTypeAndCodeNot("Condomínio", OperationType.DEBIT, CATEGORY_CODE))
                .thenReturn(false);
        when(categoryRepository.save(any(CategoryEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        final Category updated = categoryService.update(CATEGORY_CODE, " Condomínio ", null);

        assertEquals("Condomínio", updated.name());
        assertEquals(GROUP_CODE, updated.groupCode());
    }

    @Test
    void shouldRejectRenameWhenNameBelongsToAnotherCategory() {
        when(categoryRepository.findByCode(CATEGORY_CODE))
                .thenReturn(Optional.of(buildEntity(OperationType.DEBIT, CategoryStatus.ACTIVE)));
        when(categoryRepository.existsByNameAndOperationTypeAndCodeNot("Condomínio", OperationType.DEBIT, CATEGORY_CODE))
                .thenReturn(true);

        final InvalidFieldsException exception = assertThrows(InvalidFieldsException.class,
                () -> categoryService.update(CATEGORY_CODE, "Condomínio", null));

        assertTrue(exception.getValidations().containsKey("name"));
        verify(categoryRepository, never()).save(any());
    }

    @Test
    void shouldMoveCategoryToAnotherGroup() {
        final CategoryGroupEntity otherGroup = buildGroupEntity(OTHER_GROUP_CODE, "Pessoal", 2);
        when(categoryRepository.findByCode(CATEGORY_CODE))
                .thenReturn(Optional.of(buildEntity(OperationType.DEBIT, CategoryStatus.ACTIVE)));
        when(categoryGroupRepository.findByCode(OTHER_GROUP_CODE)).thenReturn(Optional.of(otherGroup));
        when(categoryRepository.save(any(CategoryEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        final Category updated = categoryService.update(CATEGORY_CODE, null, OTHER_GROUP_CODE);

        assertEquals(OTHER_GROUP_CODE, updated.groupCode());
        assertEquals("Pessoal", updated.groupName());
        assertEquals(CATEGORY_NAME, updated.name());
    }

    @Test
    void shouldNotSaveWhenUpdateHasNoChanges() {
        when(categoryRepository.findByCode(CATEGORY_CODE))
                .thenReturn(Optional.of(buildEntity(OperationType.DEBIT, CategoryStatus.ACTIVE)));

        final Category updated = categoryService.update(CATEGORY_CODE, CATEGORY_NAME, GROUP_CODE);

        assertEquals(CATEGORY_NAME, updated.name());
        verify(categoryRepository, never()).save(any());
    }

    @Test
    void shouldListGroupsInRepositoryOrder() {
        when(categoryGroupRepository.findAllByOrderByDisplayOrderAscNameAsc()).thenReturn(List.of(
                buildGroupEntity(GROUP_CODE, GROUP_NAME, 1),
                buildGroupEntity(OTHER_GROUP_CODE, "Pessoal", 2)));

        final List<CategoryGroup> groups = categoryService.findGroups();

        assertEquals(List.of(GROUP_NAME, "Pessoal"), groups.stream().map(CategoryGroup::name).toList());
        assertEquals(List.of(1, 2), groups.stream().map(CategoryGroup::displayOrder).toList());
    }

    @Test
    void shouldAcceptUseWhenCategoryIsActiveAndOperationTypeMatches() {
        when(categoryRepository.findByCode(CATEGORY_CODE))
                .thenReturn(Optional.of(buildEntity(OperationType.DEBIT, CategoryStatus.ACTIVE)));

        assertDoesNotThrow(() -> categoryService.validateForUse(CATEGORY_CODE, OperationType.DEBIT));
    }

    @Test
    void shouldRejectUseWhenCategoryDoesNotExist() {
        when(categoryRepository.findByCode(CATEGORY_CODE)).thenReturn(Optional.empty());

        final InvalidFieldsException exception = assertThrows(InvalidFieldsException.class,
                () -> categoryService.validateForUse(CATEGORY_CODE, OperationType.DEBIT));

        assertTrue(exception.getValidations().containsKey("categoryCode"));
    }

    @Test
    void shouldRejectUseWhenCategoryIsInactive() {
        when(categoryRepository.findByCode(CATEGORY_CODE))
                .thenReturn(Optional.of(buildEntity(OperationType.DEBIT, CategoryStatus.INACTIVE)));

        final InvalidFieldsException exception = assertThrows(InvalidFieldsException.class,
                () -> categoryService.validateForUse(CATEGORY_CODE, OperationType.DEBIT));

        assertTrue(exception.getValidations().containsKey("categoryCode"));
    }

    @Test
    void shouldRejectUseWhenOperationTypeDiffers() {
        when(categoryRepository.findByCode(CATEGORY_CODE))
                .thenReturn(Optional.of(buildEntity(OperationType.DEBIT, CategoryStatus.ACTIVE)));

        final InvalidFieldsException exception = assertThrows(InvalidFieldsException.class,
                () -> categoryService.validateForUse(CATEGORY_CODE, OperationType.CREDIT));

        assertTrue(exception.getValidations().containsKey("categoryCode"));
    }

}
