package com.bts.personalbudget.core.domain.service.paycycle;

import com.bts.personalbudget.core.domain.enumerator.PayCycleKind;
import com.bts.personalbudget.core.domain.enumerator.PayCycleLifecycle;
import com.bts.personalbudget.core.domain.enumerator.PayCycleStatus;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * A financial cycle: the interval {@code [startDate, endDate)}. The end is exclusive, so the day a new
 * cycle opens belongs to the new cycle and to no other; the last day of the cycle is
 * {@link #lastDay()}.
 *
 * <p>The code is the public identifier and never changes, even when the boundary dates move. The
 * reference of a cycle is the nominal month of the anchor occurrence that opens it, which is stable
 * while the dates around it are adjusted.
 *
 * @param anchorScheduleCode   schedule of the anchor occurrence that opens the cycle; null for a
 *                             TRANSITION that no anchor occurrence opens
 * @param anchorNominalMonth   nominal month of that occurrence; null exactly when the schedule is
 * @param startAdjusted        the start was fixed by a manual adjustment and is not recalculated
 * @param endAdjusted          the end was fixed by a manual adjustment and is not recalculated
 * @param version              optimistic lock version; null until the cycle is saved
 */
public record PayCycle(
        UUID code,
        LocalDate startDate,
        LocalDate endDate,
        PayCycleKind kind,
        UUID anchorScheduleCode,
        YearMonth anchorNominalMonth,
        PayCycleLifecycle lifecycle,
        boolean startAdjusted,
        boolean endAdjusted,
        Long version
) {

    public PayCycle {
        if (startDate == null || endDate == null || !startDate.isBefore(endDate)) {
            throw new IllegalArgumentException("A pay cycle needs startDate < endDate, got " + startDate + " and " + endDate);
        }
        if ((anchorScheduleCode == null) != (anchorNominalMonth == null)) {
            throw new IllegalArgumentException("A pay cycle anchor is a schedule and a month together");
        }
        if (code == null) {
            code = UUID.randomUUID();
        }
        if (kind == null) {
            kind = PayCycleKind.REGULAR;
        }
        if (lifecycle == null) {
            lifecycle = PayCycleLifecycle.ACTIVE;
        }
    }

    public boolean isActive() {
        return lifecycle == PayCycleLifecycle.ACTIVE;
    }

    /**
     * Whether the date is inside the cycle: the start counts, the end does not.
     */
    public boolean contains(final LocalDate date) {
        return !date.isBefore(startDate) && date.isBefore(endDate);
    }

    /**
     * Whether the cycle shares at least one day with {@code [from, to]}, both inclusive.
     */
    public boolean overlaps(final LocalDate from, final LocalDate to) {
        return !startDate.isAfter(to) && endDate.isAfter(from);
    }

    public LocalDate lastDay() {
        return endDate.minusDays(1);
    }

    public int totalDays() {
        return (int) ChronoUnit.DAYS.between(startDate, endDate);
    }

    /**
     * Derived, never stored: PLANNED before the start, OPEN while today is inside, PENDING_CLOSE once
     * it ended. A cancelled cycle is CANCELLED whatever the date.
     */
    public PayCycleStatus status(final LocalDate today) {
        if (lifecycle == PayCycleLifecycle.CANCELLED) {
            return PayCycleStatus.CANCELLED;
        }
        if (today.isBefore(startDate)) {
            return PayCycleStatus.PLANNED;
        }
        return today.isBefore(endDate) ? PayCycleStatus.OPEN : PayCycleStatus.PENDING_CLOSE;
    }

    /**
     * Whether the end of this cycle (which is also the start of the next) can no longer move by
     * itself: the cycle already started, or the end was fixed by hand.
     */
    public boolean endIsFrozen(final LocalDate today) {
        return endAdjusted || !startDate.isAfter(today);
    }

    public PayCycle withDates(final LocalDate newStart, final LocalDate newEnd) {
        return new PayCycle(code, newStart, newEnd, kind, anchorScheduleCode, anchorNominalMonth, lifecycle,
                startAdjusted, endAdjusted, version);
    }

    /**
     * This cycle with its end fixed by hand at {@code newEnd}: from then on the end is not recalculated.
     */
    public PayCycle withEndAdjusted(final LocalDate newEnd) {
        return new PayCycle(code, startDate, newEnd, kind, anchorScheduleCode, anchorNominalMonth, lifecycle,
                startAdjusted, true, version);
    }

    /**
     * This cycle with its start fixed by hand at {@code newStart}: from then on the start is not
     * recalculated.
     */
    public PayCycle withStartAdjusted(final LocalDate newStart) {
        return new PayCycle(code, newStart, endDate, kind, anchorScheduleCode, anchorNominalMonth, lifecycle,
                true, endAdjusted, version);
    }

    public PayCycle cancelled() {
        return new PayCycle(code, startDate, endDate, kind, anchorScheduleCode, anchorNominalMonth,
                PayCycleLifecycle.CANCELLED, startAdjusted, endAdjusted, version);
    }
}
