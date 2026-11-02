package com.bts.personalbudget.controller.category;

import com.bts.personalbudget.core.domain.enumerator.CategoryStatus;
import com.bts.personalbudget.core.domain.enumerator.OperationType;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "Response data for a category.")
public record CategoryResponse(

        @Schema(description = "Unique identifier for the category", example = "123e4567-e89b-12d3-a456-426614174000")
        UUID code,

        @Schema(description = "Category name", example = "Aluguel")
        String name,

        @Schema(description = "Type of operation the category applies to (DEBIT or CREDIT)", example = "DEBIT")
        @JsonProperty("operation_type")
        OperationType operationType,

        @Schema(description = "Category status (ACTIVE or INACTIVE)", example = "ACTIVE")
        CategoryStatus status,

        @Schema(description = "Code of the group the category belongs to", example = "123e4567-e89b-12d3-a456-426614174000")
        @JsonProperty("group_code")
        UUID groupCode,

        @Schema(description = "Name of the group the category belongs to", example = "Residência")
        @JsonProperty("group_name")
        String groupName
) {
}
