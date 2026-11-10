package com.bts.personalbudget.repository;

import com.bts.personalbudget.core.domain.entity.ReceivableWriteOffEntity;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ReceivableWriteOffRepository extends JpaRepository<ReceivableWriteOffEntity, Long> {

    /** Write-offs whose date is in {@code [from, toExclusive)}, of receivables that are still active. */
    @Query("""
            SELECT w FROM ReceivableWriteOffEntity w
            JOIN FETCH w.category
            WHERE w.effectiveDate >= :from AND w.effectiveDate < :toExclusive
              AND w.receivable.flagActive = true
            ORDER BY w.effectiveDate ASC, w.id ASC
            """)
    List<ReceivableWriteOffEntity> findActiveByEffectiveDateInterval(
            @Param("from") LocalDate from, @Param("toExclusive") LocalDate toExclusive);
}
