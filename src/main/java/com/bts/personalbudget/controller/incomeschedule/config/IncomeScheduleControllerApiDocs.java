package com.bts.personalbudget.controller.incomeschedule.config;

import com.bts.personalbudget.controller.incomeschedule.IncomeOccurrenceResponse;
import com.bts.personalbudget.controller.incomeschedule.IncomeScheduleRequest;
import com.bts.personalbudget.controller.incomeschedule.IncomeScheduleResponse;
import com.bts.personalbudget.controller.incomeschedule.IncomeScheduleUpdateRequest;
import com.bts.personalbudget.core.domain.enumerator.IncomeScheduleStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@Tag(name = "Income schedule", description = "API for managing the main income and its occurrences")
public interface IncomeScheduleControllerApiDocs {

    @Operation(
            summary = "Create an income schedule",
            description = "Creates an ACTIVE schedule: the rule that says on which day of each month the income is "
                    + "expected. The occurrences are generated as PENDING CREDIT movements on their date by the "
                    + "nightly job; when start_month is the current month and its date already passed, the next run "
                    + "generates it as an overdue PENDING movement. With replaces_fixed_bill_code the fixed bill that "
                    + "generated this receipt until now is ended the day before start_month, in the same operation.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Schedule successfully created",
                    content = @Content(schema = @Schema(implementation = IncomeScheduleResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request data, inactive or non CREDIT category, "
                    + "start_month in the past, a second active anchor, or a fixed bill that cannot be replaced")
    })
    @PostMapping
    ResponseEntity<IncomeScheduleResponse> create(@Valid @RequestBody IncomeScheduleRequest request);

    @Operation(
            summary = "List income schedules",
            description = "Returns the schedules ordered by description. The list is not paginated. "
                    + "Only ACTIVE schedules are returned unless status says otherwise.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "List of schedules"),
            @ApiResponse(responseCode = "400", description = "Invalid parameters")
    })
    ResponseEntity<List<IncomeScheduleResponse>> find(
            @Parameter(description = "Filter by status(es). Defaults to ACTIVE") List<IncomeScheduleStatus> statuses);

    @Operation(
            summary = "Retrieve an income schedule by code",
            description = "Fetches a schedule by its unique identifier (UUID). Inactive schedules are returned too.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Schedule found and returned"),
            @ApiResponse(responseCode = "404", description = "Schedule not found")
    })
    @GetMapping("/{code}")
    ResponseEntity<IncomeScheduleResponse> findByCode(@PathVariable UUID code);

    @Operation(
            summary = "Update an income schedule",
            description = "Changes the description, amount, category, rule, end month or anchor flag. Occurrences "
                    + "already generated keep their date and amount; the change applies to the ones not generated "
                    + "yet. The start month and the replaced fixed bill cannot be changed.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Schedule successfully updated"),
            @ApiResponse(responseCode = "404", description = "Schedule not found"),
            @ApiResponse(responseCode = "400", description = "Invalid request data, invalid merged rule or a second active anchor")
    })
    @PatchMapping("/{code}")
    ResponseEntity<IncomeScheduleResponse> update(@PathVariable UUID code,
                                                  @RequestBody @Valid IncomeScheduleUpdateRequest request);

    @Operation(
            summary = "Inactivate an income schedule",
            description = "Changes the status to INACTIVE. It stops generating and projecting occurrences; the ones "
                    + "already generated stay.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Schedule successfully inactivated"),
            @ApiResponse(responseCode = "404", description = "Schedule not found")
    })
    @PatchMapping("/{code}/inactivate")
    ResponseEntity<IncomeScheduleResponse> inactivate(@PathVariable UUID code);

    @Operation(
            summary = "Activate an income schedule",
            description = "Changes the status to ACTIVE. The months it was inactive are not generated: when its start "
                    + "month is earlier than the current month, it moves to the current month.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Schedule successfully activated"),
            @ApiResponse(responseCode = "404", description = "Schedule not found"),
            @ApiResponse(responseCode = "400", description = "Another active schedule is already the anchor")
    })
    @PatchMapping("/{code}/activate")
    ResponseEntity<IncomeScheduleResponse> activate(@PathVariable UUID code);

    @Operation(
            summary = "List the occurrences of a schedule",
            description = "One entry per nominal month: MATERIALIZED (a movement exists, date and amount frozen), "
                    + "PROJECTED (calculated from the rule and the current holiday calendar) or an entry that says "
                    + "why the date cannot be calculated (CALENDAR_NOT_REVIEWED, with the year to review). "
                    + "Defaults to the current month and the eleven after it; at most 36 months.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Occurrences by nominal month"),
            @ApiResponse(responseCode = "404", description = "Schedule not found"),
            @ApiResponse(responseCode = "400", description = "Invalid month format (yyyy-MM) or range")
    })
    ResponseEntity<List<IncomeOccurrenceResponse>> findOccurrences(
            @PathVariable UUID code,
            @Parameter(description = "First month, yyyy-MM. Defaults to the current month") String from,
            @Parameter(description = "Last month, yyyy-MM. Defaults to 11 months after from") String to);

    @Operation(
            summary = "Generate the occurrence of a month now",
            description = "Generates the occurrence as a PENDING CREDIT movement dated on its planned date - the "
                    + "way to register an early receipt: then update the movement to PAID_OUT with the real pay "
                    + "date, and the cash moves that day while the competence stays on the planned date. "
                    + "Idempotent: when the month already has its occurrence (from the job, an earlier call, or "
                    + "even with its movement deleted since) it is returned as it is, with 200, and nothing is "
                    + "created. Only up to one month after the current one.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Occurrence generated",
                    content = @Content(schema = @Schema(implementation = IncomeOccurrenceResponse.class))),
            @ApiResponse(responseCode = "200", description = "The month already had its occurrence; returned as it is"),
            @ApiResponse(responseCode = "404", description = "Schedule not found"),
            @ApiResponse(responseCode = "400", description = "Invalid month format (yyyy-MM), inactive schedule, month "
                    + "outside the validity or too far ahead, or a calendar year that was not reviewed")
    })
    ResponseEntity<IncomeOccurrenceResponse> materialize(
            @PathVariable UUID code,
            @Parameter(description = "Nominal month, yyyy-MM") @PathVariable String month);

}
