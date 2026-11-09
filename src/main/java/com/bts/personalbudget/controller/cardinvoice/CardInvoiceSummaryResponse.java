package com.bts.personalbudget.controller.cardinvoice;

import com.bts.personalbudget.core.domain.enumerator.CardInvoiceState;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

@Schema(description = "An invoice of a card without its installments.")
public record CardInvoiceSummaryResponse(

        @Schema(description = "Unique identifier for the invoice")
        UUID code,

        @JsonProperty("card_code")
        @Schema(description = "Card the invoice belongs to")
        UUID cardCode,

        @JsonProperty("reference_month")
        @Schema(description = "Month the invoice closes in", example = "2026-12", type = "string")
        YearMonth referenceMonth,

        @JsonProperty("closing_date")
        @Schema(description = "Date the invoice closes", example = "2026-12-25", type = "string", format = "date")
        LocalDate closingDate,

        @JsonProperty("due_date")
        @Schema(description = "Date the invoice is due", example = "2027-01-05", type = "string", format = "date")
        LocalDate dueDate,

        @Schema(description = "OPEN before the closing date, CLOSED from then on, PAID when every installment "
                + "is paid", example = "OPEN")
        CardInvoiceState state,

        @JsonProperty("total_amount")
        @Schema(description = "Sum of the installments", example = "750.00")
        BigDecimal totalAmount,

        @JsonProperty("paid_amount")
        @Schema(description = "Part of the total already paid", example = "0.00")
        BigDecimal paidAmount
) {
}
