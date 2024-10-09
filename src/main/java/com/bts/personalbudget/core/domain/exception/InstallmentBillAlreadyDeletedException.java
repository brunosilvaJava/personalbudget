package com.bts.personalbudget.core.domain.exception;

import java.util.UUID;
import lombok.Getter;

@Getter
public class InstallmentBillAlreadyDeletedException extends RuntimeException {

    private final UUID installmentBillCode;

    public InstallmentBillAlreadyDeletedException(final UUID installmentBillCode) {
        super("InstallmentBill already deleted - code: " + installmentBillCode);
        this.installmentBillCode = installmentBillCode;
    }
}

