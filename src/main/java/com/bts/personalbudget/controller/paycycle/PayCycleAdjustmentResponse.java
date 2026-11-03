package com.bts.personalbudget.controller.paycycle;

import com.bts.personalbudget.core.domain.enumerator.PayCycleAdjustmentType;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Schema(description = "One entry of the pay cycle history: what changed, between which cycles and why.")
public record PayCycleAdjustmentResponse(

        @Schema(description = "Unique identifier of the entry", example = "123e4567-e89b-12d3-a456-426614174000")
        UUID code,

        @Schema(description = "MANUAL_BOUNDARY (a boundary was moved by hand), DEACTIVATION (the cycles were turned "
                + "off), or, in the steps that record them, ANCHOR_TRANSITION and ACTIVATION", example = "MANUAL_BOUNDARY")
        PayCycleAdjustmentType type,

        @Schema(description = "Cycle before the boundary the change touched; for a DEACTIVATION, the last cycle that "
                + "had started. May be null", example = "123e4567-e89b-12d3-a456-426614174000")
        @JsonProperty("previous_cycle_code")
        UUID previousCycleCode,

        @Schema(description = "Cycle after the boundary the change touched; for a DEACTIVATION, the first cycle it "
                + "cancelled. May be null", example = "123e4567-e89b-12d3-a456-426614174001")
        @JsonProperty("next_cycle_code")
        UUID nextCycleCode,

        @Schema(description = "The day the next cycle opened before the change; for a DEACTIVATION, the start of the "
                + "first cancelled cycle", example = "2026-12-07")
        @JsonProperty("old_boundary")
        LocalDate oldBoundary,

        @Schema(description = "The day the next cycle opens after the change; null when it did not move to a date",
                example = "2026-12-04")
        @JsonProperty("new_boundary")
        LocalDate newBoundary,

        @Schema(description = "Why the change was made", example = "Salário caiu antes do feriado")
        String justification,

        @Schema(description = "Version of the previous cycle the caller expected", example = "0")
        @JsonProperty("expected_previous_version")
        Long expectedPreviousVersion,

        @Schema(description = "Version of the next cycle the caller expected", example = "0")
        @JsonProperty("expected_next_version")
        Long expectedNextVersion,

        @Schema(description = "Version of the configuration the caller expected; only for a DEACTIVATION", example = "3")
        @JsonProperty("expected_config_version")
        Long expectedConfigVersion,

        @Schema(description = "When the entry was recorded (UTC)", example = "2026-11-02T21:35:00Z", type = "string",
                format = "date-time")
        @JsonProperty("created_date")
        Instant createdDate
) {
}
