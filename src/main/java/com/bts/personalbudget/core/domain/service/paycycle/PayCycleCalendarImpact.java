package com.bts.personalbudget.core.domain.service.paycycle;

import java.util.List;

public record PayCycleCalendarImpact(
        List<PayCycleBoundaryImpact> boundaries,
        List<PayCyclePending> divergences
) {
}
