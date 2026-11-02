package com.bts.personalbudget.core.domain.service.paycycle;

import static com.bts.personalbudget.core.domain.factory.PayCycleFactory.buildCycle;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;

class PayCycleLabelTest {

    @Test
    void shouldNameTheMonthsOfTheFirstAndOfTheLastDay() {
        final PayCycle cycle = buildCycle(LocalDate.of(2026, 11, 5), LocalDate.of(2026, 12, 7), YearMonth.of(2026, 11));

        assertEquals("Nov/Dez", PayCycleLabel.of(cycle));
    }

    @Test
    void shouldUseTheLastDayAndNotTheExclusiveEnd() {
        // The cycle ends on Feb 5 exclusive: its last day is Feb 4, still February.
        final PayCycle cycle = buildCycle(LocalDate.of(2027, 1, 5), LocalDate.of(2027, 2, 5), YearMonth.of(2027, 1));

        assertEquals("Jan/Fev", PayCycleLabel.of(cycle));
    }

    @Test
    void shouldNameOneMonthWhenTheCycleStaysInIt() {
        final PayCycle cycle = buildCycle(LocalDate.of(2027, 2, 1), LocalDate.of(2027, 3, 1), YearMonth.of(2027, 2));

        assertEquals("Fev", PayCycleLabel.of(cycle));
    }

    @Test
    void shouldCarryTheYearsWhenTheCycleCrossesTheNewYear() {
        final PayCycle cycle = buildCycle(LocalDate.of(2026, 12, 7), LocalDate.of(2027, 1, 5), YearMonth.of(2026, 12));

        assertEquals("Dez/2026–Jan/2027", PayCycleLabel.of(cycle));
    }

    @Test
    void shouldNotCrossTheYearWhenTheCycleEndsOnTheFirstDayOfTheYear() {
        // Ends on Jan 1 exclusive: the last day is Dec 31, so the cycle does not touch the new year.
        final PayCycle cycle = buildCycle(LocalDate.of(2026, 12, 7), LocalDate.of(2027, 1, 1), YearMonth.of(2026, 12));

        assertEquals("Dez", PayCycleLabel.of(cycle));
    }

    @Test
    void shouldCarryTheYearsWhenTheSameMonthRepeatsInAnotherYear() {
        assertEquals("Jan/2026–Jan/2027", PayCycleLabel.of(LocalDate.of(2026, 1, 10), LocalDate.of(2027, 1, 20)));
    }

    @Test
    void shouldAbbreviateEveryMonthInPortuguese() {
        final String[] expected = {"Jan", "Fev", "Mar", "Abr", "Mai", "Jun", "Jul", "Ago", "Set", "Out", "Nov", "Dez"};
        for (int month = 1; month <= 12; month++) {
            assertEquals(expected[month - 1],
                    PayCycleLabel.of(LocalDate.of(2026, month, 1), LocalDate.of(2026, month, 2)));
        }
    }
}
