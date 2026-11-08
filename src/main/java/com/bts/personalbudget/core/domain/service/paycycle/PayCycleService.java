package com.bts.personalbudget.core.domain.service.paycycle;

import com.bts.personalbudget.core.domain.entity.IncomeScheduleEntity;
import com.bts.personalbudget.core.domain.entity.PayCycleEntity;
import com.bts.personalbudget.core.domain.enumerator.PayCycleAdjustmentType;
import com.bts.personalbudget.core.domain.enumerator.PayCycleLifecycle;
import com.bts.personalbudget.core.domain.enumerator.PayCycleTransitionShape;
import com.bts.personalbudget.core.domain.service.FinancialMovementService;
import com.bts.personalbudget.core.domain.service.calendar.HolidayCalendarService;
import com.bts.personalbudget.core.domain.service.income.IncomeSchedule;
import com.bts.personalbudget.core.domain.service.income.IncomeScheduleService;
import com.bts.personalbudget.core.domain.service.income.IncomeScheduleUpdate;
import com.bts.personalbudget.core.domain.service.paycycle.PayCycleAdjustmentPlanner.EndAdjustment;
import com.bts.personalbudget.core.domain.service.paycycle.PayCyclePlanner.Request;
import com.bts.personalbudget.exception.ConflictException;
import com.bts.personalbudget.exception.InvalidFieldsException;
import com.bts.personalbudget.mapper.PayCycleMapper;
import com.bts.personalbudget.repository.IncomeScheduleRepository;
import com.bts.personalbudget.repository.PayCycleAdjustmentRepository;
import com.bts.personalbudget.repository.PayCycleRepository;
import jakarta.persistence.EntityNotFoundException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

    /**
     * Longest justification the history stores (the width of the column).
     */
    static final int JUSTIFICATION_MAX_LENGTH = 500;

    private static final String INVALID_FIELDS_MESSAGE = "Campos inválidos";

    private final PayCycleConfigService configService;
    private final IncomeScheduleService incomeScheduleService;
    private final IncomeScheduleRepository scheduleRepository;
    private final HolidayCalendarService holidayCalendarService;
    private final FinancialMovementService financialMovementService;
    private final PayCycleRepository cycleRepository;
    private final PayCycleAdjustmentRepository adjustmentRepository;
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
        final Optional<PayCycle> current = mapper.toModelList(currentChain(
                cycleRepository.findByLifecycleIn(List.of(PayCycleLifecycle.ACTIVE)), config))
                .stream()
                .filter(cycle -> cycle.contains(today))
                .findFirst();
        return new CurrentPayCycle(
                current.map(cycle -> toViews(List.of(cycle), today, pending).get(0)).orElse(null),
                pending);
    }

    /**
     * What moving the end of the cycle (and with it the start of the next one) to {@code newEndDate}
     * would do, without changing anything: the two cycles before and after, the movements whose
     * competence date moves from one cycle to the other, and the income occurrences planned for the moved
     * days. The same rules as {@link #adjustEnd}, so what the preview accepts the application accepts.
     *
     * @throws EntityNotFoundException (404) when there is no cycle with the code
     * @throws InvalidFieldsException  (400) when the new date is missing, equals the current end, or
     *                                 leaves one of the two cycles empty
     * @throws ConflictException       (409) when the pay cycles are off or the cycle cannot be adjusted
     *                                 (cancelled, from an earlier activation, or with no next cycle)
     */
    @Transactional(readOnly = true)
    public PayCycleEndAdjustmentPreview previewEndAdjustment(final UUID code, final LocalDate newEndDate) {
        log.info("m=previewEndAdjustment, code={}, newEndDate={}", code, newEndDate);
        final LocalDate today = LocalDate.now(clock);
        final PayCycleConfig config = configService.find();
        requirePayCycleMode(config);
        final AdjustablePair pair = findAdjustablePair(code, config,
                cycleRepository.findByLifecycleIn(List.of(PayCycleLifecycle.ACTIVE)));
        final PayCycle previous = mapper.toModel(pair.previous());
        final PayCycle next = mapper.toModel(pair.next());
        final EndAdjustment adjustment = planEndAdjustment(previous, next, newEndDate);

        final List<PayCycle> chain = mapper.toModelList(pair.chain());
        final List<PayCyclePending> pendingBefore = findPending(config, chain);
        final List<PayCyclePending> pendingAfter = findPending(config, replacePair(chain, adjustment));
        return new PayCycleEndAdjustmentPreview(
                adjustment.oldBoundary(),
                adjustment.newBoundary(),
                toViews(List.of(previous, next), today, pendingBefore),
                toViews(List.of(adjustment.previous(), adjustment.next()), today, pendingAfter),
                adjustment.leavingCycleCode(),
                adjustment.receivingCycleCode(),
                financialMovementService.findByCompetenceInterval(adjustment.movedFrom(), adjustment.movedUntil()),
                incomeScheduleService.findOccurrencesPlannedBetween(adjustment.movedFrom(), adjustment.movedUntil()));
    }

    /**
     * Shows the anchor chain a rule change or schedule switch would leave, without writing anything.
     */
    @Transactional(readOnly = true)
    public PayCycleAnchorTransitionPreview previewAnchorTransition(final PayCycleAnchorChange change) {
        log.info("m=previewAnchorTransition, change={}", change);
        final LocalDate today = LocalDate.now(clock);
        final PayCycleConfig config = configService.find();
        requirePayCycleMode(config);
        final IncomeSchedule previous = incomeScheduleService.findByCode(config.anchorScheduleCode());
        final TransitionTarget target = resolveTransitionTarget(config, previous, change);
        final List<PayCycleEntity> active = cycleRepository.findByLifecycleIn(List.of(PayCycleLifecycle.ACTIVE));
        final List<PayCycle> chain = mapper.toModelList(currentChain(active, config));
        final LocalDate boundary = lastFrozenBoundary(chain, config, today);
        final PayCycleAnchorTransitionPlan plan = planAnchorTransition(
                boundary, chain, previous, target.schedule(), target.sameSchedule(), change.transitionShape());
        if (plan.transitionShapeRequired()) {
            throw invalid("transitionShape", "deve ser informado como SHORT ou LONG");
        }
        return new PayCycleAnchorTransitionPreview(
                config.version(),
                previous.code(),
                target.schedule().code(),
                boundary,
                plan.oldBoundary(),
                plan.newBoundary(),
                plan.shape(),
                toViews(plan.preserved(), today, plan.pending()),
                toViews(plan.cancelled(), today, plan.pending()),
                toViews(plan.created(), today, plan.pending()),
                plan.pending());
    }

    /**
     * Applies the transition and records it atomically. Started cycles are preserved, future cycles
     * are cancelled before the replacement chain is inserted, and the configuration version advances
     * even when the current schedule remains the anchor.
     */
    @Transactional
    public PayCycleAnchorTransitionResult transitionAnchor(final PayCycleAnchorChange change) {
        log.info("m=transitionAnchor, change={}", change);
        final String justification = requireJustification(change.justification());
        final LocalDate today = LocalDate.now(clock);
        final PayCycleConfig config = configService.lock();
        configService.requireVersion(config, change.expectedVersion());
        requirePayCycleMode(config);

        final IncomeSchedule previous = incomeScheduleService.findByCodeForUpdate(config.anchorScheduleCode());
        final TransitionTarget target = resolveTransitionTarget(config, previous, change);
        final IncomeSchedule lockedTarget = target.sameSchedule()
                ? previous
                : incomeScheduleService.findByCodeForUpdate(target.schedule().code());
        if (!lockedTarget.isActive() || (!target.sameSchedule() && lockedTarget.anchor())) {
            throw new ConflictException("A nova agenda precisa estar ativa e ainda não pode ser a âncora");
        }

        final List<PayCycleEntity> allActive = cycleRepository.findForUpdateByLifecycle(PayCycleLifecycle.ACTIVE);
        final List<PayCycleEntity> chainEntities = currentChain(allActive, config);
        final List<PayCycle> chain = mapper.toModelList(chainEntities);
        final LocalDate boundary = lastFrozenBoundary(chain, config, today);
        final PayCycleAnchorTransitionPlan plan = planAnchorTransition(
                boundary, chain, previous, target.schedule(), target.sameSchedule(), change.transitionShape());
        if (plan.transitionShapeRequired()) {
            throw invalid("transitionShape", "deve ser informado como SHORT ou LONG");
        }
        if (!plan.isCalculable()) {
            throw new ConflictException(describeTransitionBlocker(plan.pending()));
        }

        final IncomeSchedule resultingAnchor;
        if (target.sameSchedule()) {
            resultingAnchor = incomeScheduleService.updateForAnchorTransition(
                    previous.code(), target.scheduleUpdate());
        } else {
            resultingAnchor = incomeScheduleService.switchAnchorForTransition(previous.code(), lockedTarget.code());
        }

        final Map<UUID, PayCycleEntity> activeByCode = new HashMap<>();
        chainEntities.forEach(entity -> activeByCode.put(entity.getCode(), entity));
        for (final PayCycle cycle : plan.cancelled()) {
            final PayCycleEntity entity = activeByCode.get(cycle.code());
            if (entity != null) {
                entity.setLifecycle(PayCycleLifecycle.CANCELLED);
            }
        }
        if (!plan.cancelled().isEmpty()) {
            cycleRepository.flush();
        }

        final IncomeScheduleEntity anchorEntity = findScheduleEntity(resultingAnchor.code());
        final List<PayCycleEntity> created = plan.created().stream()
                .map(cycle -> mapper.toEntity(cycle, anchorEntity))
                .toList();
        final List<PayCycleEntity> savedCreated = cycleRepository.saveAll(created);
        cycleRepository.flush();

        final PayCycleEntity previousEntity = plan.preserved().isEmpty()
                ? null
                : activeByCode.get(plan.preserved().get(plan.preserved().size() - 1).code());
        final PayCycleEntity nextEntity = savedCreated.isEmpty() ? null : savedCreated.get(0);
        final PayCycleAdjustment adjustment = PayCycleAdjustment.anchorTransition(
                previousEntity == null ? null : previousEntity.getCode(),
                nextEntity == null ? null : nextEntity.getCode(),
                plan.oldBoundary(),
                plan.newBoundary(),
                justification,
                change.expectedVersion());
        final PayCycleAdjustment savedAdjustment = mapper.toModel(adjustmentRepository.saveAndFlush(
                mapper.toEntity(adjustment, previousEntity, nextEntity)));
        final PayCycleConfig updatedConfig = configService.switchAnchor(resultingAnchor.code());
        final List<PayCyclePending> pending = findPending(updatedConfig);

        final List<PayCycle> cancelled = plan.cancelled().stream()
                .map(cycle -> activeByCode.get(cycle.code()))
                .filter(Objects::nonNull)
                .map(mapper::toModel)
                .toList();
        return new PayCycleAnchorTransitionResult(
                new PayCycleConfigView(updatedConfig, pending),
                savedAdjustment,
                toViews(plan.preserved(), today, pending),
                toViews(cancelled, today, pending),
                toViews(mapper.toModelList(savedCreated), today, pending));
    }

    /**
     * The adjustments that touched the cycle, as the previous or as the next one of a boundary, newest
     * first. A deactivation is in the history of the last cycle that had started and of the first one
     * it cancelled.
     *
     * @throws EntityNotFoundException (404) when there is no cycle with the code
     */
    @Transactional(readOnly = true)
    public List<PayCycleAdjustment> findAdjustments(final UUID code) {
        log.info("m=findAdjustments, code={}", code);
        final PayCycleEntity cycle = cycleRepository.findByCode(code)
                .orElseThrow(() -> new EntityNotFoundException("PayCycleEntity not found for code " + code));
        return mapper.toAdjustmentModelList(adjustmentRepository.findByCycleId(cycle.getId()));
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
     * as history. The cycles can be activated again from the end of the last one. The reason goes to the
     * history in this same transaction, together with the last cycle that had started and the first one
     * that was cancelled, so the entry shows up in the history of both.
     *
     * @param justification why the cycles are turned off; required
     * @throws InvalidFieldsException (400) when the justification is missing or too long
     * @throws ConflictException      (409) when the version changed or the cycles are already off
     */
    @Transactional
    public PayCycleConfigView deactivate(final Long expectedVersion, final String justification) {
        log.info("m=deactivate, expectedVersion={}, justification={}", expectedVersion, justification);
        final String reason = requireJustification(justification);
        final LocalDate today = LocalDate.now(clock);
        final PayCycleConfig config = configService.lock();
        configService.requireVersion(config, expectedVersion);
        requirePayCycleMode(config);

        final List<PayCycleEntity> active = cycleRepository.findForUpdateByLifecycle(PayCycleLifecycle.ACTIVE);
        PayCycleEntity lastStarted = null;
        PayCycleEntity firstCancelled = null;
        for (final PayCycleEntity entity : currentChain(active, config)) {
            if (!entity.getStartDate().isAfter(today)) {
                lastStarted = entity;
            } else if (firstCancelled == null) {
                firstCancelled = entity;
            }
        }
        int cancelled = 0;
        for (final PayCycleEntity entity : active) {
            if (entity.getStartDate().isAfter(today)) {
                entity.setLifecycle(PayCycleLifecycle.CANCELLED);
                cancelled++;
            }
        }
        adjustmentRepository.save(mapper.toEntity(PayCycleAdjustment.deactivation(
                lastStarted == null ? null : lastStarted.getCode(),
                firstCancelled == null ? null : firstCancelled.getCode(),
                firstCancelled == null ? null : firstCancelled.getStartDate(),
                reason,
                expectedVersion), lastStarted, firstCancelled));
        final PayCycleConfig updated = configService.switchToCalendarMonth();
        log.info("m=deactivate, message=PayCyclesDeactivated, cancelled={}", cancelled);
        return new PayCycleConfigView(updated, List.of());
    }

    /**
     * Moves the end of the cycle, and with it the start of the next one, to {@code newEndDate}: both
     * cycles are marked as adjusted, so the automatic recalculation never moves that boundary again, and
     * the change is recorded in the history, all in this transaction. The two cycles are locked after the
     * configuration, in the order the whole feature uses; if either one is not at the version the caller
     * read, nothing is written and the answer is a conflict.
     *
     * <p>The previous cycle and the next one stay contiguous by construction, so no gap or overlap can
     * result. Whether a cycle can still be adjusted is decided in {@link #requireAdjustable}.
     *
     * @param expectedVersion     version of the cycle named by {@code code}, as the caller read it
     * @param expectedNextVersion version of the cycle that follows it, as the caller read it
     * @throws EntityNotFoundException (404) when there is no cycle with the code
     * @throws InvalidFieldsException  (400) when the justification is missing or too long, or the new
     *                                 date is missing, equals the current end, or leaves a cycle empty
     * @throws ConflictException       (409) when a version changed, the pay cycles are off, or the cycle
     *                                 cannot be adjusted (cancelled, from an earlier activation, or with
     *                                 no next cycle)
     */
    @Transactional
    public PayCycleEndAdjustmentResult adjustEnd(final UUID code,
                                                 final LocalDate newEndDate,
                                                 final String justification,
                                                 final Long expectedVersion,
                                                 final Long expectedNextVersion) {
        log.info("m=adjustEnd, code={}, newEndDate={}, justification={}, expectedVersion={}, expectedNextVersion={}",
                code, newEndDate, justification, expectedVersion, expectedNextVersion);
        final String reason = requireJustification(justification);
        final LocalDate today = LocalDate.now(clock);
        final PayCycleConfig config = configService.lock();
        requirePayCycleMode(config);
        final AdjustablePair pair = findAdjustablePair(code, config,
                cycleRepository.findForUpdateByLifecycle(PayCycleLifecycle.ACTIVE));
        requireVersion(pair.previous(), expectedVersion);
        requireVersion(pair.next(), expectedNextVersion);

        final PayCycle previous = mapper.toModel(pair.previous());
        final PayCycle next = mapper.toModel(pair.next());
        final EndAdjustment adjustment = planEndAdjustment(previous, next, newEndDate);

        pair.previous().setEndDate(adjustment.newBoundary());
        pair.previous().setEndAdjusted(true);
        pair.next().setStartDate(adjustment.newBoundary());
        pair.next().setStartAdjusted(true);
        cycleRepository.flush();
        final PayCycleAdjustment saved = mapper.toModel(adjustmentRepository.save(mapper.toEntity(
                PayCycleAdjustment.manualBoundary(previous, next, adjustment.newBoundary(), reason,
                        expectedVersion, expectedNextVersion),
                pair.previous(), pair.next())));

        final List<PayCycleView> views = toViews(
                mapper.toModelList(List.of(pair.previous(), pair.next())), today, findPending(config));
        log.info("m=adjustEnd, message=PayCycleBoundaryAdjusted, previous={}, next={}, from={}, to={}",
                previous.code(), next.code(), adjustment.oldBoundary(), adjustment.newBoundary());
        return new PayCycleEndAdjustmentResult(saved, views.get(0), views.get(1));
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
        final List<PayCycle> movedCycles = new ArrayList<>();
        for (final PayCycle cycle : desired) {
            final PayCycleEntity entity = stored.get(cycle.code());
            if (entity != null && (!entity.getStartDate().equals(cycle.startDate())
                    || !entity.getEndDate().equals(cycle.endDate()))) {
                log.info("m=save, message=PayCycleBoundaryMoved, code={}, from=[{},{}), to=[{},{})", cycle.code(),
                        entity.getStartDate(), entity.getEndDate(), cycle.startDate(), cycle.endDate());
                entity.setLifecycle(PayCycleLifecycle.CANCELLED);
                movedCycles.add(cycle);
                moved++;
            }
        }
        if (!movedCycles.isEmpty()) {
            cycleRepository.flush();
            movedCycles.forEach(cycle -> {
                final PayCycleEntity entity = stored.get(cycle.code());
                entity.setStartDate(cycle.startDate());
                entity.setEndDate(cycle.endDate());
                entity.setLifecycle(cycle.lifecycle());
            });
            cycleRepository.flush();
        }
        for (final PayCycle cycle : desired) {
            if (stored.get(cycle.code()) == null) {
                cycleRepository.save(mapper.toEntity(cycle, cycle.anchorScheduleCode() == null ? null : anchorEntity));
                created++;
            }
        }
        cycleRepository.flush();
        log.info("m=save, created={}, moved={}", created, moved);
    }

    private PayCyclePlan plan(final IncomeSchedule anchor,
                              final LocalDate activationDate,
                              final List<PayCycle> existing,
                              final LocalDate today) {
        return plan(anchor, activationDate, existing, today, holidayCalendarService.findCalendar());
    }

    private PayCyclePlan plan(final IncomeSchedule anchor,
                              final LocalDate activationDate,
                              final List<PayCycle> existing,
                              final LocalDate today,
                              final com.bts.personalbudget.core.domain.service.calendar.HolidayCalendar calendar) {
        return PayCyclePlanner.plan(new Request(anchor, incomeScheduleService.findMaterializedOccurrences(anchor.code()),
                calendar, activationDate, existing, today));
    }

    private PayCycleAnchorTransitionPlan planAnchorTransition(
            final LocalDate boundary,
            final List<PayCycle> chain,
            final IncomeSchedule previous,
            final IncomeSchedule next,
            final boolean sameSchedule,
            final PayCycleTransitionShape shape) {
        final List<com.bts.personalbudget.core.domain.service.income.IncomeOccurrence> materialized =
                incomeScheduleService.findMaterializedOccurrences(next.code());
        return PayCycleAnchorTransitionPlanner.plan(boundary, chain, previous, next, materialized,
                holidayCalendarService.findCalendar(), LocalDate.now(clock), sameSchedule, shape);
    }

    private TransitionTarget resolveTransitionTarget(
            final PayCycleConfig config,
            final IncomeSchedule previous,
            final PayCycleAnchorChange change) {
        final UUID targetCode = change.anchorScheduleCode() == null
                ? previous.code()
                : change.anchorScheduleCode();
        final boolean sameSchedule = previous.code().equals(targetCode);
        final IncomeScheduleUpdate update = change.scheduleUpdate();
        if (sameSchedule) {
            if (update == null || update.isEmpty()) {
                throw invalid("anchorScheduleCode", "informe uma nova regra ou outra agenda âncora");
            }
            return new TransitionTarget(
                    incomeScheduleService.previewAnchorRuleTransition(targetCode, update), update, true);
        }
        if (update != null && !update.isEmpty()) {
            throw invalid("anchorScheduleCode", "não envie uma nova regra ao trocar de agenda âncora");
        }
        final IncomeSchedule next = incomeScheduleService.findByCode(targetCode);
        if (!next.isActive()) {
            throw invalid("anchorScheduleCode", "a nova agenda âncora deve estar ativa");
        }
        return new TransitionTarget(next, null, false);
    }

    private LocalDate lastFrozenBoundary(final List<PayCycle> chain,
                                         final PayCycleConfig config,
                                         final LocalDate today) {
        LocalDate boundary = config.activationDate();
        for (int i = 0; i < chain.size(); i++) {
            final PayCycle cycle = chain.get(i);
            final boolean nextStartAdjusted = i + 1 < chain.size() && chain.get(i + 1).startAdjusted();
            if (cycle.endIsFrozen(today) || nextStartAdjusted) {
                boundary = cycle.endDate();
            }
        }
        return boundary;
    }

    private String describeTransitionBlocker(final List<PayCyclePending> pending) {
        if (pending.isEmpty()) {
            return "A transição não pode ser calculada sem criar lacuna ou sobreposição";
        }
        return switch (pending.get(0).type()) {
            case CALENDAR_NOT_REVIEWED -> "O calendário de feriados de " + pending.get(0).year()
                    + " precisa ser revisado antes de transicionar a âncora";
            case BUSINESS_DAY_NOT_FOUND -> "A regra da nova âncora não encontra dia útil suficiente em "
                    + pending.get(0).nominalMonth();
            case ANCHOR_SCHEDULE_ENDS -> "A nova agenda não tem ocorrências suficientes depois da fronteira congelada";
            case BOUNDARY_DIVERGES_FROM_ANCHOR -> "A cadeia atual não contém a fronteira congelada esperada";
            case NO_CURRENT_CYCLE -> "A transição não pode ser calculada sem um ciclo atual";
        };
    }

    private record TransitionTarget(IncomeSchedule schedule, IncomeScheduleUpdate scheduleUpdate, boolean sameSchedule) {
    }

    /**
     * What is pending right now, calculated from the chain as it would be after a refresh, without
     * saving it. Nothing is pending while the cycles are off.
     */
    private List<PayCyclePending> findPending(final PayCycleConfig config) {
        if (!config.isPayCycle()) {
            return List.of();
        }
        return findPending(config, mapper.toModelList(currentChain(
                cycleRepository.findByLifecycleIn(List.of(PayCycleLifecycle.ACTIVE)), config)));
    }

    /**
     * The same, for a chain handed over: the one that is stored, or the one a change under preview would
     * leave. The configuration is in PAY_CYCLE.
     */
    private List<PayCyclePending> findPending(final PayCycleConfig config, final List<PayCycle> existing) {
        final IncomeSchedule anchor = incomeScheduleService.findByCode(config.anchorScheduleCode());
        final LocalDate today = LocalDate.now(clock);
        final List<PayCyclePending> pending = plan(anchor, config.activationDate(), existing, today).pending();
        if (!today.isBefore(config.activationDate())
                && existing.stream().noneMatch(cycle -> cycle.contains(today))
                && !pending.contains(PayCyclePending.noCurrentCycle())) {
            final List<PayCyclePending> withNoCurrent = new ArrayList<>(pending);
            withNoCurrent.add(PayCyclePending.noCurrentCycle());
            return List.copyOf(withNoCurrent);
        }
        return pending;
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

    /**
     * A cycle and the one that follows it, as stored, and the chain of the current activation they come
     * from (to see what the pendencies would be after a change).
     */
    private record AdjustablePair(List<PayCycleEntity> chain, PayCycleEntity previous, PayCycleEntity next) {
    }

    /**
     * The cycle named by {@code code} and the one that follows it, when the end of the first one can be
     * adjusted. The pay cycles have to be on, and the cycle has to be in the chain of the current
     * activation: the first cycle of an activation starts on the activation date, so a boundary that
     * belongs to an earlier activation is history and is not moved.
     *
     * @param active the ACTIVE cycles, ordered by start date; read after {@link #requirePayCycleMode},
     *               so nothing is queried (nor locked) while the cycles are off
     * @throws EntityNotFoundException (404) when there is no cycle with the code
     * @throws ConflictException       (409) when the cycle is cancelled or from an earlier activation, or
     *                                 it is the last one of the chain
     */
    private AdjustablePair findAdjustablePair(final UUID code, final PayCycleConfig config, final List<PayCycleEntity> active) {
        final List<PayCycleEntity> chain = currentChain(active, config);
        int index = -1;
        for (int i = 0; i < chain.size(); i++) {
            if (chain.get(i).getCode().equals(code)) {
                index = i;
            }
        }
        if (index < 0) {
            final PayCycleEntity entity = cycleRepository.findByCode(code)
                    .orElseThrow(() -> new EntityNotFoundException("PayCycleEntity not found for code " + code));
            throw new ConflictException(entity.getLifecycle() == PayCycleLifecycle.CANCELLED
                    ? "O ciclo está cancelado e não pode ser ajustado"
                    : "O ciclo pertence a uma ativação anterior e não pode ser ajustado");
        }
        if (index + 1 >= chain.size()) {
            throw new ConflictException("O ciclo não tem ciclo seguinte: o fim do último ciclo acompanha "
                    + "a agenda âncora e não pode ser ajustado");
        }
        final AdjustablePair pair = new AdjustablePair(chain, chain.get(index), chain.get(index + 1));
        requireAdjustable(pair.previous(), pair.next());
        return pair;
    }

    /**
     * Whether the boundary between the two cycles can still be moved. Today any two ACTIVE cycles can;
     * this is where a cycle that was closed will be refused (409) once cycles can be closed.
     */
    private void requireAdjustable(final PayCycleEntity previous, final PayCycleEntity next) {
        if (previous.getLifecycle() != PayCycleLifecycle.ACTIVE || next.getLifecycle() != PayCycleLifecycle.ACTIVE) {
            throw new ConflictException("Só ciclos ativos podem ser ajustados");
        }
    }

    private EndAdjustment planEndAdjustment(final PayCycle previous, final PayCycle next, final LocalDate newEndDate) {
        PayCycleAdjustmentPlanner.validate(previous, next, newEndDate).ifPresent(message -> {
            throw invalid("newEndDate", message);
        });
        return PayCycleAdjustmentPlanner.adjust(previous, next, newEndDate);
    }

    /**
     * The chain with the two cycles of the adjustment in place of the ones they replace.
     */
    private static List<PayCycle> replacePair(final List<PayCycle> chain, final EndAdjustment adjustment) {
        return chain.stream()
                .map(cycle -> {
                    if (cycle.code().equals(adjustment.previous().code())) {
                        return adjustment.previous();
                    }
                    return cycle.code().equals(adjustment.next().code()) ? adjustment.next() : cycle;
                })
                .toList();
    }

    private void requireVersion(final PayCycleEntity entity, final Long expectedVersion) {
        if (!Objects.equals(entity.getVersion(), expectedVersion)) {
            log.info("m=requireVersion, message=VersionConflict, code={}, current={}, expected={}",
                    entity.getCode(), entity.getVersion(), expectedVersion);
            throw new ConflictException("O ciclo foi alterado por outra operação; recarregue e tente novamente");
        }
    }

    /**
     * @return the justification without the spaces around it
     * @throws InvalidFieldsException (400) when it is missing, blank or longer than the history stores
     */
    private String requireJustification(final String justification) {
        if (justification == null || justification.isBlank()) {
            throw invalid("justification", "deve ser informada");
        }
        final String reason = justification.strip();
        if (reason.length() > JUSTIFICATION_MAX_LENGTH) {
            throw invalid("justification", "não pode passar de " + JUSTIFICATION_MAX_LENGTH + " caracteres");
        }
        return reason;
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

    private void requirePayCycleMode(final PayCycleConfig config) {
        if (!config.isPayCycle()) {
            throw new ConflictException("Os ciclos financeiros não estão ativados");
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
