package com.bts.personalbudget.mapper;

import com.bts.personalbudget.controller.forecast.CashForecastResponse;
import com.bts.personalbudget.core.domain.service.forecast.CashForecast;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface CashForecastMapper {

    CashForecastResponse toResponse(CashForecast forecast);
}
