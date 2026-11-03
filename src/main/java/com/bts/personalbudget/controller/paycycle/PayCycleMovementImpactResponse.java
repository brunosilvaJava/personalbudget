package com.bts.personalbudget.controller.paycycle;

import com.bts.personalbudget.core.domain.enumerator.FinancialMovementStatus;
import com.bts.personalbudget.core.domain.enumerator.OperationType;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Schema(description = "A financial movement whose competence date would change from one pay cycle to the other.")
public record PayCycleMovementImpactResponse(

        @Schema(description = "Unique identifier of the movement", example = "123e4567-e89b-12d3-a456-426614174000")
        UUID code,

        @Schema(description = "Description of the movement", example = "Aluguel")
        String description,

        @Schema(description = "DEBIT or CREDIT", example = "DEBIT")
        @JsonProperty("operation_type")
        OperationType operationType,

        @Schema(description = "Amount of the movement, always positive; the operation type says the direction",
                example = "1500.00")
        BigDecimal amount,

        @Schema(description = "PENDING, LATE or PAID_OUT", example = "PENDING")
        FinancialMovementStatus status,

        @Schema(description = "Competence date: the date that decides which cycle the movement belongs to; it does "
                + "not change, only the cycle that contains it does", example = "2026-12-05")
        @JsonProperty("competence_date")
        LocalDate competenceDate,

        @Schema(description = "Code of the category, if any", example = "123e4567-e89b-12d3-a456-426614174000")
        @JsonProperty("category_code")
        UUID categoryCode
) {
}
