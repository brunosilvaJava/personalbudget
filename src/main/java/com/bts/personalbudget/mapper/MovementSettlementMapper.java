package com.bts.personalbudget.mapper;

import com.bts.personalbudget.controller.movementsettlement.MovementSettlementRequest;
import com.bts.personalbudget.controller.movementsettlement.MovementSettlementResponse;
import com.bts.personalbudget.core.domain.entity.MovementSettlementEntity;
import com.bts.personalbudget.core.domain.service.movementsettlement.MovementSettlement;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface MovementSettlementMapper {

    MovementSettlement toModel(MovementSettlementRequest request);

    @Mapping(target = "movementCode", source = "financialMovement.code")
    @Mapping(target = "operationType", source = "financialMovement.operationType")
    @Mapping(target = "categoryCode", source = "financialMovement.categoryCode")
    @Mapping(target = "competenceDate", source = "financialMovement.competenceDate")
    @Mapping(target = "accountCode", source = "account.code")
    MovementSettlement toModel(MovementSettlementEntity entity);

    List<MovementSettlement> toModelList(List<MovementSettlementEntity> entities);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "code", ignore = true)
    @Mapping(target = "reversedAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "financialMovement", ignore = true)
    @Mapping(target = "account", ignore = true)
    MovementSettlementEntity toEntity(MovementSettlement model);

    MovementSettlementResponse toResponse(MovementSettlement model);

    List<MovementSettlementResponse> toResponseList(List<MovementSettlement> models);
}
