package com.bts.personalbudget.mapper;

import com.bts.personalbudget.controller.holidaycalendar.HolidayRequest;
import com.bts.personalbudget.controller.holidaycalendar.HolidayResponse;
import com.bts.personalbudget.controller.holidaycalendar.HolidayYearResponse;
import com.bts.personalbudget.core.domain.entity.HolidayEntity;
import com.bts.personalbudget.core.domain.service.calendar.Holiday;
import com.bts.personalbudget.core.domain.service.calendar.HolidayYear;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;
import org.mapstruct.factory.Mappers;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface HolidayCalendarMapper {

    HolidayCalendarMapper INSTANCE = Mappers.getMapper(HolidayCalendarMapper.class);

    default Holiday toModel(final HolidayEntity entity) {
        return new Holiday(entity.getHolidayDate(), entity.getName());
    }

    default List<Holiday> toModelList(final List<HolidayEntity> entities) {
        return entities.stream().map(this::toModel).toList();
    }

    default Holiday toModelFromRequest(final HolidayRequest request) {
        return new Holiday(request.date(), request.name());
    }

    default List<Holiday> toModelListFromRequests(final List<HolidayRequest> requests) {
        return requests.stream().map(this::toModelFromRequest).toList();
    }

    default HolidayResponse toHolidayResponse(final Holiday holiday) {
        return new HolidayResponse(holiday.date(), holiday.name());
    }

    default HolidayYearResponse toResponse(final HolidayYear holidayYear) {
        return new HolidayYearResponse(holidayYear.year(), holidayYear.reviewed(),
                holidayYear.holidays().stream().map(this::toHolidayResponse).toList());
    }

    default List<HolidayYearResponse> toYearResponseList(final List<HolidayYear> holidayYears) {
        return holidayYears.stream().map(this::toResponse).toList();
    }

}
