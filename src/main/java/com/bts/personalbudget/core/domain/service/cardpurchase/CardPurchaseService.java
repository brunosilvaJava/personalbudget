package com.bts.personalbudget.core.domain.service.cardpurchase;

import com.bts.personalbudget.core.domain.entity.CardInvoiceEntity;
import com.bts.personalbudget.core.domain.entity.CardPurchaseEntity;
import com.bts.personalbudget.core.domain.entity.CreditCardEntity;
import com.bts.personalbudget.core.domain.entity.CreditCardHolderEntity;
import com.bts.personalbudget.core.domain.entity.CreditCardPlasticEntity;
import com.bts.personalbudget.core.domain.entity.FinancialMovementEntity;
import com.bts.personalbudget.core.domain.entity.ThirdPartyEntity;
import com.bts.personalbudget.core.domain.enumerator.CreditCardHolderStatus;
import com.bts.personalbudget.core.domain.enumerator.CreditCardPlasticStatus;
import com.bts.personalbudget.core.domain.enumerator.CreditCardStatus;
import com.bts.personalbudget.core.domain.enumerator.FinancialMovementStatus;
import com.bts.personalbudget.core.domain.enumerator.OperationType;
import com.bts.personalbudget.core.domain.enumerator.ThirdPartyStatus;
import com.bts.personalbudget.core.domain.service.category.CategoryService;
import com.bts.personalbudget.core.domain.service.creditcard.CardInvoiceCalculator;
import com.bts.personalbudget.core.domain.service.creditcard.CardInvoiceCalculator.InvoiceSlot;
import com.bts.personalbudget.core.domain.service.paycycle.PayCycleMovementGuard;
import com.bts.personalbudget.core.domain.service.thirdparty.ThirdPartyResolver;
import com.bts.personalbudget.core.domain.service.thirdparty.ReceivableService;
import com.bts.personalbudget.exception.ConflictException;
import com.bts.personalbudget.exception.InvalidFieldsException;
import com.bts.personalbudget.mapper.CardPurchaseMapper;
import com.bts.personalbudget.repository.CardInstallmentRepository;
import com.bts.personalbudget.repository.CardInvoiceRepository;
import com.bts.personalbudget.repository.CardPurchaseRepository;
import com.bts.personalbudget.repository.CreditCardRepository;
import com.bts.personalbudget.repository.CreditCardHolderRepository;
import com.bts.personalbudget.repository.CreditCardPlasticRepository;
import com.bts.personalbudget.repository.ThirdPartyRepository;
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
    private final CreditCardHolderRepository holderRepository;
    private final CreditCardPlasticRepository plasticRepository;
    private final ThirdPartyRepository thirdPartyRepository;
    private final ReceivableService receivableService;
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

        final int firstInstallment = effectiveFirstInstallment(purchase.firstInstallment(), purchase.installments());
        final CardUse cardUse = resolveCardUse(card, purchase.cardHolderCode(), purchase.cardLastDigits());
        final ThirdPartyEntity thirdParty = resolveThirdParty(ThirdPartyResolver.resolve(purchase.thirdPartyCode(),
                purchase.ownExpense(), cardUse.plastic() == null || cardUse.plastic().getDefaultThirdParty() == null
                        ? null : cardUse.plastic().getDefaultThirdParty().getCode(),
                card.getDefaultThirdParty() == null ? null : card.getDefaultThirdParty().getCode()));

        final List<CardInvoiceEntity> invoices = resolveInvoices(card, purchase.purchaseDate(), purchase.installments());
        requireEditableCompetences(invoices);
        requireWithinLimit(card, total(purchase.installmentAmount(), purchase.installments() - firstInstallment + 1), BigDecimal.ZERO);

        final CardPurchaseEntity entity = purchaseRepository.save(CardPurchaseEntity.builder()
                .card(card)
                .description(description)
                .purchaseDate(purchase.purchaseDate())
                .installments(purchase.installments())
                .installmentAmount(purchase.installmentAmount())
                .categoryCode(purchase.categoryCode())
                .cardHolder(cardUse.holder())
                .cardLastDigits(cardUse.lastDigits())
                .thirdParty(thirdParty)
                .firstInstallment(firstInstallment)
                .importKey(purchase.importKey())
                .build());
        final List<FinancialMovementEntity> movements = new ArrayList<>();
        for (int k = firstInstallment; k <= purchase.installments(); k++) {
            movements.add(newInstallment(entity, invoices.get(k - 1), k));
        }
        installmentRepository.saveAll(movements);
        if (entity.getThirdParty() != null && receivableService != null) {
            movements.forEach(receivableService::syncForInstallment);
        }
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
        return update(code, description, purchaseDate, installments, installmentAmount, categoryCode,
                null, null, null, null);
    }

    @Transactional
    public CardPurchase update(final UUID code, final String description, final LocalDate purchaseDate,
                               final Integer installments, final BigDecimal installmentAmount,
                               final UUID categoryCode, final UUID cardHolderCode, final String cardLastDigits,
                               final UUID thirdPartyCode, final Boolean ownExpense) {
        log.info("m=update, code={}, description={}, purchaseDate={}, installments={}, installmentAmount={}, categoryCode={}, "
                        + "cardHolderCode={}, cardLastDigits={}, thirdPartyCode={}, ownExpense={}",
                code, description, purchaseDate, installments, installmentAmount, categoryCode, cardHolderCode,
                cardLastDigits, thirdPartyCode, ownExpense);
        payCycleMovementGuard.lock();
        final CardPurchaseEntity purchase = purchaseRepository.findActiveByCode(code)
                .orElseThrow(() -> notFound(code));
        final CreditCardEntity card = lockCard(purchase);
        final List<FinancialMovementEntity> current = installmentRepository.findActiveByPurchaseId(purchase.getId());
        final Map<Long, LocalDate> previousDueDates = current.stream().collect(Collectors.toMap(
                FinancialMovementEntity::getId, movement -> movement.getDueDate().toLocalDate()));

        final String newDescription = description == null ? purchase.getDescription() : normalizeDescription(description);
        final LocalDate newDate = purchaseDate == null ? purchase.getPurchaseDate() : purchaseDate;
        final int newCount = installments == null ? purchase.getInstallments() : installments;
        final BigDecimal newAmount = installmentAmount == null ? purchase.getInstallmentAmount() : installmentAmount;
        validateSchedule(newDate, newCount, newAmount);
        if (newCount < purchase.getFirstInstallment()) {
            throw invalid("installments", "não pode ser menor que firstInstallment");
        }

        final boolean scheduleChanged = !newDate.equals(purchase.getPurchaseDate())
                || newCount != purchase.getInstallments()
                || newAmount.compareTo(purchase.getInstallmentAmount()) != 0;
        final boolean categoryChanged = categoryCode != null && !categoryCode.equals(purchase.getCategoryCode());
        final boolean descriptionChanged = !newDescription.equals(purchase.getDescription());
        final boolean cardUseChanged = cardHolderCode != null || cardLastDigits != null;
        final boolean thirdPartyRequested = thirdPartyCode != null || ownExpense != null;
        final CardUse newCardUse = cardUseChanged ? resolveCardUse(card, cardHolderCode, cardLastDigits) : null;
        final ThirdPartyEntity resolvedThirdParty = thirdPartyRequested ? resolveThirdParty(ThirdPartyResolver.resolve(
                thirdPartyCode, ownExpense, newCardUse == null || newCardUse.plastic() == null
                        || newCardUse.plastic().getDefaultThirdParty() == null ? null
                        : newCardUse.plastic().getDefaultThirdParty().getCode(),
                card.getDefaultThirdParty() == null ? null : card.getDefaultThirdParty().getCode())) : null;
        final boolean thirdPartyChanged = thirdPartyRequested && !sameThirdParty(purchase.getThirdParty(), resolvedThirdParty);
        if (!scheduleChanged && !categoryChanged && !descriptionChanged && !cardUseChanged && !thirdPartyChanged) {
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
        if (thirdPartyChanged && receivableService != null) {
            receivableService.requireNoReceived(current);
        }
        if (scheduleChanged || categoryChanged || thirdPartyChanged) {
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
        if (cardUseChanged) {
            purchase.setCardHolder(newCardUse.holder());
            purchase.setCardLastDigits(newCardUse.lastDigits());
        }
        if (thirdPartyChanged) {
            purchase.setThirdParty(resolvedThirdParty);
        }

        final List<FinancialMovementEntity> result = new ArrayList<>();
        final List<FinancialMovementEntity> created = new ArrayList<>();
        final int firstInstallment = existingFirstInstallment(purchase);
        for (int k = firstInstallment; k <= newCount; k++) {
            final int index = k - firstInstallment;
            if (index < current.size()) {
                final FinancialMovementEntity movement = current.get(index);
                movement.setDescription(installmentDescription(newDescription, k, newCount));
                movement.setCategoryCode(purchase.getCategoryCode());
                if (thirdPartyChanged) movement.setThirdParty(resolvedThirdParty);
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
        for (int index = newCount - firstInstallment + 1; index < current.size(); index++) {
            current.get(index).delete();
        }
        installmentRepository.saveAll(created);
        if (purchase.getThirdParty() != null && receivableService != null) {
            result.forEach(movement -> receivableService.syncForInstallment(
                    movement, previousDueDates.get(movement.getId())));
        }
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
        if (receivableService != null && installments.stream().anyMatch(movement -> movement.getThirdParty() != null)) {
            receivableService.deleteForInstallments(installments);
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
                .thirdParty(purchase.getThirdParty())
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

    private CardUse resolveCardUse(final CreditCardEntity card, final UUID holderCode, final String lastDigits) {
        final CreditCardPlasticEntity plastic;
        final CreditCardHolderEntity holder;
        if (lastDigits != null) {
            if (!lastDigits.matches("\\d{4}")) throw invalid("cardLastDigits", "deve conter exatamente 4 dígitos");
            plastic = plasticRepository.findByCardIdAndLastDigits(card.getId(), lastDigits)
                    .orElseThrow(() -> invalid("cardLastDigits", "plástico não encontrado neste cartão"));
            if (plastic.getStatus() == CreditCardPlasticStatus.INACTIVE) {
                throw new ConflictException("Plástico inativo");
            }
            holder = plastic.getHolder();
            if (holderCode != null && !holderCode.equals(holder.getCode())) {
                throw invalid("cardHolderCode", "não corresponde ao plástico informado");
            }
        } else {
            plastic = null;
            holder = holderCode == null && holderRepository == null ? null
                    : holderCode == null ? holderRepository.findTitularByCardId(card.getId())
                    .orElseThrow(() -> new IllegalStateException("Cartão sem TITULAR"))
                    : holderRepository.findByCode(holderCode)
                            .orElseThrow(() -> invalid("cardHolderCode", "titular não encontrado"));
            if (holder != null && !holder.getCard().getId().equals(card.getId())) {
                throw invalid("cardHolderCode", "não pertence ao cartão informado");
            }
        }
        if (holder != null && holder.getStatus() != CreditCardHolderStatus.ACTIVE) {
            throw invalid("cardHolderCode", "titular inativo");
        }
        return new CardUse(holder, plastic, plastic == null ? null : plastic.getLastDigits());
    }

    private ThirdPartyEntity resolveThirdParty(final UUID code) {
        if (code == null) return null;
        final ThirdPartyEntity thirdParty = thirdPartyRepository.findByCode(code)
                .orElseThrow(() -> invalid("thirdPartyCode", "terceiro não encontrado"));
        if (thirdParty.getStatus() != ThirdPartyStatus.ACTIVE) {
            throw invalid("thirdPartyCode", "terceiro inativo");
        }
        return thirdParty;
    }

    private int effectiveFirstInstallment(final Integer firstInstallment, final int installments) {
        final int result = firstInstallment == null ? 1 : firstInstallment;
        if (result < 1 || result > installments) throw invalid("firstInstallment", "deve estar entre 1 e installments");
        return result;
    }

    private static int existingFirstInstallment(final CardPurchaseEntity purchase) {
        return purchase.getFirstInstallment() == 0 ? 1 : purchase.getFirstInstallment();
    }

    private static boolean sameThirdParty(final ThirdPartyEntity a, final ThirdPartyEntity b) {
        return a == null ? b == null : b != null && a.getId().equals(b.getId());
    }

    private record CardUse(CreditCardHolderEntity holder, CreditCardPlasticEntity plastic, String lastDigits) { }

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
