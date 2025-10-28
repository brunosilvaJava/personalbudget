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
import org.springframework.data.crossstore.ChangeSetPersister;
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
            description = "This endpoint creates a new entry for a financial movement.")
    @ApiResponse(responseCode = "201", description = "Financial movement successfully created")
    ResponseEntity<Void> create(FinancialMovementRequest request);

    @Operation(
            summary = "Retrieve financial movements",
            description = "Returns a list of financial movements based on search parameters.")
    @ApiResponse(responseCode = "200", description = "List of financial movements")
    @GetMapping
    ResponseEntity<PagedFinancialMovementResponse> find(
            @RequestParam(defaultValue = "") String description,
            @RequestParam(value = "operation_type", required = false) List<OperationType> operationTypes,
            @RequestParam(value = "status", required = false) List<FinancialMovementStatus> statuses,
            @RequestParam("start_date") LocalDate startDate,
            @RequestParam("end_date") LocalDate endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "movementDate") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDirection);

    @Operation(summary = "Retrieve a financial movement by code",
            description = "Fetches a financial movement by its unique identifier (UUID).")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Financial movement found and returned"),
            @ApiResponse(responseCode = "404", description = "Financial movement not found")
    })
    ResponseEntity<FinancialMovementResponse> find(UUID code) throws ChangeSetPersister.NotFoundException;

    @Operation(summary = "Update an existing financial movement",
            description = "Updates an existing financial movement identified by its UUID.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Financial movement successfully updated"),
            @ApiResponse(responseCode = "404", description = "Financial movement not found"),
            @ApiResponse(responseCode = "400", description = "Invalid request data")
    })
    ResponseEntity<?> update(UUID code, FinancialMovementUpdateRequest updateRequest) throws ChangeSetPersister.NotFoundException;

    @Operation(summary = "Delete a financial movement by code",
            description = "Deletes an existing financial movement identified by its UUID.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Financial movement successfully deleted"),
            @ApiResponse(responseCode = "404", description = "Financial movement not found")
    })
    ResponseEntity<Void> delete(@PathVariable UUID code) throws ChangeSetPersister.NotFoundException;

}
