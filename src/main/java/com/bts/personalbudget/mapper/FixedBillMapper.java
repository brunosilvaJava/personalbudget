package com.bts.personalbudget.mapper;

import com.bts.personalbudget.controller.fixedbill.FixedBillRequest;
import com.bts.personalbudget.core.domain.entity.CalendarFixedBillEntity;
import com.bts.personalbudget.core.domain.entity.FixedBillEntity;
import com.bts.personalbudget.core.domain.model.FixedBill;
import java.util.List;
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
    List<FixedBill> toModel(List<FixedBillEntity> fixedBillEntities);
    FixedBillEntity toEntity(FixedBill fixedBill);

    @Named("calendarEntityToDays")
    default List<Integer> calendarEntityToDays(final List<CalendarFixedBillEntity> calendarFixedBillEntityList) {
        return calendarFixedBillEntityList.stream()
                .map(CalendarFixedBillEntity::getDayLaunch)
                .toList();
    }


}
