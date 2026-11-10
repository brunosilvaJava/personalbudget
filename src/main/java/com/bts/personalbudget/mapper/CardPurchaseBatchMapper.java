package com.bts.personalbudget.mapper;

import com.bts.personalbudget.controller.cardpurchase.CardPurchaseBatchItemRequest;
import com.bts.personalbudget.controller.cardpurchase.CardPurchaseBatchLineResponse;
import com.bts.personalbudget.core.domain.service.cardpurchase.CardPurchaseBatchItem;
import com.bts.personalbudget.core.domain.service.cardpurchase.CardPurchaseBatchLine;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class CardPurchaseBatchMapper {
    public CardPurchaseBatchItem toModel(final CardPurchaseBatchItemRequest request) {
        return new CardPurchaseBatchItem(request.description(), request.purchaseDate(), request.installments(),
                request.firstInstallment(), request.installmentAmount(), request.categoryCode(), request.cardLastDigits(),
                request.cardHolderCode(), request.thirdPartyCode(), request.ownExpense(), request.acceptDuplicate());
    }
    public CardPurchaseBatchLineResponse toResponse(final CardPurchaseBatchLine line) {
        return new CardPurchaseBatchLineResponse(line.status(), line.errors(), line.importKey(),
                line.invoiceReferenceMonth(), line.dueDate(), line.holderCode(), line.thirdPartyCode(), line.matches());
    }
    public List<CardPurchaseBatchLineResponse> toResponseList(final List<CardPurchaseBatchLine> lines) {
        return lines.stream().map(this::toResponse).toList();
    }
}
