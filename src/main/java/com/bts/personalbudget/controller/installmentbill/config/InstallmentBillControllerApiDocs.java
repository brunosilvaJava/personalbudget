package com.bts.personalbudget.controller.installmentbill.config;

import com.bts.personalbudget.controller.installmentbill.InstallmentBillRequest;
import com.bts.personalbudget.controller.installmentbill.InstallmentBillResponse;
import com.bts.personalbudget.controller.installmentbill.InstallmentBillUpdateRequest;
import com.bts.personalbudget.controller.installmentbill.PagedInstallmentBillResponse;
import com.bts.personalbudget.core.domain.enumerator.InstallmentBillStatus;
import com.bts.personalbudget.core.domain.enumerator.OperationType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

public interface InstallmentBillControllerApiDocs {

    @Operation(summary = "Create a new installment bill",
            description = "Creates a new installment bill with the given details.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Installment bill successfully created; returns it, with installments already due posted"),
            @ApiResponse(responseCode = "400", description = "Invalid request data")
    })
    @PostMapping
    ResponseEntity<InstallmentBillResponse> create(@RequestBody @Valid final InstallmentBillRequest installmentBillRequest);

    @Operation(summary = "Retrieve all installment bills with pagination and filters",
            description = "Returns a paginated list of installment bills based on search parameters.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Paginated list of installment bills"),
            @ApiResponse(responseCode = "400", description = "Invalid parameters, including an unknown sort_by")
    })
    ResponseEntity<PagedInstallmentBillResponse> findAll(
            @Parameter(description = "Filter by description (partial match)") String description,
            @Parameter(description = "Filter by operation type(s)") List<OperationType> operationTypes,
            @Parameter(description = "Filter by status(es)") List<InstallmentBillStatus> statuses,
            @Parameter(description = "Page number (0-indexed)") int page,
            @Parameter(description = "Number of items per page") int size,
            @Parameter(description = "Field to sort by: description, amount, operation_type, status, purchase_date, installment_total, installment_count, next_installment_date, last_installment_date") String sortBy,
            @Parameter(description = "Sort direction (ASC or DESC)") String sortDirection
    );

    @Operation(summary = "Retrieve an installment bill by code",
            description = "Fetches a specific installment bill by its unique identifier (UUID).")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Installment bill found and returned"),
            @ApiResponse(responseCode = "404", description = "Installment bill not found")
    })
    @GetMapping("/{code}")
    ResponseEntity<InstallmentBillResponse> findByCode(@PathVariable final UUID code);

    @Operation(summary = "Update an existing installment bill",
            description = "Updates the details of an existing installment bill identified by its UUID. "
                    + "Changing purchase_date after an installment has been posted does not reschedule future installments; "
                    + "before the first installment, the next date is recalculated from purchase_date. "
                    + "If the updated installment_total equals installment_count, the bill is completed immediately. "
                    + "When an update makes an installment due, overdue installments are posted in the same transaction.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Installment bill successfully updated"),
            @ApiResponse(responseCode = "400", description = "Invalid request data, including installment_total below the number already posted"),
            @ApiResponse(responseCode = "404", description = "Installment bill not found")
    })
    @PatchMapping("/{code}")
    ResponseEntity<InstallmentBillResponse> update(@PathVariable UUID code,
                                                   @RequestBody @Valid final InstallmentBillUpdateRequest installmentBillUpdateRequest);

    @Operation(summary = "Delete an installment bill by code",
            description = "Deletes an existing installment bill identified by its UUID.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Installment bill deleted, or it was already deleted"),
            @ApiResponse(responseCode = "404", description = "Installment bill not found")
    })
    @DeleteMapping("/{code}")
    ResponseEntity<Void> delete(@PathVariable final UUID code);
}