package com.bts.personalbudget.controller.financialmovement;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Paginated response for financial movements")
public record PagedFinancialMovementResponse(
        @Schema(description = "List of financial movements")
        List<FinancialMovementResponse> content,

        @Schema(description = "Current page number (0-indexed)", example = "0")
        int page,

        @Schema(description = "Number of items per page", example = "10")
        int size,

        @Schema(description = "Total number of items", example = "100")
        long totalElements,

        @Schema(description = "Total number of pages", example = "10")
        int totalPages,

        @Schema(description = "Whether this is the first page", example = "true")
        boolean first,

        @Schema(description = "Whether this is the last page", example = "false")
        boolean last
) {
}