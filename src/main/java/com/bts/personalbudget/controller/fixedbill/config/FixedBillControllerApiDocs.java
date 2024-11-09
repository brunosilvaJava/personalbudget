package com.bts.personalbudget.controller.fixedbill.config;

import com.bts.personalbudget.controller.fixedbill.FixedBillRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

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
}
