package com.bts.personalbudget.controller.fixedbill.config;

import com.bts.personalbudget.controller.fixedbill.FixedBillRequest;
import com.bts.personalbudget.controller.fixedbill.FixedBillResponse;
import com.bts.personalbudget.controller.fixedbill.FixedBillUpdateRequest;
import com.bts.personalbudget.controller.fixedbill.PagedFixedBillResponse;
import com.bts.personalbudget.core.domain.enumerator.FixedBillStatus;
import com.bts.personalbudget.core.domain.enumerator.OperationType;
import com.bts.personalbudget.core.domain.enumerator.RecurrenceType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Fixed Bill", description = "API for managing fixed bills")
public interface FixedBillControllerApiDocs {

    @Operation(
            summary = "Create a new fixed bill",
            description = "This endpoint creates a new fixed bill based on the provided request data.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Fixed bill successfully created"),
            @ApiResponse(responseCode = "400", description = "Invalid request data")
    })
    @PostMapping
    ResponseEntity<Void> create(@Valid @RequestBody FixedBillRequest fixedBillRequest);

    @Operation(
            summary = "Retrieve all fixed bills",
            description = "Returns a paginated list of fixed bills based on search parameters.")
    @ApiResponse(responseCode = "200", description = "Paginated list of fixed bills")
    ResponseEntity<PagedFixedBillResponse> find(
            @Parameter(description = "Filter by description (partial match)") String description,
            @Parameter(description = "Filter by operation type(s)") List<OperationType> operationTypes,
            @Parameter(description = "Filter by status(es)") List<FixedBillStatus> statuses,
            @Parameter(description = "Filter by recurrence type(s)") List<RecurrenceType> recurrenceTypes,
            @Parameter(description = "Page number (0-indexed)") int page,
            @Parameter(description = "Number of items per page") int size,
            @Parameter(description = "Field to sort by") String sortBy,
            @Parameter(description = "Sort direction (ASC or DESC)") String sortDirection
    );

    @Operation(
            summary = "Retrieve a fixed bill by code",
            description = "Fetches a fixed bill by its unique identifier (UUID).")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Fixed bill found and returned"),
            @ApiResponse(responseCode = "404", description = "Fixed bill not found")
    })
    @GetMapping("/{code}")
    ResponseEntity<FixedBillResponse> findByCode(@PathVariable UUID code);

    @Operation(
            summary = "Update an existing fixed bill",
            description = "Updates an existing fixed bill identified by its UUID.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Fixed bill successfully updated"),
            @ApiResponse(responseCode = "404", description = "Fixed bill not found"),
            @ApiResponse(responseCode = "400", description = "Invalid request data")
    })
    @PatchMapping("/{code}")
    ResponseEntity<FixedBillResponse> update(@PathVariable UUID code,
                                             @RequestBody @Valid FixedBillUpdateRequest fixedBillUpdateRequest);

    @Operation(
            summary = "Inactivate a fixed bill",
            description = "Changes the status of a fixed bill to INACTIVE.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Fixed bill successfully inactivated"),
            @ApiResponse(responseCode = "404", description = "Fixed bill not found")
    })
    @PatchMapping("/{code}/inactivate")
    ResponseEntity<FixedBillResponse> inactivate(@PathVariable UUID code);

    @Operation(
            summary = "Activate a fixed bill",
            description = "Changes the status of a fixed bill to ACTIVE.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Fixed bill successfully activated"),
            @ApiResponse(responseCode = "404", description = "Fixed bill not found")
    })
    @PatchMapping("/{code}/activate")
    ResponseEntity<FixedBillResponse> activate(@PathVariable UUID code);

    @Operation(
            summary = "Delete a fixed bill by code",
            description = "Soft deletes a fixed bill by setting flagActive to false.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Fixed bill successfully deleted"),
            @ApiResponse(responseCode = "404", description = "Fixed bill not found")
    })
    @DeleteMapping("/{code}")
    ResponseEntity<Void> deleteById(@PathVariable UUID code);
}