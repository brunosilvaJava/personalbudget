package com.bts.personalbudget.core.domain.service.paycycle;

import com.bts.personalbudget.core.domain.entity.IncomeScheduleEntity;
import com.bts.personalbudget.core.domain.entity.PayCycleEntity;
import com.bts.personalbudget.core.domain.enumerator.PayCycleLifecycle;
import com.bts.personalbudget.core.domain.service.calendar.HolidayCalendarService;
import com.bts.personalbudget.core.domain.service.income.IncomeSchedule;
import com.bts.personalbudget.core.domain.service.income.IncomeScheduleService;
import com.bts.personalbudget.core.domain.service.paycycle.PayCyclePlanner.Request;
import com.bts.personalbudget.exception.ConflictException;
import com.bts.personalbudget.exception.InvalidFieldsException;
import com.bts.personalbudget.mapper.PayCycleMapper;
import com.bts.personalbudget.repository.IncomeScheduleRepository;
import com.bts.personalbudget.repository.PayCycleRepository;
import jakarta.persistence.EntityNotFoundException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The pay cycles: activation, upkeep and reading. The rules live in {@link PayCyclePlanner}, which is
 * pure; this service loads what it needs, asks it what the chain should be and saves the difference.
 *
 * <p>Every write takes the locks in one order, configuration, then the anchor schedule, then the
 * cycles, so no two paths can wait on each other. The configuration lock also serializes the writers:
 * the nightly refresh and an activation never build the chain at the same time.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class PayCycleService {

    /**
     * How far from today the first cycle of an activation may start: close enough not to invent
     * history nor activate something far away.
     */
    static final int ACTIVATION_WINDOW_DAYS = 31;

    private static final String INVALID_FIELDS_MESSAGE = "Campos inválidos";

    private final PayCycleConfigService configService;
    private final IncomeScheduleService incomeScheduleService;
    private final IncomeScheduleRepository scheduleRepository;
    private final HolidayCalendarService holidayCalendarService;
    private final PayCycleRepository cycleRepository;
    private final PayCycleMapper mapper;
    private final Clock clock;

    // --- reading ---

    @Transactional(readOnly = true)
    public PayCycleConfigView findConfig() {
        log.info("m=findConfig");
        final PayCycleConfig config = configService.find();
        return new PayCycleConfigView(config, findPending(config));
    }

    /**
     * What an activation on {@code initialDate} would create, without creating anything. When the
     * first cycle cannot be calculated the answer has no cycles and says why in its pendencies.
     *
     * @throws InvalidFieldsException (400) when there is no active anchor schedule or the date is not
     *                                acceptable
     * @throws ConflictException      (409) when the cycles are already on
     */
    @Transactional(readOnly = true)
    public PayCycleActivationPreview previewActivation(final LocalDate initialDate) {
        log.info("m=previewActivation, initialDate={}", initialDate);
        final LocalDate today = LocalDate.now(clock);
        final PayCycleConfig config = configService.find();
        requireCalendarMonthMode(config);
        final IncomeSchedule anchor = incomeScheduleService.findActiveAnchor()
                .orElseThrow(() -> invalid("anchor", "não há agenda âncora ativa"));
        validateInitialDate(initialDate, today, lastEndDate(cycleRepository.findByLifecycleIn(List.of(PayCycleLifecycle.ACTIVE))));

        final PayCyclePlan plan = plan(anchor, initialDate, List.of(), today);
        return new PayCycleActivationPreview(config.version(), toViews(plan.cycles(), today, plan.pending()), plan.pending());
    }

    /**
     * Cycles ordered by start date. With {@code date}, only the one that contains it; with
     * {@code from} and/or {@code to}, the ones that share at least one day with that range, both ends
     * inclusive. Cancelled cycles only when asked.
     *
     * @throws InvalidFieldsException (400) when date is combined with from/to or the range is inverted
     */
    @Transactional(readOnly = true)
    public List<PayCycleView> findCycles(final LocalDate date,
                                         final LocalDate from,
                                         final LocalDate to,
                                         final boolean includeCancelled) {
        log.info("m=findCycles, date={}, from={}, to={}, includeCancelled={}", date, from, to, includeCancelled);
        if (date != null && (from != null || to != null)) {
            throw invalid("date", "não pode ser combinado com from ou to");
        }
        if (from != null && to != null && to.isBefore(from)) {
            throw invalid("to", "não pode ser anterior a from");
        }

        final LocalDate today = LocalDate.now(clock);
        final PayCycleConfig config = configService.find();
        final List<PayCycle> cycles = mapper.toModelList(cycleRepository.findByLifecycleIn(
                includeCancelled
                        ? List.of(PayCycleLifecycle.ACTIVE, PayCycleLifecycle.CANCELLED)
                        : List.of(PayCycleLifecycle.ACTIVE)));
        final List<PayCycle> selected = cycles.stream()
                .filter(cycle -> date == null || cycle.contains(date))
                .filter(cycle -> from == null && to == null
                        || cycle.overlaps(from != null ? from : LocalDate.MIN, to != null ? to : LocalDate.MAX))
                .toList();
        return toViews(selected, today, findPending(config));
    }

    /**
     * @throws EntityNotFoundException (404) when there is no cycle with the code
     */
    @Transactional(readOnly = true)
    public PayCycleView findByCode(final UUID code) {
        log.info("m=findByCode, code={}", code);
        final PayCycle cycle = mapper.toModel(cycleRepository.findByCode(code)
                .orElseThrow(() -> new EntityNotFoundException("PayCycleEntity not found for code " + code)));
        return toViews(List.of(cycle), LocalDate.now(clock), findPending(configService.find())).get(0);
    }

    /**
     * The active cycle that contains today. When there is none (the mode is CALENDAR_MONTH, the first
     * cycle has not started, or the chain could not reach today) the cycle is null, with the pendencies
     * that explain it.
     */
    @Transactional(readOnly = true)
    public CurrentPayCycle findCurrent() {
        log.info("m=findCurrent");
        final LocalDate today = LocalDate.now(clock);
        final PayCycleConfig config = configService.find();
        if (!config.isPayCycle()) {
            return new CurrentPayCycle(null, List.of());
        }
        final List<PayCyclePending> pending = findPending(config);
        final Optional<PayCycle> current = mapper.toModelList(cycleRepository.findByLifecycleIn(List.of(PayCycleLifecycle.ACTIVE)))
                .stream()
                .filter(cycle -> cycle.contains(today))
                .findFirst();
        return new CurrentPayCycle(
                current.map(cycle -> toViews(List.of(cycle), today, pending).get(0)).orElse(null),
                pending);
    }

    // --- writing ---

    /**
     * Turns the cycles on: creates the first cycle on {@code initialDate} and the ones that keep the
     * chain up to the current cycle plus two. Nothing before {@code initialDate} is reconstructed.
     *
     * @param expectedVersion the version of the configuration the caller read (the preview's)
     * @throws ConflictException      (409) when the version changed or the cycles are already on
     * @throws InvalidFieldsException (400) when there is no active anchor schedule, the date is not
     *                                acceptable, or the first cycle cannot be calculated
     */
    @Transactional
    public PayCycleConfigView activate(final LocalDate initialDate, final Long expectedVersion) {
        log.info("m=activate, initialDate={}, expectedVersion={}", initialDate, expectedVersion);
        final LocalDate today = LocalDate.now(clock);
        final PayCycleConfig config = configService.lock();
        configService.requireVersion(config, expectedVersion);
        requireCalendarMonthMode(config);

        final IncomeSchedule anchor = incomeScheduleService.findActiveAnchorForUpdate()
                .orElseThrow(() -> invalid("anchor", "não há agenda âncora ativa"));
        final List<PayCycleEntity> active = cycleRepository.findForUpdateByLifecycle(PayCycleLifecycle.ACTIVE);
        validateInitialDate(initialDate, today, lastEndDate(active));

        final PayCyclePlan plan = plan(anchor, initialDate, List.of(), today);
        if (plan.cycles().isEmpty()) {
            throw invalid("initialDate", describeBlocker(plan.pending()));
        }
        save(plan.cycles(), Map.of(), findScheduleEntity(anchor.code()));
        final PayCycleConfig updated = configService.switchToPayCycle(anchor.code(), initialDate);
        log.info("m=activate, message=PayCyclesActivated, activationDate={}, cycles={}, pending={}",
                initialDate, plan.cycles().size(), plan.pending().size());
        return new PayCycleConfigView(updated, plan.pending());
    }

    /**
     * Turns the cycles off: the cycles that have not started are cancelled; the ones that started stay
     * as history. The cycles can be activated again from the end of the last one.
     *
     * @throws ConflictException (409) when the version changed or the cycles are already off
     */
    @Transactional
    public PayCycleConfigView deactivate(final Long expectedVersion) {
        log.info("m=deactivate, expectedVersion={}", expectedVersion);
        final LocalDate today = LocalDate.now(clock);
        final PayCycleConfig config = configService.lock();
        configService.requireVersion(config, expectedVersion);
        if (!config.isPayCycle()) {
            throw new ConflictException("Os ciclos financeiros não estão ativados");
        }

        int cancelled = 0;
        for (final PayCycleEntity entity : cycleRepository.findForUpdateByLifecycle(PayCycleLifecycle.ACTIVE)) {
            if (entity.getStartDate().isAfter(today)) {
                entity.setLifecycle(PayCycleLifecycle.CANCELLED);
                cancelled++;
            }
        }
        final PayCycleConfig updated = configService.switchToCalendarMonth();
        log.info("m=deactivate, message=PayCyclesDeactivated, cancelled={}", cancelled);
        return new PayCycleConfigView(updated, List.of());
    }

    /**
     * Keeps the chain as the anchor dates say it should be: moves the boundaries that are free to move
     * and adds the cycles the horizon needs. Idempotent: with nothing changed it writes nothing. Does
     * nothing in CALENDAR_MONTH. Called by the nightly job after the day's receipts are posted.
     */
    @Transactional
    public void refresh() {
        log.info("m=refresh");
        final LocalDate today = LocalDate.now(clock);
        final PayCycleConfig config = configService.lock();
        if (!config.isPayCycle()) {
            log.info("m=refresh, message=PayCyclesAreOff");
            return;
        }

        final IncomeSchedule anchor = incomeScheduleService.findByCodeForUpdate(config.anchorScheduleCode());
        final List<PayCycleEntity> entities = currentChain(
                cycleRepository.findForUpdateByLifecycle(PayCycleLifecycle.ACTIVE), config);
        final PayCyclePlan plan = plan(anchor, config.activationDate(), mapper.toModelList(entities), today);

        final Map<UUID, PayCycleEntity> byCode = new HashMap<>();
        entities.forEach(entity -> byCode.put(entity.getCode(), entity));
        save(plan.cycles(), byCode, findScheduleEntity(anchor.code()));
        plan.pending().forEach(pending -> log.warn("m=refresh, message=PayCyclePending, pending={}", pending));
    }

    // --- helpers ---

    /**
     * Writes the difference between the chain the planner wants and what is stored: new cycles are
     * inserted, cycles whose dates moved are updated, and cycles that did not change are not touched,
     * so a refresh with nothing to do writes nothing and does not bump any version.
     */
    private void save(final List<PayCycle> desired,
                      final Map<UUID, PayCycleEntity> stored,
                      final IncomeScheduleEntity anchorEntity) {
        int created = 0;
        int moved = 0;
        for (final PayCycle cycle : desired) {
            final PayCycleEntity entity = stored.get(cycle.code());
            if (entity == null) {
                cycleRepository.save(mapper.toEntity(cycle, cycle.anchorScheduleCode() == null ? null : anchorEntity));
                created++;
            } else if (!entity.getStartDate().equals(cycle.startDate()) || !entity.getEndDate().equals(cycle.endDate())) {
                log.info("m=save, message=PayCycleBoundaryMoved, code={}, from=[{},{}), to=[{},{})", cycle.code(),
                        entity.getStartDate(), entity.getEndDate(), cycle.startDate(), cycle.endDate());
                entity.setStartDate(cycle.startDate());
                entity.setEndDate(cycle.endDate());
                moved++;
            }
        }
        cycleRepository.flush();
        log.info("m=save, created={}, moved={}", created, moved);
    }

    private PayCyclePlan plan(final IncomeSchedule anchor,
                              final LocalDate activationDate,
                              final List<PayCycle> existing,
                              final LocalDate today) {
        return PayCyclePlanner.plan(new Request(anchor, incomeScheduleService.findMaterializedOccurrences(anchor.code()),
                holidayCalendarService.findCalendar(), activationDate, existing, today));
    }

    /**
     * What is pending right now, calculated from the chain as it would be after a refresh, without
     * saving it. Nothing is pending while the cycles are off.
     */
    private List<PayCyclePending> findPending(final PayCycleConfig config) {
        if (!config.isPayCycle()) {
            return List.of();
        }
        final IncomeSchedule anchor = incomeScheduleService.findByCode(config.anchorScheduleCode());
        final List<PayCycle> existing = mapper.toModelList(currentChain(
                cycleRepository.findByLifecycleIn(List.of(PayCycleLifecycle.ACTIVE)), config));
        return plan(anchor, config.activationDate(), existing, LocalDate.now(clock)).pending();
    }

    /**
     * The active cycles of the current activation, in order. The ones of an earlier activation are
     * history: they all end before this one starts.
     */
    private static List<PayCycleEntity> currentChain(final List<PayCycleEntity> active, final PayCycleConfig config) {
        return active.stream()
                .filter(entity -> !entity.getStartDate().isBefore(config.activationDate()))
                .toList();
    }

    private List<PayCycleView> toViews(final List<PayCycle> cycles,
                                       final LocalDate today,
                                       final List<PayCyclePending> pending) {
        return cycles.stream()
                .map(cycle -> new PayCycleView(cycle, cycle.status(today), pending.stream()
                        .filter(item -> cycle.code().equals(item.cycleCode()))
                        .toList()))
                .toList();
    }

    private IncomeScheduleEntity findScheduleEntity(final UUID code) {
        return scheduleRepository.findByCode(code)
                .orElseThrow(() -> new EntityNotFoundException("IncomeScheduleEntity not found for code " + code));
    }

    private static Optional<LocalDate> lastEndDate(final List<PayCycleEntity> active) {
        return active.stream().map(PayCycleEntity::getEndDate).max(LocalDate::compareTo);
    }

    private void requireCalendarMonthMode(final PayCycleConfig config) {
        if (config.isPayCycle()) {
            throw new ConflictException("Os ciclos financeiros já estão ativados");
        }
    }

    /**
     * @param lastEnd the end of the last cycle that exists, if any: a new activation starts at or after it, so
     *                the new history never overlaps the old one
     */
    private void validateInitialDate(final LocalDate initialDate, final LocalDate today, final Optional<LocalDate> lastEnd) {
        if (initialDate == null) {
            throw invalid("initialDate", "deve ser informada");
        }
        final LocalDate earliest = today.minusDays(ACTIVATION_WINDOW_DAYS);
        final LocalDate latest = today.plusDays(ACTIVATION_WINDOW_DAYS);
        if (initialDate.isBefore(earliest) || initialDate.isAfter(latest)) {
            throw invalid("initialDate", "deve estar entre " + earliest + " e " + latest);
        }
        if (lastEnd.isPresent() && initialDate.isBefore(lastEnd.get())) {
            throw invalid("initialDate", "não pode ser anterior ao fim do último ciclo (" + lastEnd.get() + ")");
        }
    }

    /**
     * Why no first cycle could be created, in words for the user, from the first pendency found.
     */
    private static String describeBlocker(final List<PayCyclePending> pending) {
        if (pending.isEmpty()) {
            return "não foi possível calcular o primeiro ciclo";
        }
        final PayCyclePending first = pending.get(0);
        return switch (first.type()) {
            case CALENDAR_NOT_REVIEWED -> "o calendário de feriados de " + first.year()
                    + " precisa ser revisado para calcular o fim do primeiro ciclo";
            case BUSINESS_DAY_NOT_FOUND -> "o mês " + first.nominalMonth()
                    + " não tem dias úteis suficientes para a regra da agenda âncora";
            case ANCHOR_SCHEDULE_ENDS -> "a agenda âncora não cobre o mês " + first.nominalMonth()
                    + ", necessário para o fim do primeiro ciclo";
            case BOUNDARY_DIVERGES_FROM_ANCHOR, NO_CURRENT_CYCLE -> "não foi possível calcular o primeiro ciclo";
        };
    }

    private InvalidFieldsException invalid(final String field, final String message) {
        return new InvalidFieldsException(INVALID_FIELDS_MESSAGE, PayCycleService.class, Map.of(field, message));
    }

}
