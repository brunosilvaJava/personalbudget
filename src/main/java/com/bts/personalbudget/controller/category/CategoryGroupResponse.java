package com.bts.personalbudget.controller.category;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "Response data for a category group.")
public record CategoryGroupResponse(

        @Schema(description = "Unique identifier for the group", example = "123e4567-e89b-12d3-a456-426614174000")
        UUID code,

        @Schema(description = "Group name", example = "Residência")
        String name,

        @Schema(description = "Position of the group in lists and reports", example = "1")
        @JsonProperty("display_order")
        Integer displayOrder
) {
}
