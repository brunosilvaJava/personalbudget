package com.bts.personalbudget.core.domain.service.thirdparty;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** An amount a third party did not pay and the user absorbed: an own expense on {@code effectiveDate}. */
public record ReceivableWriteOff(UUID code, UUID categoryCode, BigDecimal amount, LocalDate effectiveDate) {
}
