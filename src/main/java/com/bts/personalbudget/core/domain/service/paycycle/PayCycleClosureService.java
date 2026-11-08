package com.bts.personalbudget.core.domain.service.paycycle;

import com.bts.personalbudget.core.domain.entity.PayCycleEntity;
import com.bts.personalbudget.core.domain.enumerator.FinancialMovementStatus;
import com.bts.personalbudget.core.domain.enumerator.PayCycleLifecycle;
import com.bts.personalbudget.core.domain.enumerator.PayCycleStatus;
import com.bts.personalbudget.core.domain.model.FinancialMovement;
import com.bts.personalbudget.core.domain.service.FinancialMovementService;
import com.bts.personalbudget.exception.ConflictException;
import com.bts.personalbudget.mapper.PayCycleMapper;
import com.bts.personalbudget.repository.IncomeScheduleRepository;
import com.bts.personalbudget.repository.PayCycleRepository;
import jakarta.persistence.EntityNotFoundException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PayCycleClosureService {
    private final PayCycleConfigService configService;
    private final IncomeScheduleRepository scheduleRepository;
    private final PayCycleRepository cycleRepository;
    private final PayCycleService payCycleService;
    private final PayCycleSummaryService summaryService;
    private final FinancialMovementService movementService;
    private final PayCycleSnapshotService snapshotService;
    private final PayCycleMapper mapper;
    private final Clock clock;

    @Transactional(readOnly = true)
    public PayCycleClosurePreview preview(final UUID code) {
        log.info("m=preview, code={}", code);
        if (!configService.find().isPayCycle()) {
            throw new ConflictException("Os ciclos financeiros não estão ativados");
        }
        final PayCycleSummary summary = summaryService.summarize(code);
        requireActiveCycle(summary.cycle().cycle());
        final List<FinancialMovement> lateMovements = findLateMovements(summary.cycle().cycle());
        final List<String> blockers = blockers(summary, lateMovements);
        if (summary.cycle().status() != PayCycleStatus.PENDING_CLOSE) {
            blockers.add("CYCLE_NOT_ENDED");
        }
        return new PayCycleClosurePreview(summary, blockers.isEmpty(), blockers, lateMovements);
    }

    @Transactional
    public PayCycleClosureResult close(final UUID code, final Long expectedVersion) {
        log.info("m=close, code={}, expectedVersion={}", code, expectedVersion);
        final PayCycleConfig config = configService.lock();
        if (!config.isPayCycle()) {
            throw new ConflictException("Os ciclos financeiros não estão ativados");
        }
        lockAnchor(config);
        final PayCycleEntity cycle = cycleRepository.findForUpdateByCode(code)
                .orElseThrow(() -> new EntityNotFoundException("PayCycleEntity not found for code " + code));
        requireActiveCycle(mapper.toModel(cycle));
        requireVersion(cycle, expectedVersion);
        if (LocalDate.now(clock).isBefore(cycle.getEndDate())) {
            throw new ConflictException("O ciclo ainda não terminou e não pode ser fechado");
        }

        cycle.setLifecycle(PayCycleLifecycle.CLOSED);
        cycleRepository.flush();
        final PayCycleSummary summary = summaryService.summarize(code);
        final List<FinancialMovement> lateMovements = findLateMovements(summary.cycle().cycle());
        final List<String> blockers = blockers(summary, lateMovements);
        if (!blockers.isEmpty()) {
            throw new ConflictException("O ciclo não pode ser fechado: " + String.join(", ", blockers));
        }

        final PayCycleSnapshot snapshot = snapshotService.recordClosure(summary);
        return new PayCycleClosureResult(summary.cycle(), snapshot);
    }

    @Transactional
    public PayCycleView reopen(final UUID code, final Long expectedVersion) {
        log.info("m=reopen, code={}, expectedVersion={}", code, expectedVersion);
        final PayCycleConfig config = configService.lock();
        lockAnchor(config);
        final PayCycleEntity cycle = cycleRepository.findForUpdateByCode(code)
                .orElseThrow(() -> new EntityNotFoundException("PayCycleEntity not found for code " + code));
        if (cycle.getLifecycle() != PayCycleLifecycle.CLOSED) {
            throw new ConflictException("Somente um ciclo fechado pode ser reaberto");
        }
        requireVersion(cycle, expectedVersion);
        cycle.setLifecycle(PayCycleLifecycle.ACTIVE);
        cycleRepository.flush();
        return payCycleService.findByCode(code);
    }

    private void lockAnchor(final PayCycleConfig config) {
        if (config.anchorScheduleCode() != null
                && scheduleRepository.findForUpdateByCode(config.anchorScheduleCode()).isEmpty()) {
            throw new EntityNotFoundException(
                    "IncomeScheduleEntity not found for code " + config.anchorScheduleCode());
        }
    }

    private List<FinancialMovement> findLateMovements(final PayCycle cycle) {
        return movementService.findByCompetenceInterval(cycle.startDate(), cycle.endDate()).stream()
                .filter(movement -> movement.status() == FinancialMovementStatus.LATE)
                .toList();
    }

    private static List<String> blockers(final PayCycleSummary summary,
                                         final List<FinancialMovement> lateMovements) {
        final List<String> blockers = new ArrayList<>();
        if (summary.projectionIncomplete()) {
            blockers.add("PROJECTION_INCOMPLETE");
        }
        if (!lateMovements.isEmpty()) {
            blockers.add("LATE_MOVEMENTS");
        }
        return blockers;
    }

    private static void requireActiveCycle(final PayCycle cycle) {
        if (cycle.lifecycle() != PayCycleLifecycle.ACTIVE) {
            throw new ConflictException("Somente ciclos ativos podem ser fechados");
        }
    }

    private static void requireVersion(final PayCycleEntity cycle, final Long expectedVersion) {
        if (expectedVersion == null || !expectedVersion.equals(cycle.getVersion())) {
            throw new ConflictException("O ciclo foi alterado; recarregue e tente novamente");
        }
    }
}
