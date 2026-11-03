package com.bts.personalbudget.core.domain.service.income;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

/**
 * An occurrence of an income schedule that was materialized as a FinancialMovement. Its identity is
 * the schedule plus the nominal month, never the date: a holiday can move the date of an occurrence
 * that was not generated yet, but cannot create a second one for the same month.
 *
 * @param plannedDate   the date the movement was generated for (its due date and competence); frozen
 *                      once materialized, later calendar changes do not rewrite it
 * @param plannedAmount the schedule's amount at the moment of the materialization
 * @param movementCode  the generated movement; stays here even if the movement is soft-deleted, which
 *                      is how a deleted receipt is not generated again
 */
public record IncomeOccurrence(
        UUID code,
        UUID scheduleCode,
        YearMonth nominalMonth,
        LocalDate plannedDate,
        BigDecimal plannedAmount,
        UUID movementCode
) {

    public IncomeOccurrence {
        if (code == null) {
            code = UUID.randomUUID();
        }
    }
}
