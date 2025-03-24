package com.bts.personalbudget.core.domain.service.recurrencebill;

import com.bts.personalbudget.core.domain.enumerator.FinancialMovementStatus;
import com.bts.personalbudget.core.domain.model.FinancialMovement;
import com.bts.personalbudget.core.domain.service.FinancialMovementService;
import com.bts.personalbudget.core.domain.service.fixedbill.FixedBill;
import com.bts.personalbudget.core.domain.service.fixedbill.FixedBillService;
import com.bts.personalbudget.core.domain.service.installmentbill.InstallmentBill;
import com.bts.personalbudget.core.domain.service.installmentbill.InstallmentBillService;
import com.bts.personalbudget.mapper.FinancialMovementMapper;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@RequiredArgsConstructor
@Service
public class RecurrenceBillService {

    private final FinancialMovementService financialMovementService;
    private final FinancialMovementMapper financialMovementMapper;
    private final InstallmentBillService installmentBillService;
    private final FixedBillService fixedBillService;

    @Transactional
    public void postRecurringBills(final LocalDate nextDueDate) {
        log.info("m=postRecurringBills nextDueDate={}", nextDueDate);
        final List<FinancialMovement> financialMovements = new ArrayList<>();

        final List<FixedBill> fixedBillList = fixedBillService.findByNextDueDate(nextDueDate);
        financialMovements.addAll(financialMovementMapper.toFinancialMovementListFromFixedBill(fixedBillList, FinancialMovementStatus.PENDING));

        final List<InstallmentBill> installmentBillList = installmentBillService.findByNextInstallmentDate(nextDueDate);
        financialMovements.addAll(financialMovementMapper.toFinancialMovementList(installmentBillList, FinancialMovementStatus.PENDING));

        financialMovementService.create(financialMovements);

        fixedBillList.forEach(fixedBill -> fixedBillService.updateNextDueDate(fixedBill, nextDueDate.plusDays(1)));
        installmentBillList.forEach(installmentBillService::updateNextInstallmentDate);
    }


}
