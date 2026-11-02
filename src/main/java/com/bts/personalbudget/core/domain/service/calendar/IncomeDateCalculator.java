package com.bts.personalbudget.core.domain.service.calendar;

import com.bts.personalbudget.core.domain.service.calendar.IncomeDateResult.BusinessDayNotFound;
import com.bts.personalbudget.core.domain.service.calendar.IncomeDateResult.CalendarNotReviewed;
import com.bts.personalbudget.core.domain.service.calendar.IncomeDateResult.Calculated;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;

/**
 * Pure date arithmetic for income schedules: no persistence, no clock. Given a rule, the nominal
 * month of the occurrence and the holiday calendar, yields the scheduled date. The same inputs
 * always yield the same result.
 *
 * <ul>
 *   <li>FIXED_DAY: the day of the month, using the month's last day when it is shorter (Feb 30 is
 *       Feb 28 or 29), and then the rule's non-business-day policy.</li>
 *   <li>NTH_BUSINESS_DAY: the Nth business day counting from the 1st of the month, so Saturday
 *       counts or not according to the schedule's business week.</li>
 * </ul>
 *
 * A business day is a weekday of the schedule's business week that is not a holiday. Holidays are
 * only consulted for years that were reviewed: a date in an unreviewed year yields
 * {@link CalendarNotReviewed} instead of being assumed a business day. Weekends are known without
 * the calendar, and the KEEP policy never needs it.
 */
public final class IncomeDateCalculator {

    private IncomeDateCalculator() {
    }

    /**
     * @param nominalMonth the month the occurrence belongs to - the identity of the occurrence,
     *                     which the resulting date may leave (see {@link Calculated})
     */
    public static IncomeDateResult calculate(final IncomeDateRule rule,
                                             final YearMonth nominalMonth,
                                             final HolidayCalendar calendar) {
        try {
            return switch (rule.ruleType()) {
                case FIXED_DAY -> new Calculated(fixedDay(rule, nominalMonth, calendar));
                case NTH_BUSINESS_DAY -> nthBusinessDay(rule, nominalMonth, calendar);
            };
        } catch (final UnreviewedYear unreviewedYear) {
            return new CalendarNotReviewed(unreviewedYear.year);
        }
    }

    private static LocalDate fixedDay(final IncomeDateRule rule,
                                      final YearMonth nominalMonth,
                                      final HolidayCalendar calendar) {
        final LocalDate nominal = nominalMonth.atDay(Math.min(rule.value(), nominalMonth.lengthOfMonth()));
        return switch (rule.nonBusinessDayPolicy()) {
            case KEEP -> nominal;
            case ANTICIPATE -> {
                LocalDate date = nominal;
                while (!isBusinessDay(date, rule, calendar)) {
                    date = date.minusDays(1);
                }
                yield date;
            }
            case POSTPONE -> {
                LocalDate date = nominal;
                while (!isBusinessDay(date, rule, calendar)) {
                    date = date.plusDays(1);
                }
                yield date;
            }
        };
    }

    private static IncomeDateResult nthBusinessDay(final IncomeDateRule rule,
                                                   final YearMonth nominalMonth,
                                                   final HolidayCalendar calendar) {
        int businessDays = 0;
        for (int day = 1; day <= nominalMonth.lengthOfMonth(); day++) {
            final LocalDate date = nominalMonth.atDay(day);
            if (isBusinessDay(date, rule, calendar)) {
                businessDays++;
                if (businessDays == rule.value()) {
                    return new Calculated(date);
                }
            }
        }
        return new BusinessDayNotFound(nominalMonth, rule.value());
    }

    private static boolean isBusinessDay(final LocalDate date,
                                         final IncomeDateRule rule,
                                         final HolidayCalendar calendar) {
        final DayOfWeek dayOfWeek = date.getDayOfWeek();
        if (!rule.businessWeek().includes(dayOfWeek)) {
            return false;
        }
        if (!calendar.isReviewed(date.getYear())) {
            throw new UnreviewedYear(date.getYear());
        }
        return !calendar.isHoliday(date);
    }

    /**
     * Internal signal that unwinds the search to {@link #calculate}; never leaves this class.
     */
    private static final class UnreviewedYear extends RuntimeException {
        private static final long serialVersionUID = 1L;

        private final int year;

        private UnreviewedYear(final int year) {
            super(null, null, false, false);
            this.year = year;
        }
    }
}
