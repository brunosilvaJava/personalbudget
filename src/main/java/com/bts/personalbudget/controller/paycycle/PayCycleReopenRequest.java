package com.bts.personalbudget.controller.paycycle;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Confirma a reabertura usando a versão atual do ciclo")
public record PayCycleReopenRequest(
        @NotNull
        @JsonProperty("expected_version")
        @Schema(description = "Versão do ciclo fechado", example = "4")
        Long expectedVersion
) {
}
