package com.bts.personalbudget.core.domain.service.installmentbill;

import com.bts.personalbudget.core.domain.entity.InstallmentBillEntity;
import com.bts.personalbudget.core.domain.enumerator.InstallmentBillStatus;
import com.bts.personalbudget.core.domain.enumerator.OperationType;
import com.bts.personalbudget.core.domain.exception.InstallmentBillAlreadyDeletedException;
import com.bts.personalbudget.mapper.InstallmentBillMapper;
import com.bts.personalbudget.repository.InstallmentBillJpaRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
@Component
public class InstallmentBillRepository {

    private final InstallmentBillJpaRepository jpaRepository;
    private final InstallmentBillMapper mapper;

    private static final Boolean FLAG_ACTIVE = Boolean.TRUE;

    @Transactional
    public void save(final InstallmentBill installmentBill) {
        log.info("m=save installmentBill={}", installmentBill);
        final InstallmentBillEntity entity = mapper.toEntity(installmentBill);
        entity.setFlagActive(Boolean.TRUE);
        jpaRepository.save(entity);
    }

    @Transactional(readOnly = true)
    public Page<InstallmentBill> findByFilters(final String description,
                                               final List<OperationType> operationTypes,
                                               final List<InstallmentBillStatus> statuses,
                                               final Pageable pageable) {
        log.info("m=findByFilters description={} operationTypes={} statuses={} pageable={}",
                description, operationTypes, statuses, pageable);
        final Page<InstallmentBillEntity> entityPage = jpaRepository.findByFilters(
                description,
                operationTypes,
                statuses,
                FLAG_ACTIVE,
                pageable
        );
        return entityPage.map(mapper::toModel);
    }

    @Transactional(readOnly = true)
    public InstallmentBill findByCode(final UUID code) {
        log.info("m=findByCode code={}", code);
        return mapper.toModel(findEntityByCode(code));
    }

    @Transactional(readOnly = true)
    public List<InstallmentBill> findAllByNextInstallmentDateAndStatus(final LocalDate nextInstallmentDate,
                                                                       final InstallmentBillStatus installmentBillStatus) {
        log.info("m=findByNextInstallmentDate nextInstallmentDate={} status={}", nextInstallmentDate, installmentBillStatus);

        return mapper.toModel(jpaRepository.findAllByNextInstallmentDateAndStatusAndFlagActive(
                nextInstallmentDate,
                installmentBillStatus,
                FLAG_ACTIVE));
    }

    @Transactional(readOnly = true)
    public List<InstallmentBill> findAllByNextInstallmentDateLessThanEqualAndStatus(final LocalDate date,
                                                                                    final InstallmentBillStatus installmentBillStatus) {
        log.info("m=findAllByNextInstallmentDateLessThanEqualAndStatus date={} status={}", date, installmentBillStatus);
        return mapper.toModel(jpaRepository.findAllByNextInstallmentDateLessThanEqualAndStatusAndFlagActive(
                date,
                installmentBillStatus,
                FLAG_ACTIVE));
    }

    @Transactional
    public InstallmentBill update(final InstallmentBill installmentBill) {
        log.info("m=update installmentBill={}", installmentBill);
        final InstallmentBillEntity entity = findEntityByCode(installmentBill.getCode());
        entity.setDescription(installmentBill.getDescription());
        entity.setAmount(installmentBill.getAmount());
        entity.setInstallmentTotal(installmentBill.getInstallmentTotal());
        entity.setOperationType(installmentBill.getOperationType());
        entity.setPurchaseDate(installmentBill.getPurchaseDate());
        entity.setInstallmentCount(installmentBill.getInstallmentCount());
        entity.setLastInstallmentDate(installmentBill.getLastInstallmentDate());
        entity.setNextInstallmentDate(installmentBill.getNextInstallmentDate());
        // Fixed in passing: status was never copied here, so InstallmentBill.conclude() (called
        // from updateInstallment() once the last installment posts) never reached the database -
        // the bill stayed PENDING forever even after nextInstallmentDate went null.
        entity.setStatus(installmentBill.getStatus());
        return mapper.toModel(entity);
    }

    /**
     * Locks the row for the duration of the transaction - see
     * InstallmentBillJpaRepository.findForUpdateByCode.
     */
    @Transactional
    public InstallmentBill findForUpdateByCode(final UUID code) {
        log.info("m=findForUpdateByCode code={}", code);
        final InstallmentBillEntity entity = jpaRepository.findForUpdateByCode(code).orElseThrow(() -> {
            log.error("m=findForUpdateByCode code={} error=installmentBill_not_found", code);
            return new EntityNotFoundException("InstallmentBill not found for code " + code);
        });
        return mapper.toModel(entity);
    }

    /**
     * Codes of the pending installment bills with at least one installment due on or before
     * {@code date}. Used by RecurrenceBillService to decide which accounts postDueInstallments
     * needs to process - each one is then handled in its own transaction.
     */
    @Transactional(readOnly = true)
    public List<UUID> findAllCodesByNextInstallmentDateLessThanEqualAndStatus(final LocalDate date,
                                                                               final InstallmentBillStatus installmentBillStatus) {
        log.info("m=findAllCodesByNextInstallmentDateLessThanEqualAndStatus date={} status={}", date, installmentBillStatus);
        return jpaRepository.findAllByNextInstallmentDateLessThanEqualAndStatusAndFlagActive(date, installmentBillStatus, FLAG_ACTIVE)
                .stream()
                .map(InstallmentBillEntity::getCode)
                .toList();
    }

    @Transactional
    public void delete(final UUID code) {
        log.info("m=delete code={}", code);
        final InstallmentBillEntity entity = findEntityByCode(code);
        entity.setFlagActive(Boolean.FALSE);
        jpaRepository.save(entity);
    }

    private InstallmentBillEntity findEntityByCode(final UUID code) {
        final InstallmentBillEntity entity = jpaRepository.findByCode(code).orElseThrow(() -> {
            log.error("m=findEntityByCode code={} error=installmentBill_not_found", code);
            return new EntityNotFoundException("InstallmentBill not found for code " + code);
        });
        if (!entity.getFlagActive()) {
            log.error("m=findEntityByCode code={} msg=installmentBill_already_deleted", code);
            throw new InstallmentBillAlreadyDeletedException(code);
        }
        return entity;
    }
}
