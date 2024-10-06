package com.bts.personalbudget.core.domain.service;

import com.bts.personalbudget.controller.fixedbill.FixedBillEntity;
import com.bts.personalbudget.controller.fixedbill.FixedBillRepository;
import com.bts.personalbudget.core.domain.entity.CalendarFixedBillEntity;
import com.bts.personalbudget.core.domain.enumerator.FixedBillStatus;
import com.bts.personalbudget.core.domain.enumerator.RecurrenceType;
import com.bts.personalbudget.core.domain.model.FixedBill;
import com.bts.personalbudget.mapper.FixedBillMapper;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@RequiredArgsConstructor
@Service
public class FixedBillService {

    private final FixedBillRepository fixedBillRepository;
    private final FixedBillMapper fixedBillMapper;

    public void save(FixedBill fixedBill) {

        if (fixedBill.getOperationType() == null ||
                fixedBill.getDescription() == null ||
                fixedBill.getAmount() == null ||
                fixedBill.getRecurrenceType() == null ||
                fixedBill.getDays() == null ||
                fixedBill.getAmount().compareTo(BigDecimal.ZERO) < 1 ||
                fixedBill.getDays().isEmpty() ||
                fixedBill.getDescription().isBlank()
        ) {
            throw new RuntimeException();
        }

        FixedBillEntity fixedBillEntity = fixedBillMapper.toEntity(fixedBill);
        fixedBillEntity.setCode(UUID.randomUUID());
        fixedBillEntity.setFlagActive(true);
        fixedBillEntity.setStatus(FixedBillStatus.ACTIVE);

        List<Integer> days = fixedBill.getDays();

        List<Integer> enabledDays = defineEnableDays(fixedBill.getRecurrenceType());
        for (Integer day : days) {
            boolean isValid = false;
            for (Integer enabledDay : enabledDays) {
                if (day.equals(enabledDay)) {
                    isValid = true;
                    break;
                }
            }
            if (!isValid) {
                if (RecurrenceType.WEEKLY == fixedBill.getRecurrenceType()) {
                    throw new RuntimeException("Dia da semana inválido");
                } else if (RecurrenceType.MONTHLY == fixedBill.getRecurrenceType()) {
                    throw new RuntimeException("Dia do mês inválido");
                } else {
                    throw new RuntimeException("Dia do ano inválido");
                }
            }
        }

        List<CalendarFixedBillEntity> calendarFixedBillEntityList = new ArrayList<>();

        for (Integer day : days) {
            CalendarFixedBillEntity calendarFixedBillEntity = new CalendarFixedBillEntity();
            calendarFixedBillEntity.setDayLaunch(day);
            calendarFixedBillEntity.setFlgLeapYear(fixedBill.getFlgLeapYear());
            calendarFixedBillEntity.setFlgActive(Boolean.TRUE);
            calendarFixedBillEntity.setFixedBill(fixedBillEntity);
            calendarFixedBillEntityList.add(calendarFixedBillEntity);
        }

        fixedBillEntity.setCalendarFixedBillEntityList(calendarFixedBillEntityList);

        fixedBillRepository.save(fixedBillEntity);

    }

    private List<Integer> createList(int first, int last) {
        List<Integer> numberList = new ArrayList<>();
        for (int x = first; x <= last; x++) {
            numberList.add(x);
        }
        return numberList;
    }

    private List<Integer> defineEnableDays(RecurrenceType recurrenceType) {
        return switch (recurrenceType) {
            case WEEKLY -> createList(1, 7);
            case MONTHLY -> createList(1, 31);
            case YEARLY -> createList(1, 365);
        };
    }

}
