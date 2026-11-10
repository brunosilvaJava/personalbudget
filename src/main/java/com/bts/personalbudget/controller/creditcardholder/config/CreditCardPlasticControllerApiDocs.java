package com.bts.personalbudget.controller.creditcardholder.config;

import com.bts.personalbudget.controller.creditcardholder.CreditCardPlasticRequest;
import com.bts.personalbudget.controller.creditcardholder.CreditCardPlasticResponse;
import com.bts.personalbudget.controller.creditcardholder.CreditCardPlasticUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;

@Tag(name = "Credit card plastic", description = "Physical or virtual numbers that share a card account")
public interface CreditCardPlasticControllerApiDocs {

    @Operation(summary = "List the plastics of a card", description = "Ordered by the last four digits.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Plastics of the card"),
            @ApiResponse(responseCode = "404", description = "Card not found")
    })
    ResponseEntity<List<CreditCardPlasticResponse>> find(UUID cardCode);

    @Operation(summary = "Register a plastic",
            description = "A plastic is identified by its last four digits and belongs to one holder of the card. "
                    + "When the kind is left out it is UNKNOWN.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Plastic created"),
            @ApiResponse(responseCode = "400", description = "Last digits are not exactly four digits"),
            @ApiResponse(responseCode = "404", description = "Card or holder not found"),
            @ApiResponse(responseCode = "409", description = "The last digits are already registered on the card, "
                    + "or the holder belongs to another card")
    })
    ResponseEntity<CreditCardPlasticResponse> create(UUID cardCode, CreditCardPlasticRequest request);

    @Operation(summary = "Update a plastic",
            description = "Fields left out are kept. default_third_party_code makes the purchases made with this "
                    + "plastic belong to that third party unless the purchase says otherwise; "
                    + "clear_default_third_party removes it and cannot be sent together with a code. Reissuing a "
                    + "plastic is registering the new digits for the same holder and marking the old one REPLACED.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Plastic updated"),
            @ApiResponse(responseCode = "400", description = "Unknown or inactive third party, or both "
                    + "default_third_party_code and clear_default_third_party informed"),
            @ApiResponse(responseCode = "404", description = "Card or plastic not found"),
            @ApiResponse(responseCode = "409", description = "The plastic belongs to another card")
    })
    ResponseEntity<CreditCardPlasticResponse> update(UUID cardCode, UUID plasticCode,
                                                     CreditCardPlasticUpdateRequest request);
}
