package com.bts.personalbudget.core.domain.service.paycycle;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

/**
 * Something that stops the cycles from being complete or exact. Pendencies are calculated when the
 * cycles are read, not stored: they go away by themselves when the calendar is reviewed or the rule is
 * fixed. In E4 they only inform; what they block (the closing of a cycle) is decided in E6.
 *
 * @param nominalMonth the month of the anchor occurrence the pendency is about
 * @param year         only for CALENDAR_NOT_REVIEWED: the calendar year that has to be reviewed
 * @param cycleCode    only for BOUNDARY_DIVERGES_FROM_ANCHOR: the cycle that starts on the boundary
 * @param boundaryDate only for BOUNDARY_DIVERGES_FROM_ANCHOR: where the boundary is
 * @param anchorDate   only for BOUNDARY_DIVERGES_FROM_ANCHOR: where the anchor occurrence is now
 */
public record PayCyclePending(
        Type type,
        YearMonth nominalMonth,
        Integer year,
        UUID cycleCode,
        LocalDate boundaryDate,
        LocalDate anchorDate
) {

    public enum Type {
        /** An anchor date needed by the horizon depends on a calendar year that was not reviewed. */
        CALENDAR_NOT_REVIEWED,
        /** The month has fewer business days than the rule's position. */
        BUSINESS_DAY_NOT_FOUND,
        /** The anchor schedule does not serve the month (its end month passed, or it is inactive). */
        ANCHOR_SCHEDULE_ENDS,
        /** A boundary that cannot move no longer matches the date of the anchor occurrence. */
        BOUNDARY_DIVERGES_FROM_ANCHOR,
        /** The mode is PAY_CYCLE and no active cycle contains today. */
        NO_CURRENT_CYCLE
    }

    public static PayCyclePending calendarNotReviewed(final YearMonth month, final int year) {
        return new PayCyclePending(Type.CALENDAR_NOT_REVIEWED, month, year, null, null, null);
    }

    public static PayCyclePending businessDayNotFound(final YearMonth month) {
        return new PayCyclePending(Type.BUSINESS_DAY_NOT_FOUND, month, null, null, null, null);
    }

    public static PayCyclePending anchorScheduleEnds(final YearMonth month) {
        return new PayCyclePending(Type.ANCHOR_SCHEDULE_ENDS, month, null, null, null, null);
    }

    public static PayCyclePending boundaryDiverges(final UUID cycleCode,
                                                   final YearMonth month,
                                                   final LocalDate boundaryDate,
                                                   final LocalDate anchorDate) {
        return new PayCyclePending(Type.BOUNDARY_DIVERGES_FROM_ANCHOR, month, null, cycleCode, boundaryDate, anchorDate);
    }

    public static PayCyclePending noCurrentCycle() {
        return new PayCyclePending(Type.NO_CURRENT_CYCLE, null, null, null, null, null);
    }
}
