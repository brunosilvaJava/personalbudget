package com.bts.personalbudget.repository;

import com.bts.personalbudget.core.domain.entity.FinancialMovementEntity;
import com.bts.personalbudget.core.domain.enumerator.FinancialMovementStatus;
import com.bts.personalbudget.core.domain.enumerator.OperationType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface FinancialMovementRepository extends JpaRepository<FinancialMovementEntity, Long> {

    Optional<FinancialMovementEntity> findByCode(UUID code);

    @Query("SELECT SUM(f.amountPaid) " +
            "FROM FinancialMovementEntity f " +
            "WHERE f.status = :status " +
            "AND f.payDate <= :payDate")
    Optional<BigDecimal> sumAmountPaidByStatusAndPayDateLessThanEqual(
            @Param("status") FinancialMovementStatus status,
            @Param("payDate") LocalDateTime payDate);

    Optional<List<FinancialMovementEntity>> findAllByDescriptionContainsAndOperationTypeInAndStatusInAndMovementDateBetween(
            String description, List<OperationType> operationTypes, List<FinancialMovementStatus> statuses,
            LocalDateTime startDate, LocalDateTime endDate);

    @Query("select fm from FinancialMovementEntity fm " +
            "WHERE" +
            " (fm.status = ?3 AND fm.dueDate BETWEEN ?1 AND ?2)" +
            "OR" +
            " (fm.status = ?4 AND fm.payDate BETWEEN ?1 AND ?2)" +
            "OR" +
            " fm.status = ?5")
    List<FinancialMovementEntity> findAllByStatusAndDates(
            LocalDateTime InitialDate,
            LocalDateTime endDate,
            FinancialMovementStatus statusByDueDate,
            FinancialMovementStatus statusByPayDate,
            FinancialMovementStatus status);

}


