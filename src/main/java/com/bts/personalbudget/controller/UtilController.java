package com.bts.personalbudget.controller;

import com.bts.personalbudget.core.domain.service.balance.BalanceService;
import com.bts.personalbudget.core.domain.service.balance.DailyBalance;
import com.bts.personalbudget.core.domain.service.installmentbill.InstallmentBill;
import com.bts.personalbudget.core.domain.service.installmentbill.InstallmentBillService;
import com.bts.personalbudget.core.domain.service.recurrencebill.RecurrenceBillService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

@Profile("local")
@Slf4j
@RequiredArgsConstructor
@RequestMapping("/util")
@RestController
public class UtilController {

    private final InstallmentBillService installmentBillService;
    private final RecurrenceBillService recurrenceBillService;
    private final BalanceService balanceService;

    @GetMapping("/installment_bill/{date}")
    public ResponseEntity<List<InstallmentBill>> findInstallmentBill(@PathVariable LocalDate date) {
        log.info("m=findInstallmentBill date={}", date);
        return ResponseEntity.ok(installmentBillService.findByNextInstallmentDate(date));
    }

    @PostMapping("/recurrence_bill/{date}")
    public ResponseEntity<List<InstallmentBill>> postRecurringBills(@PathVariable LocalDate date) {
        log.info("m=postRecurringBills date={}", date);
        recurrenceBillService.postRecurringBills(date);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/balance")
    public Set<DailyBalance> balance(@RequestParam LocalDate initialDate,
                                     @RequestParam LocalDate endDate) {
        return balanceService.findDailyBalanceBetween(initialDate, endDate);

    }

}
