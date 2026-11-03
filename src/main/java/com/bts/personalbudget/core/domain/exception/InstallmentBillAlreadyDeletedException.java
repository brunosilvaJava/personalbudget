package com.bts.personalbudget.core.domain.exception;

import jakarta.persistence.EntityNotFoundException;
import java.util.UUID;

/**
 * Extends EntityNotFoundException so that reading or updating a soft-deleted installment bill
 * answers 404, like an unknown code (see ControllerExceptionHandler). InstallmentBillService.delete
 * still catches it to keep DELETE idempotent.
 */
public class InstallmentBillAlreadyDeletedException extends EntityNotFoundException {

    public InstallmentBillAlreadyDeletedException(final UUID installmentBillCode) {
        super("InstallmentBill already deleted - code: " + installmentBillCode);
    }
}

