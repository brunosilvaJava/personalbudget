package com.bts.personalbudget.mapper;

import com.bts.personalbudget.controller.FinancialMovementRequest;
import com.bts.personalbudget.controller.FinancialMovementResponse;
import com.bts.personalbudget.controller.FinancialMovementUpdateRequest;
import com.bts.personalbudget.core.domain.entity.FinancialMovement;
import com.bts.personalbudget.core.domain.model.FinancialMovementModel;
import java.util.List;
import java.util.UUID;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;
import org.mapstruct.factory.Mappers;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface FinancialMovementMapper {

    FinancialMovementMapper INSTANCE = Mappers.getMapper(FinancialMovementMapper.class);

    FinancialMovement toEntity(FinancialMovementRequest request);
    FinancialMovement toEntity(FinancialMovementUpdateRequest updateRequest, UUID code);
    FinancialMovement toEntity(FinancialMovementModel model);
    List<FinancialMovement> toEntity(List<FinancialMovementModel> financialMovementModels);
    FinancialMovementModel toModel(FinancialMovement financialMovement);
    List<FinancialMovementResponse> toResponse(List<FinancialMovement> financialMovement);
    FinancialMovementResponse toResponse(FinancialMovement financialMovement);

}
