package com.bts.personalbudget.controller.cardinvoice;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;

@Schema(description = "Data for paying a whole card invoice.")
public record CardInvoicePaymentRequest(
        @JsonProperty("account_code")
        @Schema(description = "Account the money comes from. Optional: the card's default account, or the "
                + "default account when the card has none. It must exist and be ACTIVE",
                example = "123e4567-e89b-12d3-a456-426614174000")
        UUID accountCode,

        @NotNull
        @JsonProperty("payment_date")
        @Schema(description = "Date the payment took effect", example = "2026-12-05", type = "string",
                format = "date")
        LocalDate paymentDate
) {
}
