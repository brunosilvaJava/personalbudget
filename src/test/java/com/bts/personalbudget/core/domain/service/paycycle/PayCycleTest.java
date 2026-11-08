package com.bts.personalbudget.core.domain.service.paycycle;

import static com.bts.personalbudget.core.domain.factory.PayCycleFactory.buildCycle;
import static com.bts.personalbudget.core.domain.factory.PayCycleFactory.buildTransition;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bts.personalbudget.core.domain.enumerator.PayCycleKind;
import com.bts.personalbudget.core.domain.enumerator.PayCycleLifecycle;
import com.bts.personalbudget.core.domain.enumerator.PayCycleStatus;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PayCycleTest {

    private static final LocalDate START = LocalDate.of(2026, 11, 5);
    private static final LocalDate END = LocalDate.of(2026, 12, 7);
    private static final YearMonth NOV = YearMonth.of(2026, 11);

    private final PayCycle cycle = buildCycle(START, END, NOV);

    @Test
    void shouldContainTheStartDayAndNotTheEndDay() {
        assertTrue(cycle.contains(START));
        assertTrue(cycle.contains(END.minusDays(1)));
        assertFalse(cycle.contains(END));
        assertFalse(cycle.contains(START.minusDays(1)));
    }

    @Test
    void shouldGiveTheNextCycleTheDayThisOneEnds() {
        final PayCycle next = buildCycle(END, LocalDate.of(2027, 1, 5), YearMonth.of(2026, 12));

        assertFalse(cycle.contains(next.startDate()));
        assertTrue(next.contains(next.startDate()));
    }

    @Test
    void shouldReportTheLastDayAndTheTotalDays() {
        assertEquals(LocalDate.of(2026, 12, 6), cycle.lastDay());
        assertEquals(32, cycle.totalDays());
    }

    @Test
    void shouldBePlannedBeforeTheStart() {
        assertEquals(PayCycleStatus.PLANNED, cycle.status(START.minusDays(1)));
    }

    @Test
    void shouldBeOpenFromTheStartUntilTheLastDay() {
        assertEquals(PayCycleStatus.OPEN, cycle.status(START));
        assertEquals(PayCycleStatus.OPEN, cycle.status(cycle.lastDay()));
    }

    @Test
    void shouldBePendingCloseFromTheEndDay() {
        assertEquals(PayCycleStatus.PENDING_CLOSE, cycle.status(END));
    }

    @Test
    void shouldBeClosedWhateverTheDate() {
        final PayCycle closed = new PayCycle(cycle.code(), START, END, cycle.kind(),
                cycle.anchorScheduleCode(), cycle.anchorNominalMonth(), PayCycleLifecycle.CLOSED,
                false, false, 1L);

        assertEquals(PayCycleStatus.CLOSED, closed.status(START.minusDays(10)));
        assertEquals(PayCycleStatus.CLOSED, closed.status(START));
    }

    @Test
    void shouldBeCancelledWhateverTheDate() {
        final PayCycle cancelled = cycle.cancelled();

        assertEquals(PayCycleLifecycle.CANCELLED, cancelled.lifecycle());
        assertEquals(PayCycleStatus.CANCELLED, cancelled.status(START.minusDays(10)));
        assertEquals(PayCycleStatus.CANCELLED, cancelled.status(START));
        assertFalse(cancelled.isActive());
    }

    @Test
    void shouldFreezeTheEndOnceTheCycleStarted() {
        assertFalse(cycle.endIsFrozen(START.minusDays(1)));
        assertTrue(cycle.endIsFrozen(START));
        assertTrue(cycle.endIsFrozen(END));
    }

    @Test
    void shouldFreezeAnAdjustedEndBeforeTheCycleStarted() {
        final PayCycle adjusted = new PayCycle(cycle.code(), START, END, PayCycleKind.REGULAR,
                cycle.anchorScheduleCode(), NOV, PayCycleLifecycle.ACTIVE, false, true, 0L);

        assertTrue(adjusted.endIsFrozen(START.minusDays(10)));
    }

    @Test
    void shouldOverlapARangeThatSharesAtLeastOneDay() {
        assertTrue(cycle.overlaps(START, START));
        assertTrue(cycle.overlaps(END.minusDays(1), END.plusDays(10)));
        assertFalse(cycle.overlaps(END, END.plusDays(10)));
        assertFalse(cycle.overlaps(START.minusDays(10), START.minusDays(1)));
    }

    @Test
    void shouldKeepTheCodeWhenTheDatesMove() {
        final PayCycle moved = cycle.withDates(START, END.plusDays(1));

        assertEquals(cycle.code(), moved.code());
        assertEquals(END.plusDays(1), moved.endDate());
        assertEquals(cycle.anchorNominalMonth(), moved.anchorNominalMonth());
    }

    @Test
    void shouldCreateACodeWhenNoneIsGiven() {
        assertTrue(cycle.code() != null);
        assertFalse(cycle.code().equals(buildCycle(START, END, NOV).code()));
    }

    @Test
    void shouldRejectAnEmptyOrInvertedInterval() {
        assertThrows(IllegalArgumentException.class, () -> buildCycle(START, START, NOV));
        assertThrows(IllegalArgumentException.class, () -> buildCycle(END, START, NOV));
    }

    @Test
    void shouldRejectAnAnchorThatIsOnlyHalfThere() {
        assertThrows(IllegalArgumentException.class, () -> new PayCycle(UUID.randomUUID(), START, END,
                PayCycleKind.REGULAR, UUID.randomUUID(), null, PayCycleLifecycle.ACTIVE, false, false, 0L));
        assertThrows(IllegalArgumentException.class, () -> new PayCycle(UUID.randomUUID(), START, END,
                PayCycleKind.REGULAR, null, NOV, PayCycleLifecycle.ACTIVE, false, false, 0L));
    }

    @Test
    void shouldAllowATransitionWithNoAnchor() {
        final PayCycle transition = buildTransition(START, END);

        assertEquals(PayCycleKind.TRANSITION, transition.kind());
        assertEquals(null, transition.anchorNominalMonth());
    }
}
