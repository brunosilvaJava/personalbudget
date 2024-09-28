package com.bts.personalbudget.core.domain.repository;

import com.bts.personalbudget.core.domain.enumerator.FinancialMovementStatus;
import com.bts.personalbudget.core.domain.enumerator.OperationType;
import com.bts.personalbudget.core.domain.entity.FinancialMovementEntity;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FinancialMovementRepository extends JpaRepository<FinancialMovementEntity, Long> {

    Optional<FinancialMovementEntity> findByCode(UUID code);

    Optional<List<FinancialMovementEntity>> findAllByDescriptionContainsAndOperationTypeInAndStatusInAndMovementDateBetween(
            String description, List<OperationType> operationTypes, List<FinancialMovementStatus> statuses,
            LocalDateTime startDate, LocalDateTime endDate);
}
