package com.bts.personalbudget.core.domain.service.recurrencebill;

import com.bts.personalbudget.core.domain.service.fixedbill.FixedBillService;
import com.bts.personalbudget.core.domain.service.installmentbill.InstallmentBillService;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Orchestrates the recurrence catch-up: finds which FixedBill/InstallmentBill accounts have an
 * occurrence due on or before the given date, and posts each one in its own REQUIRES_NEW
 * transaction. The actual posting logic lives in FixedBillService.postDueOccurrences and
 * InstallmentBillService.postDueInstallments - keeping it there (rather than here) avoids a
 * circular dependency, since both of those services already have everything they need
 * (FinancialMovementService/Mapper) without depending back on this class.
 *
 * Deliberately not @Transactional at this level: each account gets its own transaction via
 * transactionTemplate, so one account failing (a bad calendar, a locked row that times out)
 * cannot roll back the accounts already posted earlier in the same run, and is caught here so
 * the rest of the batch still runs.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class RecurrenceBillService {

    private final InstallmentBillService installmentBillService;
    private final FixedBillService fixedBillService;
    private final TransactionTemplate transactionTemplate;

    public void postRecurringBills(final LocalDate date) {
        log.info("m=postRecurringBills date={}", date);

        final List<UUID> fixedBillCodes = fixedBillService.findCodesByNextDueDateLessThanEqual(date);
        fixedBillCodes.forEach(code -> postFixedBillSafely(code, date));

        final List<UUID> installmentBillCodes = installmentBillService.findCodesByNextInstallmentDateLessThanEqual(date);
        installmentBillCodes.forEach(code -> postInstallmentBillSafely(code, date));
    }

    private void postFixedBillSafely(final UUID code, final LocalDate date) {
        try {
            transactionTemplate.executeWithoutResult(status -> fixedBillService.postDueOccurrences(code, date));
        } catch (final RuntimeException ex) {
            log.error("m=postFixedBillSafely, msg=FailedToPostFixedBill, code={}, date={}", code, date, ex);
        }
    }

    private void postInstallmentBillSafely(final UUID code, final LocalDate date) {
        try {
            transactionTemplate.executeWithoutResult(status -> installmentBillService.postDueInstallments(code, date));
        } catch (final RuntimeException ex) {
            log.error("m=postInstallmentBillSafely, msg=FailedToPostInstallmentBill, code={}, date={}", code, date, ex);
        }
    }
}
