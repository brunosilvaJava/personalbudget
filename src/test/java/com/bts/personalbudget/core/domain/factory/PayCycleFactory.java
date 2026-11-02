package com.bts.personalbudget.core.domain.factory;

import static com.bts.personalbudget.core.domain.factory.IncomeScheduleFactory.SCHEDULE_CODE;

import com.bts.personalbudget.core.domain.enumerator.PayCycleKind;
import com.bts.personalbudget.core.domain.enumerator.PayCycleLifecycle;
import com.bts.personalbudget.core.domain.enumerator.PayCycleMode;
import com.bts.personalbudget.core.domain.service.paycycle.PayCycle;
import com.bts.personalbudget.core.domain.service.paycycle.PayCycleConfig;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

public final class PayCycleFactory {

    public static final UUID CONFIG_CODE = UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee");

    private PayCycleFactory() {
    }

    /**
     * A REGULAR cycle opened by the occurrence of {@code anchorMonth} of the factory's schedule.
     */
    public static PayCycle buildCycle(final LocalDate start, final LocalDate end, final YearMonth anchorMonth) {
        return new PayCycle(null, start, end, PayCycleKind.REGULAR, SCHEDULE_CODE, anchorMonth,
                PayCycleLifecycle.ACTIVE, false, false, 0L);
    }

    /**
     * A TRANSITION no anchor occurrence opens: no schedule, no reference month.
     */
    public static PayCycle buildTransition(final LocalDate start, final LocalDate end) {
        return new PayCycle(null, start, end, PayCycleKind.TRANSITION, null, null,
                PayCycleLifecycle.ACTIVE, false, false, 0L);
    }

    public static PayCycle buildEndAdjusted(final PayCycle cycle, final LocalDate newEnd) {
        return new PayCycle(cycle.code(), cycle.startDate(), newEnd, cycle.kind(), cycle.anchorScheduleCode(),
                cycle.anchorNominalMonth(), cycle.lifecycle(), cycle.startAdjusted(), true, cycle.version());
    }

    public static PayCycle buildStartAdjusted(final PayCycle cycle, final LocalDate newStart) {
        return new PayCycle(cycle.code(), newStart, cycle.endDate(), cycle.kind(), cycle.anchorScheduleCode(),
                cycle.anchorNominalMonth(), cycle.lifecycle(), true, cycle.endAdjusted(), cycle.version());
    }

    public static PayCycleConfig buildCalendarMonthConfig() {
        return new PayCycleConfig(CONFIG_CODE, PayCycleMode.CALENDAR_MONTH, null, null, 0L);
    }

    public static PayCycleConfig buildPayCycleConfig(final LocalDate activationDate, final long version) {
        return new PayCycleConfig(CONFIG_CODE, PayCycleMode.PAY_CYCLE, SCHEDULE_CODE, activationDate, version);
    }
}
