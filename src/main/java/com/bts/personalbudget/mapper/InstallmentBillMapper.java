package com.bts.personalbudget.mapper;

import com.bts.personalbudget.controller.InstallmentBillRequest;
import com.bts.personalbudget.controller.InstallmentBillResponse;
import com.bts.personalbudget.core.domain.entity.InstallmentBillEntity;
import com.bts.personalbudget.core.domain.service.installmentbill.InstallmentBill;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;
import org.mapstruct.factory.Mappers;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface InstallmentBillMapper {

    InstallmentBillMapper INSTANCE = Mappers.getMapper(InstallmentBillMapper.class);

    InstallmentBill toModel(InstallmentBillRequest request);
    InstallmentBill toModel(InstallmentBillEntity entity);
    List<InstallmentBill> toModel(List<InstallmentBillEntity> entities);
    InstallmentBillEntity toEntity(InstallmentBill model);
    InstallmentBillEntity toEntity(InstallmentBill model, Long id);
    InstallmentBillResponse toResponse(InstallmentBill model);
    List<InstallmentBillResponse> toResponse(List<InstallmentBill> models);

}