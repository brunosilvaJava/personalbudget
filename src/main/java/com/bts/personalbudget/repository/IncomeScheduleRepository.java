package com.bts.personalbudget.repository;

import com.bts.personalbudget.core.domain.entity.IncomeScheduleEntity;
import com.bts.personalbudget.core.domain.enumerator.IncomeScheduleStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
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
public interface IncomeScheduleRepository extends JpaRepository<IncomeScheduleEntity, Long> {

    Optional<IncomeScheduleEntity> findByCode(UUID code);

    /**
     * Locks the row for the duration of the transaction so the scheduled job, an early
     * materialization and an edit of the same schedule serialize instead of racing each other
     * (the same approach as FixedBillRepository.findForUpdateByCode).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM IncomeScheduleEntity s WHERE s.code = :code")
    Optional<IncomeScheduleEntity> findForUpdateByCode(@Param("code") UUID code);

    List<IncomeScheduleEntity> findAllByStatusInOrderByDescriptionAsc(Collection<IncomeScheduleStatus> statuses);

    List<IncomeScheduleEntity> findAllByStatus(IncomeScheduleStatus status);

    /**
     * The schedules the posting job has to look at: active and already started. The start month is
     * compared as the first day of the month.
     */
    List<IncomeScheduleEntity> findAllByStatusAndStartMonthLessThanEqual(IncomeScheduleStatus status, LocalDate startMonth);

    boolean existsByAnchorTrueAndStatus(IncomeScheduleStatus status);

    boolean existsByAnchorTrueAndStatusAndCodeNot(IncomeScheduleStatus status, UUID code);

    boolean existsByReplacesFixedBillCodeAndStatus(UUID replacesFixedBillCode, IncomeScheduleStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT s FROM IncomeScheduleEntity s
            WHERE s.replacesFixedBillCode = :fixedBillCode
              AND s.status = :status
              AND s.code <> :code
            """)
    List<IncomeScheduleEntity> findActiveReplacementsForUpdate(
            @Param("fixedBillCode") UUID fixedBillCode,
            @Param("status") IncomeScheduleStatus status,
            @Param("code") UUID code);

}
