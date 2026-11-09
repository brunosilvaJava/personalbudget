package com.bts.personalbudget.repository;

import com.bts.personalbudget.core.domain.entity.BudgetEnvelopeEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface BudgetEnvelopeRepository extends JpaRepository<BudgetEnvelopeEntity, Long> {

    @Query("""
            SELECT e FROM BudgetEnvelopeEntity e
            JOIN FETCH e.cycle
            WHERE e.code = :code AND e.flagActive = true
            """)
    Optional<BudgetEnvelopeEntity> findActiveByCode(@Param("code") UUID code);

    @Query("""
            SELECT e FROM BudgetEnvelopeEntity e
            JOIN FETCH e.cycle
            WHERE e.cycle.id = :cycleId AND e.flagActive = true
            ORDER BY e.id ASC
            """)
    List<BudgetEnvelopeEntity> findActiveByCycleId(@Param("cycleId") Long cycleId);

    @Query("""
            SELECT COUNT(e) > 0 FROM BudgetEnvelopeEntity e
            WHERE e.cycle.id = :cycleId AND e.categoryCode = :categoryCode AND e.flagActive = true
            """)
    boolean existsActiveByCycleIdAndCategoryCode(@Param("cycleId") Long cycleId,
                                                 @Param("categoryCode") UUID categoryCode);
}
