package com.bts.personalbudget.core.domain.service.income;

import com.bts.personalbudget.core.domain.service.calendar.HolidayCalendar;
import com.bts.personalbudget.core.domain.service.calendar.IncomeDateCalculator;
import com.bts.personalbudget.core.domain.service.calendar.IncomeDateResult;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Pure: decides, month by month, what a schedule has to say about its occurrences. The posting job,
 * the balance projection, the occurrence listing and the calendar-change preview all read the same
 * answer from here, so they cannot disagree about whether an occurrence exists or on which date.
 *
 * <p>A month with a materialized occurrence is always reported as materialized, with the frozen date
 * and amount. Any other month in the schedule's validity is calculated from the rule and the
 * calendar - only for an ACTIVE schedule: an inactive one reports what it already materialized and
 * nothing else.
 */
public final class IncomeOccurrenceResolver {

    private IncomeOccurrenceResolver() {
    }

    /**
     * @param from first nominal month to report, inclusive
     * @param to   last nominal month to report, inclusive; nothing is reported when it is before
     *             {@code from}
     * @return one view per reported month, in month order
     */
    public static List<IncomeOccurrenceView> resolve(final IncomeSchedule schedule,
                                                     final Collection<IncomeOccurrence> materialized,
                                                     final HolidayCalendar calendar,
                                                     final YearMonth from,
                                                     final YearMonth to) {
        final Map<YearMonth, IncomeOccurrence> materializedByMonth = new HashMap<>();
        materialized.forEach(occurrence -> materializedByMonth.put(occurrence.nominalMonth(), occurrence));

        final List<IncomeOccurrenceView> views = new ArrayList<>();
        for (YearMonth month = from; !month.isAfter(to); month = month.plusMonths(1)) {
            final IncomeOccurrence occurrence = materializedByMonth.get(month);
            if (occurrence != null) {
                views.add(IncomeOccurrenceView.materialized(occurrence));
            } else if (schedule.generates(month)) {
                views.add(calculate(schedule, month, calendar));
            }
        }
        return views;
    }

    /**
     * The months whose view would change if {@code proposed} replaced {@code current} as the
     * calendar. Materialized months never appear: they do not depend on the calendar.
     *
     * @return pairs of (before, after), one per changed month, in month order
     */
    public static List<IncomeOccurrenceChange> findChanges(final IncomeSchedule schedule,
                                                           final Collection<IncomeOccurrence> materialized,
                                                           final HolidayCalendar current,
                                                           final HolidayCalendar proposed,
                                                           final YearMonth from,
                                                           final YearMonth to) {
        final List<IncomeOccurrenceView> before = resolve(schedule, materialized, current, from, to);
        final List<IncomeOccurrenceView> after = resolve(schedule, materialized, proposed, from, to);
        final List<IncomeOccurrenceChange> changes = new ArrayList<>();
        for (int i = 0; i < before.size(); i++) {
            if (!Objects.equals(before.get(i), after.get(i))) {
                changes.add(new IncomeOccurrenceChange(before.get(i), after.get(i)));
            }
        }
        return changes;
    }

    private static IncomeOccurrenceView calculate(final IncomeSchedule schedule,
                                                  final YearMonth month,
                                                  final HolidayCalendar calendar) {
        return switch (IncomeDateCalculator.calculate(schedule.rule(), month, calendar)) {
            case IncomeDateResult.Calculated calculated ->
                    IncomeOccurrenceView.projected(month, calculated.date(), schedule.amount());
            case IncomeDateResult.CalendarNotReviewed notReviewed ->
                    IncomeOccurrenceView.calendarNotReviewed(month, notReviewed.year());
            case IncomeDateResult.BusinessDayNotFound notFound ->
                    IncomeOccurrenceView.businessDayNotFound(month);
        };
    }

    /**
     * @param before the month with the current calendar
     * @param after  the same month with the proposed calendar
     */
    public record IncomeOccurrenceChange(IncomeOccurrenceView before, IncomeOccurrenceView after) {
    }
}
