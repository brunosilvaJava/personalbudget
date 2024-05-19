package com.bts.personalbudget.mapper;

import com.bts.personalbudget.controller.FinancialMovementRequest;
import com.bts.personalbudget.controller.FinancialMovementResponse;
import com.bts.personalbudget.core.domain.entity.FinancialMovement;
import com.bts.personalbudget.core.domain.model.FinancialMovementModel;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface FinancialMovementMapper {

    FinancialMovement toEntity(FinancialMovementRequest request);
    List<FinancialMovement> toEntity(List<FinancialMovementModel> financialMovementModels);
    FinancialMovementModel toModel(FinancialMovement financialMovement);
    List<FinancialMovementResponse> toResponse(List<FinancialMovement> financialMovement);

}
