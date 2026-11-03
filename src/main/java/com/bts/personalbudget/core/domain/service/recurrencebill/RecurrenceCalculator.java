package com.bts.personalbudget.core.domain.service.recurrencebill;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

/**
 * Pure date arithmetic for recurrences: no persistence, no clock. Given a bill's next pending
 * occurrence and the rule that yields the one after it, lists every occurrence up to a date. Used
 * by the balance projection, which needs all occurrences in a period, not only the next one.
 */
public final class RecurrenceCalculator {

    /**
     * Guards against a schedule that never ends inside the requested window - e.g. a weekly bill
     * projected decades ahead. Far above anything a real dashboard period produces.
     */
    public static final int MAX_OCCURRENCES = 1000;

    private RecurrenceCalculator() {
    }

    /**
     * @param firstOccurrence the first occurrence to include (the bill's next pending date); may be null
     * @param endDate         last date (inclusive) to list occurrences for
     * @param nextOccurrence  given an occurrence, returns the following one, or null when there is none
     * @param maxOccurrences  upper bound on how many occurrences to list
     * @return occurrences in chronological order, from {@code firstOccurrence} up to {@code endDate}
     */
    public static List<LocalDate> occurrencesUntil(final LocalDate firstOccurrence,
                                                   final LocalDate endDate,
                                                   final UnaryOperator<LocalDate> nextOccurrence,
                                                   final int maxOccurrences) {
        final List<LocalDate> occurrences = new ArrayList<>();
        LocalDate occurrence = firstOccurrence;
        while (occurrence != null && !occurrence.isAfter(endDate) && occurrences.size() < maxOccurrences) {
            occurrences.add(occurrence);
            final LocalDate next = nextOccurrence.apply(occurrence);
            if (next != null && !next.isAfter(occurrence)) {
                // A schedule that does not move forward would repeat the same date forever.
                break;
            }
            occurrence = next;
        }
        return occurrences;
    }
}
