package com.bts.personalbudget.core.domain.service.paycycle;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * Pure: the rules of moving the boundary between two consecutive cycles by hand (the E4 plan, 4.5).
 * The boundary is the end of the first cycle and, at the same time, the start of the next one, so it
 * moves for both at once and the pair stays contiguous: no gap and no overlap can come out of it.
 *
 * <p>Nothing here reads the database, the clock or the anchor schedule. The caller hands over the two
 * cycles as they are and the new date; it gets back the validation or the pair as it would be.
 */
public final class PayCycleAdjustmentPlanner {

    private PayCycleAdjustmentPlanner() {
    }

    /**
     * The pair after the boundary moved.
     *
     * @param previous    the first cycle, ending (and fixed) at {@code newBoundary}
     * @param next        the second cycle, starting (and fixed) at {@code newBoundary}
     * @param oldBoundary where the boundary was
     * @param newBoundary where it is now
     */
    public record EndAdjustment(PayCycle previous, PayCycle next, LocalDate oldBoundary, LocalDate newBoundary) {

        /**
         * First day whose cycle changes.
         */
        public LocalDate movedFrom() {
            return oldBoundary.isBefore(newBoundary) ? oldBoundary : newBoundary;
        }

        /**
         * The day after the last one whose cycle changes (exclusive): the days that change cycle are
         * {@code [movedFrom, movedUntil)}.
         */
        public LocalDate movedUntil() {
            return oldBoundary.isBefore(newBoundary) ? newBoundary : oldBoundary;
        }

        /**
         * The cycle the moved days belong to before the change: the second one when the boundary moves
         * later (the first cycle grows), the first one when it moves earlier.
         */
        public UUID leavingCycleCode() {
            return newBoundary.isAfter(oldBoundary) ? next.code() : previous.code();
        }

        /**
         * The cycle the moved days belong to after the change.
         */
        public UUID receivingCycleCode() {
            return newBoundary.isAfter(oldBoundary) ? previous.code() : next.code();
        }
    }

    /**
     * Why {@code newEnd} cannot be the new boundary between the two cycles, in words for the user;
     * empty when it can. It has to be a date, differ from the boundary that exists, and leave both
     * cycles with at least one day: {@code start of the first < newEnd < end of the second}.
     */
    public static Optional<String> validate(final PayCycle previous, final PayCycle next, final LocalDate newEnd) {
        requireConsecutive(previous, next);
        if (newEnd == null) {
            return Optional.of("deve ser informada");
        }
        if (newEnd.equals(previous.endDate())) {
            return Optional.of("deve ser diferente do fim atual do ciclo (" + previous.endDate() + ")");
        }
        if (!newEnd.isAfter(previous.startDate()) || !newEnd.isBefore(next.endDate())) {
            return Optional.of("deve estar entre " + previous.startDate().plusDays(1) + " e "
                    + next.endDate().minusDays(1) + ", para que nenhum dos dois ciclos fique vazio");
        }
        return Optional.empty();
    }

    /**
     * The pair with the boundary at {@code newEnd}: the first cycle ends there and the second starts
     * there, both marked as adjusted so the automatic recalculation leaves them alone. Dates, kind,
     * code, reference and version of everything else stay as they were.
     *
     * @throws IllegalArgumentException when {@link #validate} would not accept {@code newEnd}
     */
    public static EndAdjustment adjust(final PayCycle previous, final PayCycle next, final LocalDate newEnd) {
        validate(previous, next, newEnd).ifPresent(message -> {
            throw new IllegalArgumentException("Invalid pay cycle boundary " + newEnd + ": " + message);
        });
        return new EndAdjustment(previous.withEndAdjusted(newEnd), next.withStartAdjusted(newEnd),
                previous.endDate(), newEnd);
    }

    private static void requireConsecutive(final PayCycle previous, final PayCycle next) {
        if (!previous.endDate().equals(next.startDate())) {
            throw new IllegalArgumentException("Pay cycles are not consecutive: " + previous.code()
                    + " ends on " + previous.endDate() + " and " + next.code() + " starts on " + next.startDate());
        }
    }
}
