package com.bts.personalbudget.mapper;

import com.bts.personalbudget.controller.dashboard.DashboardResponse;
import com.bts.personalbudget.core.domain.service.dashboard.Dashboard;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface DashboardMapper {

    DashboardResponse toResponse(Dashboard dashboard);
}