package com.bts.personalbudget.repository;

import com.bts.personalbudget.core.domain.entity.AccountTransferEntity;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AccountTransferRepository extends JpaRepository<AccountTransferEntity, Long> {

    @Query("""
            SELECT t FROM AccountTransferEntity t
            JOIN FETCH t.fromAccount
            JOIN FETCH t.toAccount
            WHERE t.code = :code
            """)
    Optional<AccountTransferEntity> findByCode(@Param("code") UUID code);

    /** No fetch join: the lock is on the transfer row only, never on the accounts. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM AccountTransferEntity t WHERE t.code = :code")
    Optional<AccountTransferEntity> findForUpdateByCode(@Param("code") UUID code);

    @Query("""
            SELECT t FROM AccountTransferEntity t
            JOIN FETCH t.fromAccount f
            JOIN FETCH t.toAccount a
            WHERE t.effectiveDate BETWEEN :startDate AND :endDate
              AND (:accountCode IS NULL OR f.code = :accountCode OR a.code = :accountCode)
            ORDER BY t.effectiveDate DESC, t.id DESC
            """)
    List<AccountTransferEntity> findByFilters(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("accountCode") UUID accountCode);

    @Query("""
            SELECT COALESCE(SUM(t.amount), 0) FROM AccountTransferEntity t
            WHERE t.toAccount.id = :accountId
              AND t.reversedAt IS NULL
              AND t.effectiveDate BETWEEN :startDate AND :endDate
            """)
    BigDecimal sumActiveIncomingBetween(@Param("accountId") Long accountId,
                                        @Param("startDate") LocalDate startDate,
                                        @Param("endDate") LocalDate endDate);

    @Query("""
            SELECT COALESCE(SUM(t.amount), 0) FROM AccountTransferEntity t
            WHERE t.fromAccount.id = :accountId
              AND t.reversedAt IS NULL
              AND t.effectiveDate BETWEEN :startDate AND :endDate
            """)
    BigDecimal sumActiveOutgoingBetween(@Param("accountId") Long accountId,
                                        @Param("startDate") LocalDate startDate,
                                        @Param("endDate") LocalDate endDate);

    @Query("""
            SELECT MIN(t.effectiveDate) FROM AccountTransferEntity t
            WHERE (t.fromAccount.id = :accountId OR t.toAccount.id = :accountId)
              AND t.reversedAt IS NULL
            """)
    Optional<LocalDate> findEarliestActiveEffectiveDateByAccount(@Param("accountId") Long accountId);
}
