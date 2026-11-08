package com.bts.personalbudget.core.domain.service.paycycle;

import com.bts.personalbudget.core.domain.enumerator.OperationType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

public record PayCycleSummary(
        PayCycleView cycle,
        BigDecimal recognizedRevenue,
        BigDecimal recognizedExpense,
        BigDecimal projectedRevenue,
        BigDecimal projectedExpense,
        List<CategoryDetail> categories,
        List<Pending> pending
) {

    public boolean projectionIncomplete() {
        return !pending.isEmpty();
    }

    public record CategoryDetail(
            UUID categoryCode,
            String categoryName,
            OperationType operationType,
            BigDecimal recognizedAmount,
            BigDecimal projectedAmount
    ) {
    }

    public record Pending(
            Source source,
            Type type,
            UUID sourceCode,
            YearMonth nominalMonth,
            Integer year,
            LocalDate boundaryDate,
            LocalDate anchorDate
    ) {
        public enum Source {
            PAY_CYCLE,
            INCOME_SCHEDULE
        }

        public enum Type {
            CALENDAR_NOT_REVIEWED,
            BUSINESS_DAY_NOT_FOUND,
            ANCHOR_SCHEDULE_ENDS,
            BOUNDARY_DIVERGES_FROM_ANCHOR,
            NO_CURRENT_CYCLE
        }
    }
}
