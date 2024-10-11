package com.bts.personalbudget.core.domain.service.installmentbill;

import com.bts.personalbudget.core.domain.enumerator.InstallmentBillStatus;
import com.bts.personalbudget.core.domain.enumerator.OperationType;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@EqualsAndHashCode
@ToString
@Getter
public class InstallmentBill {

    private UUID code;
    private OperationType operationType;
    private String description;
    private BigDecimal amount;
    private InstallmentBillStatus status;
    private LocalDate purchaseDate;
    private Integer installmentTotal;
    private Integer installmentCount;
    private LocalDate lastInstallmentDate;
    private LocalDate nextInstallmentDate;

    private static final int INITIAL_INSTALLMENT_COUNT = 0;
    private static final int DAYS_TO_NEXT_INSTALLMENT = 30;

    public InstallmentBill(UUID code, OperationType operationType, String description,
                           BigDecimal amount, InstallmentBillStatus status, LocalDate purchaseDate, Integer installmentTotal,
                           Integer installmentCount, LocalDate lastInstallmentDate, LocalDate nextInstallmentDate) {
        this.code = code;
        this.operationType = operationType;
        this.description = description;
        this.amount = amount;
        this.status = status;
        this.purchaseDate = purchaseDate;
        this.installmentTotal = installmentTotal;
        this.installmentCount = installmentCount;
        this.lastInstallmentDate = lastInstallmentDate;
        this.nextInstallmentDate = nextInstallmentDate;
        init(code);
    }

    private void init(final UUID code) {
        boolean isNew = code == null;
        if (isNew) {
            this.code = UUID.randomUUID();
            this.status = InstallmentBillStatus.PENDING;
            this.installmentCount = INITIAL_INSTALLMENT_COUNT;
            if (purchaseDate == null) {
                this.purchaseDate = LocalDate.now();
            }
            this.nextInstallmentDate = calculateNextInstallmentDate()
                    .orElseThrow(() -> new RuntimeException("next installment inaccessible"));
        }
        log.info("m=init isNew={} code={}", isNew, this.code);
    }

    protected void update(final OperationType operationType,
                          final String description,
                          final BigDecimal amount,
                          final LocalDate purchaseDate,
                          final Integer installmentTotal) {
        if (installmentCount > 0 &&
                (operationType != null || amount != null || installmentTotal != null)) {
            log.error("m=update msg=update_not_allowed code={}", code);
            throw new RuntimeException("Update not allowed, code=" + code);
        }
        this.operationType = operationType != null ? operationType : this.operationType;
        this.description = description != null ? description : this.description;
        this.amount = amount != null ? amount : this.amount;
        this.purchaseDate = purchaseDate != null ? purchaseDate : this.purchaseDate;
        this.installmentTotal = installmentTotal != null ? installmentTotal : this.installmentTotal;
    }

    protected void updateInstallment() {
        if (!containsNextInstallment()) {
            log.error("m=updateInstallment msg=does_not_contains_next_installment code={}", code);
            throw new RuntimeException("Installment concluded, code=" + code);
        }
        lastInstallmentDate = LocalDate.from(nextInstallmentDate);
        nextInstallmentDate = calculateNextInstallmentDate()
                .orElseThrow(() -> new RuntimeException("next installment inaccessible, code=" + code));
        installmentCount++;
    }

    protected void conclude() {
        if (status == InstallmentBillStatus.DONE) {
            log.info("m=conclude code={} msg=InstallmentBill_already_done", code);
            return;
        }
        status = InstallmentBillStatus.DONE;
        nextInstallmentDate = null;
    }

    protected Optional<LocalDate> calculateNextInstallmentDate() {
        if (!containsNextInstallment()) {
            log.info("m=calculateNextInstallmentDate msg=next_installment_inaccessible, code={}", code);
            return Optional.empty();
        }
        if (lastInstallmentDate == null) {
            return Optional.of(purchaseDate.plusDays(DAYS_TO_NEXT_INSTALLMENT));
        }
        return Optional.of(lastInstallmentDate.plusDays(DAYS_TO_NEXT_INSTALLMENT));
    }

    private boolean containsNextInstallment() {
        return installmentCount < installmentTotal;
    }

}
