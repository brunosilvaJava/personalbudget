package com.bts.personalbudget.core.domain.exception;

import java.util.UUID;

public class InstallmentBillAlreadyDeletedException extends RuntimeException {

    public InstallmentBillAlreadyDeletedException(final UUID installmentBillCode) {
        super("InstallmentBill already deleted - code: " + installmentBillCode);
    }
}

