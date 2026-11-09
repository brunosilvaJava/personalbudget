package com.bts.personalbudget.controller.budgetenvelope.config;

import com.bts.personalbudget.controller.budgetenvelope.BudgetEnvelopeRequest;
import com.bts.personalbudget.controller.budgetenvelope.BudgetEnvelopeResponse;
import com.bts.personalbudget.controller.budgetenvelope.BudgetEnvelopeUpdateRequest;
import com.bts.personalbudget.controller.budgetenvelope.CycleEnvelopesResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.ResponseEntity;

@Tag(name = "Budget envelope", description = "API for planning amounts per category in a pay cycle")
public interface BudgetEnvelopeControllerApiDocs {

    @Operation(summary = "Plan an amount for a category in a cycle",
            description = "One active envelope per category and cycle. The category must be an ACTIVE DEBIT "
                    + "category. A closed or cancelled cycle does not accept changes.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Envelope created"),
            @ApiResponse(responseCode = "400", description = "Invalid amount or category"),
            @ApiResponse(responseCode = "404", description = "Cycle not found"),
            @ApiResponse(responseCode = "409", description = "The category already has an envelope in the "
                    + "cycle, or the cycle is closed or cancelled")
    })
    ResponseEntity<BudgetEnvelopeResponse> create(UUID cycleCode, BudgetEnvelopeRequest request);

    @Operation(summary = "List the envelopes of a cycle",
            description = "Each envelope with its recognized (paid), committed (still to pay), consumed, "
                    + "remaining, reserved and overrun amounts, plus the totals. The consumption is the one the "
                    + "cycle summary reports, by the cycle's competence.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Envelopes of the cycle"),
            @ApiResponse(responseCode = "404", description = "Cycle not found")
    })
    ResponseEntity<CycleEnvelopesResponse> find(UUID cycleCode);

    @Operation(summary = "Change the planned amount of an envelope")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Envelope updated"),
            @ApiResponse(responseCode = "400", description = "Invalid amount"),
            @ApiResponse(responseCode = "404", description = "Envelope not found"),
            @ApiResponse(responseCode = "409", description = "The cycle is closed or cancelled")
    })
    ResponseEntity<BudgetEnvelopeResponse> update(UUID code, BudgetEnvelopeUpdateRequest request);

    @Operation(summary = "Delete an envelope",
            description = "Logical exclusion; idempotent. The category can get a new envelope in the cycle.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Envelope deleted"),
            @ApiResponse(responseCode = "409", description = "The cycle is closed or cancelled")
    })
    ResponseEntity<Void> delete(UUID code);
}
