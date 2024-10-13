package com.bts.personalbudget.core.domain.service;

import com.bts.personalbudget.controller.fixedbill.FixedBillRepository;
import com.bts.personalbudget.core.domain.entity.CalendarFixedBillEntity;
import com.bts.personalbudget.core.domain.entity.FixedBillEntity;
import com.bts.personalbudget.core.domain.enumerator.FixedBillStatus;
import com.bts.personalbudget.core.domain.enumerator.RecurrenceType;
import com.bts.personalbudget.core.domain.model.FixedBill;
import com.bts.personalbudget.mapper.FixedBillMapper;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@RequiredArgsConstructor
@Service
public class FixedBillService {

    private final FixedBillRepository fixedBillRepository;
    private final FixedBillMapper fixedBillMapper;

    public void save(FixedBill fixedBill) {
        log.info("m=save fixedBill={}", fixedBill);
        validationMandatoryFields(fixedBill);
        validationDays(fixedBill);
        FixedBillEntity fixedBillEntity = buildFixedBillEntity(fixedBill);
        List<CalendarFixedBillEntity> calendarFixedBillEntityList = buildCalendarFixedBillEntityList(fixedBill, fixedBillEntity);
        fixedBillEntity.setCalendarFixedBillEntityList(calendarFixedBillEntityList);
        fixedBillRepository.save(fixedBillEntity);
    }

    private List<CalendarFixedBillEntity> buildCalendarFixedBillEntityList(FixedBill fixedBill, FixedBillEntity fixedBillEntity) {
        log.info("m=buildCalendarFixedBillEntityList fixedBillDays={} fixedBillCode={}", fixedBill.getDays(), fixedBillEntity.getCode());
        List<CalendarFixedBillEntity> calendarFixedBillEntityList = new ArrayList<>();

        for (Integer day : fixedBill.getDays()) {
            CalendarFixedBillEntity calendarFixedBillEntity = new CalendarFixedBillEntity();
            calendarFixedBillEntity.setDayLaunch(day);
            calendarFixedBillEntity.setFlgLeapYear(fixedBill.getFlgLeapYear());
            calendarFixedBillEntity.setFlgActive(Boolean.TRUE);
            calendarFixedBillEntity.setFixedBill(fixedBillEntity);
            calendarFixedBillEntityList.add(calendarFixedBillEntity);
        }
        return calendarFixedBillEntityList;
    }

    private FixedBillEntity buildFixedBillEntity(FixedBill fixedBill) {
        FixedBillEntity fixedBillEntity = fixedBillMapper.toEntity(fixedBill);
        fixedBillEntity.setCode(UUID.randomUUID());
        fixedBillEntity.setFlagActive(true);
        fixedBillEntity.setStatus(FixedBillStatus.ACTIVE);
        LocalDate paramDate = defineNextDueDate(fixedBill, LocalDate.now()).orElse(null);
        fixedBillEntity.setNextDueDate(paramDate);
        return fixedBillEntity;
    }

    @Transactional
    public void updateNextDueDate(final FixedBill fixedBill) {
        final Optional<LocalDate> nextDueDate = defineNextDueDate(fixedBill, fixedBill.getNextDueDate());
        final Optional<FixedBillEntity> fixedBillEntityOptional = fixedBillRepository.findByCode(fixedBill.getCode());
        final FixedBillEntity fixedBillEntity = fixedBillEntityOptional.orElseThrow();
        fixedBillEntity.setNextDueDate(nextDueDate.orElseThrow());
    }

    public Optional<LocalDate> defineNextDueDate(FixedBill fixedBill, LocalDate baseData) {

        switch (fixedBill.getRecurrenceType()) {
            case WEEKLY -> {
                List<Integer> dueWeeklyDays = fixedBill.getDays();
                DayOfWeek dayOfWeekNow = baseData.getDayOfWeek();

                LocalDate nexDueDate = LocalDate.now();


                return Optional.of(nexDueDate);
            }
        }

        return Optional.empty();
    }

    private void validationDays(FixedBill fixedBill) {
        log.info("m=validationDays fixedBillDays={}", fixedBill.getDays());
        List<Integer> enabledDays = defineEnableDays(fixedBill.getRecurrenceType());
        for (Integer day : fixedBill.getDays()) {
            boolean isValid = false;
            for (Integer enabledDay : enabledDays) {
                if (day.equals(enabledDay)) {
                    isValid = true;
                    break;
                }
            }
            if (!isValid) {
                if (RecurrenceType.WEEKLY == fixedBill.getRecurrenceType()) {
                    log.error("m=validationDays error=DayOfWeekInvalid RecorrenceType={}", fixedBill.getRecurrenceType());
                    throw new RuntimeException("Dia da semana inválido");
                } else if (RecurrenceType.MONTHLY == fixedBill.getRecurrenceType()) {
                    log.error("m=validationDays error=DayOfMonthInvalid RecorrenceType={}", fixedBill.getRecurrenceType());
                    throw new RuntimeException("Dia do mês inválido");
                } else {
                    log.error("m=validationDays error=DayOfYearInvalid RecorrenceType={}", fixedBill.getRecurrenceType());
                    throw new RuntimeException("Dia do ano inválido");
                }
            }
        }
    }

    private void validationMandatoryFields(FixedBill fixedBill) {
        if (fixedBill.getOperationType() == null ||
                fixedBill.getDescription() == null ||
                fixedBill.getAmount() == null ||
                fixedBill.getRecurrenceType() == null ||
                fixedBill.getDays() == null ||
                fixedBill.getAmount().compareTo(BigDecimal.ZERO) < 1 ||
                fixedBill.getDays().isEmpty() ||
                fixedBill.getDescription().isBlank()
        ) {
            log.error("m=validationMandatoryFields error=FieldsMandatoryNull fixedBill={}", fixedBill);
            throw new RuntimeException();
        }
    }

    private List<Integer> defineEnableDays(RecurrenceType recurrenceType) {
        List<Integer> numberList = new ArrayList<>();
        for (int x = recurrenceType.getInitialDay(); x <= recurrenceType.getEndDay(); x++) {
            numberList.add(x);
        }
        return numberList;
    }

    public List<FixedBill> findByNextDueDate(final LocalDate nextDueDate) {
        log.info("m=findByNextDueDate nextDueDate={}", nextDueDate);
        final List<FixedBillEntity> fixedBillEntityList =
                fixedBillRepository.findAllByNextDueDateAndStatusAndFlagActive(nextDueDate, FixedBillStatus.ACTIVE, Boolean.TRUE);
        return fixedBillMapper.toModel(fixedBillEntityList);
    }
}
