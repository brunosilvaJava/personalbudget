package com.bts.personalbudget.core.domain.service.paycycle;

import com.bts.personalbudget.core.domain.enumerator.PayCycleTransitionShape;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record PayCycleAnchorTransitionPreview(
        long expectedVersion,
        UUID previousAnchorScheduleCode,
        UUID anchorScheduleCode,
        LocalDate boundary,
        LocalDate oldBoundary,
        LocalDate newBoundary,
        PayCycleTransitionShape transitionShape,
        List<PayCycleView> preservedCycles,
        List<PayCycleView> cancelledCycles,
        List<PayCycleView> newCycles,
        List<PayCyclePending> pending
) {
}
