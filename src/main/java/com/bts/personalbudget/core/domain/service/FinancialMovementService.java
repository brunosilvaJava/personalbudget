package com.bts.personalbudget.core.domain.service;

import com.bts.personalbudget.core.domain.entity.FinancialMovementEntity;
import com.bts.personalbudget.core.domain.enumerator.FinancialMovementStatus;
import com.bts.personalbudget.core.domain.enumerator.OperationType;
import com.bts.personalbudget.core.domain.model.FinancialMovement;
import com.bts.personalbudget.mapper.FinancialMovementMapper;
import com.bts.personalbudget.repository.FinancialMovementRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import static com.bts.personalbudget.core.domain.enumerator.FinancialMovementStatus.LATE;
import static com.bts.personalbudget.core.domain.enumerator.FinancialMovementStatus.PAID_OUT;
import static com.bts.personalbudget.core.domain.enumerator.FinancialMovementStatus.PENDING;

@Slf4j
@RequiredArgsConstructor
@Service
public class FinancialMovementService {

    private final FinancialMovementRepository repository;
    private final FinancialMovementMapper mapper;

    @Transactional
    public FinancialMovement create(final FinancialMovement financialMovement) {
        log.info("m=create, financialMovement={}", financialMovement);
        return mapper.toModel(repository.save(mapper.toEntity(financialMovement)));
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

    public Page<FinancialMovement> find(
            final String description,
            final List<OperationType> operationTypes,
            final List<FinancialMovementStatus> statuses,
            final LocalDate startDate,
            final LocalDate endDate,
            final Pageable pageable) {

        log.info("m=find, description={}, operationTypes={}, statuses={}, startDate={}, endDate={}, pageable={}",
                description, operationTypes, statuses, startDate, endDate, pageable);

        final List<OperationType> filterOperationTypes = operationTypes != null && !operationTypes.isEmpty() ?
                operationTypes : Arrays.stream(OperationType.values()).toList();
        final List<FinancialMovementStatus> filterStatuses = statuses != null && !statuses.isEmpty() ?
                statuses : Arrays.stream(FinancialMovementStatus.values()).toList();

        Page<FinancialMovementEntity> pagedEntities =
                repository.findAllByDescriptionContainsAndOperationTypeInAndStatusInAndMovementDateBetweenAndFlagActiveTrue(
                        description,
                        filterOperationTypes,
                        filterStatuses,
                        startDate.atStartOfDay(),
                        endDate.atStartOfDay().plusDays(1),
                        pageable);

        return pagedEntities.map(mapper::toModel);
    }

    public FinancialMovement find(final UUID code) {
        log.info("m=find, code={}", code);
        return mapper.toModel(findEntity(code));
    }

    @Transactional
    public FinancialMovement update(final FinancialMovement financialMovement) {
        log.info("m=update, financialMovement={}", financialMovement);
        final FinancialMovementEntity financialMovementEntity = findEntity(financialMovement.code());
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

    private void update(final FinancialMovement financialMovement, final FinancialMovementEntity financialMovementEntity) {
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

        if (financialMovement.amountPaid() != null &&
                !financialMovement.amountPaid().equals(financialMovementEntity.getAmountPaid())) {
            financialMovementEntity.setAmountPaid(financialMovement.amountPaid());
        }

        if (financialMovement.movementDate() != null &&
                !financialMovement.movementDate().equals(financialMovementEntity.getMovementDate())) {
            financialMovementEntity.setMovementDate(financialMovement.movementDate());
        }

        if (financialMovement.dueDate() != null &&
                !financialMovement.dueDate().equals(financialMovementEntity.getDueDate())) {
            financialMovementEntity.setDueDate(financialMovement.dueDate());
        }

        if (financialMovement.payDate() != null &&
                !financialMovement.payDate().equals(financialMovementEntity.getPayDate())) {
            financialMovementEntity.setPayDate(financialMovement.payDate());
        }

        if (financialMovement.status() != null &&
                !financialMovement.status().equals(financialMovementEntity.getStatus())) {
            financialMovementEntity.setStatus(financialMovement.status());
        }

        normalizeAmountSigns(financialMovementEntity);
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
        final FinancialMovementEntity financialMovementEntity = findEntity(code);
        financialMovementEntity.delete();
    }

    public List<FinancialMovement> findMovementsForBalanceCalculation(final LocalDate initialDate,
                                                                      final LocalDate endDate) {
        log.info("m=findMovementsForBalanceCalculation initialDate={} endDate={}", initialDate, endDate);
        return mapper.toModel(
                repository.findAllByStatusAndDates(
                        initialDate.atStartOfDay(),
                        endDate.atTime(23, 59, 59),
                        PENDING,
                        PAID_OUT,
                        LATE));
    }

    public BigDecimal findBalance(final LocalDate date) {
        log.info("m=findBalance, date={}", date);
        return repository.sumAmountPaidByStatusAndPayDateLessThanEqual(
                        PAID_OUT,
                        date.atTime(23, 59, 59))
                .orElse(BigDecimal.ZERO);
    }

    public BigDecimal findProjectedBalance(final LocalDate date) {
        log.info("m=findProjectedBalance, date={}", date);
        final BigDecimal balance = findBalance(date);
        final BigDecimal projectedBalance = repository.sumAmountByStatusAndDueDateLessThanEqual(
                        List.of(PENDING, LATE),
                        date.atTime(23, 59, 59))
                .orElse(BigDecimal.ZERO);
        return balance.add(projectedBalance);
    }
}