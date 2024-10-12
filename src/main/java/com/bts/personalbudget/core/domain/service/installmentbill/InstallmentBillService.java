package com.bts.personalbudget.core.domain.service.installmentbill;

import com.bts.personalbudget.core.domain.enumerator.InstallmentBillStatus;
import com.bts.personalbudget.core.domain.enumerator.OperationType;
import com.bts.personalbudget.core.domain.exception.InstallmentBillAlreadyDeletedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
@Service
public class InstallmentBillService {

    private final InstallmentBillRepository repository;

    public void create(final InstallmentBill installmentBill) {
        log.info("m=create installmentBill={}", installmentBill);
        repository.save(installmentBill);
    }

    public List<InstallmentBill> findAll() {
        log.info("m=findAll");
        return repository.findAll();
    }

    public InstallmentBill findByCode(final UUID code) {
        log.info("m=findByCode code={}", code);
        return repository.findByCode(code);
    }

    public List<InstallmentBill> findByNextInstallmentDate(final LocalDate nextInstallmentDate) {
        log.info("m=findByNextInstallmentDate date={}", nextInstallmentDate);
        return repository.findAllByNextInstallmentDate(nextInstallmentDate, InstallmentBillStatus.PENDING);
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

}
