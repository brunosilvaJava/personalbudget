package com.bts.personalbudget.core.domain.service.installmentbill;

import com.bts.personalbudget.core.domain.entity.InstallmentBillEntity;
import com.bts.personalbudget.core.domain.enumerator.InstallmentBillStatus;
import com.bts.personalbudget.core.domain.exception.InstallmentBillAlreadyDeletedException;
import com.bts.personalbudget.mapper.InstallmentBillMapper;
import com.bts.personalbudget.repository.InstallmentBillJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    public List<InstallmentBill> findAll() {
        log.info("m=findAll");
        return mapper.toModel(jpaRepository.findAllByFlagActive(Boolean.TRUE));
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
    public List<InstallmentBill> findAllByNextInstallmentDateBetweenAndStatus(final LocalDate initialDate,
                                                                              final LocalDate endDate,
                                                                              final InstallmentBillStatus installmentBillStatus) {
        log.info("m=findAllByStatusAndNextInstallmentDateBetween status={} initialDate={} endDate={}",
                installmentBillStatus, initialDate, endDate);

        final List<InstallmentBillEntity> installmentBillEntityList =
                jpaRepository.findAllByNextInstallmentDateBetweenAndStatusAndFlagActive(
                        initialDate,
                        endDate,
                        installmentBillStatus,
                        FLAG_ACTIVE);

        return mapper.toModel(installmentBillEntityList);
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
        return mapper.toModel(entity);
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
            return new RuntimeException("InstallmentBill NotFoundException by code=" + code);
        });
        if (!entity.getFlagActive()) {
            log.error("m=findEntityByCode code={} msg=installmentBill_already_deleted", code);
            throw new InstallmentBillAlreadyDeletedException(code);
        }
        return entity;
    }
}
