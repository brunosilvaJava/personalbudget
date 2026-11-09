package com.bts.personalbudget.controller.account.config;

import com.bts.personalbudget.controller.account.AccountBalanceResponse;
import com.bts.personalbudget.controller.account.AccountsBalanceResponse;
import com.bts.personalbudget.controller.account.AccountRequest;
import com.bts.personalbudget.controller.account.AccountResponse;
import com.bts.personalbudget.controller.account.AccountUpdateRequest;
import com.bts.personalbudget.core.domain.enumerator.AccountStatus;
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

@Tag(name = "Account", description = "API for managing accounts and wallets")
public interface AccountControllerApiDocs {

    @Operation(summary = "Create an account",
            description = "Creates an ACTIVE account. The initial balance is not income. A new account is never the "
                    + "default one; use the update to choose the default.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Account created",
                    content = @Content(schema = @Schema(implementation = AccountResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid data or name already used")
    })
    ResponseEntity<AccountResponse> create(AccountRequest request);

    @Operation(summary = "List accounts",
            description = "The default account comes first, then by name. Not paginated. "
                    + "Only ACTIVE accounts are returned unless status says otherwise.")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "List of accounts")})
    ResponseEntity<List<AccountResponse>> find(
            @Parameter(description = "Filter by status(es). Defaults to ACTIVE") List<AccountStatus> statuses);

    @Operation(summary = "Retrieve an account by code", description = "Inactive accounts are returned too.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Account found"),
            @ApiResponse(responseCode = "404", description = "Account not found")
    })
    ResponseEntity<AccountResponse> findByCode(UUID code);

    @Operation(summary = "Update an account",
            description = "Fields left out are kept. default=true moves the default flag to this account. "
                    + "The initial balance date cannot be later than the first active settlement of the account.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Account updated"),
            @ApiResponse(responseCode = "400", description = "Invalid data, name already used, default=false "
                    + "or initial balance date after the first settlement"),
            @ApiResponse(responseCode = "404", description = "Account not found"),
            @ApiResponse(responseCode = "409", description = "An inactive account cannot be the default")
    })
    ResponseEntity<AccountResponse> update(UUID code, AccountUpdateRequest request);

    @Operation(summary = "Inactivate an account",
            description = "Idempotent. The account leaves the pick lists but keeps its history.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Account inactive"),
            @ApiResponse(responseCode = "404", description = "Account not found"),
            @ApiResponse(responseCode = "409", description = "The default account cannot be inactivated")
    })
    ResponseEntity<AccountResponse> inactivate(UUID code);

    @Operation(summary = "Activate an account", description = "Idempotent.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Account active"),
            @ApiResponse(responseCode = "404", description = "Account not found")
    })
    ResponseEntity<AccountResponse> activate(UUID code);

    @Operation(summary = "Balance of all accounts on a date",
            description = "One balance per account (ACTIVE and INACTIVE, default first) plus their total. Transfers "
                    + "between accounts cancel out in the total.")
    @ApiResponse(responseCode = "200", description = "Balances calculated")
    ResponseEntity<AccountsBalanceResponse> balances(
            @Parameter(description = "Date of the balance, yyyy-MM-dd. Defaults to today") LocalDate date);

    @Operation(summary = "Account balance on a date",
            description = "Initial balance plus the active settlements and the net active transfers of the account from "
                    + "the initial balance date through the date (inclusive); credits and incoming transfers add, "
                    + "debits and outgoing transfers subtract. Before the initial balance "
                    + "date the balance is zero. Cash reports (/balance/daily, /dashboard) are not changed.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Balance calculated"),
            @ApiResponse(responseCode = "404", description = "Account not found")
    })
    ResponseEntity<AccountBalanceResponse> balance(
            UUID code,
            @Parameter(description = "Date of the balance, yyyy-MM-dd. Defaults to today") LocalDate date);
}
