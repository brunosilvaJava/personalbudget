package com.bts.personalbudget.core.domain.service.income;

import java.time.YearMonth;
import java.util.UUID;

public record IncomeProjectionPending(Type type, UUID scheduleCode, YearMonth nominalMonth, Integer year) {

    public enum Type {
        CALENDAR_NOT_REVIEWED,
        BUSINESS_DAY_NOT_FOUND
    }
}
