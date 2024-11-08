package com.bts.personalbudget.repository;

import com.bts.personalbudget.core.domain.entity.InstallmentBillEntity;
import com.bts.personalbudget.core.domain.enumerator.InstallmentBillStatus;
import org.springframework.data.jpa.repository.JpaRepository;
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
            Boolean flagActive);

    List<InstallmentBillEntity> findAllByNextInstallmentDateBetweenAndStatusAndFlagActive(
            LocalDate initialDate,
            LocalDate endDate,
            InstallmentBillStatus status,
            Boolean flagActives);
}
