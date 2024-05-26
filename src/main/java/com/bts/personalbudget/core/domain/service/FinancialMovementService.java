package com.bts.personalbudget.core.domain.service;

import com.bts.personalbudget.core.domain.entity.FinancialMovement;
import com.bts.personalbudget.core.domain.enumerator.FinancialMovementStatus;
import com.bts.personalbudget.core.domain.enumerator.OperationType;
import com.bts.personalbudget.core.domain.model.FinancialMovementModel;
import com.bts.personalbudget.core.domain.repository.FinancialMovementRepository;
import com.bts.personalbudget.mapper.FinancialMovementMapper;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.crossstore.ChangeSetPersister.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@RequiredArgsConstructor
@Service
public class FinancialMovementService {

    private final FinancialMovementRepository repository;
    private final FinancialMovementMapper mapper;

    public void save(final FinancialMovement financialMovement) {
        log.info("m=create, financialMovement={}", financialMovement);
        repository.save(mapper.toModel(financialMovement));
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

        return mapper.toEntity(
                repository.findAllByDescriptionContainsAndOperationTypeInAndStatusInAndMovementDateBetween(
                                description,
                                filterOperationTypes,
                                filterStatuses,
                                startDate.atStartOfDay(),
                                endDate.atStartOfDay().plusDays(1))
                        .orElseThrow());
    }

    public FinancialMovement find(final UUID code) throws NotFoundException {
        log.info("m=find, code={}", code);
        return mapper.toEntity(findModel(code));
    }

    @Transactional
    public FinancialMovement update(final FinancialMovement financialMovement) throws NotFoundException {
        log.info("m=update, financialMovement={}", financialMovement);
        final FinancialMovementModel financialMovementModel = findModel(financialMovement.code());
        update(financialMovement, financialMovementModel);
        return mapper.toEntity(financialMovementModel);
    }

    private FinancialMovementModel findModel(final UUID code) throws NotFoundException {
        return repository.findByCode(code).orElseThrow(NotFoundException::new);
    }

    private void update(final FinancialMovement financialMovement, final FinancialMovementModel financialMovementModel) {
        if (financialMovement.operationType() != null &&
                financialMovement.operationType() != financialMovementModel.getOperationType()) {
            financialMovementModel.setOperationType(financialMovement.operationType());
        }

        if (financialMovement.description() != null && !financialMovement.description().isEmpty() &&
                !financialMovement.description().equals(financialMovementModel.getDescription())) {
            financialMovementModel.setDescription(financialMovement.description());
        }

        if (financialMovement.amount() != null &&
                !financialMovement.amount().equals(financialMovementModel.getAmount())) {
            financialMovementModel.setAmount(financialMovement.amount());
        }

        if (financialMovement.amountPaid() != null &&
                !financialMovement.amountPaid().equals(financialMovementModel.getAmountPaid())) {
            financialMovementModel.setAmountPaid(financialMovement.amountPaid());
        }

        if (financialMovement.movementDate() != null &&
                !financialMovement.movementDate().equals(financialMovementModel.getMovementDate())) {
            financialMovementModel.setMovementDate(financialMovement.movementDate());
        }

        if (financialMovement.dueDate() != null &&
                !financialMovement.dueDate().equals(financialMovementModel.getDueDate())) {
            financialMovementModel.setDueDate(financialMovement.dueDate());
        }

        if (financialMovement.payDate() != null &&
                !financialMovement.payDate().equals(financialMovementModel.getPayDate())) {
            financialMovementModel.setPayDate(financialMovement.payDate());
        }

        if (financialMovement.status() != null &&
                !financialMovement.status().equals(financialMovementModel.getStatus())) {
            financialMovementModel.setStatus(financialMovement.status());
        }
    }

    @Transactional
    public void delete(final UUID code) throws NotFoundException {
        log.info("m=delete, code={}", code);
        final FinancialMovementModel financialMovementModel = findModel(code);
        financialMovementModel.delete();
    }

}
