package com.bts.personalbudget.core.domain.service.paycycle;

import com.bts.personalbudget.core.domain.model.FinancialMovement;
import com.bts.personalbudget.core.domain.service.income.IncomeOccurrenceImpact;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * What moving the boundary between a cycle and the next one would do, without doing anything.
 *
 * @param before                the two cycles as they are, the one the request names first; their
 *                              versions are the ones the application expects
 * @param after                 the two cycles as they would be, in the same order; the version is still
 *                              the current one, because nothing is saved
 * @param leavingCycleCode      the cycle the moved days belong to today
 * @param receivingCycleCode    the cycle they would belong to
 * @param movements             the active movements whose competence date is in the moved days: they
 *                              change cycle. Movements with no competence date belong to no cycle and
 *                              are not listed
 * @param incomeOccurrences     the occurrences of the active income schedules planned for the moved
 *                              days, materialized or not
 */
public record PayCycleEndAdjustmentPreview(
        LocalDate oldEndDate,
        LocalDate newEndDate,
        List<PayCycleView> before,
        List<PayCycleView> after,
        UUID leavingCycleCode,
        UUID receivingCycleCode,
        List<FinancialMovement> movements,
        List<IncomeOccurrenceImpact> incomeOccurrences
) {
}
