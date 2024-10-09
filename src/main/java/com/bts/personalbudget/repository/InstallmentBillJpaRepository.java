package com.bts.personalbudget.repository;

import com.bts.personalbudget.core.domain.entity.InstallmentBillEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InstallmentBillJpaRepository extends JpaRepository<InstallmentBillEntity, Long> {
    Optional<InstallmentBillEntity> findByCode(UUID code);
    List<InstallmentBillEntity> findAllByFlagActive(Boolean flagActive);
}
