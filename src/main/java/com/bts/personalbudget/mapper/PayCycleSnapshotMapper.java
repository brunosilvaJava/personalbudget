package com.bts.personalbudget.mapper;

import com.bts.personalbudget.core.domain.entity.PayCycleEntity;
import com.bts.personalbudget.core.domain.entity.PayCycleSnapshotCategoryEntity;
import com.bts.personalbudget.core.domain.entity.PayCycleSnapshotEntity;
import com.bts.personalbudget.core.domain.service.paycycle.PayCycleSnapshot;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;
import org.mapstruct.factory.Mappers;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface PayCycleSnapshotMapper {
    PayCycleSnapshotMapper INSTANCE = Mappers.getMapper(PayCycleSnapshotMapper.class);

    default PayCycleSnapshotEntity toEntity(PayCycleSnapshot snapshot, PayCycleEntity cycle) {
        return PayCycleSnapshotEntity.builder()
                .code(snapshot.code()).payCycle(cycle).snapshotVersion(snapshot.snapshotVersion())
                .cycleVersion(snapshot.cycleVersion()).recognizedRevenue(snapshot.recognizedRevenue())
                .recognizedExpense(snapshot.recognizedExpense()).projectedRevenue(snapshot.projectedRevenue())
                .projectedExpense(snapshot.projectedExpense()).build();
    }

    default PayCycleSnapshotCategoryEntity toEntity(PayCycleSnapshot.Category category,
                                                     PayCycleSnapshotEntity snapshot) {
        return PayCycleSnapshotCategoryEntity.builder().snapshot(snapshot)
                .categoryKey(category.categoryCode() == null ? "UNCATEGORIZED" : category.categoryCode().toString())
                .categoryCode(category.categoryCode()).categoryName(category.categoryName())
                .operationType(category.operationType()).recognizedAmount(category.recognizedAmount())
                .projectedAmount(category.projectedAmount()).build();
    }

    default PayCycleSnapshot toModel(PayCycleSnapshotEntity entity, List<PayCycleSnapshotCategoryEntity> categories) {
        return new PayCycleSnapshot(entity.getCode(), entity.getPayCycle().getCode(),
                entity.getSnapshotVersion(), entity.getCycleVersion(), entity.getCreatedDate(),
                entity.getRecognizedRevenue(), entity.getRecognizedExpense(), entity.getProjectedRevenue(),
                entity.getProjectedExpense(), categories.stream()
                        .map(c -> new PayCycleSnapshot.Category(c.getCategoryCode(), c.getCategoryName(),
                                c.getOperationType(), c.getRecognizedAmount(), c.getProjectedAmount()))
                        .toList());
    }
}
