package com.bts.personalbudget.core.domain.service.fixedbill;

import com.bts.personalbudget.controller.fixedbill.FixedBillRepository;
import com.bts.personalbudget.core.domain.entity.CalendarFixedBillEntity;
import com.bts.personalbudget.core.domain.entity.FixedBillEntity;
import com.bts.personalbudget.core.domain.enumerator.RecurrenceType;
import com.bts.personalbudget.core.domain.model.FixedBill;
import com.bts.personalbudget.core.domain.service.fixedbill.calc.CalcFixedBill;
import com.bts.personalbudget.mapper.FixedBillMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.bts.personalbudget.core.domain.enumerator.FixedBillStatus.ACTIVE;
import static com.bts.personalbudget.core.domain.enumerator.RecurrenceType.MONTHLY;
import static com.bts.personalbudget.core.domain.enumerator.RecurrenceType.WEEKLY;
import static java.lang.Boolean.TRUE;

@Slf4j
@RequiredArgsConstructor
@Service
public class FixedBillServiceImpl implements FixedBillService {

    private final FixedBillRepository fixedBillRepository;
    private final FixedBillMapper fixedBillMapper;
    private final CalcFixedBillFactory calcFixedBillFactory;

    public void save(final FixedBill fixedBill) {
        log.info("m=save fixedBill={}", fixedBill);
        validationMandatoryFields(fixedBill);
        validationDays(fixedBill);
        fixedBill.setStatus(ACTIVE);
        final FixedBillEntity fixedBillEntity = buildFixedBillEntity(fixedBill);
        final List<CalendarFixedBillEntity> calendarFixedBillEntityList = buildCalendarFixedBillEntityList(fixedBill, fixedBillEntity);
        fixedBillEntity.setCalendarFixedBillEntityList(calendarFixedBillEntityList);
        fixedBillRepository.save(fixedBillEntity);
    }

    private List<CalendarFixedBillEntity> buildCalendarFixedBillEntityList(FixedBill fixedBill, FixedBillEntity fixedBillEntity) {
        log.info("m=buildCalendarFixedBillEntityList fixedBillDays={} fixedBillCode={}", fixedBill.getDays(), fixedBillEntity.getCode());
        final List<CalendarFixedBillEntity> calendarFixedBillEntityList = new ArrayList<>();

        for (Integer day : fixedBill.getDays()) {
            CalendarFixedBillEntity calendarFixedBillEntity = new CalendarFixedBillEntity();
            calendarFixedBillEntity.setDayLaunch(day);
            calendarFixedBillEntity.setFlgLeapYear(fixedBill.getFlgLeapYear());
            calendarFixedBillEntity.setFlgActive(TRUE);
            calendarFixedBillEntity.setFixedBill(fixedBillEntity);
            calendarFixedBillEntityList.add(calendarFixedBillEntity);
        }
        return calendarFixedBillEntityList;
    }

    private FixedBillEntity buildFixedBillEntity(final FixedBill fixedBill) {
        final FixedBillEntity fixedBillEntity = fixedBillMapper.toEntity(fixedBill);
        fixedBillEntity.setCode(UUID.randomUUID());
        fixedBillEntity.setFlagActive(true);
        fixedBillEntity.setStatus(ACTIVE);
        final LocalDate paramDate = defineNextDueDate(fixedBill, LocalDate.now()).orElse(null);
        fixedBillEntity.setNextDueDate(paramDate);
        return fixedBillEntity;
    }

    @Transactional
    @Override
    public void updateNextDueDate(final FixedBill fixedBill, final LocalDate baseData) {
        final Optional<LocalDate> nextDueDate = defineNextDueDate(fixedBill, baseData);
        final Optional<FixedBillEntity> fixedBillEntityOptional = fixedBillRepository.findByCode(fixedBill.getCode());
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

    private void validationDays(final FixedBill fixedBill) {
        log.info("m=validationDays fixedBillDays={}", fixedBill.getDays());
        final List<Integer> enabledDays = defineEnableDays(fixedBill.getRecurrenceType());
        for (Integer day : fixedBill.getDays()) {
            boolean isValid = false;
            for (Integer enabledDay : enabledDays) {
                if (day.equals(enabledDay)) {
                    isValid = true;
                    break;
                }
            }
            if (!isValid) {
                if (WEEKLY == fixedBill.getRecurrenceType()) {
                    log.error("m=validationDays error=DayOfWeekInvalid RecorrenceType={}", fixedBill.getRecurrenceType());
                    throw new RuntimeException("Dia da semana inválido");
                } else if (MONTHLY == fixedBill.getRecurrenceType()) {
                    log.error("m=validationDays error=DayOfMonthInvalid RecorrenceType={}", fixedBill.getRecurrenceType());
                    throw new RuntimeException("Dia do mês inválido");
                } else {
                    log.error("m=validationDays error=DayOfYearInvalid RecorrenceType={}", fixedBill.getRecurrenceType());
                    throw new RuntimeException("Dia do ano inválido");
                }
            }
        }
    }

    private void validationMandatoryFields(final FixedBill fixedBill) {
        if (fixedBill.getOperationType() == null ||
                fixedBill.getDescription() == null ||
                fixedBill.getAmount() == null ||
                fixedBill.getRecurrenceType() == null ||
                fixedBill.getDays() == null ||
                fixedBill.getAmount().compareTo(BigDecimal.ZERO) == 0 ||
                fixedBill.getDays().isEmpty() ||
                fixedBill.getDescription().isBlank()
        ) {
            log.error("m=validationMandatoryFields error=FieldsMandatoryNull fixedBill={}", fixedBill);
            throw new RuntimeException();
        }
    }

    private List<Integer> defineEnableDays(final RecurrenceType recurrenceType) {
        List<Integer> numberList = new ArrayList<>();
        for (int x = recurrenceType.getInitialDay(); x <= recurrenceType.getEndDay(); x++) {
            numberList.add(x);
        }
        return numberList;
    }

    @Override
    public List<FixedBill> findByNextDueDate(final LocalDate nextDueDate) {
        log.info("m=findByNextDueDate nextDueDate={}", nextDueDate);
        final List<FixedBillEntity> fixedBillEntityList =
                fixedBillRepository.findAllByNextDueDateAndStatusAndFlagActive(nextDueDate, ACTIVE, TRUE);
        return fixedBillMapper.toModel(fixedBillEntityList);
    }

    @Override
    public List<FixedBill> findAllByNextDueDateBetween(final LocalDate initialDate,
                                                       final LocalDate endDate) {
        log.info("m=findAllByNextDueDateBetween initialDate={} endDate={}", initialDate, endDate);
        final List<FixedBillEntity> fixedBillEntityList =
                fixedBillRepository.findAllByFlagActiveTrueAndStatusAndNextDueDateBetween(ACTIVE, initialDate, endDate);
        return fixedBillMapper.toModel(fixedBillEntityList);
    }
}
