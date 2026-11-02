package com.bts.personalbudget.controller.categorysummary;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Schema(description = "Totals of one category group in the period, with its categories.")
public record CategorySummaryGroupResponse(

        @Schema(description = "Group code; null for the \"Sem categoria\" row, which is its own group",
                example = "123e4567-e89b-12d3-a456-426614174000", nullable = true)
        UUID code,

        @Schema(description = "Group name", example = "Residência")
        String name,

        @Schema(description = "Group display order; null for the \"Sem categoria\" row, which comes last",
                example = "1", nullable = true)
        @JsonProperty("display_order")
        Integer displayOrder,

        @Schema(description = "Paid in the period (debits negative)", example = "-2400.00")
        BigDecimal total,

        @Schema(description = "Paid plus pending in the period (debits negative)", example = "-2600.00")
        @JsonProperty("projected_total")
        BigDecimal projectedTotal,

        @Schema(description = "Share of the paid total, in percent", example = "56.47")
        BigDecimal percent,

        @Schema(description = "Share of the projected total, in percent", example = "55.12")
        @JsonProperty("projected_percent")
        BigDecimal projectedPercent,

        @Schema(description = "Categories of the group with movements in the period, largest projected total first")
        List<CategorySummaryLineResponse> categories
) {
}
