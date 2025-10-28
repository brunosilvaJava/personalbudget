package com.bts.personalbudget.core.domain.service.fixedbill;

import com.bts.personalbudget.core.domain.entity.CalendarFixedBillEntity;
import com.bts.personalbudget.core.domain.entity.FixedBillEntity;
import com.bts.personalbudget.core.domain.enumerator.FixedBillStatus;
import com.bts.personalbudget.core.domain.enumerator.OperationType;
import com.bts.personalbudget.core.domain.enumerator.RecurrenceType;
import com.bts.personalbudget.core.domain.service.fixedbill.calc.CalcFixedBill;
import com.bts.personalbudget.mapper.FixedBillMapper;
import jakarta.persistence.EntityNotFoundException;

import java.math.BigDecimal;
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

    public void save(final FixedBill fixedBill) {
        log.info("m=save fixedBill={}", fixedBill);
        validationMandatoryFields(fixedBill);
        fixedBill.validationDays();
        fixedBill.setStatus(FixedBillStatus.ACTIVE);
        final FixedBillEntity fixedBillEntity = buildFixedBillEntity(fixedBill);
        final Set<CalendarFixedBillEntity> calendarFixedBillEntityList = buildCalendarFixedBillEntityList(fixedBill, fixedBillEntity);
        fixedBillEntity.setCalendarFixedBillEntityList(calendarFixedBillEntityList);
        fixedBillRepository.save(fixedBillEntity);
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
        final LocalDate paramDate = defineNextDueDate(fixedBill, LocalDate.now()).orElse(null);
        fixedBillEntity.setNextDueDate(paramDate);
        return fixedBillEntity;
    }

    public FixedBill findByCode(final UUID code) {
        log.info("m=findByCode code={}", code);
        final FixedBillEntity fixedBillEntity = findEntity(code);
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
    public FixedBill update(final UUID code,
                            final OperationType operationType,
                            final String description,
                            final BigDecimal amount,
                            final RecurrenceType recurrenceType,
                            final Set<Integer> days,
                            final Integer referenceYear,
                            final FixedBillStatus status,
                            final LocalDate startDate,
                            final LocalDate endDate) {
        log.info("m=update, code={}, operationType={}, description={}, amount={}, recurrenceType={}, days={}, referenceYear={}, status={}, startDate={}, endDate={}",
                code, operationType, description, amount, recurrenceType, days, referenceYear, status, startDate, endDate);

        final FixedBillEntity fixedBillEntity = findEntity(code);
        final FixedBill updateFixedBill = fixedBillMapper.toModel(fixedBillEntity);

        if (operationType == null &&
                description == null &&
                amount == null &&
                recurrenceType == null &&
                days == null &&
                referenceYear == null &&
                status == null &&
                startDate == null &&
                endDate == null) {
            return updateFixedBill;
        }

        final boolean hasChanges = updateFixedBill.update(operationType, description, amount, recurrenceType, days, referenceYear, status, startDate, endDate);

        if (hasChanges) {
            log.info("m=update, message=ChangesDetected, code={}, savingToDatabase=true", code);
            validationMandatoryFields(updateFixedBill);
            updateFixedBill.validationDays();
            final FixedBillEntity updateEntity = fixedBillMapper.toEntity(updateFixedBill);
            updateEntity.setCalendarFixedBillEntityList(buildCalendarFixedBillEntityList(updateFixedBill, fixedBillEntity));
            fixedBillRepository.save(updateEntity);
        } else {
            log.info("m=update, message=NoChangesDetected, code={}, savingToDatabase=false", code);
        }

        return updateFixedBill;
    }

    @Transactional
    public FixedBill changeStatus(final UUID code, final FixedBillStatus newStatus) {
        log.info("m=changeStatus, code={}, newStatus={}", code, newStatus);

        final FixedBillEntity fixedBillEntity = findEntity(code);

        if (fixedBillEntity.getStatus() == newStatus) {
            log.warn("m=changeStatus, message=Status is already {}, code={}", newStatus, code);
            return fixedBillMapper.toModel(fixedBillEntity);
        }

        fixedBillEntity.setStatus(newStatus);

        final FixedBillEntity savedEntity = fixedBillRepository.save(fixedBillEntity);

        log.info("m=changeStatus, message=Status changed successfully, code={}, oldStatus={}, newStatus={}",
                code, fixedBillEntity.getStatus(), newStatus);

        return fixedBillMapper.toModel(savedEntity);
    }

    @Transactional
    public void delete(final UUID code) {
        log.info("m=delete, code={}", code);
        final FixedBillEntity fixedBillEntity = findEntity(code);
        fixedBillEntity.delete();
    }

    private FixedBillEntity findEntity(final UUID code) {
        final Optional<FixedBillEntity> fixedBillEntityOptional = fixedBillRepository.findByCodeAndFlagActiveTrue(code);
        return fixedBillEntityOptional.orElseThrow(() -> new EntityNotFoundException("FixedBillEntity not found for code " + code));
    }

    @Transactional
    public void updateNextDueDate(final FixedBill fixedBill, final LocalDate baseData) {
        log.info("m=updateNextDueDate, code={}, baseData={}", fixedBill.getCode(), baseData);
        final Optional<LocalDate> nextDueDate = defineNextDueDate(fixedBill, baseData);
        final Optional<FixedBillEntity> fixedBillEntityOptional = fixedBillRepository.findByCodeAndFlagActiveTrue(fixedBill.getCode());
        final FixedBillEntity fixedBillEntity = fixedBillEntityOptional.orElseThrow();
        fixedBillEntity.setNextDueDate(nextDueDate.orElseThrow());
    }

    public Optional<LocalDate> defineNextDueDate(final FixedBill fixedBill,
                                                 final LocalDate baseDate) {
        if (!fixedBill.isCurrent(baseDate)) {
            return Optional.empty();
        }
        final CalcFixedBill calcFixedBill = calcFixedBillFactory.build(fixedBill.getRecurrenceType());
        return Optional.of(calcFixedBill.calcNextDueDate(fixedBill, baseDate));
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

    public List<FixedBill> findAllByNextDueDateBetween(final LocalDate initialDate, final LocalDate endDate) {
        log.info("m=findAllByNextDueDateBetween initialDate={} endDate={}", initialDate, endDate);
        final List<FixedBillEntity> fixedBillEntityList =
                fixedBillRepository.findAllByFlagActiveTrueAndStatusAndNextDueDateBetween(ACTIVE, initialDate, endDate);
        return fixedBillMapper.toModelList(fixedBillEntityList);
    }
}
