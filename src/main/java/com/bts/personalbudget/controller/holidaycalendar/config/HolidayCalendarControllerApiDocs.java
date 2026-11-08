package com.bts.personalbudget.controller.holidaycalendar.config;

import com.bts.personalbudget.controller.holidaycalendar.HolidayCalendarPreviewResponse;
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
                    + "Pay cycles refresh only after commit, in a new transaction. A refresh failure is logged "
                    + "and does not undo this review; the daily job retries. Use this calendar's preview to see "
                    + "which income dates and cycle boundaries would move.")
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
                    + "generated keep their date and are not listed. The response is an object (not the former "
                    + "root array): income_occurrences keeps the former items; cycle_boundaries lists existing "
                    + "boundaries whose dates differ between refresh simulations under the current and proposed "
                    + "calendar, once per shared boundary, including the stored chain's final end; "
                    + "cycle_divergences lists new or changed BOUNDARY_DIVERGES_FROM_ANCHOR pendencies. "
                    + "Both simulations respect anchor-transition history: the preserved prefix belongs "
                    + "to the previous anchor rule and is not recalculated or compared with the new rule. "
                    + "Frozen boundaries never move. Cycle lists are empty in CALENDAR_MONTH. "
                    + "New horizon cycles are not listed as moved boundaries.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Review impact; all lists empty when nothing changes",
                    content = @Content(schema = @Schema(implementation = HolidayCalendarPreviewResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid year, holiday outside the year or two holidays on the same date")
    })
    @PostMapping("/{year}/preview")
    ResponseEntity<HolidayCalendarPreviewResponse> previewReview(@PathVariable int year,
                                                                    @RequestBody @Valid HolidayYearRequest request);

}
