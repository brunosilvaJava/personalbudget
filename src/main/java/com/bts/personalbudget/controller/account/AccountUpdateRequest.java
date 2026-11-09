package com.bts.personalbudget.controller.account;

import com.bts.personalbudget.core.domain.enumerator.AccountType;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(description = "Request data for updating an account. Fields left out are kept as they are.")
public record AccountUpdateRequest(

        @Size(min = 3, max = 50)
        @Schema(description = "New account name, unique", example = "Nubank conta")
        String name,

        @Schema(description = "New account type", example = "SAVINGS")
        AccountType type,

        @Digits(integer = 10, fraction = 2)
        @JsonProperty("initial_balance")
        @Schema(description = "New initial balance", example = "1500.00")
        BigDecimal initialBalance,

        @JsonProperty("initial_balance_date")
        @Schema(description = "New initial balance date. Cannot be later than the first active settlement of the account",
                example = "2026-11-01", type = "string", format = "date")
        LocalDate initialBalanceDate,

        @JsonProperty("default")
        @Schema(description = "true makes this the default account (the previous default is released). "
                + "false is rejected: choose another account as the default instead", example = "true")
        Boolean makeDefault
) {
}
