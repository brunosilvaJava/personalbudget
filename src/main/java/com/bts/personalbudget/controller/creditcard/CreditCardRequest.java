package com.bts.personalbudget.controller.creditcard;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

@Schema(description = "Request data for creating a credit card.")
public record CreditCardRequest(

        @NotBlank
        @Size(min = 3, max = 50)
        @Schema(description = "Card name, unique", example = "Nubank Roxinho")
        String name,

        @NotNull
        @Min(1)
        @Max(31)
        @JsonProperty("closing_day")
        @Schema(description = "Day of the month the invoice closes; a shorter month uses its last day", example = "25")
        Integer closingDay,

        @NotNull
        @Min(1)
        @Max(31)
        @JsonProperty("due_day")
        @Schema(description = "Day of the month the invoice is due; a shorter month uses its last day", example = "5")
        Integer dueDay,

        @DecimalMin(value = "0.00", inclusive = false)
        @Digits(integer = 10, fraction = 2)
        @JsonProperty("credit_limit")
        @Schema(description = "Optional credit limit. Without it no limit check is made", example = "5000.00")
        BigDecimal creditLimit,

        @JsonProperty("default_account_code")
        @Schema(description = "Optional account the invoice is paid from. Must be ACTIVE",
                example = "123e4567-e89b-12d3-a456-426614174000")
        UUID defaultAccountCode
) {
}
