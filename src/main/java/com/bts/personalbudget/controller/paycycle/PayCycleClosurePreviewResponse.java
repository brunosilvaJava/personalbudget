package com.bts.personalbudget.controller.paycycle;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

@Schema(description = "Prévia do fechamento e os motivos que o impedem, se houver")
public record PayCycleClosurePreviewResponse(
        @Schema(description = "Resumo por competência que será congelado no snapshot")
        PayCycleSummaryResponse summary,
        @JsonProperty("expected_version")
        @Schema(description = "Versão do ciclo a informar no fechamento", example = "3")
        Long expectedVersion,
        @JsonProperty("can_close")
        @Schema(description = "Indica se o ciclo pode ser fechado agora", example = "true")
        boolean canClose,
        @Schema(description = "PROJECTION_INCOMPLETE, LATE_MOVEMENTS ou CYCLE_NOT_ENDED")
        List<String> blockers,
        @JsonProperty("late_movement_codes")
        @Schema(description = "Códigos de movimentações atrasadas que impedem o fechamento")
        List<UUID> lateMovementCodes
) {
}
