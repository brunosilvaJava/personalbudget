package com.bts.personalbudget.core.domain.service.cardinvoice;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/** The payment of a whole invoice. {@code reversedAt} is null while the payment is active. */
public record CardInvoicePayment(
        UUID code,
        UUID invoiceCode,
        UUID accountCode,
        LocalDate paymentDate,
        BigDecimal amount,
        LocalDateTime reversedAt,
        Long version
) {
}
