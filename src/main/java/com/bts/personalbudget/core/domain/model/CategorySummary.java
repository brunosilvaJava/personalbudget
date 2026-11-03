package com.bts.personalbudget.core.domain.model;

import com.bts.personalbudget.core.domain.enumerator.CategoryStatus;
import com.bts.personalbudget.core.domain.enumerator.OperationType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Totals of one operation type in a period, by group and category. Values keep the balance sign
 * convention (debits negative); the percentages are always positive.
 *
 * @param total          what was paid in the period
 * @param projectedTotal {@code total} plus what is still pending in the period
 */
public record CategorySummary(
        LocalDate startDate,
        LocalDate endDate,
        OperationType operationType,
        BigDecimal total,
        BigDecimal projectedTotal,
        List<Group> groups
) {

    /**
     * @param code         null for the "Sem categoria" row, which is its own group
     * @param displayOrder the group's display_order; the "Sem categoria" row sorts last
     */
    public record Group(
            UUID code,
            String name,
            Integer displayOrder,
            BigDecimal total,
            BigDecimal projectedTotal,
            BigDecimal percent,
            BigDecimal projectedPercent,
            List<Line> categories
    ) {
    }

    /**
     * @param code   null for the "Sem categoria" row
     * @param status null for the "Sem categoria" row
     */
    public record Line(
            UUID code,
            String name,
            CategoryStatus status,
            BigDecimal total,
            BigDecimal projectedTotal,
            BigDecimal percent,
            BigDecimal projectedPercent
    ) {
    }

}
