package com.bts.personalbudget.controller.dashboard;

import com.bts.personalbudget.core.domain.service.dashboard.Dashboard;
import com.bts.personalbudget.core.domain.service.dashboard.DashboardService;
import com.bts.personalbudget.mapper.DashboardMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.YearMonth;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;
    private final DashboardMapper mapper;

    /**
     * GET /dashboard
     * Retorna dados do dashboard para o mês atual
     */
    @GetMapping
    public ResponseEntity<DashboardResponse> getDashboard() {
        log.info("m=getDashboard");

        final Dashboard dashboard = dashboardService.getDashboard();
        final DashboardResponse response = mapper.toResponse(dashboard);

        return ResponseEntity.ok(response);
    }

    /**
     * GET /dashboard?month=2025-10
     * Retorna dados do dashboard para um mês específico
     */
    @GetMapping(params = "month")
    public ResponseEntity<DashboardResponse> getDashboardForMonth(
            @RequestParam("month") String month
    ) {
        log.info("m=getDashboardForMonth, month={}", month);

        final YearMonth yearMonth = YearMonth.parse(month);
        final Dashboard dashboard = dashboardService.getDashboardForMonth(yearMonth);
        final DashboardResponse response = mapper.toResponse(dashboard);

        return ResponseEntity.ok(response);
    }
}