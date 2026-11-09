package com.bts.personalbudget.controller.account;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Schema(description = "What moved an account in a period, with the running balance. Reversed settlements and "
        + "transfers are left out, as in the balance.")
public record AccountStatementResponse(

        @JsonProperty("account_code")
        @Schema(description = "Account identifier")
        UUID accountCode,

        @JsonProperty("start_date")
        @Schema(description = "First day (inclusive)", example = "2026-11-01", type = "string", format = "date")
        LocalDate startDate,

        @JsonProperty("end_date")
        @Schema(description = "Last day (inclusive)", example = "2026-11-30", type = "string", format = "date")
        LocalDate endDate,

        @JsonProperty("opening_balance")
        @Schema(description = "Balance at the end of the day before start_date; 0 before the initial balance date",
                example = "1500.00")
        BigDecimal openingBalance,

        @JsonProperty("closing_balance")
        @Schema(description = "Balance on end_date: opening_balance plus the sum of the entries", example = "1379.50")
        BigDecimal closingBalance,

        @Schema(description = "Oldest first; on the same day the initial balance, then settlements, then transfers")
        List<AccountStatementEntryResponse> entries
) {
}
