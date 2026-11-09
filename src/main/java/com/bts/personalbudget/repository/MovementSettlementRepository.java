package com.bts.personalbudget.repository;

import com.bts.personalbudget.core.domain.entity.MovementSettlementEntity;
import com.bts.personalbudget.core.domain.enumerator.OperationType;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MovementSettlementRepository extends JpaRepository<MovementSettlementEntity, Long> {

    @Query("""
            SELECT s FROM MovementSettlementEntity s
            JOIN FETCH s.financialMovement m
            JOIN FETCH s.account
            WHERE m.code = :movementCode
            ORDER BY s.effectiveDate ASC, s.id ASC
            """)
    List<MovementSettlementEntity> findAllByMovementCode(@Param("movementCode") UUID movementCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT s FROM MovementSettlementEntity s
            WHERE s.code = :settlementCode
              AND s.financialMovement.id = :movementId
            """)
    Optional<MovementSettlementEntity> findForUpdateByCodeAndMovementId(
            @Param("settlementCode") UUID settlementCode,
            @Param("movementId") Long movementId);

    @Query("""
            SELECT s FROM MovementSettlementEntity s
            JOIN FETCH s.financialMovement
            WHERE s.invoicePayment.id = :paymentId
              AND s.reversedAt IS NULL
            ORDER BY s.id ASC
            """)
    List<MovementSettlementEntity> findActiveByInvoicePaymentId(@Param("paymentId") Long paymentId);

    @Query("""
            SELECT s FROM MovementSettlementEntity s
            JOIN FETCH s.financialMovement m
            JOIN FETCH s.account
            WHERE m.flagActive = true
              AND s.reversedAt IS NULL
              AND s.effectiveDate BETWEEN :startDate AND :endDate
            ORDER BY s.effectiveDate ASC, s.id ASC
            """)
    List<MovementSettlementEntity> findActiveForCashDateBetween(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    @Query("""
            SELECT s FROM MovementSettlementEntity s
            JOIN FETCH s.financialMovement m
            JOIN FETCH s.account
            WHERE m.flagActive = true
              AND s.reversedAt IS NULL
              AND m.competenceDate >= :from
              AND m.competenceDate < :toExclusive
            ORDER BY m.competenceDate ASC, s.effectiveDate ASC, s.id ASC
            """)
    List<MovementSettlementEntity> findActiveByMovementCompetenceInterval(
            @Param("from") LocalDate from,
            @Param("toExclusive") LocalDate toExclusive);

    @Query("""
            SELECT COALESCE(SUM(CASE WHEN m.operationType = :debit THEN -s.amount ELSE s.amount END), 0)
            FROM MovementSettlementEntity s
            JOIN s.financialMovement m
            WHERE m.flagActive = true
              AND s.reversedAt IS NULL
              AND s.effectiveDate <= :date
            """)
    java.math.BigDecimal sumActiveSignedAmountsThrough(
            @Param("date") LocalDate date,
            @Param("debit") OperationType debit);

    @Query("""
            SELECT COALESCE(SUM(CASE WHEN m.operationType = :debit THEN -s.amount ELSE s.amount END), 0)
            FROM MovementSettlementEntity s
            JOIN s.financialMovement m
            WHERE s.account.id = :accountId
              AND m.flagActive = true
              AND s.reversedAt IS NULL
              AND s.effectiveDate BETWEEN :startDate AND :endDate
            """)
    java.math.BigDecimal sumActiveSignedAmountsByAccountBetween(
            @Param("accountId") Long accountId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("debit") OperationType debit);

    @Query("""
            SELECT MIN(s.effectiveDate)
            FROM MovementSettlementEntity s
            JOIN s.financialMovement m
            WHERE s.account.id = :accountId
              AND m.flagActive = true
              AND s.reversedAt IS NULL
            """)
    Optional<LocalDate> findEarliestActiveEffectiveDateByAccount(@Param("accountId") Long accountId);

    /**
     * Active settlements of one account in a period, oldest first, with the movement (description,
     * type, category) and the invoice payment that generated them, when any. For the statement.
     */
    @Query("""
            SELECT s FROM MovementSettlementEntity s
            JOIN FETCH s.financialMovement m
            LEFT JOIN FETCH s.invoicePayment
            WHERE s.account.id = :accountId
              AND m.flagActive = true
              AND s.reversedAt IS NULL
              AND s.effectiveDate BETWEEN :startDate AND :endDate
            ORDER BY s.effectiveDate ASC, s.id ASC
            """)
    List<MovementSettlementEntity> findActiveByAccountBetween(
            @Param("accountId") Long accountId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);
}
