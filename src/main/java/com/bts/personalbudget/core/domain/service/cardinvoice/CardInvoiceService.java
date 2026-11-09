package com.bts.personalbudget.core.domain.service.cardinvoice;

import com.bts.personalbudget.core.domain.entity.CardInvoiceEntity;
import com.bts.personalbudget.core.domain.entity.CardPurchaseEntity;
import com.bts.personalbudget.core.domain.entity.CreditCardEntity;
import com.bts.personalbudget.core.domain.entity.FinancialMovementEntity;
import com.bts.personalbudget.core.domain.service.cardpurchase.CardInstallment;
import com.bts.personalbudget.mapper.CardInvoiceMapper;
import com.bts.personalbudget.mapper.CardPurchaseMapper;
import com.bts.personalbudget.repository.CardInstallmentRepository;
import com.bts.personalbudget.repository.CardInvoiceRepository;
import com.bts.personalbudget.repository.CardPurchaseRepository;
import com.bts.personalbudget.repository.CreditCardRepository;
import jakarta.persistence.EntityNotFoundException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read side of the card invoices (E9b). Invoices are created by the purchases that fall in them;
 * paying an invoice comes with E9c.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class CardInvoiceService {

    private final CardInvoiceRepository invoiceRepository;
    private final CardInstallmentRepository installmentRepository;
    private final CardPurchaseRepository purchaseRepository;
    private final CreditCardRepository cardRepository;
    private final CardInvoiceMapper mapper;
    private final CardPurchaseMapper purchaseMapper;
    private final Clock clock;

    /**
     * The invoices of a card, newest first, without their installments. With a reference month,
     * only that month's invoice (an empty list when none exists).
     */
    @Transactional(readOnly = true)
    public List<CardInvoice> find(final UUID cardCode, final YearMonth referenceMonth) {
        log.info("m=find, cardCode={}, referenceMonth={}", cardCode, referenceMonth);
        final CreditCardEntity card = cardRepository.findByCode(cardCode)
                .orElseThrow(() -> new EntityNotFoundException("CreditCardEntity not found for code " + cardCode));
        final List<CardInvoiceEntity> invoices = invoiceRepository.findByCardId(
                card.getId(), referenceMonth == null ? null : referenceMonth.atDay(1));
        if (invoices.isEmpty()) {
            return List.of();
        }
        final Map<Long, List<FinancialMovementEntity>> movements = installmentRepository
                .findActiveByInvoiceIdIn(invoices.stream().map(CardInvoiceEntity::getId).toList()).stream()
                .collect(Collectors.groupingBy(FinancialMovementEntity::getCardInvoiceId));
        final LocalDate today = LocalDate.now(clock);
        return invoices.stream()
                .map(invoice -> mapper.assemble(invoice, movements.getOrDefault(invoice.getId(), List.of()),
                        List.of(), today))
                .toList();
    }

    /** One invoice with its installments, in purchase and installment order. */
    @Transactional(readOnly = true)
    public CardInvoice findByCode(final UUID code) {
        log.info("m=findByCode, code={}", code);
        final CardInvoiceEntity invoice = invoiceRepository.findByCode(code)
                .orElseThrow(() -> new EntityNotFoundException("CardInvoiceEntity not found for code " + code));
        final List<FinancialMovementEntity> movements = installmentRepository.findActiveByInvoiceId(invoice.getId());
        final Map<Long, CardPurchaseEntity> purchases = movements.isEmpty() ? Map.of()
                : purchaseRepository.findAllWithCardByIdIn(
                                movements.stream().map(FinancialMovementEntity::getCardPurchaseId).distinct().toList())
                        .stream().collect(Collectors.toMap(CardPurchaseEntity::getId, purchase -> purchase));
        final List<CardInstallment> installments = movements.stream()
                .map(movement -> purchaseMapper.assembleInstallment(
                        movement, purchases.get(movement.getCardPurchaseId()), invoice))
                .toList();
        return mapper.assemble(invoice, movements, installments, LocalDate.now(clock));
    }
}
