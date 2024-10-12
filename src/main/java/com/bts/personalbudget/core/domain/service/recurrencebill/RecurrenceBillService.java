package com.bts.personalbudget.core.domain.service.recurrencebill;

import com.bts.personalbudget.core.domain.enumerator.FinancialMovementStatus;
import com.bts.personalbudget.core.domain.model.FinancialMovement;
import com.bts.personalbudget.core.domain.service.FinancialMovementService;
import com.bts.personalbudget.core.domain.service.installmentbill.InstallmentBill;
import com.bts.personalbudget.core.domain.service.installmentbill.InstallmentBillService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Service
public class RecurrenceBillService {

    private final InstallmentBillService installmentBillService;
    private final FinancialMovementService financialMovementService;

    public void postRecurringBills(final LocalDate nextInstallmentDate) {
        log.info("m=postRecurringBills nextInstallmentDate={}", nextInstallmentDate);
        final List<InstallmentBill> installmentBillList = installmentBillService.findByNextInstallmentDate(nextInstallmentDate);
        final List<FinancialMovement> financialMovementList = buildFinancialMovementList(installmentBillList);
        financialMovementService.create(financialMovementList);

    }

    private List<FinancialMovement> buildFinancialMovementList(final List<InstallmentBill> installmentBillList) {
        return installmentBillList.stream().map(installmentBill ->
                new FinancialMovement(
                        installmentBill.getOperationType(),
                        installmentBill.getDescription(),
                        installmentBill.getAmount(),
                        installmentBill.getNextInstallmentDate().atStartOfDay(),
                        installmentBill.getNextInstallmentDate().atStartOfDay(),
                        FinancialMovementStatus.PENDING,
                        installmentBill.getCode()
                )).toList();
    }

}
