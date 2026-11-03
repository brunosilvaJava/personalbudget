package com.bts.personalbudget.controller.dashboard.config;

import com.bts.personalbudget.controller.dashboard.DashboardResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "Dashboard", description = "API for the monthly summary of balances, revenue and expense")
public interface DashboardControllerApiDocs {

    @Operation(
            summary = "Retrieve the dashboard of the current month",
            description = "Returns balances, revenue, expense and negative balance alerts of the current month.")
    @ApiResponse(responseCode = "200", description = "Dashboard of the current month")
    ResponseEntity<DashboardResponse> getDashboard();

    @Operation(
            summary = "Retrieve the dashboard of a month",
            description = "Returns balances, revenue, expense and negative balance alerts of the given month.")
    @ApiResponse(responseCode = "200", description = "Dashboard of the month")
    ResponseEntity<DashboardResponse> getDashboardForMonth(
            @Parameter(description = "Month in yyyy-MM format", example = "2026-11") String month);
}
