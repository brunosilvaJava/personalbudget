package com.bts.personalbudget.core.domain.model;

import com.bts.personalbudget.core.domain.enumerator.AccountStatus;
import com.bts.personalbudget.core.domain.enumerator.AccountType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record Account(
        UUID code,
        String name,
        AccountType type,
        BigDecimal initialBalance,
        LocalDate initialBalanceDate,
        AccountStatus status,
        boolean defaultAccount
) {
}
