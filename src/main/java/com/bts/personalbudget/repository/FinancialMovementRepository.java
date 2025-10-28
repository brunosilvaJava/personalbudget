package com.bts.personalbudget.repository;

import com.bts.personalbudget.core.domain.entity.FinancialMovementEntity;
import com.bts.personalbudget.core.domain.enumerator.FinancialMovementStatus;
import com.bts.personalbudget.core.domain.enumerator.OperationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FinancialMovementRepository extends JpaRepository<FinancialMovementEntity, Long> {

    Optional<FinancialMovementEntity> findByCode(UUID code);

    Page<FinancialMovementEntity> findAllByDescriptionContainsAndOperationTypeInAndStatusInAndMovementDateBetweenAndFlagActiveTrue(
            String description,
            List<OperationType> operationTypes,
            List<FinancialMovementStatus> statuses,
            LocalDateTime startDate,
            LocalDateTime endDate,
            Pageable pageable
    );

    Optional<List<FinancialMovementEntity>> findAllByDescriptionContainsAndOperationTypeInAndStatusInAndMovementDateBetweenAndFlagActiveTrue(
            String description,
            List<OperationType> operationTypes,
            List<FinancialMovementStatus> statuses,
            LocalDateTime startDate,
            LocalDateTime endDate
    );

    @Query("""
            SELECT fm FROM FinancialMovementEntity fm WHERE fm.flagActive = true 
                        AND ((fm.status = :pending AND fm.dueDate BETWEEN :startDate AND :endDate) 
                        OR (fm.status = :paidOut AND fm.payDate BETWEEN :startDate AND :endDate) 
                        OR (fm.status = :late AND fm.dueDate BETWEEN :startDate AND :endDate))
            """)
    List<FinancialMovementEntity> findAllByStatusAndDates(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("pending") FinancialMovementStatus pending,
            @Param("paidOut") FinancialMovementStatus paidOut,
            @Param("late") FinancialMovementStatus late
    );

    @Query("""
            SELECT SUM(fm.amountPaid) FROM FinancialMovementEntity fm
                        WHERE fm.status = :status AND fm.payDate <= :payDate AND fm.flagActive = true
            """)
    Optional<BigDecimal> sumAmountPaidByStatusAndPayDateLessThanEqual(
            @Param("status") FinancialMovementStatus status,
            @Param("payDate") LocalDateTime payDate
    );

    @Query("""
            SELECT SUM(fm.amount) FROM FinancialMovementEntity fm 
                    WHERE fm.status IN :statuses AND fm.dueDate <= :dueDate AND fm.flagActive = true
            """)
    Optional<BigDecimal> sumAmountByStatusAndDueDateLessThanEqual(
            @Param("statuses") List<FinancialMovementStatus> statuses,
            @Param("dueDate") LocalDateTime dueDate
    );
}