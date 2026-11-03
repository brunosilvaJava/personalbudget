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
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@RequiredArgsConstructor
@Service
public class CategoryService {

    static final int NAME_MIN_LENGTH = 3;
    static final int NAME_MAX_LENGTH = 50;
    private static final String INVALID_FIELDS_MESSAGE = "Campos inválidos";

    private final CategoryRepository categoryRepository;
    private final CategoryGroupRepository categoryGroupRepository;
    private final CategoryMapper categoryMapper;

    @Transactional
    public Category create(final Category category) {
        log.info("m=create, category={}", category);
        final String name = normalizeName(category.name());
        final CategoryGroupEntity group = findGroupOrReject(category.groupCode());
        if (categoryRepository.existsByNameAndOperationType(name, category.operationType())) {
            throw invalid("name", "já existe categoria com esse nome para o tipo " + category.operationType());
        }

        final CategoryEntity entity = categoryMapper.toEntity(category);
        entity.setName(name);
        entity.setCategoryGroup(group);
        return categoryMapper.toModel(categoryRepository.save(entity));
    }

    /**
     * @param statuses when null or empty only ACTIVE categories are returned, so a deactivated
     *                 category leaves the pick lists without any caller having to ask for it.
     */
    public List<Category> find(final OperationType operationType,
                               final List<CategoryStatus> statuses,
                               final UUID groupCode) {
        log.info("m=find, operationType={}, statuses={}, groupCode={}", operationType, statuses, groupCode);
        final List<CategoryStatus> effectiveStatuses =
                statuses == null || statuses.isEmpty() ? List.of(CategoryStatus.ACTIVE) : statuses;
        return categoryMapper.toModelList(
                categoryRepository.findByFilters(operationType, effectiveStatuses, groupCode));
    }

    /**
     * Also returns INACTIVE categories: past movements keep pointing at them and the history
     * still needs their name.
     */
    public Category findByCode(final UUID code) {
        log.info("m=findByCode, code={}", code);
        return categoryMapper.toModel(findEntity(code));
    }

    /**
     * Also returns INACTIVE categories, for the same reason as findByCode. Codes that match no
     * category are left out of the result.
     */
    public List<Category> findAllByCodes(final Collection<UUID> codes) {
        log.info("m=findAllByCodes, codes={}", codes.size());
        if (codes.isEmpty()) {
            return List.of();
        }
        return categoryMapper.toModelList(categoryRepository.findAllByCodeIn(codes));
    }

    public List<CategoryGroup> findGroups() {
        log.info("m=findGroups");
        return categoryMapper.toGroupModelList(categoryGroupRepository.findAllByOrderByDisplayOrderAscNameAsc());
    }

    @Transactional
    public Category update(final UUID code, final String name, final UUID groupCode) {
        log.info("m=update, code={}, name={}, groupCode={}", code, name, groupCode);
        final CategoryEntity entity = findEntity(code);
        boolean hasChanges = false;

        if (name != null) {
            final String normalizedName = normalizeName(name);
            if (!normalizedName.equals(entity.getName())) {
                if (categoryRepository.existsByNameAndOperationTypeAndCodeNot(
                        normalizedName, entity.getOperationType(), code)) {
                    throw invalid("name", "já existe categoria com esse nome para o tipo " + entity.getOperationType());
                }
                entity.setName(normalizedName);
                hasChanges = true;
            }
        }

        if (groupCode != null && !groupCode.equals(entity.getCategoryGroup().getCode())) {
            entity.setCategoryGroup(findGroupOrReject(groupCode));
            hasChanges = true;
        }

        if (!hasChanges) {
            log.info("m=update, message=NoChangesDetected, code={}", code);
            return categoryMapper.toModel(entity);
        }
        return categoryMapper.toModel(categoryRepository.save(entity));
    }

    @Transactional
    public Category activate(final UUID code) {
        log.info("m=activate, code={}", code);
        return changeStatus(code, CategoryStatus.ACTIVE);
    }

    @Transactional
    public Category inactivate(final UUID code) {
        log.info("m=inactivate, code={}", code);
        return changeStatus(code, CategoryStatus.INACTIVE);
    }

    /**
     * Gate for the services that accept a category code on a movement or a bill: the category
     * must exist, be ACTIVE and have the same operation type as what is being saved.
     *
     * @throws InvalidFieldsException (400, field category_code) when any of the three fails
     */
    public void validateForUse(final UUID code, final OperationType operationType) {
        log.info("m=validateForUse, code={}, operationType={}", code, operationType);
        final CategoryEntity entity = categoryRepository.findByCode(code)
                .orElseThrow(() -> invalid("categoryCode", "categoria não encontrada"));
        if (entity.getStatus() != CategoryStatus.ACTIVE) {
            throw invalid("categoryCode", "categoria inativa");
        }
        if (entity.getOperationType() != operationType) {
            throw invalid("categoryCode",
                    "categoria é do tipo " + entity.getOperationType() + " e o lançamento é " + operationType);
        }
    }

    private Category changeStatus(final UUID code, final CategoryStatus newStatus) {
        final CategoryEntity entity = findEntity(code);
        if (entity.getStatus() == newStatus) {
            log.info("m=changeStatus, message=StatusAlreadySet, code={}, status={}", code, newStatus);
            return categoryMapper.toModel(entity);
        }
        entity.setStatus(newStatus);
        return categoryMapper.toModel(categoryRepository.save(entity));
    }

    private CategoryEntity findEntity(final UUID code) {
        return categoryRepository.findByCode(code)
                .orElseThrow(() -> new EntityNotFoundException("CategoryEntity not found for code " + code));
    }

    private CategoryGroupEntity findGroupOrReject(final UUID groupCode) {
        return categoryGroupRepository.findByCode(groupCode)
                .orElseThrow(() -> invalid("groupCode", "grupo não encontrado"));
    }

    /**
     * The request already limits the size, but the check runs on the raw text: "ab " passes a
     * minimum of 3 and is only 2 characters once trimmed.
     */
    private String normalizeName(final String name) {
        final String normalized = name == null ? "" : name.trim();
        if (normalized.length() < NAME_MIN_LENGTH || normalized.length() > NAME_MAX_LENGTH) {
            throw invalid("name", "deve ter entre " + NAME_MIN_LENGTH + " e " + NAME_MAX_LENGTH + " caracteres");
        }
        return normalized;
    }

    private InvalidFieldsException invalid(final String field, final String message) {
        return new InvalidFieldsException(INVALID_FIELDS_MESSAGE, CategoryService.class, Map.of(field, message));
    }

}
