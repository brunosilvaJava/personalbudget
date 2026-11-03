package com.bts.personalbudget.core.domain.service.income;

import java.util.UUID;

/**
 * An occurrence of an income schedule that a change of pay cycle boundary would take from one cycle to
 * the other, as the user sees it before confirming: materialized (a movement exists) or only
 * projected from the rule.
 */
public record IncomeOccurrenceImpact(UUID scheduleCode, String scheduleDescription, IncomeOccurrenceView occurrence) {
}
