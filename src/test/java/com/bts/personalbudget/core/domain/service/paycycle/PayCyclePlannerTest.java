package com.bts.personalbudget.core.domain.service.paycycle;

import static com.bts.personalbudget.core.domain.factory.HolidayCalendarFactory.buildCalendar;
import static com.bts.personalbudget.core.domain.factory.HolidayCalendarFactory.buildHoliday;
import static com.bts.personalbudget.core.domain.factory.HolidayCalendarFactory.buildReviewedCalendar;
import static com.bts.personalbudget.core.domain.factory.IncomeScheduleFactory.SCHEDULE_CODE;
import static com.bts.personalbudget.core.domain.factory.IncomeScheduleFactory.buildInactive;
import static com.bts.personalbudget.core.domain.factory.IncomeScheduleFactory.buildOccurrence;
import static com.bts.personalbudget.core.domain.factory.IncomeScheduleFactory.buildSchedule;
import static com.bts.personalbudget.core.domain.factory.PayCycleFactory.buildCycle;
import static com.bts.personalbudget.core.domain.factory.PayCycleFactory.buildEndAdjusted;
import static com.bts.personalbudget.core.domain.factory.PayCycleFactory.buildStartAdjusted;
import static com.bts.personalbudget.core.domain.factory.PayCycleFactory.buildTransition;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bts.personalbudget.core.domain.enumerator.BusinessWeek;
import com.bts.personalbudget.core.domain.enumerator.NonBusinessDayPolicy;
import com.bts.personalbudget.core.domain.enumerator.PayCycleKind;
import com.bts.personalbudget.core.domain.service.calendar.HolidayCalendar;
import com.bts.personalbudget.core.domain.service.calendar.IncomeDateRule;
import com.bts.personalbudget.core.domain.service.income.IncomeOccurrence;
import com.bts.personalbudget.core.domain.service.income.IncomeSchedule;
import com.bts.personalbudget.core.domain.service.paycycle.PayCyclePending.Type;
import com.bts.personalbudget.core.domain.service.paycycle.PayCyclePlanner.Request;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The schedule is "day 5, postponed to the next business day" (Monday to Friday), so the anchor
 * dates are: Nov 5 2026 (Thu), Dec 7 2026 (the 5th is a Saturday), Jan 5 2027 (Tue), Feb 5 (Fri),
 * Mar 5 (Fri), Apr 5 (Mon), May 5 (Wed).
 */
class PayCyclePlannerTest {

    private static final YearMonth NOV = YearMonth.of(2026, 11);
    private static final YearMonth DEC = YearMonth.of(2026, 12);
    private static final YearMonth JAN = YearMonth.of(2027, 1);
    private static final YearMonth FEB = YearMonth.of(2027, 2);

    private static final LocalDate NOV_5 = LocalDate.of(2026, 11, 5);
    private static final LocalDate DEC_7 = LocalDate.of(2026, 12, 7);
    private static final LocalDate JAN_5 = LocalDate.of(2027, 1, 5);
    private static final LocalDate FEB_5 = LocalDate.of(2027, 2, 5);
    private static final LocalDate MAR_5 = LocalDate.of(2027, 3, 5);

    private static final HolidayCalendar CALENDAR = buildReviewedCalendar(2026, 2027, 2028);

    private final IncomeSchedule schedule = buildSchedule(NOV);

    private PayCyclePlan activate(final LocalDate initial, final LocalDate today) {
        return activate(schedule, List.of(), CALENDAR, initial, today);
    }

    private PayCyclePlan activate(final IncomeSchedule anchor,
                                  final List<IncomeOccurrence> materialized,
                                  final HolidayCalendar calendar,
                                  final LocalDate initial,
                                  final LocalDate today) {
        return PayCyclePlanner.plan(new Request(anchor, materialized, calendar, initial, List.of(), today));
    }

    private PayCyclePlan replan(final List<PayCycle> existing, final HolidayCalendar calendar, final LocalDate today) {
        return PayCyclePlanner.plan(new Request(schedule, List.of(), calendar, existing.get(0).startDate(), existing, today));
    }

    private static void assertContiguous(final List<PayCycle> cycles) {
        for (int i = 1; i < cycles.size(); i++) {
            assertEquals(cycles.get(i - 1).endDate(), cycles.get(i).startDate());
        }
    }

    // --- activation ---

    @Test
    void shouldStartARegularCycleWhenTheInitialDateIsAnAnchorDate() {
        final PayCyclePlan plan = activate(NOV_5, LocalDate.of(2026, 11, 2));

        final PayCycle first = plan.cycles().get(0);
        assertEquals(PayCycleKind.REGULAR, first.kind());
        assertEquals(NOV_5, first.startDate());
        assertEquals(DEC_7, first.endDate());
        assertEquals(NOV, first.anchorNominalMonth());
        assertEquals(SCHEDULE_CODE, first.anchorScheduleCode());
        assertTrue(plan.pending().isEmpty());
    }

    @Test
    void shouldKeepTheFirstCycleAndTwoMoreWhenTheFirstHasNotStarted() {
        final PayCyclePlan plan = activate(NOV_5, LocalDate.of(2026, 11, 2));

        assertEquals(3, plan.cycles().size());
        assertEquals(DEC_7, plan.cycles().get(1).startDate());
        assertEquals(JAN_5, plan.cycles().get(1).endDate());
        assertEquals(DEC, plan.cycles().get(1).anchorNominalMonth());
        assertEquals(JAN_5, plan.cycles().get(2).startDate());
        assertEquals(FEB_5, plan.cycles().get(2).endDate());
        assertContiguous(plan.cycles());
    }

    @Test
    void shouldStartATransitionWhenTheInitialDateIsNotAnAnchorDate() {
        final PayCyclePlan plan = activate(LocalDate.of(2026, 11, 20), LocalDate.of(2026, 11, 20));

        final PayCycle first = plan.cycles().get(0);
        assertEquals(PayCycleKind.TRANSITION, first.kind());
        assertEquals(LocalDate.of(2026, 11, 20), first.startDate());
        assertEquals(DEC_7, first.endDate());
        assertNull(first.anchorNominalMonth());
        assertNull(first.anchorScheduleCode());
    }

    @Test
    void shouldFindTheFirstAnchorWhenTheScheduleStartsAfterTheDefaultSearchWindow() {
        final IncomeSchedule futureSchedule = buildSchedule(
                IncomeDateRule.fixedDay(5, NonBusinessDayPolicy.KEEP, BusinessWeek.MONDAY_TO_FRIDAY),
                YearMonth.of(2027, 3), null);

        final PayCyclePlan plan = activate(futureSchedule, List.of(), CALENDAR,
                LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 2));

        assertEquals(PayCycleKind.TRANSITION, plan.cycles().get(0).kind());
        assertEquals(LocalDate.of(2026, 11, 2), plan.cycles().get(0).startDate());
        assertEquals(MAR_5, plan.cycles().get(0).endDate());
        assertEquals(YearMonth.of(2027, 3), plan.cycles().get(1).anchorNominalMonth());
    }

    @Test
    void shouldFindTheFirstAnchorAfterAnExistingTransitionWhenScheduleStartsLater() {
        final IncomeSchedule futureSchedule = buildSchedule(
                IncomeDateRule.fixedDay(5, NonBusinessDayPolicy.KEEP, BusinessWeek.MONDAY_TO_FRIDAY),
                YearMonth.of(2027, 3), null);
        final PayCycle transition = buildTransition(LocalDate.of(2026, 11, 2), MAR_5);

        final PayCyclePlan plan = PayCyclePlanner.plan(new Request(futureSchedule, List.of(), CALENDAR,
                transition.startDate(), List.of(transition), LocalDate.of(2026, 11, 2)));

        assertEquals(YearMonth.of(2027, 3), plan.cycles().get(1).anchorNominalMonth());
        assertEquals(MAR_5, plan.cycles().get(1).startDate());
    }

    @Test
    void shouldOpenTheCycleAfterATransitionWithTheOccurrenceThatEndedIt() {
        final PayCyclePlan plan = activate(LocalDate.of(2026, 11, 20), LocalDate.of(2026, 11, 20));

        assertEquals(3, plan.cycles().size());
        final PayCycle second = plan.cycles().get(1);
        assertEquals(PayCycleKind.REGULAR, second.kind());
        assertEquals(DEC_7, second.startDate());
        assertEquals(JAN_5, second.endDate());
        assertEquals(DEC, second.anchorNominalMonth());
        assertContiguous(plan.cycles());
        assertTrue(plan.pending().isEmpty());
    }

    @Test
    void shouldNotReconstructAnythingBeforeTheInitialDate() {
        final PayCyclePlan plan = activate(NOV_5, LocalDate.of(2026, 11, 20));

        assertEquals(NOV_5, plan.cycles().get(0).startDate());
    }

    @Test
    void shouldBelongTheLaunchOnTheBoundaryDayToTheNextCycle() {
        final PayCyclePlan plan = activate(NOV_5, LocalDate.of(2026, 11, 10));

        final PayCycle first = plan.cycles().get(0);
        final PayCycle second = plan.cycles().get(1);
        assertFalse(first.contains(DEC_7));
        assertTrue(second.contains(DEC_7));
        assertTrue(first.contains(DEC_7.minusDays(1)));
    }

    @Test
    void shouldKeepTheCurrentCycleAndTwoMoreWhenTheFirstAlreadyStarted() {
        final PayCyclePlan plan = activate(NOV_5, LocalDate.of(2026, 11, 20));

        assertEquals(3, plan.cycles().size());
        assertEquals(PayCycleKind.REGULAR, plan.cycles().get(2).kind());
        assertEquals(FEB_5, plan.cycles().get(2).endDate());
    }

    @Test
    void shouldStartOnAnAnchorDateThatPostponementPushedIntoTheNextMonth() {
        // Day 31 postponed: Jan 31 2027 is a Sunday, so the January occurrence lands on Mon Feb 1.
        final IncomeSchedule lastDay = buildSchedule(IncomeDateRule.fixedDay(31,
                com.bts.personalbudget.core.domain.enumerator.NonBusinessDayPolicy.POSTPONE,
                BusinessWeek.MONDAY_TO_FRIDAY), YearMonth.of(2027, 1), null);

        final PayCyclePlan plan = activate(lastDay, List.of(), CALENDAR, LocalDate.of(2027, 2, 1), LocalDate.of(2027, 2, 1));

        final PayCycle first = plan.cycles().get(0);
        assertEquals(PayCycleKind.REGULAR, first.kind());
        assertEquals(YearMonth.of(2027, 1), first.anchorNominalMonth());
        assertEquals(LocalDate.of(2027, 3, 1), first.endDate());
    }

    @Test
    void shouldIgnoreAnUnreviewedPreviousMonthWhenActivatingNthBusinessDayAnchor() {
        final IncomeSchedule nthBusinessDay = buildSchedule(
                IncomeDateRule.nthBusinessDay(5, BusinessWeek.MONDAY_TO_FRIDAY), YearMonth.of(2026, 1), null);

        final PayCyclePlan plan = activate(nthBusinessDay, List.of(), buildReviewedCalendar(2027),
                LocalDate.of(2027, 1, 8), LocalDate.of(2027, 1, 8));

        assertEquals(3, plan.cycles().size());
        assertEquals(PayCycleKind.TRANSITION, plan.cycles().get(0).kind());
        assertEquals(LocalDate.of(2027, 2, 5), plan.cycles().get(0).endDate());
        assertTrue(plan.pending().isEmpty());
    }

    @Test
    void shouldIgnoreAnUnreviewedPreviousMonthWhenFindingNextDateForTransition() {
        final IncomeSchedule nthBusinessDay = buildSchedule(
                IncomeDateRule.nthBusinessDay(5, BusinessWeek.MONDAY_TO_FRIDAY), YearMonth.of(2026, 1), null);
        final PayCycle transition = buildTransition(LocalDate.of(2027, 1, 8), LocalDate.of(2027, 2, 5));

        final PayCyclePlan plan = PayCyclePlanner.plan(new Request(nthBusinessDay, List.of(),
                buildReviewedCalendar(2027), transition.startDate(), List.of(transition), LocalDate.of(2027, 1, 8)));

        assertEquals(3, plan.cycles().size());
        assertEquals(LocalDate.of(2027, 2, 5), plan.cycles().get(0).endDate());
        assertTrue(plan.pending().isEmpty());
    }

    // --- dates that cannot be calculated ---

    @Test
    void shouldNotCreateACycleWhoseEndDependsOnAYearThatWasNotReviewed() {
        final PayCyclePlan plan = activate(schedule, List.of(), buildReviewedCalendar(2026), DEC_7, LocalDate.of(2026, 12, 7));

        assertTrue(plan.cycles().isEmpty());
        assertEquals(List.of(PayCyclePending.calendarNotReviewed(JAN, 2027)), plan.pending());
    }

    @Test
    void shouldStopTheChainAtTheFirstMonthWhoseYearWasNotReviewed() {
        final PayCyclePlan plan = activate(schedule, List.of(), buildReviewedCalendar(2026), NOV_5, LocalDate.of(2026, 11, 6));

        assertEquals(1, plan.cycles().size());
        assertEquals(DEC_7, plan.cycles().get(0).endDate());
        assertTrue(plan.pending().contains(PayCyclePending.calendarNotReviewed(JAN, 2027)));
    }

    @Test
    void shouldReportThereIsNoCurrentCycleWhenTheChainCannotReachToday() {
        final PayCyclePlan plan = activate(schedule, List.of(), buildReviewedCalendar(2026), NOV_5, LocalDate.of(2026, 12, 20));

        assertEquals(1, plan.cycles().size());
        assertTrue(plan.pending().contains(PayCyclePending.noCurrentCycle()));
        assertTrue(plan.pending().contains(PayCyclePending.calendarNotReviewed(JAN, 2027)));
    }

    @Test
    void shouldNotReportANoCurrentCycleBeforeTheFirstCycleStarts() {
        final PayCyclePlan plan = activate(NOV_5, LocalDate.of(2026, 11, 2));

        assertFalse(plan.pending().contains(PayCyclePending.noCurrentCycle()));
    }

    @Test
    void shouldReportWhenTheAnchorScheduleEndsBeforeTheHorizon() {
        final IncomeSchedule endsInDecember = buildSchedule(
                IncomeDateRule.fixedDay(5, com.bts.personalbudget.core.domain.enumerator.NonBusinessDayPolicy.POSTPONE,
                        BusinessWeek.MONDAY_TO_FRIDAY), NOV, DEC);

        final PayCyclePlan plan = activate(endsInDecember, List.of(), CALENDAR, NOV_5, LocalDate.of(2026, 11, 6));

        assertEquals(1, plan.cycles().size());
        assertEquals(List.of(PayCyclePending.anchorScheduleEnds(JAN)), plan.pending());
    }

    @Test
    void shouldReportWhenTheAnchorScheduleIsInactive() {
        final PayCyclePlan plan = activate(buildInactive(schedule), List.of(), CALENDAR, NOV_5, LocalDate.of(2026, 11, 6));

        assertTrue(plan.cycles().isEmpty());
        assertEquals(Type.ANCHOR_SCHEDULE_ENDS, plan.pending().get(0).type());
    }

    @Test
    void shouldReportABusinessDayThatTheMonthDoesNotHave() {
        // The 15th business day of December 2026 does not exist once most of the month is holiday.
        final IncomeSchedule fifteenth = buildSchedule(IncomeDateRule.nthBusinessDay(15, BusinessWeek.MONDAY_TO_FRIDAY), NOV, null);
        final HolidayCalendar holidays = buildCalendar(Set.of(2026, 2027),
                buildHoliday(2026, 12, 1), buildHoliday(2026, 12, 2), buildHoliday(2026, 12, 3),
                buildHoliday(2026, 12, 4), buildHoliday(2026, 12, 7), buildHoliday(2026, 12, 8),
                buildHoliday(2026, 12, 9), buildHoliday(2026, 12, 10), buildHoliday(2026, 12, 11),
                buildHoliday(2026, 12, 14), buildHoliday(2026, 12, 15), buildHoliday(2026, 12, 16),
                buildHoliday(2026, 12, 17), buildHoliday(2026, 12, 18), buildHoliday(2026, 12, 21),
                buildHoliday(2026, 12, 22), buildHoliday(2026, 12, 23), buildHoliday(2026, 12, 24),
                buildHoliday(2026, 12, 25), buildHoliday(2026, 12, 28), buildHoliday(2026, 12, 29),
                buildHoliday(2026, 12, 30), buildHoliday(2026, 12, 31));

        final PayCyclePlan plan = activate(fifteenth, List.of(), holidays, LocalDate.of(2026, 11, 20), LocalDate.of(2026, 11, 20));

        assertTrue(plan.pending().stream().anyMatch(p -> p.type() == Type.BUSINESS_DAY_NOT_FOUND));
    }

    // --- frozen and free boundaries ---

    @Test
    void shouldNotMoveTheBoundaryOfACycleThatAlreadyStarted() {
        final PayCyclePlan first = activate(NOV_5, LocalDate.of(2026, 11, 10));
        // A holiday on Dec 7 now moves the December occurrence to Dec 8.
        final HolidayCalendar changed = buildCalendar(Set.of(2026, 2027, 2028), buildHoliday(2026, 12, 7));

        final PayCyclePlan plan = replan(first.cycles(), changed, LocalDate.of(2026, 11, 10));

        assertEquals(DEC_7, plan.cycles().get(0).endDate());
        assertEquals(DEC_7, plan.cycles().get(1).startDate());
        assertContiguous(plan.cycles());
    }

    @Test
    void shouldReportADivergenceWhenAFrozenBoundaryStopsMatchingTheAnchor() {
        final PayCyclePlan first = activate(NOV_5, LocalDate.of(2026, 11, 10));
        final HolidayCalendar changed = buildCalendar(Set.of(2026, 2027, 2028), buildHoliday(2026, 12, 7));

        final PayCyclePlan plan = replan(first.cycles(), changed, LocalDate.of(2026, 11, 10));

        assertEquals(List.of(PayCyclePending.boundaryDiverges(
                        plan.cycles().get(1).code(), DEC, DEC_7, LocalDate.of(2026, 12, 8))),
                plan.pending());
    }

    @Test
    void shouldMoveTheBoundaryOfACycleThatHasNotStarted() {
        final PayCyclePlan first = activate(NOV_5, LocalDate.of(2026, 11, 10));
        // A holiday on Jan 5 moves the January occurrence to Jan 6: the December cycle has not started.
        final HolidayCalendar changed = buildCalendar(Set.of(2026, 2027, 2028), buildHoliday(2027, 1, 5));

        final PayCyclePlan plan = replan(first.cycles(), changed, LocalDate.of(2026, 11, 10));

        assertEquals(LocalDate.of(2027, 1, 6), plan.cycles().get(1).endDate());
        assertEquals(LocalDate.of(2027, 1, 6), plan.cycles().get(2).startDate());
        assertEquals(FEB_5, plan.cycles().get(2).endDate());
        assertContiguous(plan.cycles());
        assertTrue(plan.pending().isEmpty());
    }

    @Test
    void shouldKeepTheCodesWhenABoundaryMoves() {
        final PayCyclePlan first = activate(NOV_5, LocalDate.of(2026, 11, 10));
        final HolidayCalendar changed = buildCalendar(Set.of(2026, 2027, 2028), buildHoliday(2027, 1, 5));

        final PayCyclePlan plan = replan(first.cycles(), changed, LocalDate.of(2026, 11, 10));

        for (int i = 0; i < 3; i++) {
            assertEquals(first.cycles().get(i).code(), plan.cycles().get(i).code());
        }
    }

    @Test
    void shouldKeepTheActivationDateAsTheStartOfTheFirstCycle() {
        final PayCyclePlan first = activate(NOV_5, LocalDate.of(2026, 11, 2));
        // A holiday on Nov 5 moves the November occurrence to Nov 6, but the user chose Nov 5.
        final HolidayCalendar changed = buildCalendar(Set.of(2026, 2027, 2028), buildHoliday(2026, 11, 5));

        final PayCyclePlan plan = replan(first.cycles(), changed, LocalDate.of(2026, 11, 2));

        assertEquals(NOV_5, plan.cycles().get(0).startDate());
        assertEquals(List.of(PayCyclePending.boundaryDiverges(
                        plan.cycles().get(0).code(), NOV, NOV_5, LocalDate.of(2026, 11, 6))),
                plan.pending());
    }

    @Test
    void shouldNotMoveABoundaryThatWasAdjustedByHand() {
        final PayCyclePlan first = activate(NOV_5, LocalDate.of(2026, 11, 10));
        final PayCycle december = first.cycles().get(1);
        final PayCycle adjusted = buildEndAdjusted(december, LocalDate.of(2027, 1, 8));
        final PayCycle january = first.cycles().get(2).withDates(LocalDate.of(2027, 1, 8), FEB_5);
        final List<PayCycle> existing = List.of(first.cycles().get(0), adjusted, january);

        final PayCyclePlan plan = replan(existing, CALENDAR, LocalDate.of(2026, 11, 10));

        assertEquals(LocalDate.of(2027, 1, 8), plan.cycles().get(1).endDate());
        assertEquals(LocalDate.of(2027, 1, 8), plan.cycles().get(2).startDate());
        assertTrue(plan.pending().stream().anyMatch(p -> p.type() == Type.BOUNDARY_DIVERGES_FROM_ANCHOR));
    }

    @Test
    void shouldNotMoveABoundaryWhoseNextStartWasAdjustedByHand() {
        final PayCyclePlan first = activate(NOV_5, LocalDate.of(2026, 11, 10));
        final PayCycle december = first.cycles().get(1).withDates(DEC_7, LocalDate.of(2027, 1, 8));
        final PayCycle january = buildStartAdjusted(first.cycles().get(2), LocalDate.of(2027, 1, 8));
        final List<PayCycle> existing = List.of(first.cycles().get(0), december, january.withDates(LocalDate.of(2027, 1, 8), FEB_5));

        final PayCyclePlan plan = replan(existing, CALENDAR, LocalDate.of(2026, 11, 10));

        assertEquals(LocalDate.of(2027, 1, 8), plan.cycles().get(1).endDate());
    }

    @Test
    void shouldFollowTheCalculatedDateOfAnOccurrenceNotMaterializedYet() {
        final PayCyclePlan first = activate(NOV_5, LocalDate.of(2026, 11, 2));
        final HolidayCalendar changed = buildCalendar(Set.of(2026, 2027, 2028), buildHoliday(2026, 12, 7));

        final PayCyclePlan plan = replan(first.cycles(), changed, LocalDate.of(2026, 11, 2));

        // Nothing started: the first boundary follows the calculated December date.
        assertEquals(LocalDate.of(2026, 12, 8), plan.cycles().get(0).endDate());
        assertEquals(LocalDate.of(2026, 12, 8), plan.cycles().get(1).startDate());
        assertTrue(plan.pending().isEmpty());
    }

    @Test
    void shouldUseTheFrozenDateOfAMaterializedOccurrenceInsteadOfTheCalendar() {
        final HolidayCalendar holiday = buildCalendar(Set.of(2026, 2027, 2028), buildHoliday(2026, 12, 7));
        final IncomeOccurrence december = buildOccurrence(DEC, DEC_7);

        final PayCyclePlan withoutMaterialization = activate(schedule, List.of(), holiday, NOV_5, LocalDate.of(2026, 11, 2));
        final PayCyclePlan withMaterialization = activate(schedule, List.of(december), holiday, NOV_5, LocalDate.of(2026, 11, 2));

        assertEquals(LocalDate.of(2026, 12, 8), withoutMaterialization.cycles().get(0).endDate());
        assertEquals(DEC_7, withMaterialization.cycles().get(0).endDate());
    }

    @Test
    void shouldNotMoveABoundaryBecauseTheReceiptCameEarly() {
        // An early receipt materializes the December occurrence with the date it was planned for.
        final PayCyclePlan before = activate(NOV_5, LocalDate.of(2026, 11, 10));

        final PayCyclePlan after = PayCyclePlanner.plan(new Request(schedule, List.of(buildOccurrence(DEC, DEC_7)),
                CALENDAR, NOV_5, before.cycles(), LocalDate.of(2026, 11, 10)));

        assertEquals(before.cycles(), after.cycles());
        assertTrue(after.pending().isEmpty());
    }

    @Test
    void shouldNotInventAnEmptyCycleWhenTheAnchorDatesGoBackwardsPastAFrozenBoundary() {
        // The November cycle started, so its end (Dec 7) is frozen. January's occurrence was
        // materialized on Dec 3: the December cycle would have to end before it starts.
        final PayCyclePlan first = activate(NOV_5, LocalDate.of(2026, 11, 10));
        final PayCycle novemberOnly = first.cycles().get(0);

        final PayCyclePlan plan = PayCyclePlanner.plan(new Request(schedule,
                List.of(buildOccurrence(JAN, LocalDate.of(2026, 12, 3))), CALENDAR, NOV_5, List.of(novemberOnly),
                LocalDate.of(2026, 11, 10)));

        assertEquals(1, plan.cycles().size());
        assertTrue(plan.pending().stream().anyMatch(p -> p.type() == Type.BOUNDARY_DIVERGES_FROM_ANCHOR));
    }

    // --- horizon and idempotence ---

    @Test
    void shouldChangeNothingWhenPlannedTwiceWithTheSameData() {
        final PayCyclePlan first = activate(NOV_5, LocalDate.of(2026, 11, 10));

        final PayCyclePlan second = replan(first.cycles(), CALENDAR, LocalDate.of(2026, 11, 10));

        assertEquals(first.cycles(), second.cycles());
        for (int i = 0; i < first.cycles().size(); i++) {
            assertTrue(first.cycles().get(i) == second.cycles().get(i));
        }
        assertEquals(first.pending(), second.pending());
    }

    @Test
    void shouldAddACycleWhenTheDayAdvancesIntoTheNextOne() {
        final PayCyclePlan first = activate(NOV_5, LocalDate.of(2026, 11, 10));
        assertEquals(3, first.cycles().size());

        final PayCyclePlan nextCycle = replan(first.cycles(), CALENDAR, DEC_7);

        assertEquals(4, nextCycle.cycles().size());
        assertEquals(FEB_5, nextCycle.cycles().get(3).startDate());
        assertEquals(MAR_5, nextCycle.cycles().get(3).endDate());
        assertEquals(FEB, nextCycle.cycles().get(3).anchorNominalMonth());
        for (int i = 0; i < 3; i++) {
            assertTrue(first.cycles().get(i) == nextCycle.cycles().get(i));
        }
    }

    @Test
    void shouldNotAddACycleWhileTheCurrentOneStillHasTwoAfterIt() {
        final PayCyclePlan first = activate(NOV_5, LocalDate.of(2026, 11, 10));

        final PayCyclePlan later = replan(first.cycles(), CALENDAR, DEC_7.minusDays(1));

        assertEquals(3, later.cycles().size());
    }

    @Test
    void shouldCatchUpSeveralCyclesAfterALongOutage() {
        final PayCyclePlan first = activate(NOV_5, LocalDate.of(2026, 11, 10));

        final PayCyclePlan catchUp = replan(first.cycles(), CALENDAR, LocalDate.of(2027, 3, 10));

        assertContiguous(catchUp.cycles());
        final PayCycle current = catchUp.cycles().stream()
                .filter(cycle -> cycle.contains(LocalDate.of(2027, 3, 10))).findFirst().orElseThrow();
        assertEquals(MAR_5, current.startDate());
        assertEquals(catchUp.cycles().indexOf(current) + 3, catchUp.cycles().size());
    }

    @Test
    void shouldExtendATransitionThatNoOccurrenceOpened() {
        final PayCyclePlan first = activate(LocalDate.of(2026, 11, 20), LocalDate.of(2026, 11, 20));
        final PayCyclePlan again = replan(first.cycles(), CALENDAR, DEC_7);

        assertEquals(4, again.cycles().size());
        assertEquals(FEB, again.cycles().get(3).anchorNominalMonth());
    }

    @Test
    void shouldRejectAChainThatIsNotContiguous() {
        final List<PayCycle> broken = List.of(
                buildCycle(NOV_5, DEC_7, NOV), buildCycle(DEC_7.plusDays(1), JAN_5, DEC));

        assertThrows(IllegalStateException.class, () -> replan(broken, CALENDAR, LocalDate.of(2026, 11, 10)));
    }

    @Test
    void shouldNotCompareATransitionWithoutAnchorWithAnyOccurrence() {
        // A transition has no anchor occurrence of its own to compare with.
        final PayCycle transition = buildTransition(LocalDate.of(2026, 11, 20), DEC_7);

        final PayCyclePlan plan = replan(List.of(transition), CALENDAR, LocalDate.of(2026, 11, 20));

        assertTrue(plan.pending().isEmpty());
        assertEquals(DEC, plan.cycles().get(1).anchorNominalMonth());
    }

    @Test
    void shouldKeepEveryBoundaryWhenTheRecalculationWouldInvertACycle() {
        final PayCyclePlan first = activate(NOV_5, LocalDate.of(2026, 11, 10));
        // January's occurrence was materialized on Dec 3: following it would end the December cycle
        // (which starts on the frozen Dec 7) before its start, so nothing moves.
        final IncomeOccurrence january = buildOccurrence(JAN, LocalDate.of(2026, 12, 3));

        final PayCyclePlan plan = PayCyclePlanner.plan(new Request(schedule, List.of(january), CALENDAR, NOV_5,
                first.cycles(), LocalDate.of(2026, 11, 10)));

        assertEquals(first.cycles(), plan.cycles());
        assertTrue(plan.pending().stream().anyMatch(p -> p.type() == Type.BOUNDARY_DIVERGES_FROM_ANCHOR));
    }
}
