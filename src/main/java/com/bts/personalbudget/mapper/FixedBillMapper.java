package com.bts.personalbudget.mapper;

import com.bts.personalbudget.controller.fixedbill.FixedBillRequest;
import com.bts.personalbudget.controller.fixedbill.FixedBillResponse;
import com.bts.personalbudget.core.domain.entity.CalendarFixedBillEntity;
import com.bts.personalbudget.core.domain.entity.FixedBillEntity;
import com.bts.personalbudget.core.domain.service.fixedbill.FixedBill;

import java.time.Year;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;
import org.mapstruct.factory.Mappers;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface FixedBillMapper {

    FixedBillMapper INSTANCE = Mappers.getMapper(FixedBillMapper.class);

    FixedBill toModel(FixedBillRequest request);
    @Mapping(target = "days", source = "calendarFixedBillEntityList", qualifiedByName = "calendarEntityToDays")
    FixedBill toModel(FixedBillEntity entity);
    FixedBillResponse toResponse(FixedBill model);
    List<FixedBill> toModelList(List<FixedBillEntity> fixedBillEntities);
    List<FixedBillResponse> toResponseList(List<FixedBill> fixedBills);
    @Mapping(target = "flagActive", constant = "true")
    FixedBillEntity toEntity(FixedBill fixedBill);

    @Named("calendarEntityToDays")
    default Set<Integer> calendarEntityToDays(final Set<CalendarFixedBillEntity> calendarFixedBillEntityList) {
        return calendarFixedBillEntityList.stream()
                .map(CalendarFixedBillEntity::getDayLaunch)
                .collect(Collectors.toSet());
    }

}
