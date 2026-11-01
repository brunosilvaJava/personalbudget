package com.bts.personalbudget.controller.balance.config;

import com.bts.personalbudget.controller.balance.DailyBalanceResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.Set;

@Tag(name = "Balance", description = "API for daily real and projected balances")
public interface BalanceControllerApiDocs {

    @Operation(
            summary = "Retrieve the daily balance of a period",
            description = "Returns one entry per day between initial_date and end_date (inclusive), with the real "
                    + "balance (paid movements) and the projected balance (also pending and late movements and the "
                    + "occurrences of fixed and installment bills not posted yet).")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Daily balances of the period"),
            @ApiResponse(responseCode = "400", description = "Missing or invalid initial_date or end_date")
    })
    Set<DailyBalanceResponse> balance(
            @Parameter(description = "First day of the period (yyyy-MM-dd)", example = "2026-11-01") LocalDate initialDate,
            @Parameter(description = "Last day of the period (yyyy-MM-dd)", example = "2026-11-30") LocalDate endDate);
}
