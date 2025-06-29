package com.bts.personalbudget.core.domain.service;

import com.bts.personalbudget.core.domain.entity.FinancialMovementEntity;
import com.bts.personalbudget.core.domain.enumerator.FinancialMovementStatus;
import com.bts.personalbudget.core.domain.enumerator.OperationType;
import com.bts.personalbudget.core.domain.model.FinancialMovement;
import com.bts.personalbudget.mapper.FinancialMovementMapper;
import com.bts.personalbudget.repository.FinancialMovementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.crossstore.ChangeSetPersister.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
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
    public void create(final FinancialMovement financialMovement) {
        log.info("m=create, financialMovement={}", financialMovement);
        repository.save(mapper.toEntity(financialMovement));
    }

    @Transactional
    public void create(final List<FinancialMovement> financialMovementList) {
        financialMovementList.forEach(this::create);
    }

    public List<FinancialMovement> find(
            final String description,
            final List<OperationType> operationTypes,
            final List<FinancialMovementStatus> statuses,
            final LocalDate startDate,
            final LocalDate endDate) {
        log.info("m=find, description={}, operationTypes={}, statuses={}, startDate={}, endDate={}",
                description, operationTypes, statuses, startDate, endDate);

        final List<OperationType> filterOperationTypes = operationTypes != null && !operationTypes.isEmpty() ?
                operationTypes : Arrays.stream(OperationType.values()).toList();
        final List<FinancialMovementStatus> filterStatuses = statuses != null && !statuses.isEmpty() ?
                statuses : Arrays.stream(FinancialMovementStatus.values()).toList();

        return mapper.toModel(
                repository.findAllByDescriptionContainsAndOperationTypeInAndStatusInAndMovementDateBetweenAndFlagActiveTrue(
                                description,
                                filterOperationTypes,
                                filterStatuses,
                                startDate.atStartOfDay(),
                                endDate.atStartOfDay().plusDays(1))
                        .orElseThrow());
    }

    public FinancialMovement find(final UUID code) throws NotFoundException {
        log.info("m=find, code={}", code);
        return mapper.toModel(findEntity(code));
    }

    @Transactional
    public FinancialMovement update(final FinancialMovement financialMovement) throws NotFoundException {
        log.info("m=update, financialMovement={}", financialMovement);
        final FinancialMovementEntity financialMovementEntity = findEntity(financialMovement.code());
        update(financialMovement, financialMovementEntity);
        return mapper.toModel(financialMovementEntity);
    }

    private FinancialMovementEntity findEntity(final UUID code) throws NotFoundException {
        return repository.findByCode(code).orElseThrow(NotFoundException::new);
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
    }

    @Transactional
    public void delete(final UUID code) throws NotFoundException {
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
