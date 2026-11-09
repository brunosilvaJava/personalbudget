package com.bts.personalbudget.controller.movementsettlement.config;

import com.bts.personalbudget.controller.movementsettlement.MovementSettlementRequest;
import com.bts.personalbudget.controller.movementsettlement.MovementSettlementResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;

public interface MovementSettlementControllerApiDocs {

    @Operation(summary = "Record a payment settlement",
            description = "Records an individual positive payment against a movement. The total of active settlements "
                    + "cannot exceed the movement amount. The movement becomes PAID_OUT only when fully settled; "
                    + "partial settlements are reflected in cash on each effective_date. The money comes from or goes to "
                    + "account_code; when omitted, the default account is used.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Settlement recorded"),
            @ApiResponse(responseCode = "400", description = "Invalid settlement fields, unknown or inactive account, "
                    + "or effective_date before the account initial balance date"),
            @ApiResponse(responseCode = "404", description = "Movement not found"),
            @ApiResponse(responseCode = "409", description = "Settlement exceeds the remaining amount")
    })
    ResponseEntity<MovementSettlementResponse> create(
            @PathVariable UUID movementCode, MovementSettlementRequest request);

    @Operation(summary = "List payment settlements",
            description = "Returns all settlements, including reversed entries, ordered by effective date.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Settlement history"),
            @ApiResponse(responseCode = "404", description = "Movement not found")
    })
    ResponseEntity<List<MovementSettlementResponse>> list(@PathVariable UUID movementCode);

    @Operation(summary = "Reverse a payment settlement",
            description = "Marks a settlement as reversed without deleting its history and recalculates the movement status.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Settlement reversed"),
            @ApiResponse(responseCode = "404", description = "Movement or settlement not found"),
            @ApiResponse(responseCode = "409", description = "Settlement is already reversed")
    })
    ResponseEntity<MovementSettlementResponse> reverse(
            @PathVariable UUID movementCode, @PathVariable UUID settlementCode);
}
