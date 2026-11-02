package com.bts.personalbudget.core.domain.service.fixedbill;

import com.bts.personalbudget.core.domain.enumerator.FixedBillStatus;
import com.bts.personalbudget.core.domain.enumerator.OperationType;
import com.bts.personalbudget.core.domain.enumerator.RecurrenceType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

/**
 * The fields a PATCH on a fixed bill may change. A null component means "keep what is stored",
 * so a category can be replaced but not removed.
 */
public record FixedBillUpdate(
        OperationType operationType,
        String description,
        BigDecimal amount,
        RecurrenceType recurrenceType,
        Set<Integer> days,
        Integer referenceYear,
        FixedBillStatus status,
        LocalDate startDate,
        LocalDate endDate,
        UUID categoryCode
) {

    public boolean isEmpty() {
        return operationType == null
                && description == null
                && amount == null
                && recurrenceType == null
                && days == null
                && referenceYear == null
                && status == null
                && startDate == null
                && endDate == null
                && categoryCode == null;
    }

}
