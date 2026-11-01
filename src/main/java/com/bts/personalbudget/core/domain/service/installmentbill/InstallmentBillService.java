package com.bts.personalbudget.core.domain.service.installmentbill;

import com.bts.personalbudget.core.domain.enumerator.FinancialMovementStatus;
import com.bts.personalbudget.core.domain.enumerator.InstallmentBillStatus;
import com.bts.personalbudget.core.domain.enumerator.OperationType;
import com.bts.personalbudget.core.domain.exception.InstallmentBillAlreadyDeletedException;
import com.bts.personalbudget.core.domain.model.FinancialMovement;
import com.bts.personalbudget.core.domain.service.FinancialMovementService;
import com.bts.personalbudget.mapper.FinancialMovementMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
@Service
public class InstallmentBillService {

    private final InstallmentBillRepository repository;
    private final FinancialMovementService financialMovementService;
    private final FinancialMovementMapper financialMovementMapper;
    private final Clock clock;

    /**
     * Upper bound on how many installments a single catch-up run will post for one account. See
     * FixedBillService.CATCH_UP_SAFETY_LIMIT for the same guard on fixed bills.
     */
    static final int CATCH_UP_SAFETY_LIMIT = 400;

    /**
     * Creates the installment bill and, for a purchase whose first installment is already due
     * (purchase_date 30+ days ago), posts the overdue installments right away in this same
     * transaction - the same thing the recovery job would do at the next midnight run.
     */
    @Transactional
    public void create(final InstallmentBill installmentBill) {
        log.info("m=create installmentBill={}", installmentBill);
        repository.save(installmentBill);
        final LocalDate today = LocalDate.now(clock);
        final LocalDate nextInstallmentDate = installmentBill.getNextInstallmentDate();
        if (nextInstallmentDate != null && !nextInstallmentDate.isAfter(today)) {
            log.info("m=create, msg=InstallmentDueOnSave, code={}, nextInstallmentDate={}, today={}",
                    installmentBill.getCode(), nextInstallmentDate, today);
            postDueInstallments(installmentBill.getCode(), today);
        }
    }

    public Page<InstallmentBill> findAll(
            final String description,
            final List<OperationType> operationTypes,
            final List<InstallmentBillStatus> statuses,
            final Pageable pageable) {

        log.info("m=findAll, description={}, operationTypes={}, statuses={}, pageable={}",
                description, operationTypes, statuses, pageable);

        return repository.findByFilters(description, operationTypes, statuses, pageable);
    }

    public InstallmentBill findByCode(final UUID code) {
        log.info("m=findByCode code={}", code);
        return repository.findByCode(code);
    }

    public List<InstallmentBill> findByNextInstallmentDate(final LocalDate nextInstallmentDate) {
        log.info("m=findByNextInstallmentDate date={}", nextInstallmentDate);
        return repository.findAllByNextInstallmentDateAndStatus(nextInstallmentDate, InstallmentBillStatus.PENDING);
    }

    /**
     * Pending installment bills with at least one installment not posted yet on or before
     * {@code endDate}. Not BETWEEN a start and end: a bill whose next installment is before a
     * period can still have installments inside it.
     */
    public List<InstallmentBill> findAllByNextInstallmentDateLessThanEqual(final LocalDate endDate) {
        log.info("m=findAllByNextInstallmentDateLessThanEqual endDate={}", endDate);
        return repository.findAllByNextInstallmentDateLessThanEqualAndStatus(endDate, InstallmentBillStatus.PENDING);
    }

    public InstallmentBill update(final UUID code,
                                  final OperationType operationType,
                                  final String description,
                                  final BigDecimal amount,
                                  final LocalDate purchaseDate,
                                  final Integer installmentTotal) {
        log.info("m=update code={} operationType={} description={} amount={} purchaseDate={} installmentTotal={}",
                code, operationType, description, amount, purchaseDate, installmentTotal);
        final InstallmentBill updateInstallmentBill = findByCode(code);
        updateInstallmentBill.update(operationType, description, amount, purchaseDate, installmentTotal);
        return repository.update(updateInstallmentBill);
    }

    public void delete(final UUID code) {
        log.info("m=delete code={}", code);
        try {
            repository.delete(code);
        } catch (InstallmentBillAlreadyDeletedException ex) {
            log.info("m=delete code={} msg=installmentBill_already_deleted", code);
        }
    }

    @Transactional
    public void updateNextInstallmentDate(final InstallmentBill installmentBill) {
        log.info("m=updateNextInstallmentDate code={}", installmentBill.getCode());
        installmentBill.updateInstallment();
        repository.update(installmentBill);
    }

    /**
     * Codes of the pending installment bills with at least one installment due on or before
     * {@code date}. Used by RecurrenceBillService to decide which accounts postDueInstallments
     * needs to process - each one is then handled in its own transaction.
     */
    public List<UUID> findCodesByNextInstallmentDateLessThanEqual(final LocalDate date) {
        log.info("m=findCodesByNextInstallmentDateLessThanEqual date={}", date);
        return repository.findAllCodesByNextInstallmentDateLessThanEqualAndStatus(date, InstallmentBillStatus.PENDING);
    }

    /**
     * Posts every installment of this bill that fell due on or before {@code today} and was not
     * posted yet, advancing installment_count/next_installment_date one step at a time (the bill
     * concludes itself once the last installment is caught up - see
     * InstallmentBill.updateInstallment()). Safe to call from the recovery job after an outage, or
     * right after save/reactivation to post today's own installment immediately - either way,
     * posting is idempotent (see FinancialMovementService.existsByRecurrenceBillCodeAndOccurrenceDate).
     */
    @Transactional
    public void postDueInstallments(final UUID code, final LocalDate today) {
        log.info("m=postDueInstallments, code={}, today={}", code, today);
        InstallmentBill installmentBill = repository.findForUpdateByCode(code);

        int iterations = 0;
        while (installmentBill.getNextInstallmentDate() != null && !installmentBill.getNextInstallmentDate().isAfter(today)) {
            if (iterations >= CATCH_UP_SAFETY_LIMIT) {
                log.warn("m=postDueInstallments, msg=SafetyLimitReached, code={}, nextInstallmentDate={}, limit={}",
                        code, installmentBill.getNextInstallmentDate(), CATCH_UP_SAFETY_LIMIT);
                break;
            }
            iterations++;

            postInstallmentIfNotPosted(installmentBill);
            installmentBill.updateInstallment();
            installmentBill = repository.update(installmentBill);
        }
    }

    private void postInstallmentIfNotPosted(final InstallmentBill installmentBill) {
        final LocalDate occurrenceDate = installmentBill.getNextInstallmentDate();
        if (financialMovementService.existsByRecurrenceBillCodeAndOccurrenceDate(installmentBill.getCode(), occurrenceDate)) {
            log.info("m=postInstallmentIfNotPosted, msg=OccurrenceAlreadyPosted, code={}, occurrenceDate={}",
                    installmentBill.getCode(), occurrenceDate);
            return;
        }
        final FinancialMovement financialMovement = financialMovementMapper.toFinancialMovement(installmentBill, FinancialMovementStatus.PENDING);
        financialMovementService.create(financialMovement);
    }
}