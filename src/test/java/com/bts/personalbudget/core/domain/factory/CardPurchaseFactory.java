package com.bts.personalbudget.core.domain.factory;

import com.bts.personalbudget.core.domain.entity.CardInvoiceEntity;
import com.bts.personalbudget.core.domain.entity.CardPurchaseEntity;
import com.bts.personalbudget.core.domain.entity.CreditCardEntity;
import com.bts.personalbudget.core.domain.entity.FinancialMovementEntity;
import com.bts.personalbudget.core.domain.enumerator.FinancialMovementStatus;
import com.bts.personalbudget.core.domain.enumerator.OperationType;
import com.bts.personalbudget.core.domain.service.cardinvoice.CardInvoice;
import com.bts.personalbudget.core.domain.service.cardpurchase.CardInstallment;
import com.bts.personalbudget.core.domain.service.cardpurchase.CardPurchase;
import com.bts.personalbudget.core.domain.enumerator.CardInvoiceState;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/**
 * A card closing on the 25th and due on the 5th (see CreditCardFactory) with a purchase of three
 * installments of 100.00 made on 2026-11-09: invoices of 2026-11 (due 2026-12-05), 2026-12 (due
 * 2027-01-05) and 2027-01 (due 2027-02-05).
 */
public final class CardPurchaseFactory {

    public static final UUID PURCHASE_CODE = UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee");
    public static final UUID INVOICE_CODE = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");
    public static final LocalDate PURCHASE_DATE = LocalDate.of(2026, 11, 9);
    public static final BigDecimal INSTALLMENT_AMOUNT = new BigDecimal("100.00");

    private CardPurchaseFactory() {
    }

    /** What a client sends: three installments of 100.00 on the default card, no code. */
    public static CardPurchase buildNewModel() {
        return new CardPurchase(null, CreditCardFactory.CARD_CODE, "Geladeira", PURCHASE_DATE, 3,
                INSTALLMENT_AMOUNT, null, null, null);
    }

    public static CardPurchaseEntity buildEntity(final CreditCardEntity card) {
        return CardPurchaseEntity.builder()
                .id(10L)
                .code(PURCHASE_CODE)
                .card(card)
                .description("Geladeira")
                .purchaseDate(PURCHASE_DATE)
                .installments(3)
                .installmentAmount(INSTALLMENT_AMOUNT)
                .flagActive(true)
                .version(0L)
                .build();
    }

    /** Invoice k (1-based) of the purchase above: closing on the 25th, due on the 5th of the next month. */
    public static CardInvoiceEntity buildInvoice(final CreditCardEntity card, final int k) {
        final YearMonth reference = YearMonth.of(2026, 10).plusMonths(k);
        return CardInvoiceEntity.builder()
                .id(200L + k)
                .code(UUID.nameUUIDFromBytes(("invoice-" + k).getBytes()))
                .card(card)
                .referenceMonth(reference.atDay(1))
                .closingDate(reference.atDay(25))
                .dueDate(reference.plusMonths(1).atDay(5))
                .build();
    }

    public static FinancialMovementEntity buildInstallment(final CardPurchaseEntity purchase,
                                                           final CardInvoiceEntity invoice, final int k) {
        return FinancialMovementEntity.builder()
                .id(100L + k)
                .code(UUID.nameUUIDFromBytes(("movement-" + k).getBytes()))
                .flagActive(true)
                .operationType(OperationType.DEBIT)
                .description(purchase.getDescription() + " (" + k + "/" + purchase.getInstallments() + ")")
                .amount(purchase.getInstallmentAmount().negate())
                .amountPaid(BigDecimal.ZERO)
                .movementDate(purchase.getPurchaseDate().atStartOfDay())
                .dueDate(invoice.getDueDate().atStartOfDay())
                .status(FinancialMovementStatus.PENDING)
                .competenceDate(invoice.getDueDate())
                .competenceOverridden(true)
                .cardPurchaseId(purchase.getId())
                .cardInvoiceId(invoice.getId())
                .installmentNumber(k)
                .build();
    }

    public static CardInstallment buildInstallmentModel() {
        return new CardInstallment(UUID.nameUUIDFromBytes("movement-1".getBytes()), PURCHASE_CODE, 1, 3,
                INVOICE_CODE, YearMonth.of(2026, 11), "Geladeira (1/3)", INSTALLMENT_AMOUNT, BigDecimal.ZERO,
                LocalDate.of(2026, 12, 5), LocalDate.of(2026, 12, 5), FinancialMovementStatus.PENDING);
    }

    public static CardPurchase buildModel() {
        return new CardPurchase(PURCHASE_CODE, CreditCardFactory.CARD_CODE, "Geladeira", PURCHASE_DATE, 3,
                INSTALLMENT_AMOUNT, new BigDecimal("300.00"), null, List.of(buildInstallmentModel()));
    }

    public static CardInvoice buildInvoiceModel(final List<CardInstallment> installments) {
        return new CardInvoice(INVOICE_CODE, CreditCardFactory.CARD_CODE, YearMonth.of(2026, 11),
                LocalDate.of(2026, 11, 25), LocalDate.of(2026, 12, 5), CardInvoiceState.OPEN,
                new BigDecimal("100.00"), BigDecimal.ZERO, installments);
    }
}
