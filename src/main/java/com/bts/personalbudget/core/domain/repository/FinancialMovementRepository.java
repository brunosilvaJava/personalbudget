package com.bts.personalbudget.core.domain.repository;

import com.bts.personalbudget.core.domain.enumerator.FinancialMovementStatus;
import com.bts.personalbudget.core.domain.enumerator.OperationType;
import com.bts.personalbudget.core.domain.entity.FinancialMovementModel;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FinancialMovementRepository extends JpaRepository<FinancialMovementModel, Long> {

    Optional<FinancialMovementModel> findByCode(UUID code);

    Optional<List<FinancialMovementModel>> findAllByDescriptionContainsAndOperationTypeInAndStatusInAndMovementDateBetween(
            String description, List<OperationType> operationTypes, List<FinancialMovementStatus> statuses,
            LocalDateTime startDate, LocalDateTime endDate);
}
