package com.bts.personalbudget.controller.financialmovement.config;

import com.bts.personalbudget.controller.financialmovement.FinancialMovementRequest;
import com.bts.personalbudget.controller.financialmovement.FinancialMovementResponse;
import com.bts.personalbudget.controller.financialmovement.FinancialMovementUpdateRequest;
import com.bts.personalbudget.controller.financialmovement.PagedFinancialMovementResponse;
import com.bts.personalbudget.core.domain.enumerator.FinancialMovementStatus;
import com.bts.personalbudget.core.domain.enumerator.OperationType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Tag(name = "Financial Movement", description = "API for managing financial movements")
public interface FinancialMovementControllerApiDocs {

    @Operation(
            summary = "Create a new financial movement",
            description = "This endpoint creates a new entry for a financial movement. competence_date is optional: when omitted it is "
                    + "derived automatically (movement_date, or occurrence date for recurring bills); when sent it is manual. "
                    + "A PENDING movement with a due date before today is stored as LATE immediately.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Financial movement successfully created; returns it, with its code"),
            @ApiResponse(responseCode = "400", description = "Invalid request data")
    })
    ResponseEntity<FinancialMovementResponse> create(FinancialMovementRequest request);

    @Operation(
            summary = "Retrieve financial movements",
            description = "Returns a list of financial movements based on search parameters. "
                    + "category_code (repeatable) keeps the movements of those categories and without_category=true "
                    + "keeps the ones with no category; sent together they match either. The status filter accepts LATE, "
                    + "which is assigned automatically to overdue pending movements by the server.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "List of financial movements"),
            @ApiResponse(responseCode = "400", description = "Invalid parameters, including a sort_by outside: description, amount, operation_type, status, movement_date, due_date, pay_date")
    })
    @GetMapping
    ResponseEntity<PagedFinancialMovementResponse> find(
            @RequestParam(defaultValue = "") String description,
            @RequestParam(value = "operation_type", required = false) List<OperationType> operationTypes,
            @RequestParam(value = "status", required = false) List<FinancialMovementStatus> statuses,
            @RequestParam("start_date") LocalDate startDate,
            @RequestParam("end_date") LocalDate endDate,
            @RequestParam(value = "category_code", required = false) List<UUID> categoryCodes,
            @RequestParam(value = "without_category", defaultValue = "false") boolean withoutCategory,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(value = "sort_by", defaultValue = "movement_date") String sortBy,
            @RequestParam(value = "sort_direction", defaultValue = "DESC") String sortDirection);

    @Operation(summary = "Retrieve a financial movement by code",
            description = "Fetches a financial movement by its unique identifier (UUID).")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Financial movement found and returned"),
            @ApiResponse(responseCode = "404", description = "Financial movement not found")
    })
    ResponseEntity<FinancialMovementResponse> find(UUID code);

    @Operation(summary = "Update an existing financial movement",
            description = "Updates an existing financial movement identified by its UUID. Sending competence_date sets a manual "
                    + "competence; restore_automatic_competence=true recalculates it (not allowed together with competence_date); "
                    + "omitting both keeps the current choice. Due and pay dates never change the competence. When status is omitted, "
                    + "a PENDING movement with a due date before today becomes LATE, and moving a LATE movement's due date to today "
                    + "or later returns its status to PENDING.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Financial movement successfully updated"),
            @ApiResponse(responseCode = "404", description = "Financial movement not found"),
            @ApiResponse(responseCode = "400", description = "Invalid request data")
    })
    ResponseEntity<?> update(UUID code, FinancialMovementUpdateRequest updateRequest);

    @Operation(summary = "Delete a financial movement by code",
            description = "Deletes a financial movement identified by its UUID. Repeating the request for a previously deleted movement succeeds.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Financial movement deleted, or it was already deleted"),
            @ApiResponse(responseCode = "404", description = "Financial movement not found")
    })
    ResponseEntity<Void> delete(@PathVariable UUID code);

}
