package com.bts.personalbudget.controller.dashboard;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(description = "Balances, revenue, expense and alerts of one month")
public record DashboardResponse(

        // Saldos
        @Schema(description = "Real balance today", example = "1500.00")
        @JsonProperty("current_balance")
        BigDecimal currentBalance,

        @Schema(description = "Projected balance today", example = "1200.00")
        @JsonProperty("current_projected_balance")
        BigDecimal currentProjectedBalance,

        @Schema(description = "Real balance at the end of the month", example = "1500.00")
        @JsonProperty("month_closing_balance")
        BigDecimal monthClosingBalance,

        @Schema(description = "Projected balance at the end of the month", example = "900.00")
        @JsonProperty("month_projected_closing_balance")
        BigDecimal monthProjectedClosingBalance,

        // Receitas do mês
        @Schema(description = "Revenue paid in the month", example = "5000.00")
        @JsonProperty("month_total_revenue")
        BigDecimal monthTotalRevenue,

        @Schema(description = "Revenue projected for the month", example = "5000.00")
        @JsonProperty("month_projected_total_revenue")
        BigDecimal monthProjectedTotalRevenue,

        // Despesas do mês
        @Schema(description = "Expense paid in the month (negative)", example = "-3500.00")
        @JsonProperty("month_total_expense")
        BigDecimal monthTotalExpense,

        @Schema(description = "Expense projected for the month (negative)", example = "-4100.00")
        @JsonProperty("month_projected_total_expense")
        BigDecimal monthProjectedTotalExpense,

        // Alertas
        @Schema(description = "Whether the projected balance goes negative in the month", example = "false")
        @JsonProperty("has_negative_balance_alert")
        boolean hasNegativeBalanceAlert,

        @Schema(description = "First day with negative projected balance, if any", example = "2026-11-20", type = "string", format = "date")
        @JsonProperty("first_negative_balance_date")
        LocalDate firstNegativeBalanceDate,

        @Schema(description = "Lowest projected balance of the month", example = "320.00")
        @JsonProperty("lowest_projected_balance")
        BigDecimal lowestProjectedBalance,

        @Schema(description = "Day of the lowest projected balance", example = "2026-11-28", type = "string", format = "date")
        @JsonProperty("lowest_balance_date")
        LocalDate lowestBalanceDate,

        // Período de referência
        @Schema(description = "Reference month, as its first day", example = "2026-11-01", type = "string", format = "date")
        @JsonProperty("reference_month")
        LocalDate referenceMonth,

        @Schema(description = "Today", example = "2026-11-01", type = "string", format = "date")
        @JsonProperty("current_date")
        LocalDate currentDate,

        // Dados adicionais
        @Schema(description = "Number of days in the month", example = "30")
        @JsonProperty("total_days_in_month")
        Integer totalDaysInMonth,

        @Schema(description = "Days elapsed in the month", example = "1")
        @JsonProperty("days_elapsed")
        Integer daysElapsed,

        @Schema(description = "Days remaining in the month", example = "29")
        @JsonProperty("days_remaining")
        Integer daysRemaining,

        // Variações
        @Schema(description = "Paid revenue plus paid expense of the month", example = "1500.00")
        @JsonProperty("month_net_change")
        BigDecimal monthNetChange,

        @Schema(description = "Projected revenue plus projected expense of the month", example = "900.00")
        @JsonProperty("projected_net_change")
        BigDecimal projectedNetChange
) {
}
