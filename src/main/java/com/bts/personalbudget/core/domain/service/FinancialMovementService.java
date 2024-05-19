package com.bts.personalbudget.core.domain.service;

import com.bts.personalbudget.core.domain.entity.FinancialMovement;
import com.bts.personalbudget.core.domain.enumerator.FinancialMovementStatus;
import com.bts.personalbudget.core.domain.enumerator.OperationType;
import com.bts.personalbudget.core.domain.repository.FinancialMovementRepository;
import com.bts.personalbudget.mapper.FinancialMovementMapper;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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

    public FinancialMovement find(final UUID code) {
        log.info("m=find, code={}", code);
        return mapper.toEntity(repository.findByCode(code));
    }
}
