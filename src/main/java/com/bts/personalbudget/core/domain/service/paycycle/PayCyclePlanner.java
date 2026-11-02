package com.bts.personalbudget.core.domain.service.paycycle;

import com.bts.personalbudget.core.domain.enumerator.DateRuleType;
import com.bts.personalbudget.core.domain.enumerator.PayCycleKind;
import com.bts.personalbudget.core.domain.enumerator.PayCycleLifecycle;
import com.bts.personalbudget.core.domain.service.calendar.HolidayCalendar;
import com.bts.personalbudget.core.domain.service.income.IncomeOccurrence;
import com.bts.personalbudget.core.domain.service.income.IncomeOccurrenceResolver;
import com.bts.personalbudget.core.domain.service.income.IncomeOccurrenceView;
import com.bts.personalbudget.core.domain.service.income.IncomeSchedule;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Pure: builds and maintains the chain of pay cycles from the dates of the anchor income schedule.
 * Nothing here reads the database or the clock; the caller hands over the schedule, what it already
 * materialized, the holiday calendar, the cycles that exist and "today".
 *
 * <p>The rules (see the E4 plan, sections 4 and 5):
 * <ul>
 *   <li>The boundary between a cycle and the next one is the planned date of the anchor occurrence that
 *       opens the next one: the frozen date when the occurrence was materialized, otherwise the date
 *       the resolver calculates now. A receipt that arrives early moves no boundary.</li>
 *   <li>A boundary is frozen once the cycle before it started, or when it was adjusted by hand. A
 *       frozen boundary is never moved by itself: when it stops matching the anchor date that becomes a
 *       {@link PayCyclePending.Type#BOUNDARY_DIVERGES_FROM_ANCHOR} pendency.</li>
 *   <li>A boundary that is not frozen follows the anchor date, but never to today or to the past.</li>
 *   <li>A cycle is only created when its start and its end are both known. A date that cannot be
 *       calculated stops the chain there and becomes a pendency; nothing is invented.</li>
 *   <li>The chain is kept up to the current cycle plus {@value #FUTURE_CYCLES} more.</li>
 *   <li>Planning twice with the same data changes nothing: the second call returns the same cycles,
 *       the very same objects for the ones that did not change.</li>
 * </ul>
 */
public final class PayCyclePlanner {

    /**
     * How many cycles are kept after the current one (or after the first, when the first has not
     * started yet).
     */
    public static final int FUTURE_CYCLES = 2;

    /**
     * An anchor date can sit in the month before or after its nominal month (anticipation and
     * postponement), so the months around a date are looked at, not only its own.
     */
    private static final int SCAN_MONTHS_BEFORE = 1;
    private static final int SCAN_MONTHS_AFTER = 2;

    private PayCyclePlanner() {
    }

    /**
     * @param anchor         the anchor income schedule
     * @param materialized   the occurrences of that schedule that already have a movement
     * @param calendar       the holiday calendar
     * @param activationDate start of the first cycle; only used when {@code existing} is empty
     * @param existing       the ACTIVE cycles of the current activation, ordered by start date and
     *                       contiguous; empty to plan a fresh activation
     * @param today          the current date
     */
    public record Request(
            IncomeSchedule anchor,
            Collection<IncomeOccurrence> materialized,
            HolidayCalendar calendar,
            LocalDate activationDate,
            List<PayCycle> existing,
            LocalDate today
    ) {
    }

    public static PayCyclePlan plan(final Request request) {
        final AnchorDates dates = new AnchorDates(request.anchor(), request.materialized(), request.calendar());
        final Set<PayCyclePending> pending = new LinkedHashSet<>();
        final List<PayCycle> chain = new ArrayList<>();

        if (request.existing().isEmpty()) {
            final Optional<PayCycle> first = firstCycle(dates, request.activationDate(), pending);
            if (first.isEmpty()) {
                return new PayCyclePlan(List.of(), List.copyOf(pending));
            }
            chain.add(first.get());
        } else {
            requireContiguous(request.existing());
            chain.addAll(recalculate(request.existing(), dates, request.today(), pending));
        }

        extend(chain, dates, request.today(), pending);
        reportAnchorMismatches(chain, dates, pending);
        if (!request.today().isBefore(chain.get(0).startDate())
                && chain.stream().noneMatch(cycle -> cycle.contains(request.today()))) {
            pending.add(PayCyclePending.noCurrentCycle());
        }
        return new PayCyclePlan(chain, List.copyOf(pending));
    }

    /**
     * The first cycle of an activation. When {@code initial} is the date of an anchor occurrence the
     * cycle is REGULAR and that occurrence is its reference; otherwise it is a TRANSITION with no
     * reference, from {@code initial} up to the next anchor date. Nothing before {@code initial} is
     * reconstructed.
     *
     * @return empty (with the reason in {@code pending}) when the end cannot be calculated
     */
    private static Optional<PayCycle> firstCycle(final AnchorDates dates,
                                                 final LocalDate initial,
                                                 final Set<PayCyclePending> pending) {
        final YearMonth initialMonth = YearMonth.from(initial);
        final YearMonth lastMonth = searchThrough(initialMonth, dates);
        YearMonth opening = null;
        for (YearMonth month = dates.firstMonthToScan(initialMonth); !month.isAfter(lastMonth);
             month = month.plusMonths(1)) {
            switch (dates.at(month)) {
                case Known known -> {
                    if (known.date().equals(initial)) {
                        opening = month;
                    } else if (known.date().isAfter(initial)) {
                        return Optional.of(new PayCycle(null, initial, known.date(),
                                opening != null ? PayCycleKind.REGULAR : PayCycleKind.TRANSITION,
                                opening != null ? dates.scheduleCode() : null, opening,
                                PayCycleLifecycle.ACTIVE, false, false, null));
                    }
                }
                case Blocked blocked -> {
                    pending.add(blocked.pending());
                    return Optional.empty();
                }
                case BeforeSchedule beforeSchedule -> {
                    // The schedule had not started: there is no occurrence to wait for.
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Moves the boundaries that are free to move to where the anchor dates are now. Frozen
     * boundaries stay. If the result would not be a strictly increasing chain nothing moves at all:
     * a cycle is never emptied or inverted to accommodate a date.
     */
    private static List<PayCycle> recalculate(final List<PayCycle> existing,
                                              final AnchorDates dates,
                                              final LocalDate today,
                                              final Set<PayCyclePending> pending) {
        final int size = existing.size();
        final LocalDate[] ends = new LocalDate[size];
        for (int i = 0; i < size; i++) {
            ends[i] = existing.get(i).endDate();
        }

        for (int i = 0; i < size; i++) {
            final PayCycle cycle = existing.get(i);
            final PayCycle next = i + 1 < size ? existing.get(i + 1) : null;
            if (cycle.endIsFrozen(today) || (next != null && next.startAdjusted())) {
                continue;
            }
            final Optional<YearMonth> opening = nextAnchorMonth(cycle, next, dates, pending);
            if (opening.isPresent() && dates.at(opening.get()) instanceof Known known
                    && known.date().isAfter(today)) {
                ends[i] = known.date();
            }
        }

        boolean increasing = true;
        LocalDate start = existing.get(0).startDate();
        for (int i = 0; i < size && increasing; i++) {
            increasing = start.isBefore(ends[i]);
            start = ends[i];
        }

        final List<PayCycle> result = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            final PayCycle cycle = existing.get(i);
            final LocalDate newStart = i == 0 ? cycle.startDate() : result.get(i - 1).endDate();
            final LocalDate newEnd = increasing ? ends[i] : cycle.endDate();
            result.add(newStart.equals(cycle.startDate()) && newEnd.equals(cycle.endDate())
                    ? cycle
                    : cycle.withDates(newStart, newEnd));
        }
        return result;
    }

    /**
     * Adds cycles at the end of the chain until the current one is followed by
     * {@value #FUTURE_CYCLES} more, or until a date cannot be calculated.
     */
    private static void extend(final List<PayCycle> chain,
                               final AnchorDates dates,
                               final LocalDate today,
                               final Set<PayCyclePending> pending) {
        while (chain.size() < cyclesNeeded(chain, today)) {
            final PayCycle last = chain.get(chain.size() - 1);
            final Optional<YearMonth> opening = nextAnchorMonth(last, null, dates, pending);
            if (opening.isEmpty()) {
                return;
            }
            switch (dates.at(opening.get().plusMonths(1))) {
                case Known known -> {
                    if (!known.date().isAfter(last.endDate())) {
                        // The anchor dates went backwards past a boundary that cannot move.
                        pending.add(PayCyclePending.boundaryDiverges(
                                null, opening.get(), last.endDate(), known.date()));
                        return;
                    }
                    chain.add(new PayCycle(null, last.endDate(), known.date(), PayCycleKind.REGULAR,
                            dates.scheduleCode(), opening.get(), PayCycleLifecycle.ACTIVE, false, false, null));
                }
                case Blocked blocked -> {
                    pending.add(blocked.pending());
                    return;
                }
                case BeforeSchedule beforeSchedule -> {
                    return;
                }
            }
        }
    }

    /**
     * The cycle that contains today (or, when none does yet, the first one) and
     * {@value #FUTURE_CYCLES} after it; one more than there is while every cycle is in the past.
     */
    private static int cyclesNeeded(final List<PayCycle> chain, final LocalDate today) {
        for (int i = 0; i < chain.size(); i++) {
            if (chain.get(i).endDate().isAfter(today)) {
                return i + FUTURE_CYCLES + 1;
            }
        }
        return chain.size() + 1;
    }

    /**
     * Compares each cycle's start with the date of the anchor occurrence that opens it. A mismatch can
     * only exist where the boundary is frozen (otherwise the recalculation fixed it): that is the
     * divergence the user has to resolve.
     */
    private static void reportAnchorMismatches(final List<PayCycle> chain,
                                               final AnchorDates dates,
                                               final Set<PayCyclePending> pending) {
        for (final PayCycle cycle : chain) {
            if (cycle.anchorNominalMonth() == null) {
                continue;
            }
            switch (dates.at(cycle.anchorNominalMonth())) {
                case Known known -> {
                    if (!known.date().equals(cycle.startDate())) {
                        pending.add(PayCyclePending.boundaryDiverges(
                                cycle.code(), cycle.anchorNominalMonth(), cycle.startDate(), known.date()));
                    }
                }
                case Blocked blocked -> pending.add(blocked.pending());
                case BeforeSchedule beforeSchedule -> {
                    // Nothing to compare with.
                }
            }
        }
        final PayCycle last = chain.get(chain.size() - 1);
        final Optional<YearMonth> opening = nextAnchorMonth(last, null, dates, pending);
        if (opening.isPresent()) {
            switch (dates.at(opening.get())) {
                case Known known -> {
                    if (!known.date().equals(last.endDate())) {
                        pending.add(PayCyclePending.boundaryDiverges(
                                null, opening.get(), last.endDate(), known.date()));
                    }
                }
                case Blocked blocked -> pending.add(blocked.pending());
                case BeforeSchedule beforeSchedule -> {
                    // Nothing to compare with.
                }
            }
        }
    }

    /**
     * The nominal month of the anchor occurrence that opens the cycle after {@code cycle}.
     */
    private static Optional<YearMonth> nextAnchorMonth(final PayCycle cycle,
                                                       final PayCycle next,
                                                       final AnchorDates dates,
                                                       final Set<PayCyclePending> pending) {
        if (next != null && next.anchorNominalMonth() != null) {
            return Optional.of(next.anchorNominalMonth());
        }
        if (cycle.anchorNominalMonth() != null) {
            return Optional.of(cycle.anchorNominalMonth().plusMonths(1));
        }
        // A transition no anchor occurrence opened: the next one is the first anchor date after its start.
        final YearMonth startMonth = YearMonth.from(cycle.startDate());
        final YearMonth lastMonth = searchThrough(startMonth, dates);
        for (YearMonth month = dates.firstMonthToScan(startMonth); !month.isAfter(lastMonth);
             month = month.plusMonths(1)) {
            switch (dates.at(month)) {
                case Known known -> {
                    if (known.date().isAfter(cycle.startDate())) {
                        return Optional.of(month);
                    }
                }
                case Blocked blocked -> {
                    pending.add(blocked.pending());
                    return Optional.empty();
                }
                case BeforeSchedule beforeSchedule -> {
                    // Not started yet: keep looking.
                }
            }
        }
        return Optional.empty();
    }

    private static YearMonth searchThrough(final YearMonth month, final AnchorDates dates) {
        final YearMonth monthWindowEnd = month.plusMonths(SCAN_MONTHS_AFTER);
        final YearMonth scheduleWindowEnd = dates.startMonth().plusMonths(SCAN_MONTHS_AFTER);
        return monthWindowEnd.isAfter(scheduleWindowEnd) ? monthWindowEnd : scheduleWindowEnd;
    }

    private static void requireContiguous(final List<PayCycle> existing) {
        for (int i = 1; i < existing.size(); i++) {
            if (!existing.get(i).startDate().equals(existing.get(i - 1).endDate())) {
                throw new IllegalStateException("Pay cycles are not contiguous between "
                        + existing.get(i - 1).code() + " and " + existing.get(i).code());
            }
        }
    }

    // What the anchor schedule says about one nominal month.
    private sealed interface AnchorDate permits Known, Blocked, BeforeSchedule {
    }

    private record Known(LocalDate date) implements AnchorDate {
    }

    private record Blocked(PayCyclePending pending) implements AnchorDate {
    }

    private record BeforeSchedule() implements AnchorDate {
    }

    private static final class AnchorDates {

        private final IncomeSchedule schedule;
        private final Collection<IncomeOccurrence> materialized;
        private final HolidayCalendar calendar;

        private AnchorDates(final IncomeSchedule schedule,
                            final Collection<IncomeOccurrence> materialized,
                            final HolidayCalendar calendar) {
            this.schedule = schedule;
            this.materialized = materialized;
            this.calendar = calendar;
        }

        private UUID scheduleCode() {
            return schedule.code();
        }

        private YearMonth firstMonthToScan(final YearMonth month) {
            return schedule.rule().ruleType() == DateRuleType.NTH_BUSINESS_DAY
                    ? month
                    : month.minusMonths(SCAN_MONTHS_BEFORE);
        }

        private YearMonth startMonth() {
            return schedule.startMonth();
        }

        private AnchorDate at(final YearMonth month) {
            final List<IncomeOccurrenceView> views =
                    IncomeOccurrenceResolver.resolve(schedule, materialized, calendar, month, month);
            if (views.isEmpty()) {
                return month.isBefore(schedule.startMonth())
                        ? new BeforeSchedule()
                        : new Blocked(PayCyclePending.anchorScheduleEnds(month));
            }
            final IncomeOccurrenceView view = views.get(0);
            return switch (view.state()) {
                case MATERIALIZED, PROJECTED -> new Known(view.plannedDate());
                case CALENDAR_NOT_REVIEWED ->
                        new Blocked(PayCyclePending.calendarNotReviewed(month, view.pendingYear()));
                case BUSINESS_DAY_NOT_FOUND -> new Blocked(PayCyclePending.businessDayNotFound(month));
            };
        }
    }
}
