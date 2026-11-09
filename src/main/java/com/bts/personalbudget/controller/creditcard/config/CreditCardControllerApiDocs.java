package com.bts.personalbudget.controller.creditcard.config;

import com.bts.personalbudget.controller.creditcard.CreditCardRequest;
import com.bts.personalbudget.controller.creditcard.CreditCardResponse;
import com.bts.personalbudget.controller.creditcard.CreditCardUpdateRequest;
import com.bts.personalbudget.core.domain.enumerator.CreditCardStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;

@Tag(name = "Credit card", description = "API for managing credit cards")
public interface CreditCardControllerApiDocs {

    @Operation(summary = "Create a credit card",
            description = "Registers an ACTIVE card with its closing and due days. The limit and the default payment "
                    + "account are optional. Purchases and invoices are not available yet.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Card created"),
            @ApiResponse(responseCode = "400", description = "Invalid data, name already used, or unknown or "
                    + "inactive default account")
    })
    ResponseEntity<CreditCardResponse> create(CreditCardRequest request);

    @Operation(summary = "List credit cards",
            description = "ACTIVE cards by default; pass status one or more times to choose others.")
    @ApiResponse(responseCode = "200", description = "List of cards")
    ResponseEntity<List<CreditCardResponse>> find(
            @Parameter(description = "Statuses to include; defaults to ACTIVE") List<CreditCardStatus> statuses);

    @Operation(summary = "Retrieve a credit card by code", description = "INACTIVE cards are returned too.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Card found"),
            @ApiResponse(responseCode = "404", description = "Card not found")
    })
    ResponseEntity<CreditCardResponse> findByCode(UUID code);

    @Operation(summary = "Update a credit card",
            description = "Fields left out are kept. The limit and the default account can be replaced but not "
                    + "cleared. New closing and due days only apply to purchases made afterwards.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Card updated"),
            @ApiResponse(responseCode = "400", description = "Invalid data, name already used, or unknown or "
                    + "inactive default account"),
            @ApiResponse(responseCode = "404", description = "Card not found")
    })
    ResponseEntity<CreditCardResponse> update(UUID code, CreditCardUpdateRequest request);

    @Operation(summary = "Inactivate a credit card", description = "Idempotent. The card leaves the default listing.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Card inactive"),
            @ApiResponse(responseCode = "404", description = "Card not found")
    })
    ResponseEntity<CreditCardResponse> inactivate(UUID code);

    @Operation(summary = "Activate a credit card", description = "Idempotent.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Card active"),
            @ApiResponse(responseCode = "404", description = "Card not found")
    })
    ResponseEntity<CreditCardResponse> activate(UUID code);
}
