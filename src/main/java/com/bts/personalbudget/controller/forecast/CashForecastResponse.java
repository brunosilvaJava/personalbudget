package com.bts.personalbudget.controller.forecast;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Schema(description = "The cash forecast from today through a horizon, with every component apart. All "
        + "components are positive and disjoint; projected_cash = cash_balance + expected_income (+ late_income "
        + "when included) - obligations - card_invoices; free_after_reserves = projected_cash - envelope_reserve "
        + "- card_beyond_horizon.")
public record CashForecastResponse(
        @Schema(type = "string", format = "date")
        LocalDate today,

        @Schema(description = "Last day included in the forecast", type = "string", format = "date")
        LocalDate until,

        @JsonProperty("include_late_income")
        @Schema(description = "The assumption used for overdue income: true counts it as money that will arrive")
        boolean includeLateIncome,

        @JsonProperty("cash_balance")
        @Schema(description = "Sum of the balances of all accounts today (initial balances included)")
        BigDecimal cashBalance,

        @JsonProperty("expected_income")
        @Schema(description = "Income still to arrive through the horizon: pending credits and income "
                + "occurrences not posted yet")
        BigDecimal expectedIncome,

        @JsonProperty("late_income")
        @Schema(description = "Overdue income. Counted only when include_late_income is true")
        BigDecimal lateIncome,

        @Schema(description = "Unpaid obligations due through the horizon, overdue ones included, plus bill "
                + "occurrences not posted yet; card installments are not here")
        BigDecimal obligations,

        @JsonProperty("card_invoices")
        @Schema(description = "Unpaid card installments due through the horizon (the invoices to pay), overdue "
                + "ones included")
        BigDecimal cardInvoices,

        @JsonProperty("projected_cash")
        @Schema(description = "Cash at the horizon before reserves")
        BigDecimal projectedCash,

        @JsonProperty("envelope_cycle_code")
        @Schema(description = "Cycle whose envelopes make the reserve; null when there is no current cycle")
        UUID envelopeCycleCode,

        @JsonProperty("envelope_reserve")
        @Schema(description = "What the envelopes of the current cycle still hold free. It is set aside, not a "
                + "dated bank outflow")
        BigDecimal envelopeReserve,

        @JsonProperty("card_beyond_horizon")
        @Schema(description = "Unpaid card installments due after the horizon: card still committed")
        BigDecimal cardBeyondHorizon,

        @JsonProperty("free_after_reserves")
        @Schema(description = "projected_cash - envelope_reserve - card_beyond_horizon")
        BigDecimal freeAfterReserves,

        @JsonProperty("lowest_projected_cash")
        @Schema(description = "Lowest projected cash over the days up to the horizon")
        BigDecimal lowestProjectedCash,

        @JsonProperty("lowest_projected_cash_date")
        @Schema(type = "string", format = "date")
        LocalDate lowestProjectedCashDate,

        @JsonProperty("first_negative_date")
        @Schema(description = "First day the projected cash is below zero; null when it never is",
                type = "string", format = "date")
        LocalDate firstNegativeDate,

        @JsonProperty("negative_cash_alert")
        @Schema(description = "The projected cash goes below zero on some day up to the horizon")
        boolean negativeCashAlert,

        @JsonProperty("insufficient_budget_alert")
        @Schema(description = "The cash does not cover the envelope reserve and the card still committed. "
                + "Independent of negative_cash_alert")
        boolean insufficientBudgetAlert,

        @JsonProperty("projection_incomplete")
        @Schema(description = "The cycle summary has pendencies: consumed amounts, and so the reserve, may be short")
        boolean projectionIncomplete,

        @JsonProperty("receivables")
        @Schema(description = "Amounts expected from third parties, kept outside expected_income")
        BigDecimal receivables
) {
    public CashForecastResponse(final LocalDate today, final LocalDate until, final boolean includeLateIncome,
                                final BigDecimal cashBalance, final BigDecimal expectedIncome, final BigDecimal lateIncome,
                                final BigDecimal obligations, final BigDecimal cardInvoices, final BigDecimal projectedCash,
                                final UUID envelopeCycleCode, final BigDecimal envelopeReserve,
                                final BigDecimal cardBeyondHorizon, final BigDecimal freeAfterReserves,
                                final BigDecimal lowestProjectedCash, final LocalDate lowestProjectedCashDate,
                                final LocalDate firstNegativeDate, final boolean negativeCashAlert,
                                final boolean insufficientBudgetAlert, final boolean projectionIncomplete) {
        this(today, until, includeLateIncome, cashBalance, expectedIncome, lateIncome, obligations, cardInvoices,
                projectedCash, envelopeCycleCode, envelopeReserve, cardBeyondHorizon, freeAfterReserves,
                lowestProjectedCash, lowestProjectedCashDate, firstNegativeDate, negativeCashAlert,
                insufficientBudgetAlert, projectionIncomplete, BigDecimal.ZERO);
    }
}
