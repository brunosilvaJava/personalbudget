package com.bts.personalbudget.controller.paycycle.config;

import com.bts.personalbudget.controller.paycycle.PayCycleCurrentNotFoundResponse;
import com.bts.personalbudget.controller.paycycle.PayCycleResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Tag(name = "Pay cycle", description = "API for reading the financial cycles")
public interface PayCycleControllerApiDocs {

    @Operation(
            summary = "List the pay cycles",
            description = "Returns the cycles ordered by start date. A cycle is the interval [start_date, "
                    + "end_date_exclusive): the day the next one opens belongs to the next one. The list is not "
                    + "paginated. With date, only the cycle that contains it; with from and/or to, the cycles that "
                    + "share at least one day with that range (both inclusive). date cannot be combined with "
                    + "from or to. Cancelled cycles are returned only with include_cancelled.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "List of cycles"),
            @ApiResponse(responseCode = "400", description = "Invalid parameters")
    })
    ResponseEntity<List<PayCycleResponse>> find(
            @Parameter(description = "Only the cycle that contains this date (yyyy-MM-dd)") LocalDate date,
            @Parameter(description = "Cycles that end after this day (yyyy-MM-dd)") LocalDate from,
            @Parameter(description = "Cycles that start on or before this day (yyyy-MM-dd)") LocalDate to,
            @Parameter(description = "Also return cancelled cycles. Defaults to false") boolean includeCancelled);

    @Operation(
            summary = "Retrieve the current pay cycle",
            description = "Returns the cycle that contains today. The answer is 404 with the pendencies that explain "
                    + "it when there is none: the pay cycles are off, the first cycle has not started, or the chain "
                    + "could not reach today (for example a calendar year that was not reviewed).")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "The current cycle",
                    content = @Content(schema = @Schema(implementation = PayCycleResponse.class))),
            @ApiResponse(responseCode = "404", description = "No cycle contains today",
                    content = @Content(schema = @Schema(implementation = PayCycleCurrentNotFoundResponse.class)))
    })
    ResponseEntity<?> findCurrent();

    @Operation(
            summary = "Retrieve a pay cycle by code",
            description = "Fetches a cycle by its unique identifier (UUID), which never changes when its dates are "
                    + "adjusted. Cancelled cycles are returned too.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Cycle found and returned",
                    content = @Content(schema = @Schema(implementation = PayCycleResponse.class))),
            @ApiResponse(responseCode = "404", description = "Cycle not found")
    })
    ResponseEntity<PayCycleResponse> findByCode(@PathVariable UUID code);

}
