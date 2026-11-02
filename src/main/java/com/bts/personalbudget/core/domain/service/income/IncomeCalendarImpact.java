package com.bts.personalbudget.core.domain.service.income;

import com.bts.personalbudget.core.domain.service.income.IncomeOccurrenceResolver.IncomeOccurrenceChange;
import java.util.UUID;

/**
 * What reviewing a calendar year would do to one occurrence that is not materialized yet.
 */
public record IncomeCalendarImpact(UUID scheduleCode, String scheduleDescription, IncomeOccurrenceChange change) {
}
