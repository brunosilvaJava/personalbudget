package com.bts.personalbudget.core.domain.service.paycycle;

import com.bts.personalbudget.core.domain.enumerator.PayCycleTransitionShape;
import com.bts.personalbudget.core.domain.service.income.IncomeScheduleUpdate;
import java.util.UUID;

public record PayCycleAnchorChange(
        UUID anchorScheduleCode,
        IncomeScheduleUpdate scheduleUpdate,
        PayCycleTransitionShape transitionShape,
        String justification,
        Long expectedVersion
) {
}
