package com.bts.personalbudget.core.domain.service.income;

import java.util.List;

public record IncomeProjectionWindow(
        List<IncomeProjection> occurrences,
        List<IncomeProjectionPending> pending
) {
}
