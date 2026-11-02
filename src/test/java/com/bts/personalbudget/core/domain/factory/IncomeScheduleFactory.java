package com.bts.personalbudget.core.domain.factory;

import static com.bts.personalbudget.core.domain.enumerator.BusinessWeek.MONDAY_TO_FRIDAY;
import static com.bts.personalbudget.core.domain.enumerator.NonBusinessDayPolicy.POSTPONE;

import com.bts.personalbudget.core.domain.enumerator.IncomeScheduleStatus;
import com.bts.personalbudget.core.domain.service.calendar.IncomeDateRule;
import com.bts.personalbudget.core.domain.service.income.IncomeOccurrence;
import com.bts.personalbudget.core.domain.service.income.IncomeSchedule;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

public final class IncomeScheduleFactory {

    public static final UUID SCHEDULE_CODE = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    public static final UUID MOVEMENT_CODE = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    public static final UUID FIXED_BILL_CODE = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    public static final UUID CATEGORY_CODE = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
    public static final BigDecimal AMOUNT = new BigDecimal("5000.00");

    private IncomeScheduleFactory() {
    }

    /**
     * Fixed day 5, postponed to the next business day (Monday to Friday), ACTIVE, from the given
     * month on and with no end.
     */
    public static IncomeSchedule buildSchedule(final YearMonth startMonth) {
        return buildSchedule(IncomeDateRule.fixedDay(5, POSTPONE, MONDAY_TO_FRIDAY), startMonth, null);
    }

    public static IncomeSchedule buildSchedule(final IncomeDateRule rule,
                                               final YearMonth startMonth,
                                               final YearMonth endMonth) {
        return new IncomeSchedule(SCHEDULE_CODE, "Salário", AMOUNT, CATEGORY_CODE, rule, startMonth, endMonth,
                false, IncomeScheduleStatus.ACTIVE, null);
    }

    /**
     * A schedule as it arrives on creation: no code, no status, an untrimmed description.
     */
    public static IncomeSchedule buildNewSchedule(final YearMonth startMonth,
                                                  final boolean anchor,
                                                  final UUID replacesFixedBillCode) {
        return new IncomeSchedule(null, "  Salário ", AMOUNT, CATEGORY_CODE,
                IncomeDateRule.fixedDay(5, POSTPONE, MONDAY_TO_FRIDAY), startMonth, null, anchor, null,
                replacesFixedBillCode);
    }

    public static IncomeSchedule buildInactive(final IncomeSchedule schedule) {
        return new IncomeSchedule(schedule.code(), schedule.description(), schedule.amount(),
                schedule.categoryCode(), schedule.rule(), schedule.startMonth(), schedule.endMonth(),
                schedule.anchor(), IncomeScheduleStatus.INACTIVE, schedule.replacesFixedBillCode());
    }

    public static IncomeOccurrence buildOccurrence(final YearMonth nominalMonth, final LocalDate plannedDate) {
        return new IncomeOccurrence(null, SCHEDULE_CODE, nominalMonth, plannedDate, AMOUNT, MOVEMENT_CODE);
    }
}
