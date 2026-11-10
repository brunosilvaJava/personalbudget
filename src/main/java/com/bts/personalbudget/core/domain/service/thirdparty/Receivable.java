package com.bts.personalbudget.core.domain.service.thirdparty;

import com.bts.personalbudget.core.domain.enumerator.ReceivableStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record Receivable(UUID code, UUID thirdPartyCode, ReceivableStatus status, BigDecimal amount,
                         BigDecimal receivedAmount, BigDecimal writtenOffAmount, BigDecimal remainingAmount,
                         LocalDate dueDate, UUID purchaseCode, Integer installmentNumber, UUID invoiceCode,
                         String description) {
}
