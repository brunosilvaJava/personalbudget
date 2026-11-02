package com.bts.personalbudget.controller.categorysummary;

import com.bts.personalbudget.core.domain.enumerator.OperationType;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Schema(description = "Totals of one operation type in a period, by group and category.")
public record CategorySummaryResponse(

        @Schema(description = "First day of the period", example = "2026-11-01", type = "string", format = "date")
        @JsonProperty("start_date")
        LocalDate startDate,

        @Schema(description = "Last day of the period", example = "2026-11-30", type = "string", format = "date")
        @JsonProperty("end_date")
        LocalDate endDate,

        @Schema(description = "Operation type summarized (DEBIT or CREDIT)", example = "DEBIT")
        @JsonProperty("operation_type")
        OperationType operationType,

        @Schema(description = "Paid in the period (debits negative)", example = "-4250.00")
        BigDecimal total,

        @Schema(description = "Paid plus pending in the period (debits negative)", example = "-4720.00")
        @JsonProperty("projected_total")
        BigDecimal projectedTotal,

        @Schema(description = "Groups with movements in the period, in display order; \"Sem categoria\" comes last")
        List<CategorySummaryGroupResponse> groups
) {
}
