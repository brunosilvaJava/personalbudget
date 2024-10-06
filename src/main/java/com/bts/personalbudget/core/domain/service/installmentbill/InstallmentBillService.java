package com.bts.personalbudget.core.domain.service.installmentbill;

import com.bts.personalbudget.core.domain.entity.InstallmentBillEntity;
import com.bts.personalbudget.core.domain.enumerator.OperationType;
import com.bts.personalbudget.core.domain.repository.InstallmentBillRepository;
import com.bts.personalbudget.mapper.InstallmentBillMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@RequiredArgsConstructor
@Service
public class InstallmentBillService {

    private final InstallmentBillRepository installmentBillRepository;
    private final InstallmentBillMapper mapper;

    @Transactional
    public void create(final InstallmentBill installmentBill) {
        log.info("m=create installmentBill={}", installmentBill);
        installmentBillRepository.save(mapper.toEntity(installmentBill));
    }

    public List<InstallmentBill> findAll() {
        log.info("m=findAll");
        return mapper.toModel(installmentBillRepository.findAll());
    }

    @Transactional(readOnly = true)
    public InstallmentBill findByCode(final UUID code) {
        log.info("m=findByCode code={}", code);
        return mapper.toModel(findEntityByCode(code));
    }

    @Transactional
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
        installmentBillRepository.save(mapper.toEntity(updateInstallmentBill, updateInstallmentBill.getId()));
        return updateInstallmentBill;
    }

    private InstallmentBillEntity findEntityByCode(UUID code) {
        return installmentBillRepository.findByCodeAndFlagActive(code, Boolean.TRUE)
                .orElseThrow(() -> {
                    log.info("m=findByCode code={} error=not_found_exception", code);
                    return new RuntimeException("InstallmentBill NotFoundException by code=" + code);
                });
    }

    @Transactional
    public void delete(final UUID code) {
        log.info("m=delete code={}", code);
        try {
            final InstallmentBill installmentBill = findByCode(code);
            if (!installmentBill.isActive()) {
                log.info("m=delete code={} msg=installmentBill_already_deleted", code);
                return;
            }
            installmentBill.delete();
            installmentBillRepository.save(mapper.toEntity(installmentBill, installmentBill.getId()));
        } catch (RuntimeException ignored){
        }
    }
}

