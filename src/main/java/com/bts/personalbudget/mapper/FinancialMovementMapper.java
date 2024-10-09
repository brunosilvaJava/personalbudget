package com.bts.personalbudget.mapper;

import com.bts.personalbudget.controller.financialmovement.FinancialMovementRequest;
import com.bts.personalbudget.controller.financialmovement.FinancialMovementResponse;
import com.bts.personalbudget.controller.financialmovement.FinancialMovementUpdateRequest;
import com.bts.personalbudget.core.domain.entity.FinancialMovementEntity;
import com.bts.personalbudget.core.domain.model.FinancialMovement;
import java.util.List;
import java.util.UUID;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;
import org.mapstruct.factory.Mappers;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface FinancialMovementMapper {

    FinancialMovementMapper INSTANCE = Mappers.getMapper(FinancialMovementMapper.class);

    FinancialMovement toModel(FinancialMovementRequest request);
    FinancialMovement toModel(FinancialMovementUpdateRequest updateRequest, UUID code);
    FinancialMovement toModel(FinancialMovementEntity entity);
    List<FinancialMovement> toModel(List<FinancialMovementEntity> financialMovementEntities);
    FinancialMovementEntity toEntity(FinancialMovement financialMovement);
    List<FinancialMovementResponse> toResponse(List<FinancialMovement> financialMovement);
    FinancialMovementResponse toResponse(FinancialMovement financialMovement);

}
