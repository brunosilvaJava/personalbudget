package com.bts.personalbudget.core.domain.service.paycycle;

import static com.bts.personalbudget.core.domain.factory.PayCycleFactory.buildCycle;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bts.personalbudget.core.domain.service.paycycle.PayCycleAdjustmentPlanner.EndAdjustment;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * The pair is October (Oct 5 to Nov 5) and November (Nov 5 to Dec 7).
 */
class PayCycleAdjustmentPlannerTest {

    private static final LocalDate OCT_5 = LocalDate.of(2026, 10, 5);
    private static final LocalDate NOV_5 = LocalDate.of(2026, 11, 5);
    private static final LocalDate DEC_7 = LocalDate.of(2026, 12, 7);

    private final PayCycle previous = buildCycle(OCT_5, NOV_5, YearMonth.of(2026, 10));
    private final PayCycle next = buildCycle(NOV_5, DEC_7, YearMonth.of(2026, 11));

    // ---- validate

    @Test
    void shouldAcceptADateInsideThePair() {
        assertEquals(Optional.empty(), PayCycleAdjustmentPlanner.validate(previous, next, LocalDate.of(2026, 11, 10)));
        assertEquals(Optional.empty(), PayCycleAdjustmentPlanner.validate(previous, next, LocalDate.of(2026, 11, 1)));
    }

    @Test
    void shouldAcceptTheFirstAndTheLastDateThatLeaveBothCyclesWithOneDay() {
        assertEquals(Optional.empty(), PayCycleAdjustmentPlanner.validate(previous, next, OCT_5.plusDays(1)));
        assertEquals(Optional.empty(), PayCycleAdjustmentPlanner.validate(previous, next, DEC_7.minusDays(1)));
    }

    @Test
    void shouldRejectAMissingDate() {
        assertEquals(Optional.of("deve ser informada"), PayCycleAdjustmentPlanner.validate(previous, next, null));
    }

    @Test
    void shouldRejectTheDateOfTheBoundaryThatAlreadyExists() {
        final Optional<String> message = PayCycleAdjustmentPlanner.validate(previous, next, NOV_5);

        assertTrue(message.orElseThrow().contains("diferente do fim atual"));
        assertTrue(message.orElseThrow().contains("2026-11-05"));
    }

    @Test
    void shouldRejectADateThatWouldEmptyThePreviousCycle() {
        assertTrue(PayCycleAdjustmentPlanner.validate(previous, next, OCT_5).isPresent());
        assertTrue(PayCycleAdjustmentPlanner.validate(previous, next, OCT_5.minusDays(10)).isPresent());
    }

    @Test
    void shouldRejectADateThatWouldEmptyTheNextCycle() {
        assertTrue(PayCycleAdjustmentPlanner.validate(previous, next, DEC_7).isPresent());
        assertTrue(PayCycleAdjustmentPlanner.validate(previous, next, DEC_7.plusDays(10)).isPresent());
    }

    @Test
    void shouldSayWhichDatesAreAccepted() {
        final String message = PayCycleAdjustmentPlanner.validate(previous, next, DEC_7).orElseThrow();

        assertTrue(message.contains("2026-10-06"));
        assertTrue(message.contains("2026-12-06"));
    }

    @Test
    void shouldFailWhenTheCyclesAreNotConsecutive() {
        final PayCycle later = buildCycle(DEC_7, LocalDate.of(2027, 1, 5), YearMonth.of(2026, 12));

        assertThrows(IllegalArgumentException.class,
                () -> PayCycleAdjustmentPlanner.validate(previous, later, LocalDate.of(2026, 11, 10)));
    }

    // ---- adjust

    @Test
    void shouldMoveTheBoundaryOfBothCyclesAndMarkThemAsAdjusted() {
        final EndAdjustment adjustment = PayCycleAdjustmentPlanner.adjust(previous, next, LocalDate.of(2026, 11, 10));

        assertEquals(OCT_5, adjustment.previous().startDate());
        assertEquals(LocalDate.of(2026, 11, 10), adjustment.previous().endDate());
        assertEquals(LocalDate.of(2026, 11, 10), adjustment.next().startDate());
        assertEquals(DEC_7, adjustment.next().endDate());
        assertTrue(adjustment.previous().endAdjusted());
        assertTrue(adjustment.next().startAdjusted());
        assertEquals(NOV_5, adjustment.oldBoundary());
        assertEquals(LocalDate.of(2026, 11, 10), adjustment.newBoundary());
    }

    @Test
    void shouldLeaveNoGapAndNoOverlapBetweenThePair() {
        for (LocalDate date = OCT_5.plusDays(1); date.isBefore(DEC_7); date = date.plusDays(1)) {
            if (date.equals(NOV_5)) {
                continue;
            }
            final EndAdjustment adjustment = PayCycleAdjustmentPlanner.adjust(previous, next, date);

            assertEquals(adjustment.previous().endDate(), adjustment.next().startDate(), "pair at " + date);
            assertTrue(adjustment.previous().startDate().isBefore(adjustment.previous().endDate()), "previous at " + date);
            assertTrue(adjustment.next().startDate().isBefore(adjustment.next().endDate()), "next at " + date);
        }
    }

    @Test
    void shouldKeepTheCodesKindsReferencesAndVersions() {
        final EndAdjustment adjustment = PayCycleAdjustmentPlanner.adjust(previous, next, LocalDate.of(2026, 11, 10));

        assertEquals(previous.code(), adjustment.previous().code());
        assertEquals(next.code(), adjustment.next().code());
        assertEquals(previous.kind(), adjustment.previous().kind());
        assertEquals(next.kind(), adjustment.next().kind());
        assertEquals(previous.anchorNominalMonth(), adjustment.previous().anchorNominalMonth());
        assertEquals(next.anchorNominalMonth(), adjustment.next().anchorNominalMonth());
        assertEquals(previous.version(), adjustment.previous().version());
        assertEquals(next.version(), adjustment.next().version());
    }

    @Test
    void shouldNotMarkTheOuterEdgesAsAdjusted() {
        final EndAdjustment adjustment = PayCycleAdjustmentPlanner.adjust(previous, next, LocalDate.of(2026, 11, 10));

        assertFalse(adjustment.previous().startAdjusted());
        assertFalse(adjustment.next().endAdjusted());
    }

    @Test
    void shouldNotChangeTheCyclesItWasGiven() {
        PayCycleAdjustmentPlanner.adjust(previous, next, LocalDate.of(2026, 11, 10));

        assertEquals(NOV_5, previous.endDate());
        assertEquals(NOV_5, next.startDate());
        assertFalse(previous.endAdjusted());
        assertFalse(next.startAdjusted());
    }

    @Test
    void shouldRefuseToAdjustToADateTheValidationRejects() {
        assertThrows(IllegalArgumentException.class, () -> PayCycleAdjustmentPlanner.adjust(previous, next, NOV_5));
        assertThrows(IllegalArgumentException.class, () -> PayCycleAdjustmentPlanner.adjust(previous, next, DEC_7));
        assertThrows(IllegalArgumentException.class, () -> PayCycleAdjustmentPlanner.adjust(previous, next, null));
    }

    // ---- the days that change cycle

    @Test
    void shouldMoveTheDaysBetweenTheOldAndTheNewBoundaryWhenItMovesLater() {
        final EndAdjustment adjustment = PayCycleAdjustmentPlanner.adjust(previous, next, LocalDate.of(2026, 11, 10));

        assertEquals(NOV_5, adjustment.movedFrom());
        assertEquals(LocalDate.of(2026, 11, 10), adjustment.movedUntil());
        assertEquals(next.code(), adjustment.leavingCycleCode());
        assertEquals(previous.code(), adjustment.receivingCycleCode());
    }

    @Test
    void shouldMoveTheDaysBetweenTheNewAndTheOldBoundaryWhenItMovesEarlier() {
        final EndAdjustment adjustment = PayCycleAdjustmentPlanner.adjust(previous, next, LocalDate.of(2026, 11, 1));

        assertEquals(LocalDate.of(2026, 11, 1), adjustment.movedFrom());
        assertEquals(NOV_5, adjustment.movedUntil());
        assertEquals(previous.code(), adjustment.leavingCycleCode());
        assertEquals(next.code(), adjustment.receivingCycleCode());
    }

    @Test
    void shouldMoveTheDaysFromTheNewBoundaryUpToButNotIncludingTheOldOne() {
        final EndAdjustment adjustment = PayCycleAdjustmentPlanner.adjust(previous, next, NOV_5.minusDays(1));

        assertEquals(NOV_5.minusDays(1), adjustment.movedFrom());
        assertEquals(NOV_5, adjustment.movedUntil());
    }
}
