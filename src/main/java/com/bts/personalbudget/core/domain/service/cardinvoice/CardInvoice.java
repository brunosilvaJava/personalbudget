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
        List<CardInstallment> installments,
        List<HolderTotal> holderTotalList,
        BigDecimal ownTotalAmount,
        BigDecimal thirdPartyTotalAmount
) {
    public CardInvoice(final UUID code, final UUID cardCode, final YearMonth referenceMonth, final LocalDate closingDate,
                       final LocalDate dueDate, final CardInvoiceState state, final BigDecimal totalAmount,
                       final BigDecimal paidAmount, final List<CardInstallment> installments) {
        this(code, cardCode, referenceMonth, closingDate, dueDate, state, totalAmount, paidAmount, installments,
                List.of(), totalAmount, BigDecimal.ZERO);
    }

    public record HolderTotal(UUID holderCode, String holderName,
                              com.bts.personalbudget.core.domain.enumerator.CreditCardHolderKind kind,
                              BigDecimal totalAmount) { }
}
