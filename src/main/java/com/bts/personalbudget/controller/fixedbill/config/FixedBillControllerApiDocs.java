package com.bts.personalbudget.controller.fixedbill.config;

import com.bts.personalbudget.controller.fixedbill.FixedBillRequest;
import com.bts.personalbudget.controller.installmentbill.FixedBillResponse;
import com.bts.personalbudget.controller.installmentbill.FixedBillUpdateRequest;
import com.bts.personalbudget.core.domain.enumerator.FixedBillStatus;
import com.bts.personalbudget.core.domain.enumerator.OperationType;
import com.bts.personalbudget.core.domain.enumerator.RecurrenceType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

public interface FixedBillControllerApiDocs {

    @Operation(
            summary = "Create a new fixed bill",
            description = "This endpoint creates a new fixed bill based on the provided request data.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Fixed bill successfully created"),
            @ApiResponse(responseCode = "400", description = "Invalid request data")
    })
    @PostMapping
    public ResponseEntity<Void> create(@Valid @RequestBody FixedBillRequest fixedBillRequest);

    @Operation(
            summary = "Retrieve all fixed bills",
            description = "Returns a list of fixed bills based on search parameters.")
    @ApiResponse(responseCode = "200", description = "List of fixed bills")
    ResponseEntity<List<FixedBillResponse>> find(
            @Parameter(description = "Filter by description (partial match)") String description,
            @Parameter(description = "Filter by operation type(s)") List<OperationType> operationTypes,
            @Parameter(description = "Filter by status(es)") List<FixedBillStatus> statuses,
            @Parameter(description = "Filter by recurrence type(s)") List<RecurrenceType> recurrenceTypes
    );

    @GetMapping("/{code}")
    ResponseEntity<FixedBillResponse> findByCode(@PathVariable UUID code);

    @PatchMapping("/{code}")
    ResponseEntity<FixedBillResponse> update(@PathVariable UUID code,
                                             @RequestBody @Valid FixedBillUpdateRequest fixedBillUpdateRequest);

    @DeleteMapping("/{code}")
    ResponseEntity<Void> deleteById(@PathVariable UUID code);
}
