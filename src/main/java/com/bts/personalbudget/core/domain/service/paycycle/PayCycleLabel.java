package com.bts.personalbudget.core.domain.service.paycycle;

import java.time.LocalDate;
import java.time.YearMonth;

/**
 * Pure: the label the user reads for a cycle, such as {@code Dez/Jan}. It is calculated from the dates
 * and is never an identifier - the code and the reference month are.
 *
 * <p>The months are those of the first and of the last day of the cycle. A cycle that stays in one
 * month is just that month ({@code Fev}); one that crosses the new year carries the years
 * ({@code Dez/2026–Jan/2027}), which is also what keeps two different cycles from sharing a label.
 */
public final class PayCycleLabel {

    private static final String[] MONTHS = {
            "Jan", "Fev", "Mar", "Abr", "Mai", "Jun", "Jul", "Ago", "Set", "Out", "Nov", "Dez"
    };

    private PayCycleLabel() {
    }

    public static String of(final PayCycle cycle) {
        return of(cycle.startDate(), cycle.lastDay());
    }

    /**
     * @param firstDay first day of the cycle
     * @param lastDay  last day of the cycle, inclusive
     */
    public static String of(final LocalDate firstDay, final LocalDate lastDay) {
        final YearMonth first = YearMonth.from(firstDay);
        final YearMonth last = YearMonth.from(lastDay);
        if (first.getYear() != last.getYear()) {
            return month(first) + "/" + first.getYear() + "–" + month(last) + "/" + last.getYear();
        }
        return first.equals(last) ? month(first) : month(first) + "/" + month(last);
    }

    private static String month(final YearMonth month) {
        return MONTHS[month.getMonthValue() - 1];
    }
}
