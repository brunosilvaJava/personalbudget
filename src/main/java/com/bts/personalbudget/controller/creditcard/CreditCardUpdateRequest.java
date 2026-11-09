package com.bts.personalbudget.controller.creditcard;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

@Schema(description = "Request data for updating a credit card. Fields left out are kept as they are.")
public record CreditCardUpdateRequest(

        @Size(min = 3, max = 50)
        @Schema(description = "New card name, unique", example = "Nubank")
        String name,

        @Min(1)
        @Max(31)
        @JsonProperty("closing_day")
        @Schema(description = "New closing day. Only applies to purchases made afterwards", example = "20")
        Integer closingDay,

        @Min(1)
        @Max(31)
        @JsonProperty("due_day")
        @Schema(description = "New due day. Only applies to purchases made afterwards", example = "28")
        Integer dueDay,

        @DecimalMin(value = "0.00", inclusive = false)
        @Digits(integer = 10, fraction = 2)
        @JsonProperty("credit_limit")
        @Schema(description = "New credit limit. It can be replaced but not cleared", example = "8000.00")
        BigDecimal creditLimit,

        @JsonProperty("default_account_code")
        @Schema(description = "New default payment account. Must be ACTIVE. It can be replaced but not cleared",
                example = "123e4567-e89b-12d3-a456-426614174000")
        UUID defaultAccountCode
) {
}
