package com.bts.personalbudget.controller.fixedbill;

import com.bts.personalbudget.core.domain.entity.FixedBillEntity;
import com.bts.personalbudget.core.domain.enumerator.FixedBillStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FixedBillRepository extends JpaRepository<FixedBillEntity, Long> {
    Optional<FixedBillEntity> findByCode(UUID code);
    List<FixedBillEntity> findAllByNextDueDateAndStatusAndFlagActive(LocalDate nextDueDate, FixedBillStatus fixedBillStatus, Boolean flagActive);

    List<FixedBillEntity> findAllByFlagActiveTrueAndStatusAndNextDueDateBetween(
            FixedBillStatus status,
            LocalDate startDate,
            LocalDate endDate);
}
