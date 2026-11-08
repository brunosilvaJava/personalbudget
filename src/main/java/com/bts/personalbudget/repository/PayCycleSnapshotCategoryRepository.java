package com.bts.personalbudget.repository;

import com.bts.personalbudget.core.domain.entity.PayCycleSnapshotCategoryEntity;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PayCycleSnapshotCategoryRepository extends JpaRepository<PayCycleSnapshotCategoryEntity, Long> {
    List<PayCycleSnapshotCategoryEntity> findAllBySnapshotIdOrderByIdAsc(Long snapshotId);

    List<PayCycleSnapshotCategoryEntity> findAllBySnapshotIdInOrderByIdAsc(Collection<Long> snapshotIds);
}
