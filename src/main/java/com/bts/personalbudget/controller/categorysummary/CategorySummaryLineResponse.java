package com.bts.personalbudget.controller.categorysummary;

import com.bts.personalbudget.core.domain.enumerator.CategoryStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.UUID;

@Schema(description = "Totals of one category in the period.")
public record CategorySummaryLineResponse(

        @Schema(description = "Category code; null for the \"Sem categoria\" row",
                example = "123e4567-e89b-12d3-a456-426614174000", nullable = true)
        UUID code,

        @Schema(description = "Category name", example = "Aluguel")
        String name,

        @Schema(description = "Category status; null for the \"Sem categoria\" row", example = "ACTIVE", nullable = true)
        CategoryStatus status,

        @Schema(description = "Paid in the period (debits negative)", example = "-1800.00")
        BigDecimal total,

        @Schema(description = "Paid plus pending in the period (debits negative)", example = "-1800.00")
        @JsonProperty("projected_total")
        BigDecimal projectedTotal,

        @Schema(description = "Share of the paid total, in percent", example = "42.35")
        BigDecimal percent,

        @Schema(description = "Share of the projected total, in percent", example = "38.10")
        @JsonProperty("projected_percent")
        BigDecimal projectedPercent
) {
}
