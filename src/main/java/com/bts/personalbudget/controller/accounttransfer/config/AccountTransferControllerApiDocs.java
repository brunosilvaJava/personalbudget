package com.bts.personalbudget.controller.accounttransfer.config;

import com.bts.personalbudget.controller.accounttransfer.AccountTransferRequest;
import com.bts.personalbudget.controller.accounttransfer.AccountTransferResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;

@Tag(name = "Account transfer", description = "API for transferring money between accounts")
public interface AccountTransferControllerApiDocs {

    @Operation(summary = "Transfer money between accounts",
            description = "Moves a positive amount from one ACTIVE account to another. It is neutral for consumption: "
                    + "it is not a movement and never appears in /balance/daily, /dashboard, /category_summary or "
                    + "the pay cycles. Only the account balances change.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Transfer recorded"),
            @ApiResponse(responseCode = "400", description = "Invalid fields: same account on both sides, unknown or "
                    + "inactive account, or effective_date before the initial balance date of an account")
    })
    ResponseEntity<AccountTransferResponse> create(AccountTransferRequest request);

    @Operation(summary = "List transfers",
            description = "Transfers whose effective date is in the period (both dates inclusive, at most 366 days), "
                    + "newest first, including reversed ones. account_code keeps the transfers where the account is "
                    + "the origin or the destination. Not paginated.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of transfers"),
            @ApiResponse(responseCode = "400", description = "Missing or invalid period")
    })
    ResponseEntity<List<AccountTransferResponse>> find(
            @Parameter(description = "First day, yyyy-MM-dd") LocalDate startDate,
            @Parameter(description = "Last day (inclusive), yyyy-MM-dd") LocalDate endDate,
            @Parameter(description = "Only transfers of this account") UUID accountCode);

    @Operation(summary = "Retrieve a transfer by code", description = "Reversed transfers are returned too.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Transfer found"),
            @ApiResponse(responseCode = "404", description = "Transfer not found")
    })
    ResponseEntity<AccountTransferResponse> findByCode(UUID code);

    @Operation(summary = "Reverse a transfer",
            description = "Marks the transfer as reversed without deleting it: both balances go back as if it had "
                    + "not happened, and the record stays in the history.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Transfer reversed"),
            @ApiResponse(responseCode = "404", description = "Transfer not found"),
            @ApiResponse(responseCode = "409", description = "Transfer is already reversed")
    })
    ResponseEntity<AccountTransferResponse> reverse(UUID code);
}
