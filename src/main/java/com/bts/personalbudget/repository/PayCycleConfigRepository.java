package com.bts.personalbudget.repository;

import com.bts.personalbudget.core.domain.entity.PayCycleConfigEntity;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface PayCycleConfigRepository extends JpaRepository<PayCycleConfigEntity, Long> {

    /**
     * The configuration is a single row, so the first one is the one. The anchor schedule is fetched:
     * the model carries its code.
     */
    @Query("""
            SELECT c FROM PayCycleConfigEntity c
            LEFT JOIN FETCH c.anchorSchedule
            ORDER BY c.id ASC
            """)
    List<PayCycleConfigEntity> findAllOrdered();

    /**
     * Locks the row for the duration of the transaction: the first step of every write to the cycles,
     * so two writers (the nightly job, an activation) never build the chain at the same time.
     *
     * <p>The anchor schedule is fetched in the same statement, so its row is locked right after the
     * configuration's and is never read before being locked: the order is configuration, then
     * schedule, then cycles. A configuration in CALENDAR_MONTH has no schedule and locks only itself.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT c FROM PayCycleConfigEntity c
            LEFT JOIN FETCH c.anchorSchedule
            ORDER BY c.id ASC
            """)
    List<PayCycleConfigEntity> findAllForUpdate();

    default Optional<PayCycleConfigEntity> findSingle() {
        return findAllOrdered().stream().findFirst();
    }

    default Optional<PayCycleConfigEntity> findSingleForUpdate() {
        return findAllForUpdate().stream().findFirst();
    }

}
