package com.bts.personalbudget.controller.category;

import com.bts.personalbudget.core.domain.enumerator.OperationType;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

@Schema(description = "Request data for creating a category.")
public record CategoryRequest(

        @NotBlank
        @Size(min = 3, max = 50)
        @Schema(description = "Category name, unique per operation type", example = "Aluguel")
        String name,

        @NotNull
        @Schema(description = "Type of operation the category applies to (DEBIT or CREDIT). Cannot be changed later", example = "DEBIT")
        @JsonProperty("operation_type")
        OperationType operationType,

        @NotNull
        @Schema(description = "Code of the group the category belongs to", example = "123e4567-e89b-12d3-a456-426614174000")
        @JsonProperty("group_code")
        UUID groupCode
) {
}
