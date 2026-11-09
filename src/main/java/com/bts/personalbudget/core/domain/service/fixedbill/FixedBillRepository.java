package com.bts.personalbudget.core.domain.service.fixedbill;

import com.bts.personalbudget.core.domain.entity.FixedBillEntity;
import com.bts.personalbudget.core.domain.enumerator.FixedBillStatus;
import com.bts.personalbudget.core.domain.enumerator.OperationType;
import com.bts.personalbudget.core.domain.enumerator.RecurrenceType;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FixedBillRepository extends JpaRepository<FixedBillEntity, Long> {

    @EntityGraph(attributePaths = {"calendarFixedBillEntityList"})
    Optional<FixedBillEntity> findByCodeAndFlagActiveTrue(UUID code);

    /**
     * Without the active filter: a bill deleted before answers the repeated DELETE with 204 instead of 404.
     */
    Optional<FixedBillEntity> findByCode(UUID code);

    List<FixedBillEntity> findAllByNextDueDateAndStatusAndFlagActive(LocalDate nextDueDate, FixedBillStatus fixedBillStatus, Boolean flagActive);

    /**
     * Fetches the calendar too: the balance projection maps every bill to a model (days included)
     * to enumerate its occurrences.
     */
    @EntityGraph(attributePaths = {"calendarFixedBillEntityList"})
    List<FixedBillEntity> findAllByNextDueDateLessThanEqualAndStatusAndFlagActive(LocalDate nextDueDate, FixedBillStatus fixedBillStatus, Boolean flagActive);

    /**
     * Locks the row for the duration of the transaction so the scheduled job and a concurrent
     * request (e.g. the account being edited while the catch-up loop runs) serialize on the
     * same FixedBill instead of racing each other.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"calendarFixedBillEntityList"})
    @Query("SELECT fb FROM FixedBillEntity fb WHERE fb.code = :code AND fb.flagActive = true")
    Optional<FixedBillEntity> findForUpdateByCode(@Param("code") UUID code);

    @Query("""
            SELECT fb FROM FixedBillEntity fb
            WHERE fb.flagActive = true
            AND (:description IS NULL OR :description = '' OR LOWER(fb.description) LIKE LOWER(CONCAT('%', :description, '%')))
            AND (:operationTypes IS NULL OR fb.operationType IN :operationTypes)
            AND (:statuses IS NULL OR fb.status IN :statuses)
            AND (:recurrenceTypes IS NULL OR fb.recurrenceType IN :recurrenceTypes)
            ORDER BY fb.description ASC
            """)
    Page<FixedBillEntity> findByFilters(
            @Param("description") String description,
            @Param("operationTypes") List<OperationType> operationTypes,
            @Param("statuses") List<FixedBillStatus> statuses,
            @Param("recurrenceTypes") List<RecurrenceType> recurrenceTypes,
            Pageable pageable
    );
}