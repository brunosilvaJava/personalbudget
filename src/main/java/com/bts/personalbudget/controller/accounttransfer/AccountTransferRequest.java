package com.bts.personalbudget.controller.accounttransfer;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Schema(description = "Data for transferring money between two accounts.")
public record AccountTransferRequest(

        @NotNull
        @JsonProperty("from_account_code")
        @Schema(description = "Account the money leaves. Must be ACTIVE", example = "123e4567-e89b-12d3-a456-426614174000")
        UUID fromAccountCode,

        @NotNull
        @JsonProperty("to_account_code")
        @Schema(description = "Account the money enters. Must be ACTIVE and different from the origin",
                example = "123e4567-e89b-12d3-a456-426614174001")
        UUID toAccountCode,

        @NotNull
        @DecimalMin(value = "0.00", inclusive = false)
        @Digits(integer = 8, fraction = 2)
        @Schema(description = "Positive transferred amount", example = "200.00")
        BigDecimal amount,

        @NotNull
        @JsonProperty("effective_date")
        @Schema(description = "Date the transfer took effect. Not before the initial balance date of either account",
                example = "2026-11-08", type = "string", format = "date")
        LocalDate effectiveDate,

        @Size(max = 100)
        @Schema(description = "Optional note", example = "Reserva do mês")
        String description
) {
}
