package com.bts.personalbudget.mapper;

import com.bts.personalbudget.controller.cardpurchase.CardInstallmentResponse;
import com.bts.personalbudget.controller.cardpurchase.CardPurchaseRequest;
import com.bts.personalbudget.controller.cardpurchase.CardPurchaseResponse;
import com.bts.personalbudget.core.domain.entity.CardInvoiceEntity;
import com.bts.personalbudget.core.domain.entity.CardPurchaseEntity;
import com.bts.personalbudget.core.domain.entity.FinancialMovementEntity;
import com.bts.personalbudget.core.domain.service.cardpurchase.CardInstallment;
import com.bts.personalbudget.core.domain.service.cardpurchase.CardPurchase;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface CardPurchaseMapper {

    @Mapping(target = "code", ignore = true)
    @Mapping(target = "totalAmount", ignore = true)
    @Mapping(target = "installmentList", ignore = true)
    @Mapping(target = "cardHolderName", ignore = true)
    @Mapping(target = "thirdPartyName", ignore = true)
    @Mapping(target = "importKey", ignore = true)
    CardPurchase toModel(CardPurchaseRequest request);

    CardPurchaseResponse toResponse(CardPurchase purchase);

    List<CardPurchaseResponse> toResponseList(List<CardPurchase> purchases);

    CardInstallmentResponse toResponse(CardInstallment installment);

    /** The installment is a financial movement: amounts come out positive for the client. */
    default CardInstallment assembleInstallment(final FinancialMovementEntity movement,
                                                final CardPurchaseEntity purchase,
                                                final CardInvoiceEntity invoice) {
        final BigDecimal paid = movement.getAmountPaid() == null ? BigDecimal.ZERO : movement.getAmountPaid().abs();
        return new CardInstallment(
                movement.getCode(),
                purchase.getCode(),
                movement.getInstallmentNumber(),
                purchase.getInstallments(),
                invoice.getCode(),
                YearMonth.from(invoice.getReferenceMonth()),
                movement.getDescription(),
                movement.getAmount().abs(),
                paid,
                movement.getDueDate().toLocalDate(),
                movement.getCompetenceDate(),
                movement.getStatus());
    }

    default CardPurchase assemblePurchase(final CardPurchaseEntity purchase,
                                          final List<CardInstallment> installments) {
        return new CardPurchase(
                purchase.getCode(),
                purchase.getCard().getCode(),
                purchase.getDescription(),
                purchase.getPurchaseDate(),
                purchase.getInstallments(),
                purchase.getInstallmentAmount(),
                purchase.getInstallmentAmount().multiply(BigDecimal.valueOf(purchase.getInstallments())),
                purchase.getCategoryCode(),
                installments,
                purchase.getCardHolder() == null ? null : purchase.getCardHolder().getCode(),
                purchase.getCardHolder() == null ? null : purchase.getCardHolder().getName(),
                purchase.getCardLastDigits(),
                purchase.getThirdParty() == null ? null : purchase.getThirdParty().getCode(),
                purchase.getThirdParty() == null ? null : purchase.getThirdParty().getName(),
                purchase.getThirdParty() == null,
                purchase.getFirstInstallment(),
                purchase.getImportKey());
    }
}
