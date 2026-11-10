package com.bts.personalbudget.core.domain.service.paycycle;

import com.bts.personalbudget.core.domain.entity.PayCycleEntity;
import com.bts.personalbudget.core.domain.enumerator.FinancialMovementStatus;
import com.bts.personalbudget.core.domain.enumerator.PayCycleLifecycle;
import com.bts.personalbudget.core.domain.enumerator.PayCycleStatus;
import com.bts.personalbudget.core.domain.model.FinancialMovement;
import com.bts.personalbudget.core.domain.service.FinancialMovementService;
import com.bts.personalbudget.core.domain.service.fixedbill.FixedBillService;
import com.bts.personalbudget.core.domain.service.income.IncomeScheduleService;
import com.bts.personalbudget.core.domain.service.installmentbill.InstallmentBillService;
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
    private final FixedBillService fixedBillService;
    private final InstallmentBillService installmentBillService;
    private final IncomeScheduleService incomeScheduleService;
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
        final List<FinancialMovement> movements = findMovements(summary.cycle().cycle());
        final List<FinancialMovement> lateMovements = findLateMovements(movements);
        final List<String> blockers = blockers(summary, lateMovements, findOverduePendingMovements(movements));
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
        final PayCycleEntity cycleBeforeMaterialization = cycleRepository.findByCode(code)
                .orElseThrow(() -> new EntityNotFoundException("PayCycleEntity not found for code " + code));
        requireActiveCycle(mapper.toModel(cycleBeforeMaterialization));
        requireVersion(cycleBeforeMaterialization, expectedVersion);
        if (LocalDate.now(clock).isBefore(cycleBeforeMaterialization.getEndDate())) {
            throw new ConflictException("O ciclo ainda não terminou e não pode ser fechado");
        }
        materializeDueOccurrences(cycleBeforeMaterialization.getEndDate().minusDays(1));

        final PayCycleEntity cycle = cycleRepository.findForUpdateByCode(code)
                .orElseThrow(() -> new EntityNotFoundException("PayCycleEntity not found for code " + code));
        requireActiveCycle(mapper.toModel(cycle));
        requireVersion(cycle, expectedVersion);
        if (LocalDate.now(clock).isBefore(cycle.getEndDate())) {
            throw new ConflictException("O ciclo ainda não terminou e não pode ser fechado");
        }

        final PayCycleSummary materializedSummary = summaryService.summarize(code);
        final List<FinancialMovement> materializedMovements = findMovements(materializedSummary.cycle().cycle());
        final List<String> materializedBlockers = blockers(
                materializedSummary,
                findLateMovements(materializedMovements),
                findOverduePendingMovements(materializedMovements));
        if (!materializedBlockers.isEmpty()) {
            throw new ConflictException("O ciclo não pode ser fechado: "
                    + String.join(", ", materializedBlockers));
        }

        cycle.setLifecycle(PayCycleLifecycle.CLOSED);
        cycleRepository.flush();
        final PayCycleSummary summary = summaryService.summarize(code);
        final List<FinancialMovement> movements = findMovements(summary.cycle().cycle());
        final List<String> blockers = blockers(
                summary, findLateMovements(movements), findOverduePendingMovements(movements));
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

    private List<FinancialMovement> findMovements(final PayCycle cycle) {
        // A receivable is money owed by a third party, not a pending obligation of the user: a late
        // receivable must not block the closure. The third party's card installment (a debt to the
        // bank) stays in the list and still blocks when it is late.
        return movementService.findByCompetenceInterval(cycle.startDate(), cycle.endDate()).stream()
                .filter(movement -> movement.receivableSourceCode() == null)
                .toList();
    }

    private static List<FinancialMovement> findLateMovements(final List<FinancialMovement> movements) {
        return movements.stream()
                .filter(movement -> movement.status() == FinancialMovementStatus.LATE)
                .toList();
    }

    private List<FinancialMovement> findOverduePendingMovements(final List<FinancialMovement> movements) {
        final LocalDate today = LocalDate.now(clock);
        return movements.stream()
                .filter(movement -> movement.status() == FinancialMovementStatus.PENDING)
                .filter(movement -> movement.dueDate() != null
                        && movement.dueDate().toLocalDate().isBefore(today))
                .toList();
    }

    private void materializeDueOccurrences(final LocalDate throughDate) {
        fixedBillService.findCodesByNextDueDateLessThanEqual(throughDate)
                .forEach(code -> fixedBillService.postDueOccurrences(code, throughDate));
        installmentBillService.findCodesByNextInstallmentDateLessThanEqual(throughDate)
                .forEach(code -> installmentBillService.postDueInstallments(code, throughDate));
        incomeScheduleService.findCodesToPost(throughDate)
                .forEach(code -> incomeScheduleService.postDueOccurrences(code, throughDate));
    }

    private static List<String> blockers(final PayCycleSummary summary,
                                         final List<FinancialMovement> lateMovements,
                                         final List<FinancialMovement> overduePendingMovements) {
        final List<String> blockers = new ArrayList<>();
        if (summary.projectionIncomplete()) {
            blockers.add("PROJECTION_INCOMPLETE");
        }
        if (!lateMovements.isEmpty()) {
            blockers.add("LATE_MOVEMENTS");
        }
        if (!overduePendingMovements.isEmpty()) {
            blockers.add("OVERDUE_PENDING_MOVEMENTS");
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
