package com.bts.personalbudget.core.domain.service.paycycle;

import java.util.List;

public record PayCycleAnchorTransitionResult(
        PayCycleConfigView configuration,
        PayCycleAdjustment adjustment,
        List<PayCycleView> preservedCycles,
        List<PayCycleView> cancelledCycles,
        List<PayCycleView> newCycles
) {
}
