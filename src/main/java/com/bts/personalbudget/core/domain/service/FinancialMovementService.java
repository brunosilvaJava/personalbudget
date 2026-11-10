package com.bts.personalbudget.core.domain.service;

import com.bts.personalbudget.core.domain.entity.FinancialMovementEntity;
import com.bts.personalbudget.core.domain.entity.IncomeOccurrenceEntity;
import com.bts.personalbudget.core.domain.enumerator.FinancialMovementStatus;
import com.bts.personalbudget.core.domain.enumerator.OperationType;
import com.bts.personalbudget.core.domain.model.FinancialMovement;
import com.bts.personalbudget.core.domain.model.FinancialMovementUpdate;
import com.bts.personalbudget.exception.ConflictException;
import com.bts.personalbudget.exception.InvalidFieldsException;
import com.bts.personalbudget.core.domain.service.category.CategoryService;
import com.bts.personalbudget.core.domain.service.paycycle.PayCycleMovementGuard;
import com.bts.personalbudget.core.domain.service.movementsettlement.MovementSettlementService;
import com.bts.personalbudget.mapper.FinancialMovementMapper;
import com.bts.personalbudget.repository.CardInvoiceRepository;
import com.bts.personalbudget.repository.CardPurchaseRepository;
import com.bts.personalbudget.repository.FinancialMovementRepository;
import com.bts.personalbudget.repository.FinancialMovementSpecifications;
import com.bts.personalbudget.repository.IncomeOccurrenceRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.bts.personalbudget.core.domain.enumerator.FinancialMovementStatus.LATE;
import static com.bts.personalbudget.core.domain.enumerator.FinancialMovementStatus.PAID_OUT;
import static com.bts.personalbudget.core.domain.enumerator.FinancialMovementStatus.PENDING;

@Slf4j
@RequiredArgsConstructor
@Service
public class FinancialMovementService {

    private final FinancialMovementRepository repository;
    private final FinancialMovementMapper mapper;
    private final CategoryService categoryService;
    private final IncomeOccurrenceRepository incomeOccurrenceRepository;
    private final Clock clock;
    private final PayCycleMovementGuard payCycleMovementGuard;
    private final MovementSettlementService movementSettlementService;
    private final CardPurchaseRepository cardPurchaseRepository;
    private final CardInvoiceRepository cardInvoiceRepository;

    /**
     * The category is validated only for a movement the user creates. One generated from a
     * recurrence bill (recurrenceBillCode set - the API has no field for it) is saved as is: a
     * bill whose category was inactivated later keeps posting its occurrences with it.
     */
    @Transactional
    public FinancialMovement create(final FinancialMovement financialMovement) {
        log.info("m=create, financialMovement={}", financialMovement);
        final FinancialMovement movementWithStatus = markOverdueAsLateWhenPending(
                financialMovement.withPaymentSummary(BigDecimal.ZERO, null, PENDING));
        final FinancialMovement movementToCreate = withCompetence(movementWithStatus);
        payCycleMovementGuard.lock();
        payCycleMovementGuard.requireEditable(movementToCreate.competenceDate());
        if (financialMovement.recurrenceBillCode() == null && financialMovement.categoryCode() != null) {
            categoryService.validateForUse(financialMovement.categoryCode(), financialMovement.operationType());
        }
        return mapper.toModel(repository.save(mapper.toEntity(movementToCreate)));
    }

    @Transactional
    public FinancialMovement createWithAutomaticCompetence(final FinancialMovement financialMovement) {
        log.info("m=createWithAutomaticCompetence, financialMovement={}", financialMovement);
        payCycleMovementGuard.lock();
        payCycleMovementGuard.requireEditable(financialMovement.competenceDate());
        return mapper.toModel(repository.save(mapper.toEntity(
                markOverdueAsLateWhenPending(financialMovement.withPaymentSummary(BigDecimal.ZERO, null, PENDING))
                        .withCompetence(financialMovement.competenceDate(), false))));
    }

    public void lockPayCycleMovementMutation() {
        log.info("m=lockPayCycleMovementMutation");
        payCycleMovementGuard.lock();
    }

    private FinancialMovement markOverdueAsLateWhenPending(final FinancialMovement financialMovement) {
        if (financialMovement.status() == PENDING
                && financialMovement.dueDate() != null
                && financialMovement.dueDate().isBefore(LocalDate.now(clock).atStartOfDay())) {
            return financialMovement.withStatus(LATE);
        }
        return financialMovement;
    }

    /**
     * A competence informed by the client is manual. Otherwise it is automatic: occurrence_date for
     * a recurring movement or movement_date for a standalone one. Income occurrences use their
     * frozen planned date through {@link #createWithAutomaticCompetence(FinancialMovement)}.
     */
    private FinancialMovement withCompetence(final FinancialMovement financialMovement) {
        if (financialMovement.competenceDate() != null) {
            return financialMovement.withCompetence(financialMovement.competenceDate(), true);
        }
        final LocalDate automaticCompetence = financialMovement.recurrenceBillCode() != null
                ? financialMovement.occurrenceDate()
                : financialMovement.movementDate().toLocalDate();
        return financialMovement.withCompetence(automaticCompetence, false);
    }

    @Transactional
    public void create(final List<FinancialMovement> financialMovementList) {
        financialMovementList.forEach(this::create);
    }

    /**
     * Deliberately not scoped to flagActive: a soft-deleted movement still counts as "already
     * generated" for this occurrence, so the recurrence job must not recreate it.
     */
    public boolean existsByRecurrenceBillCodeAndOccurrenceDate(final UUID recurrenceBillCode, final LocalDate occurrenceDate) {
        log.info("m=existsByRecurrenceBillCodeAndOccurrenceDate, recurrenceBillCode={}, occurrenceDate={}", recurrenceBillCode, occurrenceDate);
        return repository.existsByRecurrenceBillCodeAndOccurrenceDate(recurrenceBillCode, occurrenceDate);
    }

    /**
     * Movements already generated for these recurrence bills with occurrence_date in the period,
     * soft-deleted ones included (same rule as existsByRecurrenceBillCodeAndOccurrenceDate). Used
     * by the balance projection to skip occurrences that are no longer pending generation.
     */
    public List<FinancialMovement> findRecurrenceOccurrencesBetween(final Collection<UUID> recurrenceBillCodes,
                                                                    final LocalDate initialDate,
                                                                    final LocalDate endDate) {
        log.info("m=findRecurrenceOccurrencesBetween, recurrenceBillCodes={}, initialDate={}, endDate={}",
                recurrenceBillCodes.size(), initialDate, endDate);
        if (recurrenceBillCodes.isEmpty()) {
            return List.of();
        }
        return mapper.toModel(repository.findAllByRecurrenceBillCodeInAndOccurrenceDateBetween(
                recurrenceBillCodes, initialDate, endDate));
    }

    @Transactional
    public List<FinancialMovement> findRecurrenceOccurrencesBetweenForUpdate(final Collection<UUID> recurrenceBillCodes,
                                                                            final LocalDate initialDate,
                                                                            final LocalDate endDate) {
        log.info("m=findRecurrenceOccurrencesBetweenForUpdate, recurrenceBillCodes={}, initialDate={}, endDate={}",
                recurrenceBillCodes.size(), initialDate, endDate);
        if (recurrenceBillCodes.isEmpty()) {
            return List.of();
        }
        return mapper.toModel(repository.findForUpdateByRecurrenceBillCodeInAndOccurrenceDateBetween(
                recurrenceBillCodes, initialDate, endDate));
    }

    /**
     * The active movements whose competence date is in {@code [from, toExclusive)}, oldest competence
     * first: what a cycle boundary at {@code from} or {@code toExclusive} would hand from one cycle to
     * the other. Soft-deleted movements are left out, and so are the legacy ones with no competence
     * date, which belong to no interval.
     */
    public List<FinancialMovement> findByCompetenceInterval(final LocalDate from, final LocalDate toExclusive) {
        log.info("m=findByCompetenceInterval, from={}, toExclusive={}", from, toExclusive);
        return mapper.toModel(repository.findActiveByCompetenceDateInterval(from, toExclusive));
    }

    public Page<FinancialMovement> find(
            final String description,
            final List<OperationType> operationTypes,
            final List<FinancialMovementStatus> statuses,
            final LocalDate startDate,
            final LocalDate endDate,
            final List<UUID> categoryCodes,
            final boolean withoutCategory,
            final Pageable pageable) {
        return find(description, operationTypes, statuses, startDate, endDate, categoryCodes, withoutCategory,
                null, false, pageable);
    }

    public Page<FinancialMovement> find(
            final String description,
            final List<OperationType> operationTypes,
            final List<FinancialMovementStatus> statuses,
            final LocalDate startDate,
            final LocalDate endDate,
            final List<UUID> categoryCodes,
            final boolean withoutCategory,
            final UUID thirdPartyCode,
            final boolean ownOnly,
            final Pageable pageable) {

        log.info("m=find, description={}, operationTypes={}, statuses={}, startDate={}, endDate={}, categoryCodes={}, withoutCategory={}, thirdPartyCode={}, ownOnly={}, pageable={}",
                description, operationTypes, statuses, startDate, endDate, categoryCodes, withoutCategory, thirdPartyCode, ownOnly, pageable);
        if (thirdPartyCode != null && ownOnly) {
            throw new InvalidFieldsException("Campos inválidos", FinancialMovementService.class,
                    Map.of("thirdPartyCode", "não pode ser informado junto com ownOnly"));
        }

        final List<OperationType> filterOperationTypes = operationTypes != null && !operationTypes.isEmpty() ?
                operationTypes : Arrays.stream(OperationType.values()).toList();
        final List<FinancialMovementStatus> filterStatuses = statuses != null && !statuses.isEmpty() ?
                statuses : Arrays.stream(FinancialMovementStatus.values()).toList();

        final Page<FinancialMovementEntity> pagedEntities = repository.findAll(
                FinancialMovementSpecifications.filter(
                        description,
                        filterOperationTypes,
                        filterStatuses,
                        startDate.atStartOfDay(),
                        endDate.atStartOfDay().plusDays(1),
                        categoryCodes,
                        withoutCategory,
                        thirdPartyCode,
                        ownOnly),
                pageable);

        final CardLinks links = cardLinks(pagedEntities.getContent());
        return pagedEntities.map(entity -> toModelWithCardLink(entity, links));
    }

    public FinancialMovement find(final UUID code) {
        log.info("m=find, code={}", code);
        final FinancialMovementEntity entity = findEntity(code);
        return toModelWithCardLink(entity, cardLinks(List.of(entity)));
    }

    private record CardLinks(Map<Long, UUID> purchaseCodes, Map<Long, UUID> invoiceCodes) {
    }

    /**
     * The public codes of the purchases and invoices that the installments in the list belong to,
     * in one query each. Nothing is queried when no movement in the list is a card installment.
     */
    private CardLinks cardLinks(final List<FinancialMovementEntity> entities) {
        final Set<Long> purchaseIds = entities.stream().map(FinancialMovementEntity::getCardPurchaseId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        final Set<Long> invoiceIds = entities.stream().map(FinancialMovementEntity::getCardInvoiceId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        return new CardLinks(
                purchaseIds.isEmpty() ? Map.of() : codesById(cardPurchaseRepository.findCodesByIdIn(purchaseIds)),
                invoiceIds.isEmpty() ? Map.of() : codesById(cardInvoiceRepository.findCodesByIdIn(invoiceIds)));
    }

    private static Map<Long, UUID> codesById(final List<Object[]> rows) {
        return rows.stream().collect(Collectors.toMap(row -> (Long) row[0], row -> (UUID) row[1]));
    }

    private FinancialMovement toModelWithCardLink(final FinancialMovementEntity entity, final CardLinks links) {
        final FinancialMovement model = mapper.toModel(entity);
        if (entity.getCardPurchaseId() == null) {
            return model;
        }
        return model.withCardLink(links.purchaseCodes().get(entity.getCardPurchaseId()),
                links.invoiceCodes().get(entity.getCardInvoiceId()));
    }

    @Transactional
    public FinancialMovement update(final FinancialMovementUpdate financialMovement) {
        log.info("m=update, financialMovement={}", financialMovement);
        payCycleMovementGuard.lock();
        final FinancialMovementEntity financialMovementEntity = findEntity(financialMovement.code());
        rejectCardInstallment(financialMovementEntity);
        if (changesClosedBudget(financialMovement, financialMovementEntity)) {
            payCycleMovementGuard.requireEditable(financialMovementEntity.getCompetenceDate());
            payCycleMovementGuard.requireEditable(proposedCompetence(financialMovement, financialMovementEntity));
        }
        validateCategoryOnUpdate(financialMovement, financialMovementEntity);
        update(financialMovement, financialMovementEntity);
        return mapper.toModel(financialMovementEntity);
    }

    /**
     * Soft-deleted movements are not found, as in FixedBillService: GET, PATCH and DELETE on them
     * answer 404 (see ControllerExceptionHandler).
     */
    private FinancialMovementEntity findEntity(final UUID code) {
        return repository.findByCodeAndFlagActiveTrue(code)
                .orElseThrow(() -> new EntityNotFoundException("FinancialMovementEntity not found for code " + code));
    }

    private void update(final FinancialMovementUpdate financialMovement, final FinancialMovementEntity financialMovementEntity) {
        if (financialMovement.operationType() != null &&
                financialMovement.operationType() != financialMovementEntity.getOperationType()) {
            financialMovementEntity.setOperationType(financialMovement.operationType());
        }

        if (financialMovement.description() != null && !financialMovement.description().isEmpty() &&
                !financialMovement.description().equals(financialMovementEntity.getDescription())) {
            financialMovementEntity.setDescription(financialMovement.description());
        }

        if (financialMovement.amount() != null &&
                !financialMovement.amount().equals(financialMovementEntity.getAmount())) {
            financialMovementEntity.setAmount(financialMovement.amount());
        }

        if (financialMovement.movementDate() != null &&
                !financialMovement.movementDate().equals(financialMovementEntity.getMovementDate())) {
            financialMovementEntity.setMovementDate(financialMovement.movementDate());
        }

        if (financialMovement.dueDate() != null &&
                !financialMovement.dueDate().equals(financialMovementEntity.getDueDate())) {
            financialMovementEntity.setDueDate(financialMovement.dueDate());
        }

        if (financialMovement.categoryCode() != null &&
                !financialMovement.categoryCode().equals(financialMovementEntity.getCategoryCode())) {
            financialMovementEntity.setCategoryCode(financialMovement.categoryCode());
        }

        normalizeAmountSigns(financialMovementEntity);
        updateSettlementStatus(financialMovementEntity);
        updateCompetence(financialMovement, financialMovementEntity);
    }

    private void updateSettlementStatus(final FinancialMovementEntity movement) {
        final BigDecimal paid = movement.getAmountPaid() == null ? BigDecimal.ZERO : movement.getAmountPaid().abs();
        if (paid.compareTo(movement.getAmount().abs()) > 0) {
            throw new InvalidFieldsException("Campos inválidos", FinancialMovementUpdate.class,
                    Map.of("amount", "não pode ser menor que o total já liquidado"));
        }
        movement.setAmountPaid(movement.getOperationType() == OperationType.DEBIT ? paid.negate() : paid);
        movement.setPayDate(null);
        if (paid.compareTo(movement.getAmount().abs()) == 0 && paid.signum() > 0) {
            movement.setStatus(PAID_OUT);
        } else {
            movement.setStatus(movement.getDueDate().toLocalDate().isBefore(LocalDate.now(clock)) ? LATE : PENDING);
        }
    }

    private boolean changesClosedBudget(final FinancialMovementUpdate changes,
                                        final FinancialMovementEntity current) {
        final boolean operationTypeChanged = changes.operationType() != null
                && changes.operationType() != current.getOperationType();
        final OperationType finalOperationType = changes.operationType() != null
                ? changes.operationType() : current.getOperationType();
        final BigDecimal proposedAmount = mapper.normalizeValue(changes.amount(), finalOperationType);
        final boolean amountChanged = changes.amount() != null
                && (current.getAmount() == null || proposedAmount.compareTo(current.getAmount()) != 0);
        final boolean categoryChanged = changes.categoryCode() != null
                && !changes.categoryCode().equals(current.getCategoryCode());
        final boolean competenceChanged = changes.competenceDate() != null
                || changes.restoreAutomaticCompetence() && Boolean.TRUE.equals(current.getCompetenceOverridden())
                || !Objects.equals(current.getCompetenceDate(), proposedCompetence(changes, current));
        return operationTypeChanged || amountChanged || categoryChanged || competenceChanged;
    }

    private LocalDate proposedCompetence(final FinancialMovementUpdate changes,
                                         final FinancialMovementEntity current) {
        if (changes.competenceDate() != null) {
            return changes.competenceDate();
        }
        if (changes.restoreAutomaticCompetence()) {
            if (current.getRecurrenceBillCode() == null && changes.movementDate() != null) {
                return changes.movementDate().toLocalDate();
            }
            return automaticCompetence(current);
        }
        if (!Boolean.TRUE.equals(current.getCompetenceOverridden())
                && current.getRecurrenceBillCode() == null
                && changes.movementDate() != null) {
            return changes.movementDate().toLocalDate();
        }
        return current.getCompetenceDate();
    }

    /**
     * Due and pay dates never touch the competence. An automatic competence follows movement_date
     * for a standalone movement or the frozen planned date for a recurring movement; a manual one
     * is only changed by the explicit commands.
     */
    private void updateCompetence(final FinancialMovementUpdate changes, final FinancialMovementEntity entity) {
        if (changes.competenceDate() != null) {
            entity.setCompetenceDate(changes.competenceDate());
            entity.setCompetenceOverridden(true);
            return;
        }
        if (changes.restoreAutomaticCompetence()) {
            final LocalDate automaticCompetence = automaticCompetence(entity);
            if (automaticCompetence == null) {
                throw new InvalidFieldsException("Campos inválidos", FinancialMovementUpdate.class, Map.of(
                        "restoreAutomaticCompetence",
                        "movimentação recorrente sem occurrence_date não tem competência automática"));
            }
            entity.setCompetenceDate(automaticCompetence);
            entity.setCompetenceOverridden(false);
            return;
        }
        if (!Boolean.TRUE.equals(entity.getCompetenceOverridden())) {
            final LocalDate automaticCompetence = automaticCompetence(entity);
            if (automaticCompetence != null) {
                entity.setCompetenceDate(automaticCompetence);
            }
        }
    }

    private LocalDate automaticCompetence(final FinancialMovementEntity entity) {
        if (entity.getRecurrenceBillCode() == null) {
            return entity.getMovementDate().toLocalDate();
        }
        return incomeOccurrenceRepository.findByMovementCode(entity.getCode())
                .map(IncomeOccurrenceEntity::getPlannedDate)
                .orElse(entity.getOccurrenceDate());
    }

    /**
     * The category has to fit the operation type the movement ends up with, so changing only the
     * operation type is checked against the stored category too. Runs only when one of the two
     * actually changes: a movement whose category was inactivated later can still be edited.
     */
    private void validateCategoryOnUpdate(final FinancialMovementUpdate changes, final FinancialMovementEntity current) {
        final OperationType finalOperationType =
                changes.operationType() != null ? changes.operationType() : current.getOperationType();
        final UUID finalCategoryCode =
                changes.categoryCode() != null ? changes.categoryCode() : current.getCategoryCode();
        final boolean categoryChanged =
                changes.categoryCode() != null && !changes.categoryCode().equals(current.getCategoryCode());
        final boolean operationTypeChanged = finalOperationType != current.getOperationType();
        if (finalCategoryCode != null && (categoryChanged || operationTypeChanged)) {
            categoryService.validateForUse(finalCategoryCode, finalOperationType);
        }
    }

    /**
     * Re-applies the sign convention (DEBIT negative, CREDIT positive) to the stored values with
     * the movement's current operation type. The entity setters only negate a positive debit
     * value being set, so a PATCH that changes only operation_type would otherwise keep the old
     * sign and the movement would count on the wrong side of the balance.
     */
    private void normalizeAmountSigns(final FinancialMovementEntity financialMovementEntity) {
        final OperationType operationType = financialMovementEntity.getOperationType();
        financialMovementEntity.setAmount(mapper.normalizeValue(financialMovementEntity.getAmount(), operationType));
        financialMovementEntity.setAmountPaid(mapper.normalizeValue(financialMovementEntity.getAmountPaid(), operationType));
    }

    @Transactional
    public void delete(final UUID code) {
        log.info("m=delete, code={}", code);
        payCycleMovementGuard.lock();
        final FinancialMovementEntity financialMovementEntity = repository.findByCode(code)
                .orElseThrow(() -> new EntityNotFoundException("FinancialMovementEntity not found for code " + code));
        if (!Boolean.TRUE.equals(financialMovementEntity.getFlagActive())) {
            log.info("m=delete, code={}, msg=already_deleted", code);
            return;
        }
        rejectCardInstallment(financialMovementEntity);
        payCycleMovementGuard.requireEditable(financialMovementEntity.getCompetenceDate());
        financialMovementEntity.delete();
    }

    /**
     * An installment of a card purchase belongs to its purchase: its amount, dates and existence
     * follow the purchase (PATCH or DELETE /card_purchase/{code}), never the movement on its own.
     */
    private void rejectCardInstallment(final FinancialMovementEntity entity) {
        if (entity.getCardPurchaseId() != null) {
            throw new ConflictException(
                    "Esta movimentação é a parcela de uma compra de cartão; altere ou exclua a compra");
        }
    }

    @Transactional
    public int markOverdueAsLate(final LocalDate today) {
        log.info("m=markOverdueAsLate, today={}", today);
        payCycleMovementGuard.lock();
        final int updated = repository.markOverduePendingAsLate(
                PENDING, LATE, today.atStartOfDay(), Instant.now(clock));
        log.info("m=markOverdueAsLate, today={}, updated={}", today, updated);
        return updated;
    }

    public List<FinancialMovement> findMovementsForBalanceCalculation(final LocalDate initialDate,
                                                                      final LocalDate endDate) {
        log.info("m=findMovementsForBalanceCalculation initialDate={} endDate={}", initialDate, endDate);
        return mapper.toModel(
                repository.findUnpaidByDueDateBetween(
                        initialDate.atStartOfDay(),
                        endDate.atTime(23, 59, 59),
                        PENDING,
                        LATE));
    }

    /** Every unpaid movement due up to {@code endDate} (overdue ones included), for the cash forecast. */
    public List<FinancialMovement> findOpenDueThrough(final LocalDate endDate) {
        log.info("m=findOpenDueThrough, endDate={}", endDate);
        return mapper.toModel(repository.findUnpaidDueThrough(endDate.atTime(23, 59, 59), PENDING, LATE));
    }

    /** The unpaid card installments due after {@code afterDate}, for the cash forecast. */
    public List<FinancialMovement> findOpenCardInstallmentsDueAfter(final LocalDate afterDate) {
        log.info("m=findOpenCardInstallmentsDueAfter, afterDate={}", afterDate);
        return mapper.toModel(repository.findUnpaidCardInstallmentsDueAfter(
                afterDate.atTime(23, 59, 59), PENDING, LATE));
    }

    public BigDecimal findBalance(final LocalDate date) {
        log.info("m=findBalance, date={}", date);
        return movementSettlementService.sumActiveSignedAmountsThrough(date);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public BigDecimal findProjectedBalance(final LocalDate date) {
        log.info("m=findProjectedBalance, date={}", date);
        final BigDecimal balance = findBalance(date);
        final BigDecimal projectedBalance = repository.sumUnpaidAmountByStatusAndDueDateLessThanEqual(
                        List.of(PENDING, LATE),
                        date.atTime(23, 59, 59));
        return balance.add(projectedBalance);
    }
}
