package com.bts.personalbudget.controller.paycycle;

import com.bts.personalbudget.core.domain.enumerator.OperationType;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(description = "Snapshot imutável do fechamento de um ciclo")
public record PayCycleSnapshotResponse(
        UUID code,
        @JsonProperty("cycle_code") UUID cycleCode,
        @JsonProperty("snapshot_version") int snapshotVersion,
        @JsonProperty("cycle_version") long cycleVersion,
        @JsonProperty("closed_at") Instant closedAt,
        @JsonProperty("recognized_revenue") BigDecimal recognizedRevenue,
        @JsonProperty("recognized_expense") BigDecimal recognizedExpense,
        @JsonProperty("projected_revenue") BigDecimal projectedRevenue,
        @JsonProperty("projected_expense") BigDecimal projectedExpense,
        List<Category> categories
) {
    @Schema(description = "Categoria como foi nomeada no instante do fechamento")
    public record Category(
            @JsonProperty("category_code") UUID categoryCode,
            @JsonProperty("category_name") String categoryName,
            @JsonProperty("operation_type") OperationType operationType,
            @JsonProperty("recognized_amount") BigDecimal recognizedAmount,
            @JsonProperty("projected_amount") BigDecimal projectedAmount
    ) {
    }
}
