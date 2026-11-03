package com.bts.personalbudget.core.domain.service.income;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

/**
 * One nominal month of a schedule as the user sees it: already materialized, still only projected
 * from the rule, or impossible to calculate right now (and why).
 *
 * @param plannedDate the date for MATERIALIZED and PROJECTED; null when it cannot be calculated
 * @param amount      the materialized amount, or the schedule's current amount for a projection;
 *                    null when the date cannot be calculated
 * @param movementCode only for MATERIALIZED
 * @param pendingYear  only for CALENDAR_NOT_REVIEWED: the calendar year that has to be reviewed
 */
public record IncomeOccurrenceView(
        YearMonth nominalMonth,
        State state,
        LocalDate plannedDate,
        BigDecimal amount,
        UUID movementCode,
        Integer pendingYear
) {

    public enum State {
        /** A movement was generated; the date and amount are frozen. */
        MATERIALIZED,
        /** Not generated yet: the date comes from the rule and the current calendar. */
        PROJECTED,
        /** The date depends on a calendar year that was not reviewed; nothing is assumed. */
        CALENDAR_NOT_REVIEWED,
        /** The month has fewer business days than the rule's position. */
        BUSINESS_DAY_NOT_FOUND
    }

    public static IncomeOccurrenceView materialized(final IncomeOccurrence occurrence) {
        return new IncomeOccurrenceView(occurrence.nominalMonth(), State.MATERIALIZED, occurrence.plannedDate(),
                occurrence.plannedAmount(), occurrence.movementCode(), null);
    }

    public static IncomeOccurrenceView projected(final YearMonth nominalMonth,
                                                 final LocalDate plannedDate,
                                                 final BigDecimal amount) {
        return new IncomeOccurrenceView(nominalMonth, State.PROJECTED, plannedDate, amount, null, null);
    }

    public static IncomeOccurrenceView calendarNotReviewed(final YearMonth nominalMonth, final int year) {
        return new IncomeOccurrenceView(nominalMonth, State.CALENDAR_NOT_REVIEWED, null, null, null, year);
    }

    public static IncomeOccurrenceView businessDayNotFound(final YearMonth nominalMonth) {
        return new IncomeOccurrenceView(nominalMonth, State.BUSINESS_DAY_NOT_FOUND, null, null, null, null);
    }
}
