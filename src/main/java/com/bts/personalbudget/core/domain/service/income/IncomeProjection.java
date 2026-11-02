package com.bts.personalbudget.core.domain.service.income;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

/**
 * An occurrence that was not materialized yet, as the balance projection needs it. A CREDIT, with
 * the schedule's positive amount. Its identity is the schedule and nominal month, independently of
 * the planned date.
 */
public record IncomeProjection(UUID scheduleCode, YearMonth nominalMonth, LocalDate date,
                               BigDecimal amount, UUID categoryCode) {
}
