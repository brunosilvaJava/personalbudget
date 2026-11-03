package com.bts.personalbudget.repository;

import com.bts.personalbudget.core.domain.entity.PayCycleAdjustmentEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PayCycleAdjustmentRepository extends JpaRepository<PayCycleAdjustmentEntity, Long> {

    /**
     * The adjustments that touched the cycle, as the previous or as the next one, newest first. Both
     * cycles are fetched (and left-joined: a deactivation may have only one of them) because the model
     * carries their codes.
     */
    @Query("""
            SELECT a FROM PayCycleAdjustmentEntity a
            LEFT JOIN FETCH a.previousCycle
            LEFT JOIN FETCH a.nextCycle
            WHERE a.previousCycle.id = :cycleId OR a.nextCycle.id = :cycleId
            ORDER BY a.createdDate DESC, a.id DESC
            """)
    List<PayCycleAdjustmentEntity> findByCycleId(@Param("cycleId") Long cycleId);

}
