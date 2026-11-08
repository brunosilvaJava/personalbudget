package com.bts.personalbudget.core.domain.service.paycycle;

import com.bts.personalbudget.core.domain.entity.PayCycleEntity;
import com.bts.personalbudget.core.domain.enumerator.PayCycleLifecycle;
import com.bts.personalbudget.exception.ConflictException;
import com.bts.personalbudget.repository.IncomeScheduleRepository;
import com.bts.personalbudget.repository.PayCycleRepository;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class PayCycleMovementGuard {
    private final PayCycleConfigService configService;
    private final IncomeScheduleRepository scheduleRepository;
    private final PayCycleRepository cycleRepository;

    /**
     * Acquires the same leading locks as cycle writes. The returned guard can then lock a matching
     * closed cycle without allowing a closure to race with this movement transaction.
     */
    public void lock() {
        log.info("m=lock");
        final PayCycleConfig config = configService.lock();
        final UUID anchorCode = config.anchorScheduleCode();
        if (anchorCode != null) {
            scheduleRepository.findForUpdateByCode(anchorCode);
        }
    }

    public void requireEditable(final LocalDate competenceDate) {
        log.info("m=requireEditable, competenceDate={}", competenceDate);
        if (competenceDate == null) {
            return;
        }
        final PayCycleEntity closedCycle = cycleRepository
                .findClosedForUpdateByCompetenceDate(competenceDate)
                .orElse(null);
        if (closedCycle != null && closedCycle.getLifecycle() == PayCycleLifecycle.CLOSED) {
            throw new ConflictException(
                    "O ciclo está fechado; reabra-o antes de alterar suas movimentações");
        }
    }
}
