package com.bts.personalbudget.controller.account;

import com.bts.personalbudget.core.domain.enumerator.AccountStatus;
import com.bts.personalbudget.core.domain.enumerator.AccountType;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Schema(description = "Response data for an account.")
public record AccountResponse(

        @Schema(description = "Unique identifier for the account", example = "123e4567-e89b-12d3-a456-426614174000")
        UUID code,

        @Schema(description = "Account name", example = "Nubank")
        String name,

        @Schema(description = "Account type", example = "CHECKING")
        AccountType type,

        @JsonProperty("initial_balance")
        @Schema(description = "Balance at initial_balance_date (not income)", example = "1500.00")
        BigDecimal initialBalance,

        @JsonProperty("initial_balance_date")
        @Schema(description = "Date the initial balance refers to", example = "2026-11-01",
                type = "string", format = "date")
        LocalDate initialBalanceDate,

        @Schema(description = "Account status (ACTIVE or INACTIVE)", example = "ACTIVE")
        AccountStatus status,

        @JsonProperty("default")
        @Schema(description = "True for the account used when a settlement does not inform one", example = "true")
        boolean defaultAccount
) {
}
