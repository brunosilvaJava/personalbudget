package com.bts.personalbudget.mapper;

import com.bts.personalbudget.core.domain.entity.FixedBillEntity;
import com.bts.personalbudget.controller.fixedbill.FixedBillRequest;
import com.bts.personalbudget.core.domain.model.FixedBill;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;
import org.mapstruct.factory.Mappers;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface FixedBillMapper {

    FixedBillMapper INSTANCE = Mappers.getMapper(FixedBillMapper.class);


    FixedBill toModel(FixedBillRequest request);
    FixedBill toModel(FixedBillEntity model);
    List<FixedBill> toModel(List<FixedBillEntity> fixedBillEntities);
    FixedBillEntity toEntity(FixedBill fixedBill);


}
