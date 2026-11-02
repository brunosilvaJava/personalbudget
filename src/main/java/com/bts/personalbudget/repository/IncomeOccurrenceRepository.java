package com.bts.personalbudget.repository;

import com.bts.personalbudget.core.domain.entity.IncomeOccurrenceEntity;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface IncomeOccurrenceRepository extends JpaRepository<IncomeOccurrenceEntity, Long> {

    /**
     * Fetches the schedule: every caller maps the occurrence to a model that carries the schedule
     * code, and the listing would otherwise issue one query per row.
     */
    @Query("""
            SELECT o FROM IncomeOccurrenceEntity o
            JOIN FETCH o.incomeSchedule s
            WHERE s.code = :scheduleCode
            ORDER BY o.nominalMonth ASC
            """)
    List<IncomeOccurrenceEntity> findByScheduleCode(@Param("scheduleCode") UUID scheduleCode);

    @Query("""
            SELECT o FROM IncomeOccurrenceEntity o
            JOIN FETCH o.incomeSchedule s
            WHERE s.code IN :scheduleCodes
            """)
    List<IncomeOccurrenceEntity> findByScheduleCodeIn(@Param("scheduleCodes") Collection<UUID> scheduleCodes);

    @Query("""
            SELECT o FROM IncomeOccurrenceEntity o
            JOIN FETCH o.incomeSchedule s
            WHERE s.code = :scheduleCode AND o.nominalMonth = :nominalMonth
            """)
    Optional<IncomeOccurrenceEntity> findByScheduleCodeAndNominalMonth(@Param("scheduleCode") UUID scheduleCode,
                                                                       @Param("nominalMonth") LocalDate nominalMonth);

    Optional<IncomeOccurrenceEntity> findByMovementCode(UUID movementCode);

}
