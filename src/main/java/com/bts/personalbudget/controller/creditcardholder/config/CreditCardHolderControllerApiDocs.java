package com.bts.personalbudget.controller.creditcardholder.config;

import com.bts.personalbudget.controller.creditcardholder.CreditCardHolderRequest;
import com.bts.personalbudget.controller.creditcardholder.CreditCardHolderResponse;
import com.bts.personalbudget.controller.creditcardholder.CreditCardHolderUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;

@Tag(name = "Credit card holder", description = "People identified on a credit card account")
public interface CreditCardHolderControllerApiDocs {

    @Operation(summary = "List the holders of a card",
            description = "The titular comes first, then the additional holders by name. The limit, the invoices "
                    + "and the cycles stay with the card; a holder only identifies who used it.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Holders of the card"),
            @ApiResponse(responseCode = "404", description = "Card not found")
    })
    ResponseEntity<List<CreditCardHolderResponse>> find(UUID cardCode);

    @Operation(summary = "Create an additional holder",
            description = "Every card already has exactly one TITULAR, created with the card. This creates an "
                    + "ADICIONAL holder, ACTIVE.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Holder created"),
            @ApiResponse(responseCode = "400", description = "Invalid name"),
            @ApiResponse(responseCode = "404", description = "Card not found"),
            @ApiResponse(responseCode = "409", description = "A holder with this name already exists on the card")
    })
    ResponseEntity<CreditCardHolderResponse> create(UUID cardCode, CreditCardHolderRequest request);

    @Operation(summary = "Rename a holder", description = "Works for the titular too. Fields left out are kept.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Holder updated"),
            @ApiResponse(responseCode = "400", description = "Invalid name"),
            @ApiResponse(responseCode = "404", description = "Card or holder not found"),
            @ApiResponse(responseCode = "409", description = "Name already used on the card, or the holder "
                    + "belongs to another card")
    })
    ResponseEntity<CreditCardHolderResponse> update(UUID cardCode, UUID holderCode,
                                                    CreditCardHolderUpdateRequest request);

    @Operation(summary = "Inactivate a holder", description = "Idempotent. The titular cannot be inactivated.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Holder inactive"),
            @ApiResponse(responseCode = "404", description = "Card or holder not found"),
            @ApiResponse(responseCode = "409", description = "The titular cannot be inactivated")
    })
    ResponseEntity<CreditCardHolderResponse> inactivate(UUID cardCode, UUID holderCode);

    @Operation(summary = "Activate a holder", description = "Idempotent.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Holder active"),
            @ApiResponse(responseCode = "404", description = "Card or holder not found")
    })
    ResponseEntity<CreditCardHolderResponse> activate(UUID cardCode, UUID holderCode);
}
