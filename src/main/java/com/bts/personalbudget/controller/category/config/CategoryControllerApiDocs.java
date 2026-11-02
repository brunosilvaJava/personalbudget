package com.bts.personalbudget.controller.category.config;

import com.bts.personalbudget.controller.category.CategoryRequest;
import com.bts.personalbudget.controller.category.CategoryResponse;
import com.bts.personalbudget.controller.category.CategoryUpdateRequest;
import com.bts.personalbudget.core.domain.enumerator.CategoryStatus;
import com.bts.personalbudget.core.domain.enumerator.OperationType;
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

@Tag(name = "Category", description = "API for managing categories")
public interface CategoryControllerApiDocs {

    @Operation(
            summary = "Create a new category",
            description = "Creates an ACTIVE category in the given group. The name must be unique per operation type.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Category successfully created",
                    content = @Content(schema = @Schema(implementation = CategoryResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request data, unknown group or name already used for the operation type")
    })
    @PostMapping
    ResponseEntity<CategoryResponse> create(@Valid @RequestBody CategoryRequest categoryRequest);

    @Operation(
            summary = "List categories",
            description = "Returns the categories ordered by group and name. The list is not paginated. "
                    + "Only ACTIVE categories are returned unless status says otherwise.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "List of categories"),
            @ApiResponse(responseCode = "400", description = "Invalid parameters")
    })
    ResponseEntity<List<CategoryResponse>> find(
            @Parameter(description = "Filter by operation type") OperationType operationType,
            @Parameter(description = "Filter by status(es). Defaults to ACTIVE") List<CategoryStatus> statuses,
            @Parameter(description = "Filter by group code") UUID groupCode
    );

    @Operation(
            summary = "Retrieve a category by code",
            description = "Fetches a category by its unique identifier (UUID). Inactive categories are returned too.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Category found and returned"),
            @ApiResponse(responseCode = "404", description = "Category not found")
    })
    @GetMapping("/{code}")
    ResponseEntity<CategoryResponse> findByCode(@PathVariable UUID code);

    @Operation(
            summary = "Update a category",
            description = "Renames the category and/or moves it to another group. The operation type cannot be changed.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Category successfully updated"),
            @ApiResponse(responseCode = "404", description = "Category not found"),
            @ApiResponse(responseCode = "400", description = "Invalid request data, unknown group or name already used for the operation type")
    })
    @PatchMapping("/{code}")
    ResponseEntity<CategoryResponse> update(@PathVariable UUID code,
                                            @RequestBody @Valid CategoryUpdateRequest categoryUpdateRequest);

    @Operation(
            summary = "Inactivate a category",
            description = "Changes the status of a category to INACTIVE. Past movements keep it; new ones cannot use it.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Category successfully inactivated"),
            @ApiResponse(responseCode = "404", description = "Category not found")
    })
    @PatchMapping("/{code}/inactivate")
    ResponseEntity<CategoryResponse> inactivate(@PathVariable UUID code);

    @Operation(
            summary = "Activate a category",
            description = "Changes the status of a category to ACTIVE.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Category successfully activated"),
            @ApiResponse(responseCode = "404", description = "Category not found")
    })
    @PatchMapping("/{code}/activate")
    ResponseEntity<CategoryResponse> activate(@PathVariable UUID code);

}
