package com.bts.personalbudget.controller.creditcard;

import com.bts.personalbudget.core.domain.enumerator.CreditCardStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.UUID;

@Schema(description = "Response data for a credit card.")
public record CreditCardResponse(

        @Schema(description = "Unique identifier for the card", example = "123e4567-e89b-12d3-a456-426614174000")
        UUID code,

        @Schema(description = "Card name", example = "Nubank Roxinho")
        String name,

        @JsonProperty("closing_day")
        @Schema(description = "Day of the month the invoice closes", example = "25")
        Integer closingDay,

        @JsonProperty("due_day")
        @Schema(description = "Day of the month the invoice is due", example = "5")
        Integer dueDay,

        @JsonProperty("credit_limit")
        @Schema(description = "Credit limit; null when the card has no limit check", example = "5000.00")
        BigDecimal creditLimit,

        @JsonProperty("default_account_code")
        @Schema(description = "Account the invoice is paid from by default; null when not set")
        UUID defaultAccountCode,

        @JsonProperty("default_third_party_code")
        UUID defaultThirdPartyCode,

        @Schema(description = "Card status (ACTIVE or INACTIVE)", example = "ACTIVE")
        CreditCardStatus status
) {
}
