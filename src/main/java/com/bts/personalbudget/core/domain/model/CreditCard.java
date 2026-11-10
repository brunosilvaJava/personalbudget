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
        UUID defaultThirdPartyCode,
        CreditCardStatus status
) {
    /** Compatibility constructor for callers created before third-party defaults existed. */
    public CreditCard(final UUID code, final String name, final Integer closingDay, final Integer dueDay,
                      final BigDecimal creditLimit, final UUID defaultAccountCode, final CreditCardStatus status) {
        this(code, name, closingDay, dueDay, creditLimit, defaultAccountCode, null, status);
    }
}
