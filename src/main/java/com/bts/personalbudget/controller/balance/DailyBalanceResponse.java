package com.bts.personalbudget.controller.balance;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(description = "Real and projected balance of one day")
public record DailyBalanceResponse(
        @Schema(description = "Day of the balance", example = "2026-11-01", type = "string", format = "date")
        LocalDate date,

        @Schema(description = "Real balance: paid movements only")
        Balance balance,

        @Schema(description = "Projected balance: paid plus pending and late movements and recurrence occurrences")
        Projected projected
) {
    public record Balance(
            @Schema(description = "Balance at the start of the day", example = "1500.00")
            BigDecimal opening,

            @Schema(description = "Revenue paid on the day", example = "200.00")
            @JsonProperty("total_revenue")
            BigDecimal totalRevenue,

            @Schema(description = "Expense paid on the day (negative)", example = "-50.00")
            @JsonProperty("total_expense")
            BigDecimal totalExpense,

            @Schema(description = "Balance at the end of the day", example = "1650.00")
            BigDecimal closing) {
    }

    public record Projected(
            @Schema(description = "Projected balance at the start of the day", example = "1500.00")
            BigDecimal opening,

            @Schema(description = "Revenue pending on the day", example = "200.00")
            @JsonProperty("pending_total_revenue")
            BigDecimal pendingTotalRevenue,

            @Schema(description = "Expense pending on the day (negative)", example = "-50.00")
            @JsonProperty("pending_total_expense")
            BigDecimal pendingTotalExpense,

            @Schema(description = "Projected balance at the end of the day", example = "1650.00")
            BigDecimal closing) {
    }
}
