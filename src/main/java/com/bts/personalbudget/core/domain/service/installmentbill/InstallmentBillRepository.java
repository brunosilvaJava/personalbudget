package com.bts.personalbudget.core.domain.service.installmentbill;

import com.bts.personalbudget.core.domain.entity.InstallmentBillEntity;
import com.bts.personalbudget.core.domain.exception.InstallmentBillAlreadyDeletedException;
import com.bts.personalbudget.core.domain.repository.InstallmentBillJpaRepository;
import com.bts.personalbudget.mapper.InstallmentBillMapper;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@RequiredArgsConstructor
@Component
public class InstallmentBillRepository {

    private final InstallmentBillJpaRepository jpaRepository;
    private final InstallmentBillMapper mapper;

    @Transactional
    public void save(final InstallmentBill installmentBill) {
        log.info("m=save installmentBill={}", installmentBill);
        jpaRepository.save(mapper.toEntity(installmentBill));
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

    @Transactional
    public InstallmentBill update(final InstallmentBill installmentBill) {
        log.info("m=update installmentBill={}", installmentBill);
        final InstallmentBillEntity entity = mapper.toEntity(installmentBill, installmentBill.getId());
        final InstallmentBillEntity installmentBillEntity = jpaRepository.save(entity);
        return mapper.toModel(installmentBillEntity);
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
