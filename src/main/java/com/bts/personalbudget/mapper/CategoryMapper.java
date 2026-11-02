package com.bts.personalbudget.mapper;

import com.bts.personalbudget.controller.category.CategoryGroupResponse;
import com.bts.personalbudget.controller.category.CategoryRequest;
import com.bts.personalbudget.controller.category.CategoryResponse;
import com.bts.personalbudget.core.domain.entity.CategoryEntity;
import com.bts.personalbudget.core.domain.entity.CategoryGroupEntity;
import com.bts.personalbudget.core.domain.model.Category;
import com.bts.personalbudget.core.domain.model.CategoryGroup;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import org.mapstruct.factory.Mappers;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface CategoryMapper {

    CategoryMapper INSTANCE = Mappers.getMapper(CategoryMapper.class);

    Category toModel(CategoryRequest request);

    @Mapping(target = "groupCode", source = "categoryGroup.code")
    @Mapping(target = "groupName", source = "categoryGroup.name")
    Category toModel(CategoryEntity entity);

    List<Category> toModelList(List<CategoryEntity> entities);

    CategoryResponse toResponse(Category category);

    List<CategoryResponse> toResponseList(List<Category> categories);

    /**
     * The group is not set here: it comes from a lookup by group code that the service owns.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "code", ignore = true)
    @Mapping(target = "categoryGroup", ignore = true)
    @Mapping(target = "status", constant = "ACTIVE")
    CategoryEntity toEntity(Category category);

    CategoryGroup toModel(CategoryGroupEntity entity);

    List<CategoryGroup> toGroupModelList(List<CategoryGroupEntity> entities);

    CategoryGroupResponse toResponse(CategoryGroup group);

    List<CategoryGroupResponse> toGroupResponseList(List<CategoryGroup> groups);

}
