package com.bts.personalbudget.controller.creditcard;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.UUID;

@Schema(description = "Limit of a credit card: what is used and what is left.")
public record CreditCardLimitResponse(

        @JsonProperty("card_code")
        @Schema(description = "Card identifier")
        UUID cardCode,

        @JsonProperty("credit_limit")
        @Schema(description = "Credit limit; null when the card has no limit check", example = "5000.00")
        BigDecimal creditLimit,

        @Schema(description = "Unpaid part of every active installment of the card, future ones included",
                example = "1250.00")
        BigDecimal used,

        @Schema(description = "credit_limit - used; null without a limit; negative when the limit was lowered "
                + "under what is used", example = "3750.00")
        BigDecimal available
) {
}
