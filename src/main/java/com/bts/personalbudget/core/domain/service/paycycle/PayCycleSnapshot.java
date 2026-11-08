package com.bts.personalbudget.core.domain.service.paycycle;

import com.bts.personalbudget.core.domain.enumerator.OperationType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PayCycleSnapshot(
        UUID code, UUID cycleCode, int snapshotVersion, long cycleVersion, Instant closedAt,
        BigDecimal recognizedRevenue, BigDecimal recognizedExpense,
        BigDecimal projectedRevenue, BigDecimal projectedExpense, List<Category> categories) {

    public PayCycleSnapshot {
        categories = List.copyOf(categories);
    }

    public record Category(UUID categoryCode, String categoryName, OperationType operationType,
                           BigDecimal recognizedAmount, BigDecimal projectedAmount) {
    }
}
