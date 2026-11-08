package com.bts.personalbudget.repository;

import com.bts.personalbudget.core.domain.entity.PayCycleSnapshotEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PayCycleSnapshotRepository extends JpaRepository<PayCycleSnapshotEntity, Long> {
    List<PayCycleSnapshotEntity> findAllByPayCycleIdOrderBySnapshotVersionDesc(Long cycleId);
    Optional<PayCycleSnapshotEntity> findFirstByPayCycleIdOrderBySnapshotVersionDesc(Long cycleId);
    boolean existsByPayCycleIdAndCycleVersion(Long cycleId, Long cycleVersion);
}
