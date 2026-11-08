package com.bts.personalbudget.core.domain.service.paycycle;

import com.bts.personalbudget.core.domain.enumerator.PayCycleKind;
import com.bts.personalbudget.core.domain.enumerator.PayCycleLifecycle;
import com.bts.personalbudget.core.domain.enumerator.PayCycleTransitionShape;
import com.bts.personalbudget.core.domain.service.calendar.HolidayCalendar;
import com.bts.personalbudget.core.domain.service.income.IncomeOccurrence;
import com.bts.personalbudget.core.domain.service.income.IncomeOccurrenceResolver;
import com.bts.personalbudget.core.domain.service.income.IncomeOccurrenceView;
import com.bts.personalbudget.core.domain.service.income.IncomeSchedule;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Pure planning for an anchor change. Existing cycles up to the frozen boundary are kept; the
 * remaining chain is replaced from that boundary without making a date up when its calendar is
 * unknown.
 */
public final class PayCycleAnchorTransitionPlanner {

    private static final int SCAN_MONTHS_AFTER_BOUNDARY = 8;
    private static final int REGULAR_CYCLES_AFTER_BOUNDARY = PayCyclePlanner.FUTURE_CYCLES + 1;

    private PayCycleAnchorTransitionPlanner() {
    }

    public static PayCycleAnchorTransitionPlan plan(
            final LocalDate boundary,
            final List<PayCycle> existing,
            final IncomeSchedule previousAnchor,
            final IncomeSchedule newAnchor,
            final Collection<IncomeOccurrence> materialized,
            final HolidayCalendar calendar,
            final LocalDate today,
            final boolean sameSchedule,
            final PayCycleTransitionShape requestedShape) {
        final List<PayCycle> preserved = existing.stream()
                .filter(cycle -> !cycle.endDate().isAfter(boundary))
                .toList();
        final List<PayCycle> cancelled = existing.stream()
                .filter(cycle -> !cycle.startDate().isBefore(boundary))
                .map(PayCycle::cancelled)
                .toList();

        if (!existing.isEmpty()) {
            final PayCycle lastPreserved = preserved.isEmpty() ? null : preserved.get(preserved.size() - 1);
            final boolean boundaryExists = lastPreserved != null && lastPreserved.endDate().equals(boundary)
                    || existing.stream().anyMatch(cycle -> cycle.startDate().equals(boundary));
            if (!boundaryExists) {
                return blocked(boundary, preserved, List.of(),
                        PayCyclePending.boundaryDiverges(null, YearMonth.from(boundary), boundary, boundary));
            }
        }

        final List<OccurrenceDate> targetDates = occurrenceDates(
                newAnchor, materialized, calendar, boundary, false);
        final Optional<PayCyclePending> targetBlocker = firstBlocker(newAnchor, materialized, calendar, boundary);
        final List<OccurrenceDate> dates = strictlyIncreasing(targetDates).stream()
                .filter(date -> targetBlocker.isEmpty()
                        || date.month().isBefore(targetBlocker.get().nominalMonth()))
                .toList();
        if (dates.isEmpty()) {
            return blocked(boundary, preserved, List.of(), targetBlocker.orElseGet(
                    () -> PayCyclePending.anchorScheduleEnds(YearMonth.from(boundary))));
        }

        final OccurrenceDate first = dates.get(0);
        final boolean startsAtBoundary = first.date().equals(boundary);
        PayCycleTransitionShape shape = null;
        if (!startsAtBoundary) {
            if (sameSchedule) {
                final Optional<LocalDate> oldNext = existing.stream()
                        .filter(cycle -> cycle.startDate().equals(boundary))
                        .map(PayCycle::endDate)
                        .findFirst()
                        .or(() -> occurrenceDates(previousAnchor, materialized, calendar, boundary, true).stream()
                                .map(OccurrenceDate::date)
                                .findFirst());
                if (oldNext.isEmpty() || first.date().isAfter(oldNext.get())) {
                    shape = PayCycleTransitionShape.LONG;
                } else if (first.date().isBefore(oldNext.get())) {
                    shape = PayCycleTransitionShape.SHORT;
                }
            } else {
                shape = requestedShape;
                if (shape == null) {
                    return shapeRequired(boundary, preserved, cancelled);
                }
            }
        }

        final int needed = shape == PayCycleTransitionShape.LONG
                ? REGULAR_CYCLES_AFTER_BOUNDARY + 2
                : shape == PayCycleTransitionShape.SHORT
                        ? REGULAR_CYCLES_AFTER_BOUNDARY + 1
                        : REGULAR_CYCLES_AFTER_BOUNDARY + 1;
        if (dates.size() < needed) {
            return blocked(boundary, preserved, List.of(), targetBlocker.orElseGet(
                    () -> PayCyclePending.anchorScheduleEnds(YearMonth.from(boundary))));
        }

        final PayCycle oldNext = existing.stream()
                .filter(cycle -> cycle.startDate().equals(boundary))
                .findFirst()
                .orElse(null);
        final List<PayCycle> created = buildCycles(boundary, dates, shape, newAnchor, oldNext);
        return new PayCycleAnchorTransitionPlan(
                boundary,
                oldNext == null ? null : oldNext.endDate(),
                created.get(0).endDate(),
                shape,
                preserved,
                cancelled,
                created,
                List.of(),
                false);
    }

    private static List<PayCycle> buildCycles(
            final LocalDate boundary,
            final List<OccurrenceDate> dates,
            final PayCycleTransitionShape shape,
            final IncomeSchedule anchor,
            final PayCycle oldNext) {
        final List<PayCycle> cycles = new ArrayList<>();
        int nextIndex;
        if (shape == PayCycleTransitionShape.SHORT) {
            cycles.add(new PayCycle(null, boundary, dates.get(0).date(), PayCycleKind.TRANSITION,
                    null, null, PayCycleLifecycle.ACTIVE, false, false, null));
            nextIndex = 0;
        } else if (shape == PayCycleTransitionShape.LONG) {
            cycles.add(new PayCycle(null, boundary, dates.get(1).date(), PayCycleKind.TRANSITION,
                    anchor.code(), dates.get(0).month(), PayCycleLifecycle.ACTIVE, false, false, null));
            nextIndex = 1;
        } else {
            if (dates.get(0).date().isAfter(boundary)) {
                cycles.add(new PayCycle(null, boundary, dates.get(0).date(), PayCycleKind.REGULAR,
                        oldNext.anchorScheduleCode(), oldNext.anchorNominalMonth(),
                        PayCycleLifecycle.ACTIVE, oldNext.startAdjusted(), false, null));
            }
            nextIndex = 0;
        }

        for (int i = 0; i < REGULAR_CYCLES_AFTER_BOUNDARY; i++) {
            final OccurrenceDate start = dates.get(nextIndex + i);
            final OccurrenceDate end = dates.get(nextIndex + i + 1);
            cycles.add(new PayCycle(null, start.date(), end.date(), PayCycleKind.REGULAR,
                    anchor.code(), start.month(), PayCycleLifecycle.ACTIVE, false, false, null));
        }
        return List.copyOf(cycles);
    }

    private static List<OccurrenceDate> strictlyIncreasing(final List<OccurrenceDate> dates) {
        final List<OccurrenceDate> result = new ArrayList<>();
        LocalDate previous = null;
        for (final OccurrenceDate date : dates) {
            if (previous != null && previous.equals(date.date())) {
                return List.of();
            }
            if (previous == null || date.date().isAfter(previous)) {
                result.add(date);
                previous = date.date();
            }
        }
        return List.copyOf(result);
    }

    private static List<OccurrenceDate> occurrenceDates(
            final IncomeSchedule schedule,
            final Collection<IncomeOccurrence> materialized,
            final HolidayCalendar calendar,
            final LocalDate boundary,
            final boolean strictlyAfter) {
        final YearMonth from = IncomeOccurrenceResolver.firstRelevantNominalMonth(
                schedule, materialized, YearMonth.from(boundary));
        final YearMonth to = YearMonth.from(boundary).plusMonths(SCAN_MONTHS_AFTER_BOUNDARY);
        return IncomeOccurrenceResolver.resolve(schedule, materialized, calendar, from, to).stream()
                .filter(view -> view.state() == IncomeOccurrenceView.State.MATERIALIZED
                        || view.state() == IncomeOccurrenceView.State.PROJECTED)
                .filter(view -> view.plannedDate() != null)
                .filter(view -> strictlyAfter
                        ? view.plannedDate().isAfter(boundary)
                        : !view.plannedDate().isBefore(boundary))
                .map(view -> new OccurrenceDate(view.nominalMonth(), view.plannedDate()))
                .sorted(Comparator.comparing(OccurrenceDate::date).thenComparing(OccurrenceDate::month))
                .toList();
    }

    private static Optional<PayCyclePending> firstBlocker(
            final IncomeSchedule schedule,
            final Collection<IncomeOccurrence> materialized,
            final HolidayCalendar calendar,
            final LocalDate boundary) {
        final YearMonth from = IncomeOccurrenceResolver.firstRelevantNominalMonth(
                schedule, materialized, YearMonth.from(boundary));
        final YearMonth to = YearMonth.from(boundary).plusMonths(SCAN_MONTHS_AFTER_BOUNDARY);
        return IncomeOccurrenceResolver.resolve(schedule, materialized, calendar, from, to).stream()
                .filter(view -> view.state() == IncomeOccurrenceView.State.CALENDAR_NOT_REVIEWED
                        || view.state() == IncomeOccurrenceView.State.BUSINESS_DAY_NOT_FOUND)
                .map(view -> view.state() == IncomeOccurrenceView.State.CALENDAR_NOT_REVIEWED
                        ? PayCyclePending.calendarNotReviewed(view.nominalMonth(), view.pendingYear())
                        : PayCyclePending.businessDayNotFound(view.nominalMonth()))
                .findFirst();
    }

    private static PayCycleAnchorTransitionPlan blocked(
            final LocalDate boundary,
            final List<PayCycle> preserved,
            final List<PayCycle> cancelled,
            final PayCyclePending pending) {
        return new PayCycleAnchorTransitionPlan(
                boundary, null, null, null, preserved, cancelled, List.of(), List.of(pending), false);
    }

    private static PayCycleAnchorTransitionPlan shapeRequired(
            final LocalDate boundary,
            final List<PayCycle> preserved,
            final List<PayCycle> cancelled) {
        return new PayCycleAnchorTransitionPlan(
                boundary, null, null, null, preserved, cancelled, List.of(), List.of(), true);
    }

    private record OccurrenceDate(YearMonth month, LocalDate date) {
    }
}
