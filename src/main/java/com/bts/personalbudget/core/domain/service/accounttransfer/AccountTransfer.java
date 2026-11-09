package com.bts.personalbudget.core.domain.service.accounttransfer;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record AccountTransfer(
        UUID code,
        UUID fromAccountCode,
        UUID toAccountCode,
        BigDecimal amount,
        LocalDate effectiveDate,
        String description,
        LocalDateTime reversedAt,
        Long version
) {
}
