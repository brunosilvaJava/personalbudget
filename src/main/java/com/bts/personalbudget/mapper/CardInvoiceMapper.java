package com.bts.personalbudget.mapper;

import com.bts.personalbudget.controller.cardinvoice.CardInvoiceResponse;
import com.bts.personalbudget.controller.cardpurchase.CardInstallmentResponse;
import com.bts.personalbudget.controller.cardinvoice.CardInvoiceSummaryResponse;
import com.bts.personalbudget.core.domain.entity.CardInvoiceEntity;
import com.bts.personalbudget.core.domain.entity.CardPurchaseEntity;
import com.bts.personalbudget.core.domain.entity.FinancialMovementEntity;
import com.bts.personalbudget.core.domain.enumerator.CardInvoiceState;
import com.bts.personalbudget.core.domain.enumerator.FinancialMovementStatus;
import com.bts.personalbudget.core.domain.service.cardinvoice.CardInvoice;
import com.bts.personalbudget.core.domain.service.cardpurchase.CardInstallment;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface CardInvoiceMapper {

    CardInvoiceResponse toResponse(CardInvoice invoice);

    CardInstallmentResponse toResponse(CardInstallment installment);

    CardInvoiceSummaryResponse toSummaryResponse(CardInvoice invoice);

    List<CardInvoiceSummaryResponse> toSummaryResponseList(List<CardInvoice> invoices);

    /**
     * Totals and the PAID state come from the invoice's active installments (positive amounts); the
     * date decides OPEN or CLOSED. {@code installments} is empty for a listing.
     */
    default CardInvoice assemble(final CardInvoiceEntity invoice, final List<FinancialMovementEntity> movements,
                                 final List<CardInstallment> installments, final java.util.Map<Long, CardPurchaseEntity> purchases,
                                 final LocalDate today) {
        final BigDecimal total = movements.stream().map(movement -> movement.getAmount().abs())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        final BigDecimal paid = movements.stream()
                .map(movement -> movement.getAmountPaid() == null ? BigDecimal.ZERO : movement.getAmountPaid().abs())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        final boolean allPaid = !movements.isEmpty()
                && movements.stream().allMatch(movement -> movement.getStatus() == FinancialMovementStatus.PAID_OUT);
        final java.util.Map<java.util.UUID, HolderAmounts> byHolder = new java.util.LinkedHashMap<>();
        BigDecimal own = BigDecimal.ZERO;
        BigDecimal thirdParty = BigDecimal.ZERO;
        for (final FinancialMovementEntity movement : movements) {
            final BigDecimal amount = movement.getAmount().abs();
            if (movement.getThirdParty() == null) own = own.add(amount); else thirdParty = thirdParty.add(amount);
            final CardPurchaseEntity purchase = purchases.get(movement.getCardPurchaseId());
            if (purchase != null && purchase.getCardHolder() != null) {
                final var holder = purchase.getCardHolder();
                byHolder.compute(holder.getCode(), (code, current) -> current == null
                        ? new HolderAmounts(holder.getName(), holder.getKind(), amount)
                        : new HolderAmounts(current.name(), current.kind(), current.amount().add(amount)));
            }
        }
        return new CardInvoice(
                invoice.getCode(),
                invoice.getCard().getCode(),
                YearMonth.from(invoice.getReferenceMonth()),
                invoice.getClosingDate(),
                invoice.getDueDate(),
                CardInvoiceState.of(invoice.getClosingDate(), today, allPaid),
                total,
                paid,
                installments,
                byHolder.entrySet().stream().map(entry -> new CardInvoice.HolderTotal(entry.getKey(),
                        entry.getValue().name(), entry.getValue().kind(), entry.getValue().amount())).toList(),
                own, thirdParty);
    }

    record HolderAmounts(String name,
                         com.bts.personalbudget.core.domain.enumerator.CreditCardHolderKind kind,
                         BigDecimal amount) { }
}
