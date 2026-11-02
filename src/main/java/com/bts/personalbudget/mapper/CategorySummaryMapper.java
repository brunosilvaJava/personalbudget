package com.bts.personalbudget.mapper;

import com.bts.personalbudget.controller.categorysummary.CategorySummaryGroupResponse;
import com.bts.personalbudget.controller.categorysummary.CategorySummaryLineResponse;
import com.bts.personalbudget.controller.categorysummary.CategorySummaryResponse;
import com.bts.personalbudget.core.domain.model.CategorySummary;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;
import org.mapstruct.factory.Mappers;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface CategorySummaryMapper {

    CategorySummaryMapper INSTANCE = Mappers.getMapper(CategorySummaryMapper.class);

    CategorySummaryResponse toResponse(CategorySummary summary);

    CategorySummaryGroupResponse toResponse(CategorySummary.Group group);

    CategorySummaryLineResponse toResponse(CategorySummary.Line line);

    List<CategorySummaryGroupResponse> toGroupResponseList(List<CategorySummary.Group> groups);

    List<CategorySummaryLineResponse> toLineResponseList(List<CategorySummary.Line> lines);

}
