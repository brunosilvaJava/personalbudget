package com.bts.personalbudget.core.domain.repository;

import com.bts.personalbudget.core.domain.entity.InstallmentBillEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InstallmentBillRepository extends JpaRepository<InstallmentBillEntity, Long> {
    Optional<InstallmentBillEntity> findByCodeAndFlagActive(UUID code, Boolean flgActive);
}
