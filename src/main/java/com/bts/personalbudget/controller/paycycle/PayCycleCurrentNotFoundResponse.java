package com.bts.personalbudget.controller.paycycle;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "No cycle contains today, with the pendencies that explain why.")
public record PayCycleCurrentNotFoundResponse(

        @Schema(description = "What happened", example = "Nenhum ciclo em andamento")
        String message,

        @Schema(description = "Why there is no current cycle, when there is a reason")
        List<PayCyclePendingResponse> pending
) {
}
