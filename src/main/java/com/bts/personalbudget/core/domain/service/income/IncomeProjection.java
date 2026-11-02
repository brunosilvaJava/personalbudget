package com.bts.personalbudget.core.domain.service.income;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * An occurrence that was not materialized yet, as the balance projection needs it. A CREDIT, with
 * the schedule's positive amount.
 */
public record IncomeProjection(UUID scheduleCode, LocalDate date, BigDecimal amount, UUID categoryCode) {
}
