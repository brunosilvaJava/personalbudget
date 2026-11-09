package com.bts.personalbudget.core.domain.model;

import com.bts.personalbudget.core.domain.enumerator.CreditCardStatus;
import java.math.BigDecimal;
import java.util.UUID;

public record CreditCard(
        UUID code,
        String name,
        Integer closingDay,
        Integer dueDay,
        BigDecimal creditLimit,
        UUID defaultAccountCode,
        CreditCardStatus status
) {
}
