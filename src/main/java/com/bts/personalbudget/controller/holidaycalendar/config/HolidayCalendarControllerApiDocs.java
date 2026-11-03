package com.bts.personalbudget.controller.holidaycalendar.config;

import com.bts.personalbudget.controller.holidaycalendar.HolidayChangeImpactResponse;
import com.bts.personalbudget.controller.holidaycalendar.HolidayYearRequest;
import com.bts.personalbudget.controller.holidaycalendar.HolidayYearResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

@Tag(name = "Holiday calendar", description = "Local holiday calendar, reviewed year by year")
public interface HolidayCalendarControllerApiDocs {

    @Operation(
            summary = "List the reviewed years",
            description = "Returns the years whose holidays were reviewed, oldest first, with their holidays. "
                    + "A year that is not in the list is not prepared yet.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Reviewed years")
    })
    @GetMapping
    ResponseEntity<List<HolidayYearResponse>> findReviewedYears();

    @Operation(
            summary = "Retrieve a year of the calendar",
            description = "Returns the holidays of the year and whether it was reviewed. A year that was never "
                    + "reviewed answers reviewed = false with no holidays.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Year of the calendar",
                    content = @Content(schema = @Schema(implementation = HolidayYearResponse.class))),
            @ApiResponse(responseCode = "400", description = "Year out of the supported range")
    })
    @GetMapping("/{year}")
    ResponseEntity<HolidayYearResponse> findYear(@PathVariable int year);

    @Operation(
            summary = "Review a year",
            description = "Replaces the holidays of the year with the list sent and marks the year as reviewed, "
                    + "even with an empty list. Income occurrences already generated keep the date they were "
                    + "generated on; the ones not generated yet follow the new calendar. "
                    + "Use the preview endpoint of the income schedules to see which dates would move.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Year reviewed",
                    content = @Content(schema = @Schema(implementation = HolidayYearResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid year, holiday outside the year or two holidays on the same date")
    })
    @PutMapping("/{year}")
    ResponseEntity<HolidayYearResponse> reviewYear(@PathVariable int year,
                                                   @RequestBody @Valid HolidayYearRequest request);

    @Operation(
            summary = "Preview the review of a year",
            description = "Shows which income occurrences not generated yet would move, or stop being calculable, "
                    + "if the year were reviewed with the holidays sent. Changes nothing: occurrences already "
                    + "generated keep their date and are not listed.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Occurrences affected by the review; empty when none is"),
            @ApiResponse(responseCode = "400", description = "Invalid year, holiday outside the year or two holidays on the same date")
    })
    @PostMapping("/{year}/preview")
    ResponseEntity<List<HolidayChangeImpactResponse>> previewReview(@PathVariable int year,
                                                                    @RequestBody @Valid HolidayYearRequest request);

}
