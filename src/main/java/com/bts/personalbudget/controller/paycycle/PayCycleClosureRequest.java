package com.bts.personalbudget.controller.paycycle;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Confirma o fechamento usando a versão retornada pela prévia")
public record PayCycleClosureRequest(
        @NotNull
        @JsonProperty("expected_version")
        @Schema(description = "Versão do ciclo recebida na prévia", example = "3")
        Long expectedVersion
) {
}
