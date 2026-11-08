package com.bts.personalbudget.repository;

import com.bts.personalbudget.core.domain.entity.FinancialMovementEntity;
import com.bts.personalbudget.core.domain.enumerator.FinancialMovementStatus;
import com.bts.personalbudget.core.domain.enumerator.OperationType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FinancialMovementRepository
        extends JpaRepository<FinancialMovementEntity, Long>, JpaSpecificationExecutor<FinancialMovementEntity> {

    Optional<FinancialMovementEntity> findByCode(UUID code);

    Optional<FinancialMovementEntity> findByCodeAndFlagActiveTrue(UUID code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT fm FROM FinancialMovementEntity fm
            WHERE fm.code = :code AND fm.flagActive = true
            """)
    Optional<FinancialMovementEntity> findForUpdateByCodeAndFlagActiveTrue(@Param("code") UUID code);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE FinancialMovementEntity fm
            SET fm.status = :late, fm.lastModifiedDate = :modifiedAt
            WHERE fm.status = :pending
              AND fm.dueDate < :overdueBefore
              AND fm.flagActive = true
            """)
    int markOverduePendingAsLate(
            @Param("pending") FinancialMovementStatus pending,
            @Param("late") FinancialMovementStatus late,
            @Param("overdueBefore") LocalDateTime overdueBefore,
            @Param("modifiedAt") Instant modifiedAt);

    /**
     * Deliberately not scoped to flagActive = true: a movement the user soft-deleted still
     * counts as "already generated" for this occurrence, so the recurrence job must not
     * recreate it.
     */
    boolean existsByRecurrenceBillCodeAndOccurrenceDate(UUID recurrenceBillCode, LocalDate occurrenceDate);

    /**
     * Same rule as existsByRecurrenceBillCodeAndOccurrenceDate (not scoped to flagActive), in
     * bulk: the occurrences of these recurrence bills already generated within the period.
     */
    List<FinancialMovementEntity> findAllByRecurrenceBillCodeInAndOccurrenceDateBetween(
            Collection<UUID> recurrenceBillCodes,
            LocalDate initialDate,
            LocalDate endDate);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT fm FROM FinancialMovementEntity fm
            WHERE fm.recurrenceBillCode IN :recurrenceBillCodes
              AND fm.occurrenceDate BETWEEN :initialDate AND :endDate
            """)
    List<FinancialMovementEntity> findForUpdateByRecurrenceBillCodeInAndOccurrenceDateBetween(
            @Param("recurrenceBillCodes") Collection<UUID> recurrenceBillCodes,
            @Param("initialDate") LocalDate initialDate,
            @Param("endDate") LocalDate endDate);

    Optional<List<FinancialMovementEntity>> findAllByDescriptionContainsAndOperationTypeInAndStatusInAndMovementDateBetweenAndFlagActiveTrue(
            String description,
            List<OperationType> operationTypes,
            List<FinancialMovementStatus> statuses,
            LocalDateTime startDate,
            LocalDateTime endDate
    );

    @Query("""
            SELECT fm FROM FinancialMovementEntity fm WHERE fm.flagActive = true
              AND fm.status IN (:pending, :late)
              AND fm.dueDate BETWEEN :startDate AND :endDate
            """)
    List<FinancialMovementEntity> findUnpaidByDueDateBetween(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("pending") FinancialMovementStatus pending,
            @Param("late") FinancialMovementStatus late
    );

    @Query("""
            SELECT COALESCE(SUM(fm.amount - COALESCE(fm.amountPaid, 0)), 0)
            FROM FinancialMovementEntity fm
            WHERE fm.status IN (:statuses) AND fm.dueDate <= :dueDate AND fm.flagActive = true
            """)
    BigDecimal sumUnpaidAmountByStatusAndDueDateLessThanEqual(
            @Param("statuses") List<FinancialMovementStatus> statuses,
            @Param("dueDate") LocalDateTime dueDate
    );

    /**
     * The active movements whose competence date is in {@code [from, toExclusive)}, oldest competence
     * first. A movement without competence date matches nothing. Backed by
     * idx_financial_movement_competence_date.
     */
    @Query("""
            SELECT fm FROM FinancialMovementEntity fm
            WHERE fm.flagActive = true
              AND fm.competenceDate >= :from AND fm.competenceDate < :toExclusive
            ORDER BY fm.competenceDate ASC, fm.id ASC
            """)
    List<FinancialMovementEntity> findActiveByCompetenceDateInterval(
            @Param("from") LocalDate from,
            @Param("toExclusive") LocalDate toExclusive
    );
}