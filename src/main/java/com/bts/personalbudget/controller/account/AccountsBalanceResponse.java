package com.bts.personalbudget.controller.account;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Schema(description = "Balance of every account on a date and their sum.")
public record AccountsBalanceResponse(

        @Schema(description = "Date of the balance (inclusive)", example = "2026-11-08", type = "string", format = "date")
        LocalDate date,

        @Schema(description = "One balance per account, ACTIVE and INACTIVE; the default account comes first")
        List<AccountBalanceResponse> accounts,

        @Schema(description = "Sum of the balances of all accounts; transfers cancel out", example = "2579.50")
        BigDecimal total
) {
}
