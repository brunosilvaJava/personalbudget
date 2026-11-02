package com.bts.personalbudget.controller.holidaycalendar;

import com.bts.personalbudget.controller.holidaycalendar.config.HolidayCalendarControllerApiDocs;
import com.bts.personalbudget.core.domain.service.calendar.HolidayCalendarService;
import com.bts.personalbudget.core.domain.service.calendar.HolidayYear;
import com.bts.personalbudget.core.domain.service.income.IncomeScheduleService;
import com.bts.personalbudget.mapper.HolidayCalendarMapper;
import com.bts.personalbudget.mapper.IncomeScheduleMapper;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@Tag(name = "Holiday calendar", description = "Local holiday calendar, reviewed year by year")
@RequiredArgsConstructor
@RequestMapping("/holiday_calendar")
@RestController
public class HolidayCalendarController implements HolidayCalendarControllerApiDocs {

    private final HolidayCalendarService holidayCalendarService;
    private final HolidayCalendarMapper holidayCalendarMapper;
    private final IncomeScheduleService incomeScheduleService;
    private final IncomeScheduleMapper incomeScheduleMapper;

    @Override
    @GetMapping
    public ResponseEntity<List<HolidayYearResponse>> findReviewedYears() {
        log.info("m=findReviewedYears");
        return ResponseEntity.ok(holidayCalendarMapper.toYearResponseList(holidayCalendarService.findReviewedYears()));
    }

    @Override
    @GetMapping("/{year}")
    public ResponseEntity<HolidayYearResponse> findYear(@PathVariable int year) {
        log.info("m=findYear, year={}", year);
        return ResponseEntity.ok(holidayCalendarMapper.toResponse(holidayCalendarService.findYear(year)));
    }

    @Override
    @PutMapping("/{year}")
    public ResponseEntity<HolidayYearResponse> reviewYear(@PathVariable int year,
                                                          @RequestBody @Valid HolidayYearRequest request) {
        log.info("m=reviewYear, year={}, holidays={}", year, request.holidays().size());
        final HolidayYear holidayYear = holidayCalendarService.reviewYear(
                year, holidayCalendarMapper.toModelListFromRequests(request.holidays()));
        return ResponseEntity.ok(holidayCalendarMapper.toResponse(holidayYear));
    }

    @Override
    @PostMapping("/{year}/preview")
    public ResponseEntity<List<HolidayChangeImpactResponse>> previewReview(
            @PathVariable int year,
            @RequestBody @Valid HolidayYearRequest request) {
        log.info("m=previewReview, year={}, holidays={}", year, request.holidays().size());
        return ResponseEntity.ok(incomeScheduleMapper.toImpactResponseList(
                incomeScheduleService.previewCalendarChange(
                        year, holidayCalendarMapper.toModelListFromRequests(request.holidays()))));
    }

}
