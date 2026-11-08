package com.bts.personalbudget.core.domain.service.installmentbill;

import com.bts.personalbudget.core.domain.enumerator.FinancialMovementStatus;
import com.bts.personalbudget.core.domain.enumerator.InstallmentBillStatus;
import com.bts.personalbudget.core.domain.enumerator.OperationType;
import com.bts.personalbudget.core.domain.exception.InstallmentBillAlreadyDeletedException;
import com.bts.personalbudget.core.domain.model.FinancialMovement;
import com.bts.personalbudget.core.domain.service.FinancialMovementService;
import com.bts.personalbudget.core.domain.service.category.CategoryService;
import com.bts.personalbudget.exception.InvalidFieldsException;
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
import java.util.Map;
import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
@Service
public class InstallmentBillService {

    private final InstallmentBillRepository repository;
    private final FinancialMovementService financialMovementService;
    private final FinancialMovementMapper financialMovementMapper;
    private final Clock clock;
    private final CategoryService categoryService;

    /**
     * Upper bound on how many installments a single catch-up run will post for one account. See
     * FixedBillService.CATCH_UP_SAFETY_LIMIT for the same guard on fixed bills.
     */
    static final int CATCH_UP_SAFETY_LIMIT = 400;

    /**
     * Creates the installment bill and, for a purchase whose first installment is already due
     * (purchase_date 30+ days ago), posts the overdue installments right away in this same
     * transaction - the same thing the recovery job would do at the next midnight run.
     *
     * @return the bill as stored after that posting, so installment_count and
     * next_installment_date already reflect the installments posted on save
     */
    @Transactional
    public InstallmentBill create(final InstallmentBill installmentBill) {
        log.info("m=create installmentBill={}", installmentBill);
        if (installmentBill.getCategoryCode() != null) {
            categoryService.validateForUse(installmentBill.getCategoryCode(), installmentBill.getOperationType());
        }
        financialMovementService.lockPayCycleMovementMutation();
        repository.save(installmentBill);
        final LocalDate today = LocalDate.now(clock);
        final LocalDate nextInstallmentDate = installmentBill.getNextInstallmentDate();
        if (nextInstallmentDate != null && !nextInstallmentDate.isAfter(today)) {
            log.info("m=create, msg=InstallmentDueOnSave, code={}, nextInstallmentDate={}, today={}",
                    installmentBill.getCode(), nextInstallmentDate, today);
            postDueInstallments(installmentBill.getCode(), today);
        }
        return repository.findByCode(installmentBill.getCode());
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

    @Transactional
    public InstallmentBill update(final UUID code,
                                  final OperationType operationType,
                                  final String description,
                                  final BigDecimal amount,
                                  final LocalDate purchaseDate,
                                  final Integer installmentTotal,
                                  final UUID categoryCode) {
        log.info("m=update code={} operationType={} description={} amount={} purchaseDate={} installmentTotal={} categoryCode={}",
                code, operationType, description, amount, purchaseDate, installmentTotal, categoryCode);
        financialMovementService.lockPayCycleMovementMutation();
        final InstallmentBill updateInstallmentBill = repository.findForUpdateByCode(code);
        if (installmentTotal != null && installmentTotal < updateInstallmentBill.getInstallmentCount()) {
            throw new InvalidFieldsException("Campos inválidos", InstallmentBillService.class,
                    Map.of("installmentTotal", "não pode ser menor que o número de parcelas já lançadas"));
        }
        validateCategoryOnUpdate(updateInstallmentBill, operationType, categoryCode);
        updateInstallmentBill.update(operationType, description, amount, purchaseDate, installmentTotal);
        if (categoryCode != null) {
            updateInstallmentBill.setCategoryCode(categoryCode);
        }
        final InstallmentBill updated = repository.update(updateInstallmentBill);
        if (updated.getStatus() == InstallmentBillStatus.DONE) {
            return updated;
        }
        final LocalDate today = LocalDate.now(clock);
        if (updated.getNextInstallmentDate() != null && !updated.getNextInstallmentDate().isAfter(today)) {
            postDueInstallments(code, today);
            return repository.findByCode(code);
        }
        return updated;
    }

    /**
     * The category has to fit the operation type the bill ends up with, so changing only the
     * operation type is checked against the stored category too. Runs only when one of the two
     * actually changes: a bill whose category was inactivated later can still be edited.
     */
    private void validateCategoryOnUpdate(final InstallmentBill current,
                                          final OperationType operationType,
                                          final UUID categoryCode) {
        final OperationType finalOperationType = operationType != null ? operationType : current.getOperationType();
        final UUID finalCategoryCode = categoryCode != null ? categoryCode : current.getCategoryCode();
        final boolean categoryChanged = categoryCode != null && !categoryCode.equals(current.getCategoryCode());
        final boolean operationTypeChanged = finalOperationType != current.getOperationType();
        if (finalCategoryCode != null && (categoryChanged || operationTypeChanged)) {
            categoryService.validateForUse(finalCategoryCode, finalOperationType);
        }
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
        financialMovementService.lockPayCycleMovementMutation();
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