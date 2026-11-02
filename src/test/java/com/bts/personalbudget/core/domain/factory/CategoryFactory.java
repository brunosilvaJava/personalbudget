package com.bts.personalbudget.core.domain.factory;

import com.bts.personalbudget.core.domain.entity.CategoryEntity;
import com.bts.personalbudget.core.domain.entity.CategoryGroupEntity;
import com.bts.personalbudget.core.domain.enumerator.CategoryStatus;
import com.bts.personalbudget.core.domain.enumerator.OperationType;
import com.bts.personalbudget.core.domain.model.Category;
import java.util.UUID;

public final class CategoryFactory {

    public static final UUID GROUP_CODE = UUID.fromString("11111111-1111-1111-1111-111111111111");
    public static final UUID OTHER_GROUP_CODE = UUID.fromString("33333333-3333-3333-3333-333333333333");
    public static final UUID CATEGORY_CODE = UUID.fromString("22222222-2222-2222-2222-222222222222");
    public static final String GROUP_NAME = "Residência";
    public static final String CATEGORY_NAME = "Aluguel";

    private CategoryFactory() {
    }

    public static CategoryGroupEntity buildGroupEntity() {
        return buildGroupEntity(GROUP_CODE, GROUP_NAME, 1);
    }

    public static CategoryGroupEntity buildGroupEntity(final UUID code, final String name, final int displayOrder) {
        return CategoryGroupEntity.builder()
                .id(1L)
                .code(code)
                .name(name)
                .displayOrder(displayOrder)
                .build();
    }

    public static CategoryEntity buildEntity(final OperationType operationType, final CategoryStatus status) {
        return CategoryEntity.builder()
                .id(10L)
                .code(CATEGORY_CODE)
                .name(CATEGORY_NAME)
                .operationType(operationType)
                .status(status)
                .categoryGroup(buildGroupEntity())
                .build();
    }

    /**
     * A category as it arrives on creation: no code, no status, only the group code.
     */
    public static Category buildNewModel(final String name, final OperationType operationType) {
        return new Category(null, name, operationType, null, GROUP_CODE, null);
    }

}
