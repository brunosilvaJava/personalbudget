package com.bts.personalbudget.core.domain.service.calendar;

import java.time.LocalDate;
import java.time.YearMonth;

/**
 * The outcome of {@link IncomeDateCalculator#calculate}. A date that cannot be determined is a
 * result, not a guess: the caller reports the pendency instead of assuming every day is a business
 * day.
 */
public sealed interface IncomeDateResult {

    /**
     * @param date the scheduled date; it may fall in a month other than the nominal one when a
     *             FIXED_DAY rule anticipates or postpones across the month boundary
     */
    record Calculated(LocalDate date) implements IncomeDateResult {
    }

    /**
     * The calculation needed to know the holidays of a year that was not reviewed. Reviewing that
     * year and calculating again may reveal a neighbouring year (e.g. an anticipation from Jan 1
     * that reaches December of the previous year).
     */
    record CalendarNotReviewed(int year) implements IncomeDateResult {
    }

    /**
     * An NTH_BUSINESS_DAY rule asked for a position the month does not have (every business day
     * of the month was used up by holidays).
     */
    record BusinessDayNotFound(YearMonth month, int position) implements IncomeDateResult {
    }
}
