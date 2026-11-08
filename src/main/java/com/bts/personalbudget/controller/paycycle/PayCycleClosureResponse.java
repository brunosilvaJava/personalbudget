package com.bts.personalbudget.controller.paycycle;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Ciclo fechado e snapshot imutável criado na mesma transação")
public record PayCycleClosureResponse(PayCycleResponse cycle, PayCycleSnapshotResponse snapshot) {
}
