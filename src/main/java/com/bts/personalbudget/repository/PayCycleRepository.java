package com.bts.personalbudget.repository;

import com.bts.personalbudget.core.domain.entity.PayCycleEntity;
import com.bts.personalbudget.core.domain.enumerator.PayCycleLifecycle;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PayCycleRepository extends JpaRepository<PayCycleEntity, Long> {

    /**
     * Fetches the anchor schedule: every caller maps the cycle to a model that carries its code, and the
     * listing would otherwise issue one query per row.
     */
    @Query("""
            SELECT c FROM PayCycleEntity c
            LEFT JOIN FETCH c.anchorSchedule
            WHERE c.lifecycle IN :lifecycles
               OR c.lifecycle = com.bts.personalbudget.core.domain.enumerator.PayCycleLifecycle.CLOSED
            ORDER BY c.startDate ASC, c.id ASC
            """)
    List<PayCycleEntity> findByLifecycleIn(@Param("lifecycles") Collection<PayCycleLifecycle> lifecycles);

    @Query("""
            SELECT c FROM PayCycleEntity c
            LEFT JOIN FETCH c.anchorSchedule
            WHERE c.code = :code
            """)
    Optional<PayCycleEntity> findByCode(@Param("code") UUID code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM PayCycleEntity c WHERE c.code = :code")
    Optional<PayCycleEntity> findForUpdateByCode(@Param("code") UUID code);

    /**
     * The cycles of a lifecycle, locked. A query for ACTIVE also includes CLOSED cycles because both
     * remain in the non-cancelled chain. Not fetch-joined: the schedule is locked before cycles.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT c FROM PayCycleEntity c
            WHERE c.lifecycle = :lifecycle
               OR ( :lifecycle = com.bts.personalbudget.core.domain.enumerator.PayCycleLifecycle.ACTIVE
                    AND c.lifecycle = com.bts.personalbudget.core.domain.enumerator.PayCycleLifecycle.CLOSED)
            ORDER BY c.startDate ASC, c.id ASC
            """)
    List<PayCycleEntity> findForUpdateByLifecycle(@Param("lifecycle") PayCycleLifecycle lifecycle);

}
