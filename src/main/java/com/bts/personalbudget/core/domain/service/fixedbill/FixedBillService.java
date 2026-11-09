package com.bts.personalbudget.core.domain.service.fixedbill;

import com.bts.personalbudget.core.domain.entity.CalendarFixedBillEntity;
import com.bts.personalbudget.core.domain.entity.FixedBillEntity;
import com.bts.personalbudget.core.domain.enumerator.FixedBillStatus;
import com.bts.personalbudget.core.domain.enumerator.OperationType;
import com.bts.personalbudget.core.domain.enumerator.RecurrenceType;
import com.bts.personalbudget.core.domain.enumerator.FinancialMovementStatus;
import com.bts.personalbudget.core.domain.model.FinancialMovement;
import com.bts.personalbudget.core.domain.service.FinancialMovementService;
import com.bts.personalbudget.core.domain.service.category.CategoryService;
import com.bts.personalbudget.core.domain.service.fixedbill.calc.CalcFixedBill;
import com.bts.personalbudget.core.domain.service.recurrencebill.RecurrenceCalculator;
import com.bts.personalbudget.mapper.FinancialMovementMapper;
import com.bts.personalbudget.mapper.FixedBillMapper;
import jakarta.persistence.EntityNotFoundException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.*;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.bts.personalbudget.core.domain.enumerator.FixedBillStatus.ACTIVE;
import static com.bts.personalbudget.core.domain.enumerator.FixedBillStatus.INACTIVE;

@Slf4j
@RequiredArgsConstructor
@Service
public class FixedBillService {

    private final FixedBillRepository fixedBillRepository;
    private final FixedBillMapper fixedBillMapper;
    private final CalcFixedBillFactory calcFixedBillFactory;
    private final Clock clock;
    private final FinancialMovementService financialMovementService;
    private final FinancialMovementMapper financialMovementMapper;
    private final CategoryService categoryService;

    /**
     * Upper bound on how many occurrences a single catch-up run will post for one account.
     * Guards against an endless loop if a FixedBill is somehow misconfigured (e.g. a calendar
     * that never satisfies isCurrent again) - the job logs a warning and moves on instead of
     * hanging the whole recurrence run.
     */
    static final int CATCH_UP_SAFETY_LIMIT = 400;

    /**
     * Creates the fixed bill and, if its first occurrence is today, posts that occurrence right
     * away (in this same transaction) instead of waiting for the next midnight run.
     */
    @Transactional
    public FixedBill save(final FixedBill fixedBill) {
        log.info("m=save fixedBill={}", fixedBill);
        validationMandatoryFields(fixedBill);
        fixedBill.validationDays();
        if (fixedBill.getCategoryCode() != null) {
            categoryService.validateForUse(fixedBill.getCategoryCode(), fixedBill.getOperationType());
        }
        fixedBill.setStatus(FixedBillStatus.ACTIVE);
        financialMovementService.lockPayCycleMovementMutation();
        final FixedBillEntity fixedBillEntity = buildFixedBillEntity(fixedBill);
        final Set<CalendarFixedBillEntity> calendarFixedBillEntityList = buildCalendarFixedBillEntityList(fixedBill, fixedBillEntity);
        fixedBillEntity.setCalendarFixedBillEntityList(calendarFixedBillEntityList);
        fixedBillRepository.save(fixedBillEntity);
        return postDueOccurrencesIfDue(fixedBillEntity.getCode(), fixedBillEntity.getNextDueDate())
                .orElseGet(() -> fixedBillMapper.toModel(fixedBillEntity));
    }

    private Set<CalendarFixedBillEntity> buildCalendarFixedBillEntityList(FixedBill fixedBill, FixedBillEntity fixedBillEntity) {
        log.info("m=buildCalendarFixedBillEntityList fixedBillDays={} fixedBillCode={}", fixedBill.getDays(), fixedBillEntity.getCode());
        final Set<CalendarFixedBillEntity> calendarFixedBillEntityList = new HashSet<>();

        for (Integer day : fixedBill.getDays()) {
            final Optional<CalendarFixedBillEntity> calendarByDay = fixedBillEntity.findCalendarByDay(day);
            if (calendarByDay.isPresent()) {
                calendarFixedBillEntityList.add(calendarByDay.get());
            } else {
                calendarFixedBillEntityList.add(new CalendarFixedBillEntity(day, fixedBillEntity));
            }
        }
        return calendarFixedBillEntityList;
    }

    private FixedBillEntity buildFixedBillEntity(final FixedBill fixedBill) {
        final FixedBillEntity fixedBillEntity = fixedBillMapper.toEntity(fixedBill);
        fixedBillEntity.setCode(UUID.randomUUID());
        fixedBillEntity.setFlagActive(true);
        fixedBillEntity.setStatus(FixedBillStatus.ACTIVE);
        final LocalDate paramDate = defineNextDueDate(fixedBill, defineScheduleBaseDate(fixedBill)).orElse(null);
        fixedBillEntity.setNextDueDate(paramDate);
        return fixedBillEntity;
    }

    /**
     * Schedules start from today or from the start date, whichever is later. Today's own
     * occurrence is safe to include: save/update/reactivation post it right away
     * (postDueOccurrencesIfDue), and posting is idempotent if the job already did. A start date
     * in the past never yields retroactive occurrences, since the base is never before today.
     */
    private LocalDate defineScheduleBaseDate(final FixedBill fixedBill) {
        final LocalDate today = LocalDate.now(clock);
        final LocalDate startDate = fixedBill.getStartDate();
        return startDate != null && startDate.isAfter(today) ? startDate : today;
    }

    public FixedBill findByCode(final UUID code) {
        log.info("m=findByCode code={}", code);
        final FixedBillEntity fixedBillEntity = findEntity(code);
        return fixedBillMapper.toModel(fixedBillEntity);
    }

    @Transactional
    public FixedBill findByCodeForUpdate(final UUID code) {
        log.info("m=findByCodeForUpdate code={}", code);
        final FixedBillEntity fixedBillEntity = fixedBillRepository.findForUpdateByCode(code)
                .orElseThrow(() -> new EntityNotFoundException("FixedBillEntity not found for code " + code));
        return fixedBillMapper.toModel(fixedBillEntity);
    }

    public Page<FixedBill> find(
            final String description,
            final List<OperationType> operationTypes,
            final List<FixedBillStatus> statuses,
            final List<RecurrenceType> recurrenceTypes,
            final Pageable pageable) {

        log.info("m=find, description={}, operationTypes={}, statuses={}, recurrenceTypes={}, pageable={}",
                description, operationTypes, statuses, recurrenceTypes, pageable);

        final List<OperationType> filterOperationTypes = operationTypes != null && !operationTypes.isEmpty() ?
                operationTypes : Arrays.stream(OperationType.values()).toList();
        final List<FixedBillStatus> filterStatuses = statuses != null && !statuses.isEmpty() ?
                statuses : Arrays.stream(FixedBillStatus.values()).toList();
        final List<RecurrenceType> filterRecurrenceTypes = recurrenceTypes != null && !recurrenceTypes.isEmpty() ?
                recurrenceTypes : Arrays.stream(RecurrenceType.values()).toList();

        final Page<FixedBillEntity> pagedEntities = fixedBillRepository.findByFilters(
                description,
                filterOperationTypes,
                filterStatuses,
                filterRecurrenceTypes,
                pageable
        );

        return pagedEntities.map(fixedBillMapper::toModel);
    }

    @Transactional
    public FixedBill update(final UUID code, final FixedBillUpdate changes) {
        log.info("m=update, code={}, changes={}", code, changes);

        final FixedBillEntity fixedBillEntity = findEntity(code);
        final FixedBill updateFixedBill = fixedBillMapper.toModel(fixedBillEntity);

        if (changes.isEmpty()) {
            return updateFixedBill;
        }

        validateCategoryOnUpdate(updateFixedBill, changes);

        final FixedBill.Schedule previousSchedule = updateFixedBill.schedule();
        final boolean hasChanges = updateFixedBill.update(changes);

        if (hasChanges) {
            log.info("m=update, message=ChangesDetected, code={}, savingToDatabase=true", code);
            validationMandatoryFields(updateFixedBill);
            updateFixedBill.validationDays();
            final boolean scheduleChanged = !previousSchedule.equals(updateFixedBill.schedule());
            if (scheduleChanged) {
                final LocalDate nextDueDate = defineNextDueDate(updateFixedBill, defineScheduleBaseDate(updateFixedBill)).orElse(null);
                log.info("m=update, message=ScheduleChanged, code={}, nextDueDate={}", code, nextDueDate);
                updateFixedBill.setNextDueDate(nextDueDate);
                financialMovementService.lockPayCycleMovementMutation();
            }
            final FixedBillEntity updateEntity = fixedBillMapper.toEntity(updateFixedBill);
            updateEntity.setCalendarFixedBillEntityList(buildCalendarFixedBillEntityList(updateFixedBill, fixedBillEntity));
            fixedBillRepository.save(updateEntity);
            if (scheduleChanged) {
                // If the job already posted today, exists() finds it and only the date advances.
                return postDueOccurrencesIfDue(code, updateFixedBill.getNextDueDate()).orElse(updateFixedBill);
            }
        } else {
            log.info("m=update, message=NoChangesDetected, code={}, savingToDatabase=false", code);
        }

        return updateFixedBill;
    }

    @Transactional
    public FixedBill changeStatus(final UUID code, final FixedBillStatus newStatus) {
        log.info("m=changeStatus, code={}, newStatus={}", code, newStatus);

        if (newStatus == ACTIVE) {
            financialMovementService.lockPayCycleMovementMutation();
        }
        final FixedBillEntity fixedBillEntity = findEntity(code);

        if (fixedBillEntity.getStatus() == newStatus) {
            log.warn("m=changeStatus, message=Status is already {}, code={}", newStatus, code);
            return fixedBillMapper.toModel(fixedBillEntity);
        }

        fixedBillEntity.setStatus(newStatus);

        if (newStatus == ACTIVE) {
            final FixedBill fixedBill = fixedBillMapper.toModel(fixedBillEntity);
            final LocalDate nextDueDate = defineNextDueDate(fixedBill, defineScheduleBaseDate(fixedBill)).orElse(null);
            log.info("m=changeStatus, message=ScheduleRecalculated, code={}, nextDueDate={}", code, nextDueDate);
            fixedBillEntity.setNextDueDate(nextDueDate);
        }

        final FixedBillEntity savedEntity = fixedBillRepository.save(fixedBillEntity);

        log.info("m=changeStatus, message=Status changed successfully, code={}, oldStatus={}, newStatus={}",
                code, fixedBillEntity.getStatus(), newStatus);

        if (newStatus == ACTIVE) {
            final Optional<FixedBill> posted = postDueOccurrencesIfDue(code, fixedBillEntity.getNextDueDate());
            if (posted.isPresent()) {
                return posted.get();
            }
        }

        return fixedBillMapper.toModel(savedEntity);
    }

    @Transactional
    public void delete(final UUID code) {
        log.info("m=delete, code={}", code);
        final FixedBillEntity fixedBillEntity = fixedBillRepository.findByCode(code)
                .orElseThrow(() -> new EntityNotFoundException("FixedBillEntity not found for code " + code));
        if (!Boolean.TRUE.equals(fixedBillEntity.getFlagActive())) {
            log.info("m=delete, code={}, msg=already_deleted", code);
            return;
        }
        fixedBillEntity.delete();
    }

    /**
     * The category has to fit the operation type the bill ends up with, so changing only the
     * operation type is checked against the stored category too. Runs only when one of the two
     * actually changes: a bill whose category was inactivated later can still be edited.
     */
    private void validateCategoryOnUpdate(final FixedBill current, final FixedBillUpdate changes) {
        final OperationType finalOperationType =
                changes.operationType() != null ? changes.operationType() : current.getOperationType();
        final UUID finalCategoryCode =
                changes.categoryCode() != null ? changes.categoryCode() : current.getCategoryCode();
        final boolean categoryChanged =
                changes.categoryCode() != null && !changes.categoryCode().equals(current.getCategoryCode());
        final boolean operationTypeChanged = finalOperationType != current.getOperationType();
        if (finalCategoryCode != null && (categoryChanged || operationTypeChanged)) {
            categoryService.validateForUse(finalCategoryCode, finalOperationType);
        }
    }

    private FixedBillEntity findEntity(final UUID code) {
        final Optional<FixedBillEntity> fixedBillEntityOptional = fixedBillRepository.findByCodeAndFlagActiveTrue(code);
        return fixedBillEntityOptional.orElseThrow(() -> new EntityNotFoundException("FixedBillEntity not found for code " + code));
    }

    @Transactional
    public void updateNextDueDate(final FixedBill fixedBill, final LocalDate baseData) {
        log.info("m=updateNextDueDate, code={}, baseData={}", fixedBill.getCode(), baseData);
        final Optional<LocalDate> nextDueDate = defineNextDueDate(fixedBill, baseData);
        final FixedBillEntity fixedBillEntity = findEntity(fixedBill.getCode());

        if (nextDueDate.isEmpty()) {
            log.info("m=updateNextDueDate, msg=NoNextDueDate, code={}, status={}", fixedBill.getCode(), INACTIVE);
            fixedBill.conclude();
            fixedBillEntity.setStatus(fixedBill.getStatus());
            fixedBillEntity.setNextDueDate(null);
            return;
        }

        fixedBillEntity.setNextDueDate(nextDueDate.get());
    }

    public Optional<LocalDate> defineNextDueDate(final FixedBill fixedBill,
                                                 final LocalDate baseDate) {
        if (!fixedBill.isCurrent(baseDate)) {
            return Optional.empty();
        }
        final CalcFixedBill calcFixedBill = calcFixedBillFactory.build(fixedBill.getRecurrenceType());
        final LocalDate nextDueDate = calcFixedBill.calcNextDueDate(fixedBill, baseDate);
        if (!fixedBill.isCurrent(nextDueDate)) {
            log.info("m=defineNextDueDate, msg=NextDueDateAfterEndDate, code={}, nextDueDate={}, endDate={}",
                    fixedBill.getCode(), nextDueDate, fixedBill.getEndDate());
            return Optional.empty();
        }
        return Optional.of(nextDueDate);
    }

    private void validationMandatoryFields(final FixedBill fixedBill) {
        if (fixedBill.getOperationType() == null ||
                fixedBill.getDescription() == null ||
                fixedBill.getAmount() == null ||
                fixedBill.getRecurrenceType() == null ||
                fixedBill.getDays() == null ||
                fixedBill.getDays().isEmpty() ||
                fixedBill.getDescription().isBlank()
        ) {
            log.error("m=validationMandatoryFields error=FieldsMandatoryNull fixedBill={}", fixedBill);
            throw new RuntimeException();
        }
    }

    public List<FixedBill> findByNextDueDate(final LocalDate nextDueDate) {
        log.info("m=findByNextDueDate nextDueDate={}", nextDueDate);
        final List<FixedBillEntity> fixedBillEntityList =
                fixedBillRepository.findAllByNextDueDateAndStatusAndFlagActive(nextDueDate, FixedBillStatus.ACTIVE, Boolean.TRUE);
        return fixedBillMapper.toModelList(fixedBillEntityList);
    }

    /**
     * Active fixed bills with at least one pending occurrence on or before {@code endDate}. Not
     * BETWEEN a start and end: a bill whose next_due_date is before a period can still have
     * occurrences inside it.
     */
    public List<FixedBill> findAllByNextDueDateLessThanEqual(final LocalDate endDate) {
        log.info("m=findAllByNextDueDateLessThanEqual endDate={}", endDate);
        final List<FixedBillEntity> fixedBillEntityList =
                fixedBillRepository.findAllByNextDueDateLessThanEqualAndStatusAndFlagActive(endDate, ACTIVE, Boolean.TRUE);
        return fixedBillMapper.toModelList(fixedBillEntityList);
    }

    /**
     * Every occurrence not posted yet, from next_due_date up to {@code endDate}, following the
     * same rule the posting loop advances by (defineNextDueDate from the day after each
     * occurrence) - so the projection shows exactly what the job will post.
     */
    public List<LocalDate> findOccurrencesUntil(final FixedBill fixedBill, final LocalDate endDate) {
        log.info("m=findOccurrencesUntil, code={}, nextDueDate={}, endDate={}", fixedBill.getCode(), fixedBill.getNextDueDate(), endDate);
        return RecurrenceCalculator.occurrencesUntil(
                        fixedBill.getNextDueDate(),
                        endDate,
                        occurrence -> defineNextDueDate(fixedBill, occurrence.plusDays(1)).orElse(null),
                        RecurrenceCalculator.MAX_OCCURRENCES)
                .stream()
                .filter(fixedBill::isCurrent)
                .toList();
    }

    /**
     * Codes of the fixed bills with at least one occurrence due on or before {@code date}. Used
     * by RecurrenceBillService to decide which accounts postDueOccurrences needs to process -
     * each one is then handled in its own transaction.
     */
    public List<UUID> findCodesByNextDueDateLessThanEqual(final LocalDate date) {
        log.info("m=findCodesByNextDueDateLessThanEqual date={}", date);
        return fixedBillRepository.findAllByNextDueDateLessThanEqualAndStatusAndFlagActive(date, ACTIVE, Boolean.TRUE)
                .stream()
                .map(FixedBillEntity::getCode)
                .toList();
    }

    /**
     * Posts every occurrence of this fixed bill that fell due on or before {@code today} and was
     * not posted yet, then advances next_due_date past today (concluding the bill if there is no
     * next occurrence). Safe to call from the recovery job after an outage, or right after
     * save/update/reactivation to post today's own occurrence immediately - either way, posting
     * is idempotent (see FinancialMovementService.existsByRecurrenceBillCodeAndOccurrenceDate) and
     * never looks at an occurrence older than the bill's own schedule allows (isCurrent).
     */
    @Transactional
    public void postDueOccurrences(final UUID code, final LocalDate today) {
        log.info("m=postDueOccurrences, code={}, today={}", code, today);
        financialMovementService.lockPayCycleMovementMutation();
        lockAndPostDueOccurrences(code, today);
    }

    /**
     * Called right after save/update/reactivation, inside that same transaction (REQUIRED, never
     * REQUIRES_NEW: a new transaction would not see the row just written). Skips the locked read
     * when nothing is due yet. Returns the bill as it stands after posting, so the caller's
     * response already shows next_due_date in the following period.
     */
    private Optional<FixedBill> postDueOccurrencesIfDue(final UUID code, final LocalDate nextDueDate) {
        final LocalDate today = LocalDate.now(clock);
        if (nextDueDate == null || nextDueDate.isAfter(today)) {
            return Optional.empty();
        }
        log.info("m=postDueOccurrencesIfDue, msg=OccurrenceDueOnSave, code={}, nextDueDate={}, today={}", code, nextDueDate, today);
        return Optional.of(fixedBillMapper.toModel(lockAndPostDueOccurrences(code, today)));
    }

    /**
     * The locked read serializes this account between the job and a concurrent request, and -
     * inside a request that has just saved the bill - flushes that pending write first, so the
     * row read here is the one just written.
     */
    private FixedBillEntity lockAndPostDueOccurrences(final UUID code, final LocalDate today) {
        final FixedBillEntity fixedBillEntity = fixedBillRepository.findForUpdateByCode(code)
                .orElseThrow(() -> new EntityNotFoundException("FixedBillEntity not found for code " + code));

        int iterations = 0;
        while (fixedBillEntity.getNextDueDate() != null && !fixedBillEntity.getNextDueDate().isAfter(today)) {
            if (iterations >= CATCH_UP_SAFETY_LIMIT) {
                log.warn("m=postDueOccurrences, msg=SafetyLimitReached, code={}, nextDueDate={}, limit={}",
                        code, fixedBillEntity.getNextDueDate(), CATCH_UP_SAFETY_LIMIT);
                break;
            }
            iterations++;

            final FixedBill fixedBill = fixedBillMapper.toModel(fixedBillEntity);
            final LocalDate occurrenceDate = fixedBill.getNextDueDate();
            postOccurrenceIfDue(fixedBill, occurrenceDate);

            final Optional<LocalDate> nextDueDate = defineNextDueDate(fixedBill, occurrenceDate.plusDays(1));
            if (nextDueDate.isEmpty()) {
                fixedBill.conclude();
                fixedBillEntity.setStatus(fixedBill.getStatus());
                fixedBillEntity.setNextDueDate(null);
            } else {
                fixedBillEntity.setNextDueDate(nextDueDate.get());
            }
        }
        return fixedBillEntity;
    }

    private void postOccurrenceIfDue(final FixedBill fixedBill, final LocalDate occurrenceDate) {
        if (!fixedBill.isCurrent(occurrenceDate)) {
            log.info("m=postOccurrenceIfDue, msg=OccurrenceOutsideValidity, code={}, occurrenceDate={}, endDate={}",
                    fixedBill.getCode(), occurrenceDate, fixedBill.getEndDate());
            return;
        }
        if (financialMovementService.existsByRecurrenceBillCodeAndOccurrenceDate(fixedBill.getCode(), occurrenceDate)) {
            log.info("m=postOccurrenceIfDue, msg=OccurrenceAlreadyPosted, code={}, occurrenceDate={}",
                    fixedBill.getCode(), occurrenceDate);
            return;
        }
        final FinancialMovement financialMovement = financialMovementMapper.toFinancialMovement(fixedBill, FinancialMovementStatus.PENDING);
        financialMovementService.create(financialMovement);
    }
}
