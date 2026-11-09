package com.bts.personalbudget.controller.forecast;

import com.bts.personalbudget.controller.forecast.config.CashForecastControllerApiDocs;
import com.bts.personalbudget.core.domain.service.forecast.CashForecastService;
import com.bts.personalbudget.mapper.CashForecastMapper;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@Tag(name = "Cash forecast", description = "API for the cash forecast")
@RequiredArgsConstructor
@RequestMapping("/forecast")
@RestController
public class CashForecastController implements CashForecastControllerApiDocs {

    private final CashForecastService service;
    private final CashForecastMapper mapper;

    @Override
    @GetMapping
    public ResponseEntity<CashForecastResponse> forecast(
            @RequestParam(value = "until", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) final LocalDate until,
            @RequestParam(value = "include_late_income", defaultValue = "false") final boolean includeLateIncome) {
        log.info("m=forecast, until={}, includeLateIncome={}", until, includeLateIncome);
        return ResponseEntity.ok(mapper.toResponse(service.forecast(until, includeLateIncome)));
    }
}
