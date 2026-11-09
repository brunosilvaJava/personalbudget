package com.bts.personalbudget.controller.account;

import com.bts.personalbudget.core.domain.enumerator.AccountType;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(description = "Request data for creating an account.")
public record AccountRequest(

        @NotBlank
        @Size(min = 3, max = 50)
        @Schema(description = "Account name, unique", example = "Nubank")
        String name,

        @NotNull
        @Schema(description = "Account type", example = "CHECKING")
        AccountType type,

        @Digits(integer = 10, fraction = 2)
        @JsonProperty("initial_balance")
        @Schema(description = "Balance the account already had at initial_balance_date. It is not income. "
                + "Defaults to 0", example = "1500.00")
        BigDecimal initialBalance,

        @JsonProperty("initial_balance_date")
        @Schema(description = "Date the initial balance refers to. Defaults to today",
                example = "2026-11-01", type = "string", format = "date")
        LocalDate initialBalanceDate
) {
}
