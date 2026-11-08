package com.bts.personalbudget.core.domain.service.paycycle;

import com.bts.personalbudget.core.domain.entity.PayCycleEntity;
import com.bts.personalbudget.core.domain.entity.PayCycleSnapshotCategoryEntity;
import com.bts.personalbudget.core.domain.entity.PayCycleSnapshotEntity;
import com.bts.personalbudget.core.domain.enumerator.PayCycleLifecycle;
import com.bts.personalbudget.exception.ConflictException;
import com.bts.personalbudget.mapper.PayCycleSnapshotMapper;
import com.bts.personalbudget.repository.PayCycleRepository;
import com.bts.personalbudget.repository.PayCycleSnapshotCategoryRepository;
import com.bts.personalbudget.repository.PayCycleSnapshotRepository;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PayCycleSnapshotService {
    private final PayCycleConfigService configService;
    private final PayCycleRepository cycleRepository;
    private final PayCycleSnapshotRepository snapshotRepository;
    private final PayCycleSnapshotCategoryRepository categoryRepository;
    private final PayCycleSnapshotMapper mapper;

    /**
     * Called in the same transaction that locks configuration, anchor and cycle in that order,
     * closes and flushes the cycle, then calculates its summary at the resulting version. This
     * layer does not expose closure until recovery and movement guards are integrated.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public PayCycleSnapshot recordClosure(final PayCycleSummary summary) {
        log.info("m=recordClosure, cycleCode={}", summary.cycle().cycle().code());
        configService.lock();
        final UUID code = summary.cycle().cycle().code();
        final PayCycleEntity cycle = cycleRepository.findForUpdateByCode(code)
                .orElseThrow(() -> new EntityNotFoundException("Pay cycle not found"));
        if (cycle.getLifecycle() != PayCycleLifecycle.CLOSED
                || !Objects.equals(cycle.getVersion(), summary.cycle().cycle().version())) {
            throw new ConflictException("O ciclo foi alterado ou não está fechado");
        }
        if (summary.projectionIncomplete()) {
            throw new ConflictException("O ciclo tem pendências de projeção e não pode ser fechado");
        }
        if (snapshotRepository.existsByPayCycleIdAndCycleVersion(cycle.getId(), cycle.getVersion())) {
            throw new ConflictException("Já existe um snapshot para esta versão do ciclo");
        }
        final int version = snapshotRepository.findFirstByPayCycleIdOrderBySnapshotVersionDesc(cycle.getId())
                .map(PayCycleSnapshotEntity::getSnapshotVersion).orElse(0) + 1;
        final PayCycleSnapshot snapshot = new PayCycleSnapshot(UUID.randomUUID(), code, version,
                cycle.getVersion(), null, summary.recognizedRevenue(), summary.recognizedExpense(),
                summary.projectedRevenue(), summary.projectedExpense(), summary.categories().stream()
                .map(c -> new PayCycleSnapshot.Category(c.categoryCode(), c.categoryName(), c.operationType(),
                        c.recognizedAmount(), c.projectedAmount())).toList());
        final PayCycleSnapshotEntity saved = snapshotRepository.saveAndFlush(mapper.toEntity(snapshot, cycle));
        categoryRepository.saveAll(snapshot.categories().stream().map(c -> mapper.toEntity(c, saved)).toList());
        categoryRepository.flush();
        return mapper.toModel(saved, categoryRepository.findAllBySnapshotIdOrderByIdAsc(saved.getId()));
    }

    @Transactional(readOnly = true)
    public List<PayCycleSnapshot> findSnapshots(final UUID code) {
        log.info("m=findSnapshots, code={}", code);
        final PayCycleEntity cycle = cycleRepository.findByCode(code)
                .orElseThrow(() -> new EntityNotFoundException("Pay cycle not found"));
        final List<PayCycleSnapshotEntity> snapshots =
                snapshotRepository.findAllByPayCycleIdOrderBySnapshotVersionDesc(cycle.getId());
        if (snapshots.isEmpty()) {
            return List.of();
        }
        final List<Long> snapshotIds = snapshots.stream().map(PayCycleSnapshotEntity::getId).toList();
        final Map<Long, List<PayCycleSnapshotCategoryEntity>> categoriesBySnapshot =
                categoryRepository.findAllBySnapshotIdInOrderByIdAsc(snapshotIds).stream()
                        .collect(Collectors.groupingBy(category -> category.getSnapshot().getId()));
        return snapshots.stream()
                .map(snapshot -> mapper.toModel(snapshot, categoriesBySnapshot.getOrDefault(snapshot.getId(), List.of())))
                .toList();
    }
}
