package com.bts.personalbudget.controller.thirdparty.config;

import com.bts.personalbudget.controller.thirdparty.ThirdPartyRequest;
import com.bts.personalbudget.controller.thirdparty.ThirdPartyResponse;
import com.bts.personalbudget.controller.thirdparty.ThirdPartyUpdateRequest;
import com.bts.personalbudget.core.domain.enumerator.ThirdPartyStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;

@Tag(name = "Third party", description = "People who reimburse purchases made on a card")
public interface ThirdPartyControllerApiDocs {

    @Operation(summary = "Create a third party",
            description = "Registers an ACTIVE third party. A purchase marked as theirs does not count as the "
                    + "user's own consumption.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Third party created"),
            @ApiResponse(responseCode = "400", description = "Invalid data or name already used")
    })
    ResponseEntity<ThirdPartyResponse> create(ThirdPartyRequest request);

    @Operation(summary = "List third parties",
            description = "ACTIVE ones by default; pass status one or more times to choose others.")
    @ApiResponse(responseCode = "200", description = "List of third parties")
    ResponseEntity<List<ThirdPartyResponse>> find(
            @Parameter(description = "Statuses to include; defaults to ACTIVE") List<ThirdPartyStatus> status);

    @Operation(summary = "Retrieve a third party by code", description = "INACTIVE ones are returned too.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Third party found"),
            @ApiResponse(responseCode = "404", description = "Third party not found")
    })
    ResponseEntity<ThirdPartyResponse> findByCode(UUID code);

    @Operation(summary = "Rename a third party", description = "Fields left out are kept.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Third party updated"),
            @ApiResponse(responseCode = "400", description = "Invalid data or name already used"),
            @ApiResponse(responseCode = "404", description = "Third party not found")
    })
    ResponseEntity<ThirdPartyResponse> update(UUID code, ThirdPartyUpdateRequest request);

    @Operation(summary = "Activate a third party", description = "Idempotent.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Third party active"),
            @ApiResponse(responseCode = "404", description = "Third party not found")
    })
    ResponseEntity<ThirdPartyResponse> activate(UUID code);

    @Operation(summary = "Inactivate a third party",
            description = "Idempotent.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Third party inactive"),
            @ApiResponse(responseCode = "404", description = "Third party not found")
    })
    ResponseEntity<ThirdPartyResponse> inactivate(UUID code);
}
