package com.bts.personalbudget.core.domain.service.paycycle;

import com.bts.personalbudget.core.domain.enumerator.PayCycleTransitionShape;
import java.time.LocalDate;
import java.util.List;

/**
 * The replacement chain at the last frozen boundary, before any persistence takes place.
 */
public record PayCycleAnchorTransitionPlan(
        LocalDate boundary,
        LocalDate oldBoundary,
        LocalDate newBoundary,
        PayCycleTransitionShape shape,
        List<PayCycle> preserved,
        List<PayCycle> cancelled,
        List<PayCycle> created,
        List<PayCyclePending> pending,
        boolean transitionShapeRequired
) {

    public boolean isCalculable() {
        return !created.isEmpty() && pending.stream()
                .noneMatch(pending -> pending.type() == PayCyclePending.Type.CALENDAR_NOT_REVIEWED
                        || pending.type() == PayCyclePending.Type.BUSINESS_DAY_NOT_FOUND
                        || pending.type() == PayCyclePending.Type.ANCHOR_SCHEDULE_ENDS);
    }
}
