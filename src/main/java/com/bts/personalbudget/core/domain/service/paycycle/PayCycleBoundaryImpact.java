package com.bts.personalbudget.core.domain.service.paycycle;

import java.time.LocalDate;
import java.util.UUID;

public record PayCycleBoundaryImpact(
        UUID previousCycleCode,
        UUID nextCycleCode,
        LocalDate oldBoundary,
        LocalDate newBoundary
) {
}
