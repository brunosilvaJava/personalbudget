package com.bts.personalbudget.core.domain.service.paycycle;

import static com.bts.personalbudget.core.domain.factory.HolidayCalendarFactory.buildReviewedCalendar;
import static com.bts.personalbudget.core.domain.factory.IncomeScheduleFactory.buildSchedule;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bts.personalbudget.core.domain.enumerator.BusinessWeek;
import com.bts.personalbudget.core.domain.enumerator.DateRuleType;
import com.bts.personalbudget.core.domain.enumerator.NonBusinessDayPolicy;
import com.bts.personalbudget.core.domain.enumerator.PayCycleKind;
import com.bts.personalbudget.core.domain.enumerator.PayCycleTransitionShape;
import com.bts.personalbudget.core.domain.service.calendar.HolidayCalendar;
import com.bts.personalbudget.core.domain.service.calendar.IncomeDateRule;
import com.bts.personalbudget.core.domain.service.income.IncomeSchedule;
import com.bts.personalbudget.core.domain.service.income.IncomeOccurrence;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PayCycleAnchorTransitionPlannerTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 2);
    private static final LocalDate NOV_5 = LocalDate.of(2026, 11, 5);
    private static final LocalDate NOV_29 = LocalDate.of(2026, 11, 29);
    private static final LocalDate DEC_7 = LocalDate.of(2026, 12, 7);
    private static final HolidayCalendar CALENDAR = buildReviewedCalendar(2026, 2027, 2028);

    @Test
    void shouldBuildAShortTransitionWhenTheSameAnchorDateMovesEarlier() {
        final IncomeSchedule previous = buildSchedule(YearMonth.of(2026, 10));
        final IncomeSchedule next = withRule(previous, IncomeDateRule.fixedDay(
                10, NonBusinessDayPolicy.KEEP, BusinessWeek.MONDAY_TO_FRIDAY));

        final PayCycleAnchorTransitionPlan plan = plan(NOV_5, chain(NOV_5), previous, next, true, null);

        assertTrue(plan.isCalculable());
        assertEquals(PayCycleTransitionShape.SHORT, plan.shape());
        assertEquals(PayCycleKind.TRANSITION, plan.created().get(0).kind());
        assertEquals(NOV_5, plan.created().get(0).startDate());
        assertEquals(LocalDate.of(2026, 11, 10), plan.created().get(0).endDate());
        assertEquals(PayCycleKind.REGULAR, plan.created().get(1).kind());
        assertContiguous(plan.created());
    }

    @Test
    void shouldBuildALongTransitionWhenTheSameAnchorDateMovesLater() {
        final IncomeSchedule previous = buildSchedule(YearMonth.of(2026, 10));
        final IncomeSchedule next = withRule(previous, IncomeDateRule.fixedDay(
                10, NonBusinessDayPolicy.KEEP, BusinessWeek.MONDAY_TO_FRIDAY));
        final List<PayCycle> existing = chain(NOV_29);

        final PayCycleAnchorTransitionPlan plan =
                plan(NOV_29, existing, previous, next, true, null);

        assertTrue(plan.isCalculable());
        assertEquals(PayCycleTransitionShape.LONG, plan.shape());
        assertEquals(PayCycleKind.TRANSITION, plan.created().get(0).kind());
        assertEquals(LocalDate.of(2027, 1, 10), plan.created().get(0).endDate());
        assertEquals(YearMonth.of(2026, 12), plan.created().get(0).anchorNominalMonth());
        assertContiguous(plan.created());
    }

    @Test
    void shouldContinueRegularlyWhenTheSameAnchorRuleKeepsTheNextBoundaryUnchanged() {
        final IncomeSchedule previous = buildSchedule(YearMonth.of(2026, 10));
        final IncomeSchedule next = withRule(previous, IncomeDateRule.fixedDay(
                7, NonBusinessDayPolicy.KEEP, BusinessWeek.MONDAY_TO_FRIDAY));

        final PayCycleAnchorTransitionPlan plan =
                plan(NOV_29, chain(NOV_29), previous, next, true, null);

        assertTrue(plan.isCalculable());
        assertNull(plan.shape());
        assertEquals(NOV_29, plan.created().get(0).startDate());
        assertEquals(PayCycleKind.REGULAR, plan.created().get(0).kind());
        assertEquals(DEC_7, plan.created().get(0).endDate());
        assertEquals(YearMonth.of(2026, 11), plan.created().get(0).anchorNominalMonth());
        assertEquals(YearMonth.of(2026, 12), plan.created().get(1).anchorNominalMonth());
        assertContiguous(plan.created());
    }

    @Test
    void shouldIgnorePreviousUnreviewedYearWhenBusinessDayCannotReachJanuary() {
        final IncomeSchedule previous = buildSchedule(YearMonth.of(2026, 10));
        final IncomeSchedule next = withRule(previous, IncomeDateRule.nthBusinessDay(
                5, BusinessWeek.MONDAY_TO_FRIDAY));
        final LocalDate boundary = LocalDate.of(2027, 1, 5);
        final var plan = PayCycleAnchorTransitionPlanner.plan(boundary, List.of(), previous, next, List.of(),
                buildReviewedCalendar(2027, 2028), TODAY, false, PayCycleTransitionShape.SHORT);
        assertTrue(plan.isCalculable());
        assertTrue(plan.pending().isEmpty());
    }

    @Test
    void shouldIgnorePreviousUnreviewedYearWhenAnticipationCannotReachJanuary() {
        final IncomeSchedule previous = buildSchedule(YearMonth.of(2026, 10));
        final IncomeSchedule next = withRule(previous, IncomeDateRule.fixedDay(
                10, NonBusinessDayPolicy.ANTICIPATE, BusinessWeek.MONDAY_TO_FRIDAY));
        final var plan = PayCycleAnchorTransitionPlanner.plan(LocalDate.of(2027, 1, 5), List.of(),
                previous, next, List.of(), buildReviewedCalendar(2027, 2028), TODAY,
                false, PayCycleTransitionShape.SHORT);
        assertTrue(plan.isCalculable());
    }

    @Test
    void shouldRetainAPreviousMaterializedOccurrenceThatReachesJanuary() {
        final IncomeSchedule previous = buildSchedule(YearMonth.of(2026, 10));
        final IncomeSchedule next = withRule(previous, IncomeDateRule.nthBusinessDay(
                5, BusinessWeek.MONDAY_TO_FRIDAY));
        final LocalDate january3 = LocalDate.of(2027, 1, 3);
        final IncomeOccurrence december = new IncomeOccurrence(null, next.code(), YearMonth.of(2026, 12),
                january3, next.amount(), UUID.randomUUID());
        final var plan = PayCycleAnchorTransitionPlanner.plan(LocalDate.of(2027, 1, 1), List.of(),
                previous, next, List.of(december), buildReviewedCalendar(2027, 2028), TODAY,
                false, PayCycleTransitionShape.SHORT);
        assertTrue(plan.isCalculable());
        assertEquals(january3, plan.created().get(0).endDate());
        assertEquals(YearMonth.of(2026, 12), plan.created().get(1).anchorNominalMonth());
    }

    @Test
    void shouldKeepPreviousYearBlockerWhenPostponementCanReachJanuary() {
        final IncomeSchedule previous = buildSchedule(YearMonth.of(2026, 10));
        final IncomeSchedule next = withRule(previous, IncomeDateRule.fixedDay(
                31, NonBusinessDayPolicy.POSTPONE, BusinessWeek.MONDAY_TO_FRIDAY));
        final var plan = PayCycleAnchorTransitionPlanner.plan(LocalDate.of(2027, 1, 1), List.of(),
                previous, next, List.of(), buildReviewedCalendar(2027, 2028), TODAY,
                false, PayCycleTransitionShape.SHORT);
        assertFalse(plan.isCalculable());
        assertEquals(2026, plan.pending().get(0).year());
        assertEquals(YearMonth.of(2026, 12), plan.pending().get(0).nominalMonth());
    }

    @Test
    void shouldContinueRegularlyWhenTheNewScheduleDateIsTheFrozenBoundary() {
        final IncomeSchedule previous = buildSchedule(YearMonth.of(2026, 10));
        final IncomeSchedule next = new IncomeSchedule(UUID.randomUUID(), previous.description(), previous.amount(),
                previous.categoryCode(), previous.rule(), previous.startMonth(), previous.endMonth(),
                false, previous.status(), null);

        final PayCycleAnchorTransitionPlan plan =
                plan(NOV_5, chain(NOV_5), previous, next, false, null);

        assertTrue(plan.isCalculable());
        assertEquals(NOV_5, plan.created().get(0).startDate());
        assertEquals(PayCycleKind.REGULAR, plan.created().get(0).kind());
        assertEquals(next.code(), plan.created().get(0).anchorScheduleCode());
    }

    @Test
    void shouldRequireTheShapeWhenSwitchingToALaterScheduleDate() {
        final IncomeSchedule previous = buildSchedule(YearMonth.of(2026, 10));
        final IncomeSchedule next = new IncomeSchedule(UUID.randomUUID(), previous.description(), previous.amount(),
                previous.categoryCode(), IncomeDateRule.fixedDay(
                        10, NonBusinessDayPolicy.KEEP, BusinessWeek.MONDAY_TO_FRIDAY),
                previous.startMonth(), null, false, previous.status(), null);

        final PayCycleAnchorTransitionPlan plan =
                plan(NOV_5, chain(NOV_5), previous, next, false, null);

        assertTrue(plan.transitionShapeRequired());
        assertFalse(plan.isCalculable());
    }

    @Test
    void shouldBuildALongTransitionForANewAnchorWhenSelected() {
        final IncomeSchedule previous = buildSchedule(YearMonth.of(2026, 10));
        final IncomeSchedule next = new IncomeSchedule(UUID.randomUUID(), previous.description(), previous.amount(),
                previous.categoryCode(), IncomeDateRule.fixedDay(
                        10, NonBusinessDayPolicy.KEEP, BusinessWeek.MONDAY_TO_FRIDAY),
                previous.startMonth(), null, false, previous.status(), null);

        final PayCycleAnchorTransitionPlan plan = plan(
                NOV_5, chain(NOV_5), previous, next, false, PayCycleTransitionShape.LONG);

        assertTrue(plan.isCalculable());
        assertEquals(PayCycleTransitionShape.LONG, plan.shape());
        assertEquals(LocalDate.of(2026, 12, 10), plan.created().get(0).endDate());
        assertEquals(YearMonth.of(2026, 11), plan.created().get(0).anchorNominalMonth());
        assertContiguous(plan.created());
    }

    @Test
    void shouldBuildAShortTransitionForANewAnchorWhenSelected() {
        final IncomeSchedule previous = buildSchedule(YearMonth.of(2026, 10));
        final IncomeSchedule next = new IncomeSchedule(UUID.randomUUID(), previous.description(), previous.amount(),
                previous.categoryCode(), IncomeDateRule.fixedDay(
                        29, NonBusinessDayPolicy.KEEP, BusinessWeek.MONDAY_TO_FRIDAY),
                previous.startMonth(), null, false, previous.status(), null);

        final PayCycleAnchorTransitionPlan plan = plan(
                NOV_5, chain(NOV_5), previous, next, false, PayCycleTransitionShape.SHORT);

        assertTrue(plan.isCalculable());
        assertEquals(PayCycleTransitionShape.SHORT, plan.shape());
        assertEquals(NOV_5, plan.created().get(0).startDate());
        assertEquals(NOV_29, plan.created().get(0).endDate());
        assertEquals(PayCycleKind.TRANSITION, plan.created().get(0).kind());
        assertEquals(NOV_29, plan.created().get(1).startDate());
        assertContiguous(plan.created());
    }

    @Test
    void shouldLeaveUnreviewedCalendarDatesAsPendencies() {
        final IncomeSchedule previous = buildSchedule(YearMonth.of(2026, 10));
        final IncomeSchedule next = new IncomeSchedule(UUID.randomUUID(), previous.description(), previous.amount(),
                previous.categoryCode(), IncomeDateRule.fixedDay(
                        10, NonBusinessDayPolicy.POSTPONE, BusinessWeek.MONDAY_TO_FRIDAY),
                previous.startMonth(), null, false, previous.status(), null);

        final PayCycleAnchorTransitionPlan plan = PayCycleAnchorTransitionPlanner.plan(
                NOV_5, chain(NOV_5), previous, next, List.of(), HolidayCalendar.empty(), TODAY,
                false, PayCycleTransitionShape.SHORT);

        assertTrue(plan.created().isEmpty());
        assertEquals(PayCyclePending.Type.CALENDAR_NOT_REVIEWED, plan.pending().get(0).type());
    }

    private PayCycleAnchorTransitionPlan plan(
            final LocalDate boundary,
            final List<PayCycle> existing,
            final IncomeSchedule previous,
            final IncomeSchedule next,
            final boolean sameSchedule,
            final PayCycleTransitionShape shape) {
        return PayCycleAnchorTransitionPlanner.plan(
                boundary, existing, previous, next, List.of(), CALENDAR, TODAY, sameSchedule, shape);
    }

    private List<PayCycle> chain(final LocalDate boundary) {
        final LocalDate nextDate = boundary.equals(NOV_29)
                ? LocalDate.of(2026, 12, 7)
                : LocalDate.of(2026, 12, 7);
        return List.of(
                cycle(LocalDate.of(2026, 10, 5), boundary, YearMonth.of(2026, 10)),
                cycle(boundary, nextDate, YearMonth.of(2026, 11)),
                cycle(nextDate, LocalDate.of(2027, 1, 5), YearMonth.of(2026, 12)));
    }

    private PayCycle cycle(final LocalDate start, final LocalDate end, final YearMonth month) {
        return new PayCycle(UUID.randomUUID(), start, end, PayCycleKind.REGULAR,
                com.bts.personalbudget.core.domain.factory.IncomeScheduleFactory.SCHEDULE_CODE,
                month, com.bts.personalbudget.core.domain.enumerator.PayCycleLifecycle.ACTIVE,
                false, false, 0L);
    }

    private IncomeSchedule withRule(final IncomeSchedule schedule, final IncomeDateRule rule) {
        return new IncomeSchedule(schedule.code(), schedule.description(), schedule.amount(), schedule.categoryCode(),
                rule, schedule.startMonth(), schedule.endMonth(), schedule.anchor(), schedule.status(),
                schedule.replacesFixedBillCode());
    }

    private void assertContiguous(final List<PayCycle> cycles) {
        for (int i = 1; i < cycles.size(); i++) {
            assertEquals(cycles.get(i - 1).endDate(), cycles.get(i).startDate());
        }
    }
}
