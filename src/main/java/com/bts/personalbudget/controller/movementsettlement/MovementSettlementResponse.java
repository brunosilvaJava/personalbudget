package com.bts.personalbudget.controller.movementsettlement;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Schema(description = "A payment settlement; reversed entries remain visible in the history.")
public record MovementSettlementResponse(
        @Schema(description = "Public settlement identifier")
        UUID code,

        @JsonProperty("movement_code")
        @Schema(description = "Code of the financial movement")
        UUID movementCode,

        @Schema(description = "Positive settled amount")
        BigDecimal amount,

        @JsonProperty("effective_date")
        @Schema(description = "Date the payment took effect", type = "string", format = "date")
        LocalDate effectiveDate,

        @JsonProperty("reversed_at")
        @Schema(description = "Date and time the settlement was reversed; null while active")
        LocalDateTime reversedAt,

        @Schema(description = "Optimistic-lock version")
        Long version,

        @JsonProperty("account_code")
        @Schema(description = "Account the money came from or went to")
        UUID accountCode
) {
}
