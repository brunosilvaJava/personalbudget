package com.bts.personalbudget.core.domain.service.recurrencebill;

import com.bts.personalbudget.core.domain.enumerator.FinancialMovementStatus;
import com.bts.personalbudget.core.domain.model.FinancialMovement;
import com.bts.personalbudget.core.domain.model.FixedBill;
import com.bts.personalbudget.core.domain.service.FinancialMovementService;
import com.bts.personalbudget.core.domain.service.FixedBillService;
import com.bts.personalbudget.core.domain.service.installmentbill.InstallmentBill;
import com.bts.personalbudget.core.domain.service.installmentbill.InstallmentBillService;
import java.time.LocalDate;
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
    private final InstallmentBillService installmentBillService;
    private final FixedBillService fixedBillService;

    @Transactional
    public void postRecurringBills(final LocalDate nextDueDate) {
        log.info("m=postRecurringBills nextDueDate={}", nextDueDate);
        postInstallmentBills(nextDueDate);
        postFixedBills(nextDueDate);
    }

    private void postFixedBills(LocalDate nextDueDate) {
        final List<FixedBill> fixedBillList = fixedBillService.findByNextDueDate(nextDueDate);
        log.info("m=postInstallmentBills nextDueDate={} fixedBillList={}", fixedBillList, fixedBillList.size());
        if (fixedBillList.isEmpty()) {
            return;
        }
        final List<FinancialMovement> financialMovementList = buildFinancialMovementListFromFixedBill(fixedBillList);
        financialMovementService.create(financialMovementList);
        fixedBillList.forEach(fixedBillService::updateNextDueDate);
    }

    private List<FinancialMovement> buildFinancialMovementListFromFixedBill(final List<FixedBill> fixedBillList) {
        return fixedBillList.stream().map(fixedBill ->
                new FinancialMovement(
                        fixedBill.getOperationType(),
                        fixedBill.getDescription(),
                        fixedBill.getAmount(),
                        fixedBill.getNextDueDate().atStartOfDay(),
                        fixedBill.getNextDueDate().atStartOfDay(),
                        FinancialMovementStatus.PENDING,
                        fixedBill.getCode()
                )).toList();
    }

    private void postInstallmentBills(LocalDate nextInstallmentDate) {
        final List<InstallmentBill> installmentBillList = installmentBillService.findByNextInstallmentDate(nextInstallmentDate);
        log.info("m=postInstallmentBills nextInstallmentDate={} installmentBillList={}", nextInstallmentDate, installmentBillList.size());
        if (installmentBillList.isEmpty()) {
            return;
        }
        final List<FinancialMovement> financialMovementList = buildFinancialMovementList(installmentBillList);
        financialMovementService.create(financialMovementList);
        installmentBillList.forEach(installmentBillService::updateNextInstallmentDate);
    }

    private List<FinancialMovement> buildFinancialMovementList(final List<InstallmentBill> installmentBillList) {
        return installmentBillList.stream().map(installmentBill ->
                new FinancialMovement(
                        installmentBill.getOperationType(),
                        installmentBill.getDescription(),
                        installmentBill.getAmount(),
                        installmentBill.getPurchaseDate().atStartOfDay(),
                        installmentBill.getNextInstallmentDate().atStartOfDay(),
                        FinancialMovementStatus.PENDING,
                        installmentBill.getCode()
                )).toList();
    }

}
