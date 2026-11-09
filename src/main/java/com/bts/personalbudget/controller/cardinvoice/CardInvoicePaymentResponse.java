package com.bts.personalbudget.controller.cardinvoice;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Schema(description = "The payment of an invoice; reversed payments remain visible in the history.")
public record CardInvoicePaymentResponse(
        @Schema(description = "Public payment identifier")
        UUID code,

        @JsonProperty("invoice_code")
        @Schema(description = "Code of the paid invoice")
        UUID invoiceCode,

        @JsonProperty("account_code")
        @Schema(description = "Account the money came from")
        UUID accountCode,

        @JsonProperty("payment_date")
        @Schema(description = "Date the payment took effect", type = "string", format = "date")
        LocalDate paymentDate,

        @Schema(description = "Total paid (positive): what was still owed on the invoice")
        BigDecimal amount,

        @JsonProperty("reversed_at")
        @Schema(description = "When the payment was reversed; null while active")
        LocalDateTime reversedAt,

        @Schema(description = "Optimistic-lock version")
        Long version
) {
}
