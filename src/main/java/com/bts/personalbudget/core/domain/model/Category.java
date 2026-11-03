package com.bts.personalbudget.core.domain.model;

import com.bts.personalbudget.core.domain.enumerator.CategoryStatus;
import com.bts.personalbudget.core.domain.enumerator.OperationType;
import java.util.UUID;

public record Category(
        UUID code,
        String name,
        OperationType operationType,
        CategoryStatus status,
        UUID groupCode,
        String groupName
) {
}
