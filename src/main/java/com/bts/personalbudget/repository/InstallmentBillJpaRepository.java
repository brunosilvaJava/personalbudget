package com.bts.personalbudget.repository;

import com.bts.personalbudget.core.domain.entity.InstallmentBillEntity;
import com.bts.personalbudget.core.domain.enumerator.InstallmentBillStatus;
import com.bts.personalbudget.core.domain.enumerator.OperationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InstallmentBillJpaRepository extends JpaRepository<InstallmentBillEntity, Long> {

    Optional<InstallmentBillEntity> findByCode(UUID code);

    List<InstallmentBillEntity> findAllByFlagActive(Boolean flagActive);

    List<InstallmentBillEntity> findAllByNextInstallmentDateAndStatusAndFlagActive(
            LocalDate nextInstallmentDate,
            InstallmentBillStatus status,
            Boolean flagActive
    );

    List<InstallmentBillEntity> findAllByNextInstallmentDateBetweenAndStatusAndFlagActive(
            LocalDate initialDate,
            LocalDate endDate,
            InstallmentBillStatus status,
            Boolean flagActive
    );

    @Query("SELECT ib FROM InstallmentBillEntity ib WHERE " +
            "(:description IS NULL OR :description = '' OR LOWER(ib.description) LIKE LOWER(CONCAT('%', :description, '%'))) AND " +
            "(:operationTypes IS NULL OR ib.operationType IN :operationTypes) AND " +
            "(:statuses IS NULL OR ib.status IN :statuses) AND " +
            "ib.flagActive = :flagActive")
    Page<InstallmentBillEntity> findByFilters(
            @Param("description") String description,
            @Param("operationTypes") List<OperationType> operationTypes,
            @Param("statuses") List<InstallmentBillStatus> statuses,
            @Param("flagActive") Boolean flagActive,
            Pageable pageable
    );
}