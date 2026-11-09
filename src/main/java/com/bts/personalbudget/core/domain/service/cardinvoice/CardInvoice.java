package com.bts.personalbudget.core.domain.service.cardinvoice;

import com.bts.personalbudget.core.domain.enumerator.CardInvoiceState;
import com.bts.personalbudget.core.domain.service.cardpurchase.CardInstallment;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/**
 * An invoice of a card: its dates, derived state, totals (positive) and, in the detail only, its
 * installments. {@code installments} is empty in a listing.
 */
public record CardInvoice(
        UUID code,
        UUID cardCode,
        YearMonth referenceMonth,
        LocalDate closingDate,
        LocalDate dueDate,
        CardInvoiceState state,
        BigDecimal totalAmount,
        BigDecimal paidAmount,
        List<CardInstallment> installments
) {
}
