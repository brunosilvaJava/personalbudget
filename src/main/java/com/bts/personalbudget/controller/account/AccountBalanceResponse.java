package com.bts.personalbudget.controller.account;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Schema(description = "Balance of an account on a date.")
public record AccountBalanceResponse(

        @JsonProperty("account_code")
        @Schema(description = "Account identifier")
        UUID accountCode,

        @Schema(description = "Date of the balance (inclusive)", example = "2026-11-08", type = "string", format = "date")
        LocalDate date,

        @JsonProperty("initial_balance")
        @Schema(description = "Initial balance counted in the result; 0 when the date is before the initial balance date",
                example = "1500.00")
        BigDecimal initialBalance,

        @JsonProperty("settlements_total")
        @Schema(description = "Sum of the active settlements of the account (credits positive, debits negative) "
                + "from the initial balance date through the date", example = "-320.50")
        BigDecimal settlementsTotal,

        @JsonProperty("transfers_total")
        @Schema(description = "Net active transfers of the account (incoming positive, outgoing negative) "
                + "from the initial balance date through the date", example = "200.00")
        BigDecimal transfersTotal,

        @Schema(description = "initial_balance + settlements_total + transfers_total", example = "1379.50")
        BigDecimal balance
) {
}
