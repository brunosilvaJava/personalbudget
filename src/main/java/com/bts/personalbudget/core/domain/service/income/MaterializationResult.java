package com.bts.personalbudget.core.domain.service.income;

/**
 * @param created false when the occurrence of that month already existed and was returned as it was:
 *                materializing is idempotent, so the job and an early receipt can both ask for the
 *                same month without creating a second movement
 */
public record MaterializationResult(IncomeOccurrence occurrence, boolean created) {
}
