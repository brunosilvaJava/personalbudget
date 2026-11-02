package com.bts.personalbudget.core.domain.service.paycycle;

import com.bts.personalbudget.core.domain.enumerator.PayCycleMode;
import java.time.LocalDate;
import java.util.UUID;

/**
 * The single configuration of the financial cycle.
 *
 * @param anchorScheduleCode the anchor income schedule in use; null in CALENDAR_MONTH
 * @param activationDate     start of the first cycle of the current activation; null in CALENDAR_MONTH
 * @param version            optimistic lock version; every write to the cycles states the version it
 *                           read, and a different one is a conflict
 */
public record PayCycleConfig(
        UUID code,
        PayCycleMode mode,
        UUID anchorScheduleCode,
        LocalDate activationDate,
        long version
) {

    public boolean isPayCycle() {
        return mode == PayCycleMode.PAY_CYCLE;
    }
}
