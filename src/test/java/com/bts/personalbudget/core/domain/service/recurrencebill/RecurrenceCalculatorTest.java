package com.bts.personalbudget.core.domain.service.recurrencebill;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecurrenceCalculatorTest {

    private static final LocalDate JANUARY_10 = LocalDate.of(2026, 1, 10);

    @Test
    void shouldListOccurrencesUpToEndDateInclusive() {
        List<LocalDate> occurrences = RecurrenceCalculator.occurrencesUntil(
                JANUARY_10, LocalDate.of(2026, 3, 10), date -> date.plusMonths(1), RecurrenceCalculator.MAX_OCCURRENCES);

        assertEquals(List.of(JANUARY_10, LocalDate.of(2026, 2, 10), LocalDate.of(2026, 3, 10)), occurrences);
    }

    @Test
    void shouldStopWhenThereIsNoNextOccurrence() {
        final LocalDate lastOccurrence = LocalDate.of(2026, 2, 10);

        List<LocalDate> occurrences = RecurrenceCalculator.occurrencesUntil(
                JANUARY_10, LocalDate.of(2026, 12, 31),
                date -> date.isBefore(lastOccurrence) ? date.plusMonths(1) : null,
                RecurrenceCalculator.MAX_OCCURRENCES);

        assertEquals(List.of(JANUARY_10, lastOccurrence), occurrences);
    }

    @Test
    void shouldStopAtMaxOccurrences() {
        List<LocalDate> occurrences = RecurrenceCalculator.occurrencesUntil(
                JANUARY_10, LocalDate.of(2100, 1, 1), date -> date.plusDays(1), 3);

        assertEquals(List.of(JANUARY_10, JANUARY_10.plusDays(1), JANUARY_10.plusDays(2)), occurrences);
    }

    @Test
    void shouldStopWhenScheduleDoesNotMoveForward() {
        List<LocalDate> occurrences = RecurrenceCalculator.occurrencesUntil(
                JANUARY_10, LocalDate.of(2026, 12, 31), date -> date, RecurrenceCalculator.MAX_OCCURRENCES);

        assertEquals(List.of(JANUARY_10), occurrences);
    }

    @Test
    void shouldReturnNoOccurrenceWhenFirstIsNullOrAfterEndDate() {
        assertTrue(RecurrenceCalculator.occurrencesUntil(
                null, JANUARY_10, date -> date.plusMonths(1), RecurrenceCalculator.MAX_OCCURRENCES).isEmpty());
        assertTrue(RecurrenceCalculator.occurrencesUntil(
                JANUARY_10, JANUARY_10.minusDays(1), date -> date.plusMonths(1), RecurrenceCalculator.MAX_OCCURRENCES).isEmpty());
    }
}
