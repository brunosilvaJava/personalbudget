package com.bts.personalbudget.core.domain.service.income;

import com.bts.personalbudget.core.domain.entity.IncomeOccurrenceEntity;
import com.bts.personalbudget.core.domain.entity.IncomeScheduleEntity;
import com.bts.personalbudget.core.domain.enumerator.FinancialMovementStatus;
import com.bts.personalbudget.core.domain.enumerator.IncomeScheduleStatus;
import com.bts.personalbudget.core.domain.enumerator.OperationType;
import com.bts.personalbudget.core.domain.model.FinancialMovement;
import com.bts.personalbudget.core.domain.service.FinancialMovementService;
import com.bts.personalbudget.core.domain.service.calendar.Holiday;
import com.bts.personalbudget.core.domain.service.calendar.HolidayCalendar;
import com.bts.personalbudget.core.domain.service.calendar.HolidayCalendarService;
import com.bts.personalbudget.core.domain.service.calendar.IncomeDateRule;
import com.bts.personalbudget.core.domain.enumerator.DateRuleType;
import com.bts.personalbudget.core.domain.enumerator.NonBusinessDayPolicy;
import com.bts.personalbudget.core.domain.service.category.CategoryService;
import com.bts.personalbudget.core.domain.service.fixedbill.FixedBill;
import com.bts.personalbudget.core.domain.service.fixedbill.FixedBillService;
import com.bts.personalbudget.core.domain.service.fixedbill.FixedBillUpdate;
import com.bts.personalbudget.core.domain.service.paycycle.PayCycleConfig;
import com.bts.personalbudget.core.domain.service.paycycle.PayCycleConfigService;
import com.bts.personalbudget.exception.ConflictException;
import com.bts.personalbudget.exception.InvalidFieldsException;
import com.bts.personalbudget.mapper.IncomeScheduleMapper;
import com.bts.personalbudget.repository.IncomeOccurrenceRepository;
import com.bts.personalbudget.repository.IncomeScheduleRepository;
import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Income schedules and their occurrences. The posting job, an early receipt and the balance
 * projection all go through {@link IncomeOccurrenceResolver}, and every materialization goes through
 * {@link #createOccurrence}: one occurrence per schedule and nominal month, whoever asks first.
 *
 * <p>An occurrence only becomes a row (and a FinancialMovement) when it is materialized. Until then
 * it is computed from the rule and the holiday calendar, so a calendar review moves it without
 * leaving stale rows behind; once materialized, its date and amount are frozen.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class IncomeScheduleService {

    static final int DESCRIPTION_MIN_LENGTH = 3;
    static final int DESCRIPTION_MAX_LENGTH = 50;

    /**
     * How far ahead of the current month an occurrence can be materialized on request (an early
     * receipt). The job itself never materializes anything dated after today.
     */
    static final int MAX_MONTHS_AHEAD = 1;

    /**
     * Longest span the occurrence listing answers for, so one request cannot ask for decades.
     */
    static final int MAX_LISTING_MONTHS = 36;

    private static final String INVALID_FIELDS_MESSAGE = "Campos inválidos";
    private static final LocalDate LAST_SUPPORTED_DATE = LocalDate.of(9999, 12, 31);

    private final IncomeScheduleRepository scheduleRepository;
    private final IncomeOccurrenceRepository occurrenceRepository;
    private final IncomeScheduleMapper mapper;
    private final HolidayCalendarService holidayCalendarService;
    private final FinancialMovementService financialMovementService;
    private final FixedBillService fixedBillService;
    private final CategoryService categoryService;
    private final PayCycleConfigService payCycleConfigService;
    private final Clock clock;

    /**
     * Creates an ACTIVE schedule. When it replaces a fixed bill, that bill is ended the day before
     * the schedule's first month in this same transaction, so the two never generate the same receipt.
     */
    @Transactional
    public IncomeSchedule create(final IncomeSchedule schedule) {
        log.info("m=create, schedule={}", schedule);
        final IncomeSchedule validated = validateNew(schedule);
        if (validated.replacesFixedBillCode() != null) {
            replaceFixedBill(validated);
        }
        return mapper.toModel(scheduleRepository.save(mapper.toEntity(validated)));
    }

    /**
     * @param statuses when null or empty only ACTIVE schedules are returned, as in the categories
     */
    public List<IncomeSchedule> find(final List<IncomeScheduleStatus> statuses) {
        log.info("m=find, statuses={}", statuses);
        final List<IncomeScheduleStatus> effectiveStatuses =
                statuses == null || statuses.isEmpty() ? List.of(IncomeScheduleStatus.ACTIVE) : statuses;
        return mapper.toModelList(scheduleRepository.findAllByStatusInOrderByDescriptionAsc(effectiveStatuses));
    }

    /**
     * Also returns INACTIVE schedules: their materialized occurrences are still history.
     */
    public IncomeSchedule findByCode(final UUID code) {
        log.info("m=findByCode, code={}", code);
        return mapper.toModel(findEntity(code));
    }

    @Transactional
    public IncomeSchedule update(final UUID code, final IncomeScheduleUpdate changes) {
        log.info("m=update, code={}, changes={}", code, changes);
        final IncomeScheduleEntity entity = lockEntity(code);
        final IncomeSchedule current = mapper.toModel(entity);
        if (changes.isEmpty()) {
            return current;
        }

        final IncomeSchedule updated = applyChanges(current, changes);
        if (isConfiguredAnchor(code)
                && (!current.rule().equals(updated.rule())
                || !Objects.equals(current.endMonth(), updated.endMonth())
                || current.anchor() != updated.anchor())) {
            throw anchorTransitionRequired();
        }
        if (updated.equals(current)) {
            log.info("m=update, message=NoChangesDetected, code={}", code);
            return current;
        }
        if (updated.anchor() && !current.anchor() && updated.isActive()) {
            rejectSecondActiveAnchor(code);
        }
        mapper.copyEditableFields(updated, entity);
        return mapper.toModel(scheduleRepository.save(entity));
    }

    @Transactional
    public IncomeSchedule inactivate(final UUID code) {
        log.info("m=inactivate, code={}", code);
        return changeStatus(code, IncomeScheduleStatus.INACTIVE);
    }

    /**
     * Reactivating does not generate the months the schedule was inactive: its first month served
     * moves to the current month when it was earlier. Otherwise the next run of the job would post
     * a receipt for every month in between. A schedule whose end month has already passed cannot be
     * reactivated until that end month is extended or removed.
     */
    @Transactional
    public IncomeSchedule activate(final UUID code) {
        log.info("m=activate, code={}", code);
        return changeStatus(code, IncomeScheduleStatus.ACTIVE);
    }

    /**
     * The months of the schedule, materialized or not, for the user to see before anything is
     * generated.
     *
     * @param from first month, inclusive; defaults to the current month
     * @param to   last month, inclusive; defaults to eleven months after {@code from}
     */
    public List<IncomeOccurrenceView> findOccurrences(final UUID code, final YearMonth from, final YearMonth to) {
        log.info("m=findOccurrences, code={}, from={}, to={}", code, from, to);
        final YearMonth effectiveFrom = from != null ? from : YearMonth.now(clock);
        final YearMonth effectiveTo = to != null ? to : effectiveFrom.plusMonths(11);
        validateListingRange(effectiveFrom, effectiveTo);

        final IncomeSchedule schedule = mapper.toModel(findEntity(code));
        final List<IncomeOccurrence> materialized =
                mapper.toOccurrenceModelList(occurrenceRepository.findByScheduleCode(code));
        return IncomeOccurrenceResolver.resolve(
                schedule, materialized, holidayCalendarService.findCalendar(), effectiveFrom, effectiveTo);
    }

    /**
     * The ACTIVE schedule that opens the pay cycles, if there is one.
     */
    public Optional<IncomeSchedule> findActiveAnchor() {
        log.info("m=findActiveAnchor");
        return scheduleRepository.findByAnchorTrueAndStatus(IncomeScheduleStatus.ACTIVE).map(mapper::toModel);
    }

    /**
     * Same as {@link #findActiveAnchor}, locking the schedule for the transaction, so the pay cycles
     * are never built from a rule that is being edited.
     */
    @Transactional
    public Optional<IncomeSchedule> findActiveAnchorForUpdate() {
        log.info("m=findActiveAnchorForUpdate");
        return scheduleRepository.findAnchorForUpdate(IncomeScheduleStatus.ACTIVE).map(mapper::toModel);
    }

    /**
     * Same as {@link #findByCode}, locking the schedule for the transaction. The pay cycles read it
     * under this lock so an edit of its rule cannot slip in between the read and the cycles written
     * from it; their lock order is configuration, then this schedule, then the cycles.
     */
    @Transactional
    public IncomeSchedule findByCodeForUpdate(final UUID code) {
        log.info("m=findByCodeForUpdate, code={}", code);
        return mapper.toModel(lockEntity(code));
    }

    public IncomeSchedule previewAnchorRuleTransition(final UUID code, final IncomeScheduleUpdate changes) {
        log.info("m=previewAnchorRuleTransition, code={}, changes={}", code, changes);
        final IncomeSchedule current = mapper.toModel(findEntity(code));
        if (!current.isActive() || !current.anchor()) {
            throw new ConflictException("A agenda âncora foi alterada por outra operação; recarregue e tente novamente");
        }
        return applyChanges(current, changes);
    }

    /**
     * The schedule whose rule is being changed by the explicit pay-cycle transition. The caller has
     * already acquired the configuration and schedule locks in the required order.
     */
    @Transactional
    public IncomeSchedule updateForAnchorTransition(final UUID code, final IncomeScheduleUpdate changes) {
        log.info("m=updateForAnchorTransition, code={}, changes={}", code, changes);
        final IncomeScheduleEntity entity = lockEntity(code);
        final IncomeSchedule current = mapper.toModel(entity);
        if (!current.isActive() || !current.anchor()) {
            throw new ConflictException("A agenda âncora foi alterada por outra operação; recarregue e tente novamente");
        }
        final IncomeSchedule updated = applyChanges(current, changes);
        mapper.copyEditableFields(updated, entity);
        return mapper.toModel(scheduleRepository.saveAndFlush(entity));
    }

    /**
     * Switches the active anchor without ever leaving two active anchor rows, while retaining both
     * schedule locks until the enclosing transition commits.
     */
    @Transactional
    public IncomeSchedule switchAnchorForTransition(final UUID previousCode, final UUID nextCode) {
        log.info("m=switchAnchorForTransition, previousCode={}, nextCode={}", previousCode, nextCode);
        final IncomeScheduleEntity previous = lockEntity(previousCode);
        if (previousCode.equals(nextCode)) {
            return mapper.toModel(previous);
        }
        final IncomeScheduleEntity next = lockEntity(nextCode);
        if (next.getStatus() != IncomeScheduleStatus.ACTIVE) {
            throw invalid("anchorScheduleCode", "a nova agenda âncora deve estar ativa");
        }
        if (!Boolean.TRUE.equals(previous.getAnchor()) || Boolean.TRUE.equals(next.getAnchor())) {
            throw new ConflictException("A agenda âncora foi alterada por outra operação; recarregue e tente novamente");
        }
        previous.setAnchor(false);
        scheduleRepository.saveAndFlush(previous);
        next.setAnchor(true);
        return mapper.toModel(scheduleRepository.saveAndFlush(next));
    }

    /**
     * Every occurrence of the schedule that already has a movement, oldest first: the frozen dates the
     * pay cycles follow instead of the calendar.
     */
    public List<IncomeOccurrence> findMaterializedOccurrences(final UUID code) {
        log.info("m=findMaterializedOccurrences, code={}", code);
        return mapper.toOccurrenceModelList(occurrenceRepository.findByScheduleCode(code));
    }

    /**
     * Generates the occurrence of the month now - an early receipt, or a month the job has not
     * reached - as a PENDING CREDIT movement dated on the occurrence's planned date. The receipt
     * itself is then registered on the movement as any other (status PAID_OUT, pay date), so the cash
     * moves on the day the money arrived while the competence stays on the planned date.
     *
     * <p>Idempotent: when the month already has its occurrence - generated by the job, by an earlier
     * call, or even with its movement deleted since - it is returned as it is and nothing is created.
     *
     * @throws InvalidFieldsException (400) when the schedule is inactive, the month is outside its
     *                                validity or further ahead than {@value #MAX_MONTHS_AHEAD} month,
     *                                or its date cannot be calculated (calendar year not reviewed)
     */
    @Transactional
    public MaterializationResult materialize(final UUID code, final YearMonth nominalMonth) {
        log.info("m=materialize, code={}, nominalMonth={}", code, nominalMonth);
        final IncomeScheduleEntity entity = lockEntity(code);
        final Optional<IncomeOccurrenceEntity> existing =
                occurrenceRepository.findByScheduleCodeAndNominalMonth(code, mapper.toDate(nominalMonth));
        if (existing.isPresent()) {
            log.info("m=materialize, message=OccurrenceAlreadyMaterialized, code={}, nominalMonth={}", code, nominalMonth);
            return new MaterializationResult(mapper.toModel(existing.get()), false);
        }

        final IncomeSchedule schedule = mapper.toModel(entity);
        if (!schedule.isActive()) {
            throw invalid("code", "a agenda está inativa");
        }
        if (!schedule.covers(nominalMonth)) {
            throw invalid("nominalMonth", "fora da vigência da agenda (" + schedule.startMonth()
                    + (schedule.endMonth() == null ? " em diante)" : " a " + schedule.endMonth() + ")"));
        }
        final YearMonth lastMonthAllowed = YearMonth.now(clock).plusMonths(MAX_MONTHS_AHEAD);
        if (nominalMonth.isAfter(lastMonthAllowed)) {
            throw invalid("nominalMonth", "só é possível gerar até " + lastMonthAllowed);
        }

        final IncomeOccurrenceView view = IncomeOccurrenceResolver.resolve(
                schedule, List.of(), holidayCalendarService.findCalendar(), nominalMonth, nominalMonth).get(0);
        return switch (view.state()) {
            case PROJECTED -> new MaterializationResult(createOccurrence(entity, schedule, view), true);
            case CALENDAR_NOT_REVIEWED -> throw invalid("nominalMonth",
                    "a data depende do calendário de " + view.pendingYear() + ", que não foi revisado");
            case BUSINESS_DAY_NOT_FOUND -> throw invalid("nominalMonth",
                    "o mês não tem dias úteis suficientes para a regra da agenda");
            // The resolver reports MATERIALIZED only for an occurrence passed in, and none is here.
            case MATERIALIZED -> throw new IllegalStateException("Unexpected materialized view for " + nominalMonth);
        };
    }

    /**
     * Codes of the active schedules the posting job has to look at: already started, or starting
     * next month (an anticipation can bring next month's receipt to this month).
     */
    public List<UUID> findCodesToPost(final LocalDate today) {
        log.info("m=findCodesToPost, today={}", today);
        return scheduleRepository.findAllByStatusAndStartMonthLessThanEqual(
                        IncomeScheduleStatus.ACTIVE, YearMonth.from(today).plusMonths(1).atDay(1))
                .stream()
                .map(IncomeScheduleEntity::getCode)
                .toList();
    }

    /**
     * Materializes every occurrence of the schedule dated on or before {@code today} that does not
     * exist yet, oldest first. Safe to call again and after an outage: an occurrence that already
     * exists is never generated twice, and the months before the schedule's first month are never
     * looked at. A month that cannot be calculated (calendar year not reviewed) is skipped with a
     * warning and tried again on the next run.
     *
     * @return how many occurrences were materialized
     */
    @Transactional
    public int postDueOccurrences(final UUID code, final LocalDate today) {
        log.info("m=postDueOccurrences, code={}, today={}", code, today);
        final Optional<IncomeScheduleEntity> locked = scheduleRepository.findForUpdateByCode(code);
        if (locked.isEmpty()) {
            log.warn("m=postDueOccurrences, message=ScheduleNotFound, code={}", code);
            return 0;
        }
        final IncomeScheduleEntity entity = locked.get();
        final IncomeSchedule schedule = mapper.toModel(entity);
        if (!schedule.isActive()) {
            return 0;
        }

        final List<IncomeOccurrence> materialized =
                mapper.toOccurrenceModelList(occurrenceRepository.findByScheduleCode(code));
        final List<IncomeOccurrenceView> views = IncomeOccurrenceResolver.resolve(
                schedule, materialized, holidayCalendarService.findCalendar(),
                schedule.startMonth(), YearMonth.from(today).plusMonths(1));

        int posted = 0;
        for (final IncomeOccurrenceView view : views) {
            switch (view.state()) {
                case PROJECTED -> {
                    if (!view.plannedDate().isAfter(today)) {
                        createOccurrence(entity, schedule, view);
                        posted++;
                    }
                }
                case CALENDAR_NOT_REVIEWED -> log.warn(
                        "m=postDueOccurrences, message=CalendarNotReviewed, code={}, nominalMonth={}, year={}",
                        code, view.nominalMonth(), view.pendingYear());
                case BUSINESS_DAY_NOT_FOUND -> log.warn(
                        "m=postDueOccurrences, message=BusinessDayNotFound, code={}, nominalMonth={}",
                        code, view.nominalMonth());
                case MATERIALIZED -> {
                    // Already generated, by an earlier run or by an early receipt: nothing to do.
                }
            }
        }
        return posted;
    }

    /**
     * Every occurrence of the active schedules not materialized yet that falls on or before
     * {@code endDate}, for the balance projection. Same rule as the job: what is projected is what
     * the job will post. Months that cannot be calculated are left out, with a warning - the
     * projection does not assume a date it does not know.
     */
    public List<IncomeProjection> findProjectedOccurrencesUntil(final LocalDate endDate) {
        log.info("m=findProjectedOccurrencesUntil, endDate={}", endDate);
        final List<IncomeSchedule> schedules =
                mapper.toModelList(scheduleRepository.findAllByStatus(IncomeScheduleStatus.ACTIVE));
        if (schedules.isEmpty()) {
            return List.of();
        }

        final HolidayCalendar calendar = holidayCalendarService.findCalendar();
        final Map<UUID, List<IncomeOccurrence>> occurrencesBySchedule = findOccurrencesBySchedule(schedules);
        final YearMonth lastMonth = YearMonth.from(endDate).plusMonths(1);

        final List<IncomeProjection> projections = new ArrayList<>();
        for (final IncomeSchedule schedule : schedules) {
            IncomeOccurrenceResolver.resolve(schedule, occurrencesBySchedule.getOrDefault(schedule.code(), List.of()),
                            calendar, schedule.startMonth(), lastMonth)
                    .forEach(view -> {
                        switch (view.state()) {
                            case PROJECTED -> {
                                if (!view.plannedDate().isAfter(endDate)) {
                                    projections.add(new IncomeProjection(
                                            schedule.code(), view.nominalMonth(), view.plannedDate(), view.amount(),
                                            schedule.categoryCode()));
                                }
                            }
                            case CALENDAR_NOT_REVIEWED -> log.warn(
                                    "m=findProjectedOccurrencesUntil, message=CalendarNotReviewed, code={}, nominalMonth={}, year={}",
                                    schedule.code(), view.nominalMonth(), view.pendingYear());
                            case BUSINESS_DAY_NOT_FOUND -> log.warn(
                                    "m=findProjectedOccurrencesUntil, message=BusinessDayNotFound, code={}, nominalMonth={}",
                                    schedule.code(), view.nominalMonth());
                            case MATERIALIZED -> {
                                // Already a movement: counted there, never projected on top of it.
                            }
                        }
                    });
        }
        return projections;
    }

    /**
     * The virtual income occurrences that can belong to {@code [from, toExclusive)}, together with
     * calculation pendings for nominal months whose date could fall in that interval.
     */
    @Transactional(readOnly = true)
    public IncomeProjectionWindow findProjectionBetween(final LocalDate from, final LocalDate toExclusive) {
        log.info("m=findProjectionBetween, from={}, toExclusive={}", from, toExclusive);
        if (!from.isBefore(toExclusive)) {
            return new IncomeProjectionWindow(List.of(), List.of());
        }

        final List<IncomeSchedule> schedules =
                mapper.toModelList(scheduleRepository.findAllByStatus(IncomeScheduleStatus.ACTIVE));
        if (schedules.isEmpty()) {
            return new IncomeProjectionWindow(List.of(), List.of());
        }

        final HolidayCalendar calendar = holidayCalendarService.findCalendar();
        final Map<UUID, List<IncomeOccurrence>> occurrencesBySchedule = findOccurrencesBySchedule(schedules);
        final List<IncomeProjection> projections = new ArrayList<>();
        final List<IncomeProjectionPending> pending = new ArrayList<>();

        for (final IncomeSchedule schedule : schedules) {
            final ProjectionMonths projectionMonths = projectionMonths(from, toExclusive, schedule.rule(), calendar);
            final YearMonth firstMonth = projectionMonths.first().isBefore(schedule.startMonth())
                    ? schedule.startMonth() : projectionMonths.first();
            final YearMonth lastMonth = schedule.endMonth() != null && schedule.endMonth().isBefore(projectionMonths.last())
                    ? schedule.endMonth() : projectionMonths.last();
            if (firstMonth.isAfter(lastMonth)) {
                continue;
            }

            IncomeOccurrenceResolver.resolve(schedule,
                            occurrencesBySchedule.getOrDefault(schedule.code(), List.of()),
                            calendar, firstMonth, lastMonth)
                    .forEach(view -> {
                        switch (view.state()) {
                            case PROJECTED -> {
                                if (!view.plannedDate().isBefore(from) && view.plannedDate().isBefore(toExclusive)) {
                                    projections.add(new IncomeProjection(
                                            schedule.code(), view.nominalMonth(), view.plannedDate(), view.amount(),
                                            schedule.categoryCode()));
                                }
                            }
                            case CALENDAR_NOT_REVIEWED -> pending.add(new IncomeProjectionPending(
                                    IncomeProjectionPending.Type.CALENDAR_NOT_REVIEWED,
                                    schedule.code(), view.nominalMonth(), view.pendingYear()));
                            case BUSINESS_DAY_NOT_FOUND -> pending.add(new IncomeProjectionPending(
                                    IncomeProjectionPending.Type.BUSINESS_DAY_NOT_FOUND,
                                    schedule.code(), view.nominalMonth(), null));
                            case MATERIALIZED -> {
                                // The movement is included by its competence date, never projected again.
                            }
                        }
                    });
        }

        projections.sort(Comparator.comparing(IncomeProjection::date).thenComparing(IncomeProjection::scheduleCode));
        pending.sort(Comparator.comparing(IncomeProjectionPending::nominalMonth)
                .thenComparing(IncomeProjectionPending::scheduleCode)
                .thenComparing(IncomeProjectionPending::type));
        return new IncomeProjectionWindow(List.copyOf(projections), List.copyOf(pending));
    }

    private static ProjectionMonths projectionMonths(final LocalDate from,
                                                    final LocalDate toExclusive,
                                                    final IncomeDateRule rule,
                                                    final HolidayCalendar calendar) {
        YearMonth first = YearMonth.from(from);
        YearMonth last = YearMonth.from(toExclusive.minusDays(1));
        if (rule.ruleType() != DateRuleType.FIXED_DAY
                || rule.nonBusinessDayPolicy() == NonBusinessDayPolicy.KEEP) {
            return new ProjectionMonths(first, last);
        }

        // A date can only move away from its nominal month in the policy's direction. Bound the
        // search by the reviewed calendar span, as the calendar-impact preview does.
        final int firstReviewedYear = calendar.reviewedYears().stream().mapToInt(Integer::intValue).min().orElse(0);
        final int lastReviewedYear = calendar.reviewedYears().stream().mapToInt(Integer::intValue).max().orElse(-1);
        final long displacementMonths = lastReviewedYear < firstReviewedYear
                ? 0
                : ((long) lastReviewedYear - firstReviewedYear + 1) * 12;
        if (rule.nonBusinessDayPolicy() == NonBusinessDayPolicy.POSTPONE) {
            first = first.minusMonths(displacementMonths);
        } else if (rule.nonBusinessDayPolicy() == NonBusinessDayPolicy.ANTICIPATE) {
            last = last.plusMonths(displacementMonths);
        }
        return new ProjectionMonths(first, last);
    }

    private record ProjectionMonths(YearMonth first, YearMonth last) {
    }

    /**
     * The occurrences of the active schedules planned for {@code [from, toExclusive)}, materialized
     * (their frozen date) or only projected (the rule and the current calendar), oldest first: what a
     * pay cycle boundary inside that range would hand from one cycle to the other. Months that cannot
     * be calculated have no date and are left out; the pay cycle pendencies already say so.
     */
    public List<IncomeOccurrenceImpact> findOccurrencesPlannedBetween(final LocalDate from, final LocalDate toExclusive) {
        log.info("m=findOccurrencesPlannedBetween, from={}, toExclusive={}", from, toExclusive);
        final List<IncomeSchedule> schedules =
                mapper.toModelList(scheduleRepository.findAllByStatus(IncomeScheduleStatus.ACTIVE));
        if (schedules.isEmpty() || !from.isBefore(toExclusive)) {
            return List.of();
        }

        final HolidayCalendar calendar = holidayCalendarService.findCalendar();
        final Map<UUID, List<IncomeOccurrence>> occurrencesBySchedule = findOccurrencesBySchedule(schedules);
        // A calculated date can move across reviewed years; include their full span on either side.
        final int firstReviewedYear = calendar.reviewedYears().stream().mapToInt(Integer::intValue).min().orElse(0);
        final int lastReviewedYear = calendar.reviewedYears().stream().mapToInt(Integer::intValue).max().orElse(-1);
        final long displacementMonths = lastReviewedYear < firstReviewedYear
                ? 0
                : ((long) lastReviewedYear - firstReviewedYear + 1) * 12;
        final YearMonth firstMonth = YearMonth.from(from).minusMonths(displacementMonths);
        final YearMonth lastMonth = YearMonth.from(toExclusive.minusDays(1)).plusMonths(displacementMonths);

        final List<IncomeOccurrenceImpact> impacts = new ArrayList<>();
        for (final IncomeSchedule schedule : schedules) {
            final List<IncomeOccurrence> occurrences =
                    occurrencesBySchedule.getOrDefault(schedule.code(), List.of());
            occurrences.stream()
                    .filter(occurrence -> !occurrence.plannedDate().isBefore(from)
                            && occurrence.plannedDate().isBefore(toExclusive))
                    .map(IncomeOccurrenceView::materialized)
                    .forEach(view -> impacts.add(
                            new IncomeOccurrenceImpact(schedule.code(), schedule.description(), view)));
            IncomeOccurrenceResolver.resolve(schedule, occurrences, calendar, firstMonth, lastMonth)
                    .stream()
                    .filter(view -> view.state() == IncomeOccurrenceView.State.PROJECTED)
                    .filter(view -> !view.plannedDate().isBefore(from) && view.plannedDate().isBefore(toExclusive))
                    .forEach(view -> impacts.add(
                            new IncomeOccurrenceImpact(schedule.code(), schedule.description(), view)));
        }
        impacts.sort(Comparator.comparing((IncomeOccurrenceImpact impact) -> impact.occurrence().plannedDate())
                .thenComparing(IncomeOccurrenceImpact::scheduleDescription));
        return impacts;
    }

    /**
     * What reviewing {@code year} with these holidays would do to the occurrences that are not
     * materialized yet: the ones whose date moves, or that become (or stop being) impossible to
     * calculate. Changes nothing. Materialized occurrences are not listed: they keep their date.
     */
    public List<IncomeCalendarImpact> previewCalendarChange(final int year, final List<Holiday> holidays) {
        log.info("m=previewCalendarChange, year={}, holidays={}", year, holidays.size());
        holidayCalendarService.validateYear(year);
        final HolidayCalendar current = holidayCalendarService.findCalendar();
        final HolidayCalendar proposed = current.replacingYear(year, holidays);

        // An anticipation or a postponement can carry a date across the new year in either direction.
        final YearMonth from = YearMonth.of(year, 1).minusMonths(1);
        final YearMonth to = YearMonth.of(year, 12).plusMonths(1);

        final List<IncomeSchedule> schedules =
                mapper.toModelList(scheduleRepository.findAllByStatus(IncomeScheduleStatus.ACTIVE));
        if (schedules.isEmpty() || to.isBefore(from)) {
            return List.of();
        }
        final Map<UUID, List<IncomeOccurrence>> occurrencesBySchedule = findOccurrencesBySchedule(schedules);

        final List<IncomeCalendarImpact> impacts = new ArrayList<>();
        for (final IncomeSchedule schedule : schedules) {
            IncomeOccurrenceResolver.findChanges(schedule,
                            occurrencesBySchedule.getOrDefault(schedule.code(), List.of()), current, proposed, from, to)
                    .forEach(change -> impacts.add(
                            new IncomeCalendarImpact(schedule.code(), schedule.description(), change)));
        }
        return impacts;
    }

    /**
     * The only place an occurrence is created: the movement and the occurrence row are written in
     * the caller's transaction, which holds the schedule's lock, so both exist or neither does. The
     * movement carries the schedule's code as its recurrence bill code and the nominal month as its
     * occurrence date; its planned date remains its due date and competence.
     */
    private IncomeOccurrence createOccurrence(final IncomeScheduleEntity entity,
                                              final IncomeSchedule schedule,
                                              final IncomeOccurrenceView view) {
        final LocalDate plannedDate = view.plannedDate();
        final FinancialMovement movement = financialMovementService.createWithAutomaticCompetence(new FinancialMovement(
                null,
                OperationType.CREDIT,
                schedule.description(),
                schedule.amount(),
                null,
                plannedDate.atStartOfDay(),
                plannedDate.atStartOfDay(),
                null,
                FinancialMovementStatus.PENDING,
                Boolean.TRUE,
                schedule.code(),
                mapper.toDate(view.nominalMonth()),
                schedule.categoryCode(),
                plannedDate,
                Boolean.FALSE));

        final IncomeOccurrenceEntity saved = occurrenceRepository.save(IncomeOccurrenceEntity.builder()
                .incomeSchedule(entity)
                .nominalMonth(mapper.toDate(view.nominalMonth()))
                .plannedDate(plannedDate)
                .plannedAmount(schedule.amount())
                .movementCode(movement.code())
                .build());
        log.info("m=createOccurrence, message=OccurrenceMaterialized, code={}, nominalMonth={}, plannedDate={}, movementCode={}",
                schedule.code(), view.nominalMonth(), plannedDate, movement.code());
        return mapper.toModel(saved);
    }

    private IncomeSchedule validateNew(final IncomeSchedule schedule) {
        final String description = normalizeDescription(schedule.description());
        final BigDecimal amount = validateAmount(schedule.amount());
        if (schedule.categoryCode() != null) {
            categoryService.validateForUse(schedule.categoryCode(), OperationType.CREDIT);
        }
        if (schedule.startMonth() == null) {
            throw invalid("startMonth", "deve ser informado");
        }
        if (schedule.startMonth().isBefore(YearMonth.now(clock))) {
            throw invalid("startMonth", "não pode ser anterior ao mês atual; receitas de meses passados "
                    + "são registradas como movimentações");
        }
        if (schedule.endMonth() != null && schedule.endMonth().isBefore(schedule.startMonth())) {
            throw invalid("endMonth", "não pode ser anterior ao mês inicial " + schedule.startMonth());
        }
        if (schedule.anchor()) {
            rejectSecondActiveAnchor(null);
        }
        return new IncomeSchedule(schedule.code(), description, amount, schedule.categoryCode(), schedule.rule(),
                schedule.startMonth(), schedule.endMonth(), schedule.anchor(), IncomeScheduleStatus.ACTIVE,
                schedule.replacesFixedBillCode());
    }

    /**
     * Hands the receipt over: the fixed bill must be a CREDIT, must not have posted anything from
     * the schedule's first month on (that would be the same receipt twice), and is ended the day
     * before that month - so it stops generating, and cannot start again without someone moving its
     * end date back on purpose.
     */
    private void replaceFixedBill(final IncomeSchedule schedule) {
        final UUID fixedBillCode = schedule.replacesFixedBillCode();
        final FixedBill fixedBill;
        try {
            fixedBill = fixedBillService.findByCodeForUpdate(fixedBillCode);
        } catch (final EntityNotFoundException ex) {
            throw invalid("replacesFixedBillCode", "conta fixa não encontrada");
        }
        if (fixedBill.getOperationType() != OperationType.CREDIT) {
            throw invalid("replacesFixedBillCode", "a conta fixa não é uma receita (CREDIT)");
        }
        rejectActiveReplacement(fixedBillCode, schedule.code());

        final LocalDate firstDayServed = schedule.startMonth().atDay(1);
        final boolean alreadyPosted = financialMovementService
                .findRecurrenceOccurrencesBetweenForUpdate(List.of(fixedBillCode), firstDayServed, LAST_SUPPORTED_DATE)
                .stream()
                .anyMatch(movement -> Boolean.TRUE.equals(movement.flagActive()));
        if (alreadyPosted) {
            throw invalid("startMonth", "a conta fixa já lançou uma ocorrência a partir de " + schedule.startMonth()
                    + "; escolha um mês posterior ou exclua o lançamento");
        }

        final LocalDate lastDayOfFixedBill = firstDayServed.minusDays(1);
        if (fixedBill.getEndDate() == null || fixedBill.getEndDate().isAfter(lastDayOfFixedBill)) {
            log.info("m=replaceFixedBill, message=EndingFixedBill, fixedBillCode={}, endDate={}", fixedBillCode, lastDayOfFixedBill);
            fixedBillService.update(fixedBillCode, new FixedBillUpdate(
                    null, null, null, null, null, null, null, null, lastDayOfFixedBill, null));
        }
    }

    private void rejectActiveReplacement(final UUID fixedBillCode, final UUID code) {
        if (!scheduleRepository.findActiveReplacementsForUpdate(
                fixedBillCode, IncomeScheduleStatus.ACTIVE, code).isEmpty()) {
            throw invalid("replacesFixedBillCode", "já existe uma agenda ativa substituindo essa conta fixa");
        }
    }

    private IncomeSchedule applyChanges(final IncomeSchedule current, final IncomeScheduleUpdate changes) {
        final String description =
                changes.description() != null ? normalizeDescription(changes.description()) : current.description();
        final BigDecimal amount = changes.amount() != null ? validateAmount(changes.amount()) : current.amount();

        UUID categoryCode = current.categoryCode();
        if (changes.categoryCode() != null && !changes.categoryCode().equals(current.categoryCode())) {
            categoryService.validateForUse(changes.categoryCode(), OperationType.CREDIT);
            categoryCode = changes.categoryCode();
        }

        final IncomeDateRule rule = changes.changesRule() ? mergeRule(current.rule(), changes) : current.rule();
        final boolean anchor = changes.anchor() != null ? changes.anchor() : current.anchor();
        return new IncomeSchedule(current.code(), description, amount, categoryCode, rule, current.startMonth(),
                resolveEndMonth(current, changes), anchor, current.status(), current.replacesFixedBillCode());
    }

    private IncomeDateRule mergeRule(final IncomeDateRule current, final IncomeScheduleUpdate changes) {
        return new IncomeDateRule(
                changes.ruleType() != null ? changes.ruleType() : current.ruleType(),
                changes.ruleValue() != null ? changes.ruleValue() : current.value(),
                changes.businessWeek() != null ? changes.businessWeek() : current.businessWeek(),
                changes.nonBusinessDayPolicy() != null ? changes.nonBusinessDayPolicy() : current.nonBusinessDayPolicy());
    }

    private YearMonth resolveEndMonth(final IncomeSchedule current, final IncomeScheduleUpdate changes) {
        if (changes.removeEndMonth() && changes.endMonth() != null) {
            throw invalid("endMonth", "não pode ser informado junto com remove_end_month");
        }
        YearMonth endMonth = current.endMonth();
        if (changes.removeEndMonth()) {
            endMonth = null;
        } else if (changes.endMonth() != null) {
            endMonth = changes.endMonth();
        }
        if (endMonth != null && endMonth.isBefore(current.startMonth())) {
            throw invalid("endMonth", "não pode ser anterior ao mês inicial " + current.startMonth());
        }
        return endMonth;
    }

    private IncomeSchedule changeStatus(final UUID code, final IncomeScheduleStatus newStatus) {
        final IncomeScheduleEntity entity = lockEntity(code);
        if (entity.getStatus() == newStatus) {
            log.info("m=changeStatus, message=StatusAlreadySet, code={}, status={}", code, newStatus);
            return mapper.toModel(entity);
        }
        if (newStatus == IncomeScheduleStatus.INACTIVE && isConfiguredAnchor(code)) {
            throw anchorTransitionRequired();
        }
        if (newStatus == IncomeScheduleStatus.ACTIVE) {
            final UUID fixedBillCode = entity.getReplacesFixedBillCode();
            if (fixedBillCode != null) {
                fixedBillService.findByCodeForUpdate(fixedBillCode);
                rejectActiveReplacement(fixedBillCode, code);
            }

            if (Boolean.TRUE.equals(entity.getAnchor())) {
                rejectSecondActiveAnchor(code);
            }
            final LocalDate currentMonthStart = YearMonth.now(clock).atDay(1);
            if (entity.getEndMonth() != null && entity.getEndMonth().isBefore(currentMonthStart)) {
                throw invalid("endMonth", "deve ser estendido ou removido antes de reativar a agenda");
            }
            if (entity.getStartMonth().isBefore(currentMonthStart)) {
                entity.setStartMonth(currentMonthStart);
            }
        }
        entity.setStatus(newStatus);
        return mapper.toModel(scheduleRepository.save(entity));
    }

    private boolean isConfiguredAnchor(final UUID code) {
        final PayCycleConfig config = payCycleConfigService.find();
        return config.isPayCycle() && code.equals(config.anchorScheduleCode());
    }

    private ConflictException anchorTransitionRequired() {
        return new ConflictException("A agenda âncora não pode mudar enquanto os ciclos estão ativos; "
                + "use a transição de âncora");
    }

    /**
     * @param code the schedule being changed, left out of the check; null for a schedule not saved yet
     */
    private void rejectSecondActiveAnchor(final UUID code) {
        final boolean exists = code == null
                ? scheduleRepository.existsByAnchorTrueAndStatus(IncomeScheduleStatus.ACTIVE)
                : scheduleRepository.existsByAnchorTrueAndStatusAndCodeNot(IncomeScheduleStatus.ACTIVE, code);
        if (exists) {
            throw invalid("anchor", "já existe uma agenda âncora ativa");
        }
    }

    private Map<UUID, List<IncomeOccurrence>> findOccurrencesBySchedule(final List<IncomeSchedule> schedules) {
        final List<UUID> codes = schedules.stream().map(IncomeSchedule::code).toList();
        return mapper.toOccurrenceModelList(occurrenceRepository.findByScheduleCodeIn(codes)).stream()
                .collect(Collectors.groupingBy(IncomeOccurrence::scheduleCode));
    }

    private void validateListingRange(final YearMonth from, final YearMonth to) {
        if (to.isBefore(from)) {
            throw invalid("to", "não pode ser anterior a from");
        }
        if (ChronoUnit.MONTHS.between(from, to) + 1 > MAX_LISTING_MONTHS) {
            throw invalid("to", "o intervalo não pode passar de " + MAX_LISTING_MONTHS + " meses");
        }
    }

    /**
     * Same trim-then-check as the categories: the request limits the raw text, "ab " passes a
     * minimum of 3 and is only 2 characters once trimmed.
     */
    private String normalizeDescription(final String description) {
        final String normalized = description == null ? "" : description.trim();
        if (normalized.length() < DESCRIPTION_MIN_LENGTH || normalized.length() > DESCRIPTION_MAX_LENGTH) {
            throw invalid("description", "deve ter entre " + DESCRIPTION_MIN_LENGTH + " e "
                    + DESCRIPTION_MAX_LENGTH + " caracteres");
        }
        return normalized;
    }

    private BigDecimal validateAmount(final BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw invalid("amount", "deve ser maior que zero");
        }
        return amount;
    }

    private IncomeScheduleEntity findEntity(final UUID code) {
        return scheduleRepository.findByCode(code)
                .orElseThrow(() -> new EntityNotFoundException("IncomeScheduleEntity not found for code " + code));
    }

    private IncomeScheduleEntity lockEntity(final UUID code) {
        return scheduleRepository.findForUpdateByCode(code)
                .orElseThrow(() -> new EntityNotFoundException("IncomeScheduleEntity not found for code " + code));
    }

    private InvalidFieldsException invalid(final String field, final String message) {
        return new InvalidFieldsException(INVALID_FIELDS_MESSAGE, IncomeScheduleService.class, Map.of(field, message));
    }

}
