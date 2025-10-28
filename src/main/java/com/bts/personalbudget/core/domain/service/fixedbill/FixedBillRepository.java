package com.bts.personalbudget.core.domain.service.fixedbill;

import com.bts.personalbudget.core.domain.entity.FixedBillEntity;
import com.bts.personalbudget.core.domain.enumerator.FixedBillStatus;
import com.bts.personalbudget.core.domain.enumerator.OperationType;
import com.bts.personalbudget.core.domain.enumerator.RecurrenceType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FixedBillRepository extends JpaRepository<FixedBillEntity, Long> {

    @EntityGraph(attributePaths = {"calendarFixedBillEntityList"})
    Optional<FixedBillEntity> findByCodeAndFlagActiveTrue(UUID code);

    List<FixedBillEntity> findAllByNextDueDateAndStatusAndFlagActive(LocalDate nextDueDate, FixedBillStatus fixedBillStatus, Boolean flagActive);

    List<FixedBillEntity> findAllByFlagActiveTrueAndStatusAndNextDueDateBetween(
            FixedBillStatus status,
            LocalDate startDate,
            LocalDate endDate);

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