package com.bts.personalbudget.controller.category;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import java.util.UUID;

@Schema(description = "Request data for updating a category. Fields left out are kept as they are.")
public record CategoryUpdateRequest(

        @Size(min = 3, max = 50)
        @Schema(description = "New category name, unique per operation type", example = "Aluguel e condomínio")
        String name,

        @Schema(description = "Code of the group to move the category to", example = "123e4567-e89b-12d3-a456-426614174000")
        @JsonProperty("group_code")
        UUID groupCode
) {
}
