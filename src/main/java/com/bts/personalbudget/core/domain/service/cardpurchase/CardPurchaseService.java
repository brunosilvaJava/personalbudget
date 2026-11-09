package com.bts.personalbudget.core.domain.service.cardpurchase;

import com.bts.personalbudget.core.domain.entity.CardInvoiceEntity;
import com.bts.personalbudget.core.domain.entity.CardPurchaseEntity;
import com.bts.personalbudget.core.domain.entity.CreditCardEntity;
import com.bts.personalbudget.core.domain.entity.FinancialMovementEntity;
import com.bts.personalbudget.core.domain.enumerator.CreditCardStatus;
import com.bts.personalbudget.core.domain.enumerator.FinancialMovementStatus;
import com.bts.personalbudget.core.domain.enumerator.OperationType;
import com.bts.personalbudget.core.domain.service.category.CategoryService;
import com.bts.personalbudget.core.domain.service.creditcard.CardInvoiceCalculator;
import com.bts.personalbudget.core.domain.service.creditcard.CardInvoiceCalculator.InvoiceSlot;
import com.bts.personalbudget.core.domain.service.paycycle.PayCycleMovementGuard;
import com.bts.personalbudget.exception.ConflictException;
import com.bts.personalbudget.exception.InvalidFieldsException;
import com.bts.personalbudget.mapper.CardPurchaseMapper;
import com.bts.personalbudget.repository.CardInstallmentRepository;
import com.bts.personalbudget.repository.CardInvoiceRepository;
import com.bts.personalbudget.repository.CardPurchaseRepository;
import com.bts.personalbudget.repository.CreditCardRepository;
import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Card purchases (E9b). Each installment is a DEBIT financial movement whose due date and
 * competence are the due date of the invoice it falls in, so reports, cycles and cash projection
 * see it with no extra logic. Paying the invoice (E9c) is what moves cash; the purchase itself
 * never counts twice.
 *
 * <p>Lock order, always: pay-cycle guard (configuration, anchor schedule), then the card row, then
 * the closed cycle of each competence. Cycle and card writes never take them the other way round.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class CardPurchaseService {

    static final int MAX_INSTALLMENTS = 60;
    static final int MAX_DESCRIPTION_LENGTH = 100;
    static final int MAX_PERIOD_DAYS = 366;
    /** A movement amount is DECIMAL(10,2). */
    static final BigDecimal MAX_INSTALLMENT_AMOUNT = new BigDecimal("99999999.99");
    private static final String INVALID_FIELDS_MESSAGE = "Campos inválidos";

    private final CardPurchaseRepository purchaseRepository;
    private final CardInvoiceRepository invoiceRepository;
    private final CardInstallmentRepository installmentRepository;
    private final CreditCardRepository cardRepository;
    private final CardPurchaseMapper mapper;
    private final CategoryService categoryService;
    private final PayCycleMovementGuard payCycleMovementGuard;
    private final Clock clock;

    @Transactional
    public CardPurchase create(final CardPurchase purchase) {
        log.info("m=create, purchase={}", purchase);
        final String description = normalizeDescription(purchase.description());
        validateSchedule(purchase.purchaseDate(), purchase.installments(), purchase.installmentAmount());
        if (purchase.cardCode() == null) {
            throw invalid("cardCode", "deve ser informado");
        }
        if (purchase.categoryCode() != null) {
            categoryService.validateForUse(purchase.categoryCode(), OperationType.DEBIT);
        }

        payCycleMovementGuard.lock();
        final CreditCardEntity card = cardRepository.findForUpdateByCode(purchase.cardCode())
                .orElseThrow(() -> invalid("cardCode", "cartão não encontrado"));
        if (card.getStatus() != CreditCardStatus.ACTIVE) {
            throw invalid("cardCode", "cartão inativo");
        }

        final List<CardInvoiceEntity> invoices = resolveInvoices(card, purchase.purchaseDate(), purchase.installments());
        requireEditableCompetences(invoices);
        requireWithinLimit(card, total(purchase.installmentAmount(), purchase.installments()), BigDecimal.ZERO);

        final CardPurchaseEntity entity = purchaseRepository.save(CardPurchaseEntity.builder()
                .card(card)
                .description(description)
                .purchaseDate(purchase.purchaseDate())
                .installments(purchase.installments())
                .installmentAmount(purchase.installmentAmount())
                .categoryCode(purchase.categoryCode())
                .build());
        final List<FinancialMovementEntity> movements = new ArrayList<>();
        for (int k = 1; k <= purchase.installments(); k++) {
            movements.add(newInstallment(entity, invoices.get(k - 1), k));
        }
        installmentRepository.saveAll(movements);
        return mapper.assemblePurchase(entity, toInstallments(entity, movements, invoices));
    }

    @Transactional(readOnly = true)
    public CardPurchase findByCode(final UUID code) {
        log.info("m=findByCode, code={}", code);
        final CardPurchaseEntity entity = purchaseRepository.findActiveByCode(code).orElseThrow(() -> notFound(code));
        return assemble(List.of(entity)).get(0);
    }

    /**
     * Active purchases whose purchase date is in the period (both dates inclusive, at most 366
     * days), newest first. Not paginated.
     *
     * @param cardCode when informed, only purchases of that card
     */
    @Transactional(readOnly = true)
    public List<CardPurchase> find(final LocalDate startDate, final LocalDate endDate, final UUID cardCode) {
        log.info("m=find, startDate={}, endDate={}, cardCode={}", startDate, endDate, cardCode);
        if (startDate == null || endDate == null) {
            throw invalid("startDate", "o período deve ser informado");
        }
        if (endDate.isBefore(startDate)) {
            throw invalid("endDate", "não pode ser anterior à data inicial");
        }
        if (startDate.plusDays(MAX_PERIOD_DAYS).isBefore(endDate)) {
            throw invalid("endDate", "o período não pode passar de " + MAX_PERIOD_DAYS + " dias");
        }
        return assemble(purchaseRepository.findActiveByFilters(startDate, endDate, cardCode));
    }

    /**
     * Fields left null are kept; the card cannot change. Date, installments and amount (the
     * schedule) are only accepted while no installment has a payment; the installments are then
     * recalculated in place (extra ones are soft-deleted, missing ones created). A category change
     * moves consumption, so it needs every competence of the purchase in an open cycle.
     */
    @Transactional
    public CardPurchase update(final UUID code, final String description, final LocalDate purchaseDate,
                               final Integer installments, final BigDecimal installmentAmount,
                               final UUID categoryCode) {
        log.info("m=update, code={}, description={}, purchaseDate={}, installments={}, installmentAmount={}, "
                + "categoryCode={}", code, description, purchaseDate, installments, installmentAmount, categoryCode);
        payCycleMovementGuard.lock();
        final CardPurchaseEntity purchase = purchaseRepository.findActiveByCode(code)
                .orElseThrow(() -> notFound(code));
        final CreditCardEntity card = lockCard(purchase);
        final List<FinancialMovementEntity> current = installmentRepository.findActiveByPurchaseId(purchase.getId());

        final String newDescription = description == null ? purchase.getDescription() : normalizeDescription(description);
        final LocalDate newDate = purchaseDate == null ? purchase.getPurchaseDate() : purchaseDate;
        final int newCount = installments == null ? purchase.getInstallments() : installments;
        final BigDecimal newAmount = installmentAmount == null ? purchase.getInstallmentAmount() : installmentAmount;
        validateSchedule(newDate, newCount, newAmount);

        final boolean scheduleChanged = !newDate.equals(purchase.getPurchaseDate())
                || newCount != purchase.getInstallments()
                || newAmount.compareTo(purchase.getInstallmentAmount()) != 0;
        final boolean categoryChanged = categoryCode != null && !categoryCode.equals(purchase.getCategoryCode());
        final boolean descriptionChanged = !newDescription.equals(purchase.getDescription());
        if (!scheduleChanged && !categoryChanged && !descriptionChanged) {
            log.info("m=update, message=NoChangesDetected, code={}", code);
            return assemble(List.of(purchase)).get(0);
        }

        if (categoryChanged) {
            categoryService.validateForUse(categoryCode, OperationType.DEBIT);
        }
        if (scheduleChanged && current.stream().anyMatch(CardPurchaseService::hasPayment)) {
            throw new ConflictException(
                    "A compra tem parcela já paga; data, parcelas e valor não podem mais ser alterados");
        }
        if (scheduleChanged || categoryChanged) {
            current.forEach(movement -> payCycleMovementGuard.requireEditable(movement.getCompetenceDate()));
        }

        final List<CardInvoiceEntity> invoices;
        if (scheduleChanged) {
            invoices = resolveInvoices(card, newDate, newCount);
            requireEditableCompetences(invoices);
            requireWithinLimit(card, total(newAmount, newCount), unpaidTotal(current));
        } else {
            invoices = invoicesOf(current);
        }

        purchase.setDescription(newDescription);
        purchase.setPurchaseDate(newDate);
        purchase.setInstallments(newCount);
        purchase.setInstallmentAmount(newAmount);
        if (categoryChanged) {
            purchase.setCategoryCode(categoryCode);
        }

        final List<FinancialMovementEntity> result = new ArrayList<>();
        final List<FinancialMovementEntity> created = new ArrayList<>();
        for (int k = 1; k <= newCount; k++) {
            if (k <= current.size()) {
                final FinancialMovementEntity movement = current.get(k - 1);
                movement.setDescription(installmentDescription(newDescription, k, newCount));
                movement.setCategoryCode(purchase.getCategoryCode());
                if (scheduleChanged) {
                    applySchedule(movement, newDate, newAmount, invoices.get(k - 1));
                }
                result.add(movement);
            } else {
                final FinancialMovementEntity movement = newInstallment(purchase, invoices.get(k - 1), k);
                created.add(movement);
                result.add(movement);
            }
        }
        for (int k = newCount + 1; k <= current.size(); k++) {
            current.get(k - 1).delete();
        }
        installmentRepository.saveAll(created);
        return mapper.assemblePurchase(purchase, toInstallments(purchase, result, invoices));
    }

    /** Soft delete: the installments leave every report and the limit is released. */
    @Transactional
    public void delete(final UUID code) {
        log.info("m=delete, code={}", code);
        payCycleMovementGuard.lock();
        final CardPurchaseEntity purchase = purchaseRepository.findActiveByCode(code)
                .orElseThrow(() -> notFound(code));
        lockCard(purchase);
        final List<FinancialMovementEntity> installments = installmentRepository.findActiveByPurchaseId(purchase.getId());
        if (installments.stream().anyMatch(CardPurchaseService::hasPayment)) {
            throw new ConflictException("A compra tem parcela já paga e não pode ser excluída");
        }
        installments.forEach(movement -> payCycleMovementGuard.requireEditable(movement.getCompetenceDate()));
        installments.forEach(FinancialMovementEntity::delete);
        purchase.delete();
    }

    private CreditCardEntity lockCard(final CardPurchaseEntity purchase) {
        return cardRepository.findForUpdateByCode(purchase.getCard().getCode())
                .orElseThrow(() -> new EntityNotFoundException(
                        "CreditCardEntity not found for code " + purchase.getCard().getCode()));
    }

    /**
     * The invoice of each installment, in order, created when it does not exist yet. An invoice
     * keeps the dates it was created with, even if the card's days changed afterwards. A paid
     * invoice does not take a retroactive installment.
     */
    private List<CardInvoiceEntity> resolveInvoices(final CreditCardEntity card, final LocalDate purchaseDate,
                                                    final int installments) {
        final List<InvoiceSlot> slots = CardInvoiceCalculator.installmentSlots(
                purchaseDate, installments, card.getClosingDay(), card.getDueDay());
        final List<CardInvoiceEntity> invoices = new ArrayList<>(slots.size());
        for (final InvoiceSlot slot : slots) {
            final LocalDate referenceMonth = slot.referenceMonth().atDay(1);
            final Optional<CardInvoiceEntity> existing =
                    invoiceRepository.findByCardIdAndReferenceMonth(card.getId(), referenceMonth);
            if (existing.isPresent()) {
                if (isPaid(existing.get())) {
                    throw new ConflictException("A fatura de " + slot.referenceMonth()
                            + " já está paga e não aceita novas parcelas");
                }
                invoices.add(existing.get());
            } else {
                invoices.add(invoiceRepository.save(CardInvoiceEntity.builder()
                        .card(card)
                        .referenceMonth(referenceMonth)
                        .closingDate(slot.closingDate())
                        .dueDate(slot.dueDate())
                        .build()));
            }
        }
        return invoices;
    }

    private boolean isPaid(final CardInvoiceEntity invoice) {
        final long total = installmentRepository.countActiveByInvoiceId(invoice.getId());
        return total > 0 && total == installmentRepository.countActiveByInvoiceIdAndStatus(
                invoice.getId(), FinancialMovementStatus.PAID_OUT);
    }

    private void requireEditableCompetences(final List<CardInvoiceEntity> invoices) {
        invoices.stream().map(CardInvoiceEntity::getDueDate).distinct()
                .forEach(payCycleMovementGuard::requireEditable);
    }

    /**
     * Available = limit - unpaid installments of the card (future ones count). {@code ownUnpaid} is
     * what the purchase being edited already holds, which the new total replaces.
     */
    private void requireWithinLimit(final CreditCardEntity card, final BigDecimal newTotal,
                                    final BigDecimal ownUnpaid) {
        if (card.getCreditLimit() == null) {
            return;
        }
        final BigDecimal used = installmentRepository.sumUnpaidByCardId(card.getId()).subtract(ownUnpaid);
        final BigDecimal available = card.getCreditLimit().subtract(used);
        if (newTotal.compareTo(available) > 0) {
            throw new ConflictException("Limite do cartão insuficiente: disponível " + money(available.max(BigDecimal.ZERO))
                    + " e a compra soma " + money(newTotal));
        }
    }

    private FinancialMovementEntity newInstallment(final CardPurchaseEntity purchase, final CardInvoiceEntity invoice,
                                                   final int number) {
        final FinancialMovementEntity movement = FinancialMovementEntity.builder()
                .operationType(OperationType.DEBIT)
                .description(installmentDescription(purchase.getDescription(), number, purchase.getInstallments()))
                .amountPaid(BigDecimal.ZERO)
                .categoryCode(purchase.getCategoryCode())
                .cardPurchaseId(purchase.getId())
                .installmentNumber(number)
                .competenceOverridden(true)
                .build();
        applySchedule(movement, purchase.getPurchaseDate(), purchase.getInstallmentAmount(), invoice);
        return movement;
    }

    /**
     * Competence and due date are the invoice's due date; the purchase date stays as the movement
     * date. The competence is flagged as manual so an automatic recalculation never moves it. An
     * installment is never paid here, so it is PENDING, or LATE when its due date already passed.
     */
    private void applySchedule(final FinancialMovementEntity movement, final LocalDate purchaseDate,
                               final BigDecimal installmentAmount, final CardInvoiceEntity invoice) {
        movement.setAmount(installmentAmount.negate());
        movement.setMovementDate(purchaseDate.atStartOfDay());
        movement.setDueDate(invoice.getDueDate().atStartOfDay());
        movement.setCompetenceDate(invoice.getDueDate());
        movement.setCardInvoiceId(invoice.getId());
        movement.setStatus(invoice.getDueDate().isBefore(LocalDate.now(clock))
                ? FinancialMovementStatus.LATE : FinancialMovementStatus.PENDING);
    }

    private List<CardPurchase> assemble(final List<CardPurchaseEntity> purchases) {
        if (purchases.isEmpty()) {
            return List.of();
        }
        final List<FinancialMovementEntity> movements = installmentRepository.findActiveByPurchaseIdIn(
                purchases.stream().map(CardPurchaseEntity::getId).toList());
        final Map<Long, CardInvoiceEntity> invoices = invoiceRepository.findAllWithCardByIdIn(
                        movements.stream().map(FinancialMovementEntity::getCardInvoiceId).distinct().toList())
                .stream().collect(Collectors.toMap(CardInvoiceEntity::getId, invoice -> invoice));
        final Map<Long, List<FinancialMovementEntity>> byPurchase = movements.stream()
                .collect(Collectors.groupingBy(FinancialMovementEntity::getCardPurchaseId));
        return purchases.stream()
                .map(purchase -> mapper.assemblePurchase(purchase,
                        byPurchase.getOrDefault(purchase.getId(), List.of()).stream()
                                .map(movement -> mapper.assembleInstallment(
                                        movement, purchase, invoices.get(movement.getCardInvoiceId())))
                                .toList()))
                .toList();
    }

    private List<CardInvoiceEntity> invoicesOf(final List<FinancialMovementEntity> movements) {
        final Map<Long, CardInvoiceEntity> byId = new HashMap<>();
        if (!movements.isEmpty()) {
            invoiceRepository.findAllWithCardByIdIn(
                            movements.stream().map(FinancialMovementEntity::getCardInvoiceId).distinct().toList())
                    .forEach(invoice -> byId.put(invoice.getId(), invoice));
        }
        return movements.stream().map(movement -> byId.get(movement.getCardInvoiceId())).toList();
    }

    private List<CardInstallment> toInstallments(final CardPurchaseEntity purchase,
                                                 final List<FinancialMovementEntity> movements,
                                                 final List<CardInvoiceEntity> invoices) {
        final Map<Long, CardInvoiceEntity> byId = invoices.stream()
                .collect(Collectors.toMap(CardInvoiceEntity::getId, invoice -> invoice, (a, b) -> a));
        return movements.stream()
                .map(movement -> mapper.assembleInstallment(movement, purchase, byId.get(movement.getCardInvoiceId())))
                .toList();
    }

    private static boolean hasPayment(final FinancialMovementEntity movement) {
        return movement.getStatus() == FinancialMovementStatus.PAID_OUT
                || movement.getAmountPaid() != null && movement.getAmountPaid().signum() != 0;
    }

    private static BigDecimal unpaidTotal(final List<FinancialMovementEntity> movements) {
        return movements.stream()
                .map(movement -> movement.getAmount().abs()
                        .subtract(movement.getAmountPaid() == null ? BigDecimal.ZERO : movement.getAmountPaid().abs()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal total(final BigDecimal installmentAmount, final int installments) {
        return installmentAmount.multiply(BigDecimal.valueOf(installments));
    }

    private static String installmentDescription(final String description, final int number, final int count) {
        return description + " (" + number + "/" + count + ")";
    }

    private static String money(final BigDecimal value) {
        return "R$ " + value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private String normalizeDescription(final String description) {
        final String normalized = description == null ? "" : description.trim();
        if (normalized.isEmpty() || normalized.length() > MAX_DESCRIPTION_LENGTH) {
            throw invalid("description", "deve ter entre 1 e " + MAX_DESCRIPTION_LENGTH + " caracteres");
        }
        return normalized;
    }

    private void validateSchedule(final LocalDate purchaseDate, final Integer installments,
                                  final BigDecimal installmentAmount) {
        if (purchaseDate == null) {
            throw invalid("purchaseDate", "deve ser informada");
        }
        if (installments == null || installments < 1 || installments > MAX_INSTALLMENTS) {
            throw invalid("installments", "deve estar entre 1 e " + MAX_INSTALLMENTS);
        }
        if (installmentAmount == null || installmentAmount.signum() <= 0) {
            throw invalid("installmentAmount", "deve ser maior que zero");
        }
        if (installmentAmount.stripTrailingZeros().scale() > 2) {
            throw invalid("installmentAmount", "deve ter no máximo 2 casas decimais");
        }
        if (installmentAmount.compareTo(MAX_INSTALLMENT_AMOUNT) > 0) {
            throw invalid("installmentAmount", "deve ser no máximo " + MAX_INSTALLMENT_AMOUNT);
        }
    }

    private EntityNotFoundException notFound(final UUID code) {
        return new EntityNotFoundException("CardPurchaseEntity not found for code " + code);
    }

    private InvalidFieldsException invalid(final String field, final String message) {
        return new InvalidFieldsException(INVALID_FIELDS_MESSAGE, CardPurchaseService.class, Map.of(field, message));
    }
}
