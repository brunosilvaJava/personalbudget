package com.bts.personalbudget.controller.accounttransfer;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Schema(description = "A transfer between accounts; reversed entries remain visible in the history.")
public record AccountTransferResponse(

        @Schema(description = "Public transfer identifier")
        UUID code,

        @JsonProperty("from_account_code")
        @Schema(description = "Account the money left")
        UUID fromAccountCode,

        @JsonProperty("to_account_code")
        @Schema(description = "Account the money entered")
        UUID toAccountCode,

        @Schema(description = "Positive transferred amount", example = "200.00")
        BigDecimal amount,

        @JsonProperty("effective_date")
        @Schema(description = "Date the transfer took effect", type = "string", format = "date")
        LocalDate effectiveDate,

        @Schema(description = "Optional note")
        String description,

        @JsonProperty("reversed_at")
        @Schema(description = "Date and time the transfer was reversed; null while active")
        LocalDateTime reversedAt,

        @Schema(description = "Optimistic-lock version")
        Long version
) {
}
