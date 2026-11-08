package com.bts.personalbudget.core.domain.service.paycycle;

import com.bts.personalbudget.core.domain.entity.IncomeScheduleEntity;
import com.bts.personalbudget.core.domain.entity.PayCycleConfigEntity;
import com.bts.personalbudget.core.domain.enumerator.PayCycleMode;
import com.bts.personalbudget.exception.ConflictException;
import com.bts.personalbudget.mapper.PayCycleMapper;
import com.bts.personalbudget.repository.IncomeScheduleRepository;
import com.bts.personalbudget.repository.PayCycleConfigRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The single configuration row and nothing else: it depends on its own repository (and on the income
 * schedule repository, only to point at the anchor), never on a service that depends on the pay cycles,
 * so the income schedules can ask it whether the cycles are on without a circular dependency.
 *
 * <p>The row is also the lock every write to the cycles takes first.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class PayCycleConfigService {

    private final PayCycleConfigRepository configRepository;
    private final IncomeScheduleRepository scheduleRepository;
    private final PayCycleMapper mapper;
    private final EntityManager entityManager;

    public PayCycleConfig find() {
        log.info("m=find");
        return mapper.toModel(configRepository.findSingle().orElseThrow(PayCycleConfigService::missing));
    }

    public boolean isPayCycleActive() {
        log.info("m=isPayCycleActive");
        return find().isPayCycle();
    }

    /**
     * Reads the configuration locking its row (and the anchor schedule's, when there is one) until the
     * end of the transaction. Every write to the cycles starts here.
     */
    @Transactional
    public PayCycleConfig lock() {
        log.info("m=lock");
        return mapper.toModel(lockEntity());
    }

    /**
     * @throws ConflictException (409) when the configuration changed since the caller read it
     */
    public void requireVersion(final PayCycleConfig config, final Long expectedVersion) {
        if (expectedVersion == null || config.version() != expectedVersion) {
            log.info("m=requireVersion, message=VersionConflict, current={}, expected={}", config.version(), expectedVersion);
            throw new ConflictException(
                    "A configuração dos ciclos foi alterada por outra operação; recarregue e tente novamente");
        }
    }

    @Transactional
    public PayCycleConfig switchToPayCycle(final UUID anchorScheduleCode, final LocalDate activationDate) {
        log.info("m=switchToPayCycle, anchorScheduleCode={}, activationDate={}", anchorScheduleCode, activationDate);
        final PayCycleConfigEntity entity = lockEntity();
        final IncomeScheduleEntity anchor = scheduleRepository.findByCode(anchorScheduleCode)
                .orElseThrow(() -> new EntityNotFoundException("IncomeScheduleEntity not found for code " + anchorScheduleCode));
        entity.setMode(PayCycleMode.PAY_CYCLE);
        entity.setAnchorSchedule(anchor);
        entity.setActivationDate(activationDate);
        return mapper.toModel(configRepository.saveAndFlush(entity));
    }

    @Transactional
    public PayCycleConfig switchToCalendarMonth() {
        log.info("m=switchToCalendarMonth");
        final PayCycleConfigEntity entity = lockEntity();
        entity.setMode(PayCycleMode.CALENDAR_MONTH);
        entity.setAnchorSchedule(null);
        entity.setActivationDate(null);
        return mapper.toModel(configRepository.saveAndFlush(entity));
    }

    /**
     * Updates the anchor while PAY_CYCLE stays active. The forced optimistic increment makes the
     * preview version stale after every applied transition, including a rule change on the same
     * schedule where the configuration columns themselves do not change.
     */
    @Transactional
    public PayCycleConfig switchAnchor(final UUID anchorScheduleCode) {
        log.info("m=switchAnchor, anchorScheduleCode={}", anchorScheduleCode);
        final PayCycleConfigEntity entity = lockEntity();
        if (entity.getMode() != PayCycleMode.PAY_CYCLE) {
            throw new ConflictException("Os ciclos financeiros não estão ativados");
        }
        final long currentVersion = entity.getVersion();
        final IncomeScheduleEntity anchor = scheduleRepository.findByCode(anchorScheduleCode)
                .orElseThrow(() -> new EntityNotFoundException(
                        "IncomeScheduleEntity not found for code " + anchorScheduleCode));
        entity.setAnchorSchedule(anchor);
        entityManager.flush();
        // A same-anchor rule change leaves this row clean, so persist its version explicitly.
        if (entity.getVersion() == currentVersion
                && configRepository.incrementVersion(entity.getId(), currentVersion) != 1) {
            throw new ConflictException(
                    "A configuração dos ciclos foi alterada por outra operação; recarregue e tente novamente");
        }
        entityManager.refresh(entity);
        return mapper.toModel(entity);
    }

    private PayCycleConfigEntity lockEntity() {
        return configRepository.findSingleForUpdate().orElseThrow(PayCycleConfigService::missing);
    }

    private static IllegalStateException missing() {
        return new IllegalStateException("The pay cycle configuration row is missing: the migration creates it");
    }

}
